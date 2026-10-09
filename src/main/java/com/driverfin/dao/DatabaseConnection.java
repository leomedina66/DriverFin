package com.driverfin.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.driverfin.model.DailyRecord;

/**
 * Manages SQLite database connection and database initialization/seeding.
 */
public class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());
    private static final String DB_NAME = "driverfin.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_NAME;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "SQLite JDBC Driver not found in classpath.", e);
        }
    }

    private DatabaseConnection() {
        // Utility class
    }

    /**
     * Obtains a new connection to the SQLite database.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Initializes the database tables and seeds demo data if empty.
     */
    public static void initDatabase() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS daily_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                time_start TEXT NOT NULL,
                time_end TEXT NOT NULL,
                km_start REAL NOT NULL,
                km_end REAL NOT NULL,
                earn_uber REAL NOT NULL DEFAULT 0.0,
                earn_99 REAL NOT NULL DEFAULT 0.0,
                earn_others REAL NOT NULL DEFAULT 0.0,
                cost_fuel REAL NOT NULL DEFAULT 0.0,
                cost_food_other REAL NOT NULL DEFAULT 0.0,
                notes TEXT
            );
            """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            LOGGER.info("Database initialized successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing SQLite database", e);
            throw new RuntimeException("Database initialization failed", e);
        }

        seedDemoDataIfEmpty();
    }

    /**
     * Pre-populates the database with sample demo records if table is empty.
     * Uses a single transaction to ensure atomicity — all records are inserted
     * together or none at all.
     */
    public static void seedDemoDataIfEmpty() {
        DailyRecordDAO dao = new DailyRecordDAO();
        if (!dao.findAll().isEmpty()) {
            return;
        }

        LOGGER.info("No existing records found. Seeding sample demo data...");
        LocalDate today = LocalDate.now();

        DailyRecord rec1 = new DailyRecord(
            today.minusDays(4),
            LocalTime.of(7, 30),
            LocalTime.of(16, 45),
            12450.0,
            12630.0,
            210.50,
            145.00,
            30.00,
            85.00,
            25.00,
            "Quinta-feira movimentada no centro e aeroporto"
        );

        DailyRecord rec2 = new DailyRecord(
            today.minusDays(3),
            LocalTime.of(8, 0),
            LocalTime.of(17, 30),
            12630.0,
            12840.0,
            240.00,
            110.00,
            50.00,
            90.00,
            30.00,
            "Sexta-feira boa, várias viagens longas"
        );

        DailyRecord rec3 = new DailyRecord(
            today.minusDays(2),
            LocalTime.of(14, 0),
            LocalTime.of(23, 15),
            12840.0,
            13070.0,
            320.00,
            180.00,
            0.00,
            105.00,
            35.00,
            "Sábado à noite - alta demanda na zona sul"
        );

        DailyRecord rec4 = new DailyRecord(
            today.minusDays(1),
            LocalTime.of(15, 0),
            LocalTime.of(22, 0),
            13070.0,
            13220.0,
            190.00,
            95.00,
            20.00,
            70.00,
            20.00,
            "Domingo tranquilo"
        );

        // Insere todos os registros demo em uma única transação atômica
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                dao.saveWithConnection(conn, rec1);
                dao.saveWithConnection(conn, rec2);
                dao.saveWithConnection(conn, rec3);
                dao.saveWithConnection(conn, rec4);
                conn.commit();
                LOGGER.info("Demo data seeded successfully.");
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "Error seeding demo data, transaction rolled back", e);
                throw new RuntimeException("Failed to seed demo data", e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error obtaining connection for demo data seeding", e);
            throw new RuntimeException("Failed to seed demo data", e);
        }
    }
}
