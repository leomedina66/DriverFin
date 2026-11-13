package com.driverfin.dao;

import com.driverfin.util.ShiftDateRange;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates the schema and upgrades older databases.
 * Schema version is kept in SQLite's built-in "PRAGMA user_version":
 * 0 = unknown/legacy (money may be REAL in reais), 1 = money stored as INTEGER cents,
 * 2 = like 1, and the import of the old "driverfin.db" (daily_records) was already done.
 */
public class DatabaseInitializer {
    private static final Logger LOGGER = Logger.getLogger(DatabaseInitializer.class.getName());
    static final int CENTS_SCHEMA_VERSION = 1;
    static final int LEGACY_IMPORT_DONE_VERSION = 2;
    static final String BACKUP_SUFFIX = ".bak-v0";
    static final String OLD_DB_FILE_NAME = "driverfin.db";
    static final String MIGRATED_SUFFIX = ".migrated";

    private static final List<String> MONEY_COLUMNS = List.of(
        "uber_earnings", "app99_earnings", "other_earnings", "cost_fuel", "food_and_other_costs");

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS work_shifts (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
        "date TEXT NOT NULL," +
        "time_start TEXT NOT NULL," +
        "time_end TEXT NOT NULL," +
        "km_start REAL NOT NULL," +
        "km_end REAL NOT NULL," +
        "uber_earnings INTEGER NOT NULL DEFAULT 0," +
        "app99_earnings INTEGER NOT NULL DEFAULT 0," +
        "other_earnings INTEGER NOT NULL DEFAULT 0," +
        "cost_fuel INTEGER NOT NULL DEFAULT 0," +
        "food_and_other_costs INTEGER NOT NULL DEFAULT 0," +
        "notes TEXT" +
        ");";

    private static final String TIME_OK_SQL = "%1$s GLOB '[0-2][0-9]:[0-5][0-9]' AND strftime('%%H:%%M', %1$s) = %1$s";
    private static final List<String> OLD_MONEY_COLUMNS =
        List.of("earn_uber", "earn_99", "earn_others", "cost_fuel", "cost_food_other");
    private static final int MAX_OLD_MONEY_REAIS = 1_000_000;
    private static final long KM_LIMIT = 1_000_000_000L;
    private static final int MAX_NOTES_LENGTH = 500;

    /**
     * What an old row must look like to be imported: a real date from 2000 to one year ahead (yyyy-MM-dd; the limit
     * is the one {@code ?} of the statement, filled with the same rule as {@link ShiftDateRange}), times as HH:mm,
     * finite numbers for the KM (0 up to nine digits, the end not before the start) and for each money value
     * (0 to 1.000.000,00), notes of at most 500 characters.
     * Everything is a constant: no value from the file is ever put into the SQL text.
     */
    private static String validOldRowSql() {
        StringBuilder where = new StringBuilder(
            "date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' AND date(date) = date " +
            "AND date >= '2000-01-01' AND date <= ? AND ");
        where.append(String.format(TIME_OK_SQL, "time_start")).append(" AND ")
            .append(String.format(TIME_OK_SQL, "time_end"));
        for (String km : List.of("km_start", "km_end")) {
            where.append(" AND typeof(").append(km).append(") IN ('integer', 'real') AND ").append(km)
                .append(" >= 0 AND ").append(km).append(" < ").append(KM_LIMIT);
        }
        where.append(" AND km_end >= km_start");
        for (String money : OLD_MONEY_COLUMNS) {
            where.append(" AND typeof(COALESCE(").append(money).append(", 0)) IN ('integer', 'real')")
                .append(" AND COALESCE(").append(money).append(", 0) BETWEEN 0 AND ").append(MAX_OLD_MONEY_REAIS);
        }
        where.append(" AND (notes IS NULL OR (typeof(notes) = 'text' AND length(notes) <= ")
            .append(MAX_NOTES_LENGTH).append("))");
        return where.toString();
    }

    private static final String MIGRATE_OLD_DATA_SQL =
        "INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end, uber_earnings, app99_earnings, " +
        "other_earnings, cost_fuel, food_and_other_costs, notes) " +
        "SELECT date, time_start, time_end, km_start, km_end, " +
        "CAST(ROUND(COALESCE(earn_uber, 0) * 100) AS INTEGER), CAST(ROUND(COALESCE(earn_99, 0) * 100) AS INTEGER), " +
        "CAST(ROUND(COALESCE(earn_others, 0) * 100) AS INTEGER), CAST(ROUND(COALESCE(cost_fuel, 0) * 100) AS INTEGER), " +
        "CAST(ROUND(COALESCE(cost_food_other, 0) * 100) AS INTEGER), notes " +
        "FROM old_db.daily_records WHERE " + validOldRowSql();

    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final DatabaseConnection dbConnection;

    public DatabaseInitializer(DatabaseConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public void initDatabase() {
        try (Connection conn = dbConnection.getConnection()) {
            boolean needsConversion;
            int version;
            try (Statement stmt = conn.createStatement()) {
                boolean tableExisted = tableExists(stmt, "main", "work_shifts");
                version = readUserVersion(stmt);
                needsConversion = tableExisted && version < CENTS_SCHEMA_VERSION && hasLegacyRealMoney(stmt);
                if (needsConversion) {
                    backupBeforeConversion(stmt);
                }
            }

            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                if (needsConversion) {
                    convertRealReaisToCents(stmt);
                    LOGGER.info("Schema upgraded: money columns converted from reais to cents.");
                }
                stmt.execute(CREATE_TABLE_SQL);
                // never lower a newer version (e.g. 2 = legacy import done)
                stmt.execute("PRAGMA user_version = " + Math.max(version, CENTS_SCHEMA_VERSION));
                conn.commit();
                LOGGER.info("Database initialized successfully.");
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing SQLite database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Copies the whole database to "<name>.bak-v0" (never overwriting an older backup) before the
     * irreversible reais -> cents conversion. If the copy fails the conversion must not run.
     * Must be called outside a transaction (VACUUM INTO requires it).
     */
    private void backupBeforeConversion(Statement stmt) {
        try {
            Path dbFile = mainDatabaseFile(stmt);
            Path backup = freeBackupPath(dbFile);
            stmt.execute("VACUUM INTO '" + backup.toAbsolutePath().toString().replace("'", "''") + "'");
            LOGGER.info("Backup of the pre-conversion database created at " + backup.toAbsolutePath());
        } catch (SQLException | IOException e) {
            throw new IllegalStateException(
                "Could not back up the database before converting money to cents; conversion aborted, data untouched.", e);
        }
    }

    private Path mainDatabaseFile(Statement stmt) throws SQLException, IOException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA database_list")) {
            while (rs.next()) {
                String file = rs.getString("file");
                if ("main".equals(rs.getString("name")) && file != null && !file.isEmpty()) {
                    return Path.of(file);
                }
            }
        }
        throw new IOException("The database is not stored in a file, so it cannot be backed up.");
    }

    private Path freeBackupPath(Path dbFile) {
        String name = dbFile.getFileName() + BACKUP_SUFFIX;
        Path backup = dbFile.resolveSibling(name);
        if (Files.exists(backup)) {
            backup = dbFile.resolveSibling(name + "-" + LocalDateTime.now().format(BACKUP_TIMESTAMP));
        }
        return backup;
    }

    /**
     * Imports records from a "driverfin.db" in the current working directory, which is where the
     * first (pre-AppData) version of the app kept its data.
     */
    public void migrateOldData() {
        migrateOldData(new File(OLD_DB_FILE_NAME));
    }

    /**
     * Copies "daily_records" of the old database into work_shifts (reais -> cents) only when the import was
     * not already recorded (user_version 2), work_shifts is empty and the file really has the old table.
     * The "import done" marker is written in the same transaction as the copy; after success the file is
     * also renamed to "driverfin.db.migrated". The marker keeps deleted records from coming back even if
     * the rename fails.
     */
    public void migrateOldData(File oldDbFile) {
        if (!oldDbFile.isFile()) return;

        boolean migrated = false;
        try (Connection conn = dbConnection.getConnection()) {
            try (Statement safety = conn.createStatement()) {
                // The file comes from outside: its views, triggers and generated columns must not run functions.
                safety.execute("PRAGMA trusted_schema = OFF");
            }
            try (PreparedStatement attach = conn.prepareStatement("ATTACH DATABASE ? AS old_db")) {
                attach.setString(1, oldDbFile.getAbsolutePath());
                attach.execute();
            }
            try (Statement stmt = conn.createStatement()) {
                migrated = copyOldRecords(conn, stmt);
                stmt.execute("DETACH DATABASE old_db");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error during migration", e);
            return;
        }

        if (migrated) {
            renameToMigrated(oldDbFile);
        }
    }

    private boolean copyOldRecords(Connection conn, Statement stmt) throws SQLException {
        if (readUserVersion(stmt) >= LEGACY_IMPORT_DONE_VERSION) {
            LOGGER.info("Legacy import already recorded in the database; migration skipped.");
            return false;
        }
        if (!tableExists(stmt, "old_db", "daily_records")) return false;
        if (hasGeneratedOrHiddenColumn(stmt)) {
            LOGGER.warning("Old database found, but its daily_records table has generated or hidden columns; "
                + "migration refused and the file was left untouched.");
            return false;
        }
        if (!isEmpty(stmt, "work_shifts")) {
            LOGGER.warning("Old database found but work_shifts already has data; migration skipped.");
            return false;
        }
        int found = countRows(stmt, "old_db.daily_records");
        int imported;
        conn.setAutoCommit(false);
        try {
            imported = insertValidOldRows(conn);
            stmt.execute("PRAGMA user_version = " + LEGACY_IMPORT_DONE_VERSION);
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        LOGGER.info("Data migrated successfully.");
        if (imported < found) {
            LOGGER.log(Level.WARNING, "{0} old row(s) were ignored because their data was not valid.", found - imported);
        }
        return true;
    }

    private int insertValidOldRows(Connection conn) throws SQLException {
        try (PreparedStatement insert = conn.prepareStatement(MIGRATE_OLD_DATA_SQL)) {
            insert.setString(1, LocalDate.now().plusYears(ShiftDateRange.YEARS_AHEAD).toString());
            return insert.executeUpdate();
        }
    }

    /** A column that is computed (GENERATED ...) or hidden could run code of the old file on every read. */
    private boolean hasGeneratedOrHiddenColumn(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA old_db.table_xinfo(daily_records)")) {
            while (rs.next()) {
                if (rs.getInt("hidden") != 0) return true;
            }
        }
        return false;
    }

    private void renameToMigrated(File oldDbFile) {
        Path source = oldDbFile.toPath();
        Path target = source.resolveSibling(oldDbFile.getName() + MIGRATED_SUFFIX);
        try {
            Files.move(source, target);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Migrated, but could not rename old database file " + source, e);
        }
    }

    // schema is "main" or "old_db" (fixed internal names, never user input)
    private boolean tableExists(Statement stmt, String schema, String table) throws SQLException {
        String sql = "SELECT 1 FROM " + schema + ".sqlite_master WHERE type='table' AND name='" + table + "'";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    private boolean isEmpty(Statement stmt, String table) throws SQLException {
        return countRows(stmt, table) == 0;
    }

    // table is a fixed internal name, never user input
    private int countRows(Statement stmt, String table) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int readUserVersion(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Old databases declared the money columns as REAL (reais); new ones declare INTEGER (cents). */
    private boolean hasLegacyRealMoney(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(work_shifts)")) {
            while (rs.next()) {
                if ("uber_earnings".equals(rs.getString("name"))) {
                    return "REAL".equalsIgnoreCase(rs.getString("type"));
                }
            }
        }
        return false;
    }

    private void convertRealReaisToCents(Statement stmt) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE work_shifts SET ");
        for (int i = 0; i < MONEY_COLUMNS.size(); i++) {
            String col = MONEY_COLUMNS.get(i);
            if (i > 0) sql.append(", ");
            sql.append(col).append(" = CAST(ROUND(").append(col).append(" * 100) AS INTEGER)");
        }
        stmt.executeUpdate(sql.toString());
    }
}
