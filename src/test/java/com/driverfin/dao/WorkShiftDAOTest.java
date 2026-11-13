package com.driverfin.dao;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkShiftDAOTest {

    @TempDir
    File tempDir;

    private DatabaseConnection connectionTo(String fileName) {
        return new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, fileName).getAbsolutePath());
    }

    private WorkShift sampleShift(BigDecimal uber) {
        return new WorkShift(null, LocalDate.of(2024, 3, 5), LocalTime.of(8, 0), LocalTime.of(12, 0),
            100.0, 160.5, uber, new BigDecimal("0.10"), BigDecimal.ZERO,
            new BigDecimal("33.33"), BigDecimal.ZERO, "nota");
    }

    private int userVersion(DatabaseConnection db) throws Exception {
        try (Connection c = db.getConnection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    @Test
    public void centsRoundTrip() throws Exception {
        DatabaseConnection db = connectionTo("roundtrip.db");
        new DatabaseInitializer(db).initDatabase();
        WorkShiftDAO dao = new WorkShiftDAO(db);

        dao.save(sampleShift(new BigDecimal("19.99")));

        WorkShift loaded = dao.findAll().get(0);
        assertEquals(new BigDecimal("19.99"), loaded.getUberEarnings());
        assertEquals(new BigDecimal("0.10"), loaded.getApp99Earnings());
        assertEquals(new BigDecimal("33.33"), loaded.getCostFuel());
        assertEquals(1, userVersion(db));
    }

    @Test
    public void overflowFailsLoudly() throws Exception {
        DatabaseConnection db = connectionTo("overflow.db");
        new DatabaseInitializer(db).initDatabase();
        WorkShiftDAO dao = new WorkShiftDAO(db);

        assertThrows(ArithmeticException.class, () -> dao.save(sampleShift(new BigDecimal("1E+30"))));
    }

    @Test
    public void upgradesLegacyRealDatabaseToCents() throws Exception {
        DatabaseConnection db = connectionTo("legacy.db");
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE work_shifts (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, " +
                "time_start TEXT NOT NULL, time_end TEXT NOT NULL, km_start REAL NOT NULL, km_end REAL NOT NULL, " +
                "uber_earnings REAL NOT NULL DEFAULT 0, app99_earnings REAL NOT NULL DEFAULT 0, " +
                "other_earnings REAL NOT NULL DEFAULT 0, cost_fuel REAL NOT NULL DEFAULT 0, " +
                "food_and_other_costs REAL NOT NULL DEFAULT 0, notes TEXT)");
            s.execute("INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end, uber_earnings, " +
                "app99_earnings, other_earnings, cost_fuel, food_and_other_costs, notes) " +
                "VALUES ('2024-01-02', '08:00', '12:00', 10, 20, 150.5, 19.99, 0, 33.33, 1.1, 'x')");
        }
        DatabaseInitializer initializer = new DatabaseInitializer(db);

        initializer.initDatabase();
        initializer.initDatabase(); // second start must NOT multiply by 100 again

        WorkShift loaded = new WorkShiftDAO(db).findAll().get(0);
        assertEquals(new BigDecimal("150.50"), loaded.getUberEarnings());
        assertEquals(new BigDecimal("19.99"), loaded.getApp99Earnings());
        assertEquals(new BigDecimal("33.33"), loaded.getCostFuel());
        assertEquals(new BigDecimal("1.10"), loaded.getFoodAndOtherCosts());
        assertEquals(1, userVersion(db));
    }

    @Test
    public void migratesOldDailyRecordsFileAndRenamesIt() throws Exception {
        File oldFile = new File(tempDir, "driverfin.db");
        DatabaseConnection oldDb = new DatabaseConnection("jdbc:sqlite:" + oldFile.getAbsolutePath());
        try (Connection c = oldDb.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE daily_records (id INTEGER PRIMARY KEY, date TEXT, time_start TEXT, time_end TEXT, " +
                "km_start REAL, km_end REAL, earn_uber REAL, earn_99 REAL, earn_others REAL, cost_fuel REAL, " +
                "cost_food_other REAL, notes TEXT)");
            s.execute("INSERT INTO daily_records VALUES (1, '2024-01-02', '08:00', '12:00', 10, 20, " +
                "19.99, 150.5, 0, 33.33, 1.1, 'old')");
        }

        DatabaseConnection newDb = connectionTo("new.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();
        initializer.migrateOldData(oldFile);

        List<WorkShift> shifts = new WorkShiftDAO(newDb).findAll();
        assertEquals(1, shifts.size());
        assertEquals(new BigDecimal("19.99"), shifts.get(0).getUberEarnings());
        assertEquals(new BigDecimal("150.50"), shifts.get(0).getApp99Earnings());
        assertFalse(oldFile.exists());
        assertTrue(new File(tempDir, "driverfin.db.migrated").exists());
    }

    @Test
    public void ignoresFileWithoutDailyRecordsTable() throws Exception {
        File otherFile = new File(tempDir, "other.db");
        try (Connection c = new DatabaseConnection("jdbc:sqlite:" + otherFile.getAbsolutePath()).getConnection();
             PreparedStatement p = c.prepareStatement("CREATE TABLE something (id INTEGER)")) {
            p.execute();
        }
        DatabaseConnection newDb = connectionTo("new2.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();

        initializer.migrateOldData(otherFile);

        assertTrue(otherFile.exists());
        assertTrue(new WorkShiftDAO(newDb).findAll().isEmpty());
    }

    private static final String LEGACY_TABLE_SQL = "CREATE TABLE work_shifts (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "date TEXT NOT NULL, time_start TEXT NOT NULL, time_end TEXT NOT NULL, km_start REAL NOT NULL, " +
        "km_end REAL NOT NULL, uber_earnings %1$s NOT NULL DEFAULT 0, app99_earnings %1$s NOT NULL DEFAULT 0, " +
        "other_earnings %1$s NOT NULL DEFAULT 0, cost_fuel %1$s NOT NULL DEFAULT 0, " +
        "food_and_other_costs %1$s NOT NULL DEFAULT 0, notes TEXT)";

    private static final String OLD_ROW_OK = "(1, '2024-01-02', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'o')";

    private void createWorkShiftsWithOneRow(DatabaseConnection db, String moneyType, String uberValue) throws Exception {
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute(String.format(LEGACY_TABLE_SQL, moneyType));
            s.execute("INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end, uber_earnings, notes) " +
                "VALUES ('2024-01-02', '08:00', '12:00', 10, 20, " + uberValue + ", 'x')");
        }
    }

    private File createOldDailyRecordsFile(String name, String... rows) throws Exception {
        File oldFile = new File(tempDir, name);
        try (Connection c = new DatabaseConnection("jdbc:sqlite:" + oldFile.getAbsolutePath()).getConnection();
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE daily_records (id INTEGER PRIMARY KEY, date TEXT, time_start TEXT, time_end TEXT, " +
                "km_start REAL, km_end REAL, earn_uber REAL, earn_99 REAL, earn_others REAL, cost_fuel REAL, " +
                "cost_food_other REAL, notes TEXT)");
            for (String row : rows) {
                s.execute("INSERT INTO daily_records VALUES " + row);
            }
        }
        return oldFile;
    }

    private double rawUber(File dbFile) throws Exception {
        try (Connection c = new DatabaseConnection("jdbc:sqlite:" + dbFile.getAbsolutePath()).getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT uber_earnings FROM work_shifts")) {
            return rs.getDouble(1);
        }
    }

    private File[] backupsOf(String dbName) {
        return tempDir.listFiles((dir, name) -> name.startsWith(dbName + DatabaseInitializer.BACKUP_SUFFIX));
    }

    @Test
    public void legacyUpgradeCreatesBackupWithOriginalReaisAndSecondStartDoesNotRepeat() throws Exception {
        DatabaseConnection db = connectionTo("bk.db");
        createWorkShiftsWithOneRow(db, "REAL", "150.5");
        DatabaseInitializer initializer = new DatabaseInitializer(db);

        initializer.initDatabase();

        File[] backups = backupsOf("bk.db");
        assertEquals(1, backups.length);
        assertEquals("bk.db.bak-v0", backups[0].getName());
        assertEquals(150.5, rawUber(backups[0]), 0.0001);
        assertEquals(15050, rawUber(new File(tempDir, "bk.db")), 0.0001);

        initializer.initDatabase();

        assertEquals(1, backupsOf("bk.db").length);
        assertEquals(15050, rawUber(new File(tempDir, "bk.db")), 0.0001);
    }

    @Test
    public void existingBackupIsNeverOverwritten() throws Exception {
        DatabaseConnection db = connectionTo("keep.db");
        createWorkShiftsWithOneRow(db, "REAL", "10");
        File previousBackup = new File(tempDir, "keep.db.bak-v0");
        Files.writeString(previousBackup.toPath(), "precious");

        new DatabaseInitializer(db).initDatabase();

        assertEquals("precious", Files.readString(previousBackup.toPath()));
        assertEquals(2, backupsOf("keep.db").length);
    }

    @Test
    public void integerDeclaredDatabaseWithVersionZeroOnlyGetsVersionOneWithoutConversionOrBackup() throws Exception {
        DatabaseConnection db = connectionTo("int.db");
        createWorkShiftsWithOneRow(db, "INTEGER", "15050");

        new DatabaseInitializer(db).initDatabase();

        assertEquals(15050, rawUber(new File(tempDir, "int.db")), 0.0001);
        assertEquals(1, userVersion(db));
        assertEquals(0, backupsOf("int.db").length);
    }

    @Test
    public void importMarksVersionTwoAndInitDoesNotLowerIt() throws Exception {
        File oldFile = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK);
        DatabaseConnection newDb = connectionTo("marker.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();

        initializer.migrateOldData(oldFile);

        assertEquals(DatabaseInitializer.LEGACY_IMPORT_DONE_VERSION, userVersion(newDb));
        initializer.initDatabase();
        assertEquals(DatabaseInitializer.LEGACY_IMPORT_DONE_VERSION, userVersion(newDb));
    }

    @Test
    public void recordedImportIsNotRepeatedEvenWithEmptyTableAndOldFileStillPresent() throws Exception {
        File oldFile = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK);
        DatabaseConnection newDb = connectionTo("again.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();
        initializer.migrateOldData(oldFile);
        WorkShiftDAO dao = new WorkShiftDAO(newDb);
        dao.delete(dao.findAll().get(0).getId());
        assertTrue(dao.findAll().isEmpty());
        // simulates a locked file that could not be renamed: the old file is still there
        File restored = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK);

        initializer.migrateOldData(restored);

        assertTrue(dao.findAll().isEmpty());
        assertTrue(restored.exists());
    }

    @Test
    public void migrationSkippedAndFileKeptWhenWorkShiftsAlreadyHasData() throws Exception {
        File oldFile = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK);
        DatabaseConnection newDb = connectionTo("full.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();
        WorkShiftDAO dao = new WorkShiftDAO(newDb);
        dao.save(sampleShift(new BigDecimal("5.00")));

        initializer.migrateOldData(oldFile);

        assertEquals(1, dao.findAll().size());
        assertTrue(oldFile.exists());
        assertFalse(new File(tempDir, "driverfin.db.migrated").exists());
        assertEquals(1, userVersion(newDb));
    }

    @Test
    public void failureInTheMiddleOfTheCopyRollsBackEverything() throws Exception {
        // 2nd row has a note that a trigger of the new database rejects: the 1st row is inserted, then the insert fails
        File oldFile = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK,
            "(2, '2024-01-03', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'boom')");
        DatabaseConnection newDb = connectionTo("rb.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();
        try (Connection c = newDb.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TRIGGER reject_boom BEFORE INSERT ON work_shifts WHEN NEW.notes = 'boom' " +
                "BEGIN SELECT RAISE(ABORT, 'rejected'); END");
        }

        initializer.migrateOldData(oldFile);

        assertTrue(new WorkShiftDAO(newDb).findAll().isEmpty());
        assertEquals(1, userVersion(newDb));
        assertTrue(oldFile.exists());
        assertFalse(new File(tempDir, "driverfin.db.migrated").exists());
    }

    @Test
    public void importSkipsRowsWithInvalidDataAndKeepsTheValidOnes() throws Exception {
        File oldFile = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK,
            "(2, NULL, '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'null date')",
            "(3, '2024-13-45', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'impossible date')",
            "(4, '02/01/2024', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'wrong date format')",
            "(5, '2024-01-02', '8h', '12:00', 10, 20, 1, 2, 0, 3, 4, 'wrong time format')",
            "(6, '2024-01-02', '25:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'hour 25')",
            "(7, '2024-01-02', '08:00', '12:00', 10, 20, 9e999, 2, 0, 3, 4, 'infinite money')",
            "(8, '2024-01-02', '08:00', '12:00', 10, 20, 2000000, 2, 0, 3, 4, 'money above the ceiling')",
            "(9, '2024-01-02', '08:00', '12:00', 10, 20, -5, 2, 0, 3, 4, 'negative money')",
            "(10, '2024-01-02', '08:00', '12:00', 10, 9e999, 1, 2, 0, 3, 4, 'infinite km')",
            "(11, '2024-01-02', '08:00', '12:00', -1, 20, 1, 2, 0, 3, 4, 'negative km')",
            "(12, '1999-12-31', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'before 2000')",
            "(13, '2024-01-02', '08:00', '12:00', 10, 20, 'abc', 2, 0, 3, 4, 'text as money')");
        DatabaseConnection newDb = connectionTo("skip.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();

        initializer.migrateOldData(oldFile);

        List<WorkShift> shifts = new WorkShiftDAO(newDb).findAll();
        assertEquals(1, shifts.size());
        assertEquals("o", shifts.get(0).getNotes());
        assertEquals(DatabaseInitializer.LEGACY_IMPORT_DONE_VERSION, userVersion(newDb));
        assertTrue(new File(tempDir, "driverfin.db.migrated").exists());
    }

    @Test
    public void importSkipsLongNotesAndKmThatGoBackwardsAndKeepsGoodRows() throws Exception {
        File oldFile = createOldDailyRecordsFile("driverfin.db", OLD_ROW_OK,
            "(2, '2024-01-03', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, '" + "n".repeat(501) + "')",
            "(3, '2024-01-04', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, '" + "n".repeat(500) + "')",
            "(4, '2024-01-05', '08:00', '12:00', 50, 20, 1, 2, 0, 3, 4, 'km goes backwards')",
            "(5, '2024-01-06', '08:00', '12:00', 20, 20, 1, 2, 0, 3, 4, NULL)");
        DatabaseConnection newDb = connectionTo("limits.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();

        initializer.migrateOldData(oldFile);

        List<WorkShift> shifts = new WorkShiftDAO(newDb).findAll();
        assertEquals(3, shifts.size());
        assertTrue(shifts.stream().noneMatch(s -> "km goes backwards".equals(s.getNotes())));
        assertTrue(shifts.stream().noneMatch(s -> s.getNotes() != null && s.getNotes().length() > 500));
    }

    @Test
    public void importAcceptsTheLastAllowedDayAndSkipsTheNextOne() throws Exception {
        LocalDate lastAllowed = LocalDate.now().plusYears(1);
        File oldFile = createOldDailyRecordsFile("driverfin.db",
            "(1, '" + lastAllowed + "', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'last day')",
            "(2, '" + lastAllowed.plusDays(1) + "', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'too far')");
        DatabaseConnection newDb = connectionTo("future.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();

        initializer.migrateOldData(oldFile);

        List<WorkShift> shifts = new WorkShiftDAO(newDb).findAll();
        assertEquals(1, shifts.size());
        assertEquals("last day", shifts.get(0).getNotes());
    }

    @Test
    public void importIsRefusedWhenTheOldTableHasAGeneratedColumn() throws Exception {
        File oldFile = new File(tempDir, "driverfin.db");
        try (Connection c = new DatabaseConnection("jdbc:sqlite:" + oldFile.getAbsolutePath()).getConnection();
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE daily_records (id INTEGER PRIMARY KEY, date TEXT, time_start TEXT, time_end TEXT, " +
                "km_start REAL, km_end REAL, earn_uber REAL, earn_99 REAL, earn_others REAL, cost_fuel REAL, " +
                "cost_food_other REAL, notes TEXT, total REAL GENERATED ALWAYS AS (earn_uber + earn_99) VIRTUAL)");
            s.execute("INSERT INTO daily_records (id, date, time_start, time_end, km_start, km_end, earn_uber, " +
                "earn_99, earn_others, cost_fuel, cost_food_other, notes) " +
                "VALUES (1, '2024-01-02', '08:00', '12:00', 10, 20, 1, 2, 0, 3, 4, 'o')");
        }
        DatabaseConnection newDb = connectionTo("generated.db");
        DatabaseInitializer initializer = new DatabaseInitializer(newDb);
        initializer.initDatabase();

        initializer.migrateOldData(oldFile);

        assertTrue(new WorkShiftDAO(newDb).findAll().isEmpty());
        assertTrue(oldFile.exists(), "the old file must stay where it is");
        assertFalse(new File(tempDir, "driverfin.db.migrated").exists());
        assertEquals(1, userVersion(newDb));
    }

    @Test
    public void findAllSkipsARowWithAnUnreadableDateOrTimeAndKeepsTheOthers() throws Exception {
        DatabaseConnection db = connectionTo("damaged.db");
        new DatabaseInitializer(db).initDatabase();
        WorkShiftDAO dao = new WorkShiftDAO(db);
        dao.save(sampleShift(new BigDecimal("5.00")));
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end) " +
                "VALUES ('not a date', '08:00', '12:00', 1, 2)");
            s.execute("INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end) " +
                "VALUES ('2024-01-02', '8h', '12:00', 1, 2)");
        }

        List<WorkShift> shifts = dao.findAll();

        assertEquals(1, shifts.size());
        assertEquals(new BigDecimal("5.00"), shifts.get(0).getUberEarnings());
    }

    private WorkShift shiftAt(LocalDate date, LocalTime start, LocalTime end, double kmEnd) {
        return new WorkShift(null, date, start, end, 1.0, kmEnd, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    private WorkShiftDAO emptyDao(String fileName) throws Exception {
        DatabaseConnection db = connectionTo(fileName);
        new DatabaseInitializer(db).initDatabase();
        return new WorkShiftDAO(db);
    }

    @Test
    public void findLastShiftOnEmptyDatabaseIsEmpty() throws Exception {
        assertEquals(Optional.empty(), emptyDao("last-empty.db").findLastShift());
    }

    @Test
    public void findLastShiftPicksTheLatestDate() throws Exception {
        WorkShiftDAO dao = emptyDao("last-date.db");
        dao.save(shiftAt(LocalDate.of(2024, 3, 6), LocalTime.of(8, 0), LocalTime.of(12, 0), 200.0));
        dao.save(shiftAt(LocalDate.of(2024, 3, 5), LocalTime.of(8, 0), LocalTime.of(12, 0), 100.0));

        assertEquals(200.0, dao.findLastShift().orElseThrow().getKmEnd());
    }

    @Test
    public void findLastShiftPicksTheLatestStartOnTheSameDateEvenIfSavedFirst() throws Exception {
        WorkShiftDAO dao = emptyDao("last-time.db");
        LocalDate day = LocalDate.of(2024, 3, 5);
        dao.save(shiftAt(day, LocalTime.of(14, 0), LocalTime.of(18, 0), 300.0));
        dao.save(shiftAt(day, LocalTime.of(8, 0), LocalTime.of(12, 0), 150.0));

        assertEquals(LocalTime.of(14, 0), dao.findLastShift().orElseThrow().getTimeStart());
    }

    @Test
    public void findLastShiftPicksTheHighestIdOnTheSameDateAndTime() throws Exception {
        WorkShiftDAO dao = emptyDao("last-id.db");
        LocalDate day = LocalDate.of(2024, 3, 5);
        dao.save(shiftAt(day, LocalTime.of(8, 0), LocalTime.of(12, 0), 150.0));
        WorkShift saved = shiftAt(day, LocalTime.of(8, 0), LocalTime.of(12, 0), 160.0);
        dao.save(saved);

        WorkShift last = dao.findLastShift().orElseThrow();
        assertEquals(saved.getId(), last.getId());
        assertEquals(160.0, last.getKmEnd());
    }

    @Test
    public void findLastShiftUsesTheStartDateOfAShiftCrossingMidnight() throws Exception {
        WorkShiftDAO dao = emptyDao("last-midnight.db");
        LocalDate day = LocalDate.of(2024, 3, 5);
        dao.save(shiftAt(day, LocalTime.of(10, 0), LocalTime.of(14, 0), 150.0));
        dao.save(shiftAt(day, LocalTime.of(22, 0), LocalTime.of(2, 0), 250.0));

        assertEquals(LocalTime.of(22, 0), dao.findLastShift().orElseThrow().getTimeStart());
    }
}
