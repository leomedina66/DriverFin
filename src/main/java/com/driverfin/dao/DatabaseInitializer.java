package com.driverfin.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseInitializer {
    private static final Logger LOGGER = Logger.getLogger(DatabaseInitializer.class.getName());
    private final DatabaseConnection dbConnection;

    public DatabaseInitializer(DatabaseConnection dbConnection) {
        this.dbConnection = dbConnection;
    }

    public void initDatabase() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS work_shifts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                time_start TEXT NOT NULL,
                time_end TEXT NOT NULL,
                km_start REAL NOT NULL,
                km_end REAL NOT NULL,
                uber_earnings REAL NOT NULL DEFAULT 0.0,
                app99_earnings REAL NOT NULL DEFAULT 0.0,
                other_earnings REAL NOT NULL DEFAULT 0.0,
                cost_fuel REAL NOT NULL DEFAULT 0.0,
                food_and_other_costs REAL NOT NULL DEFAULT 0.0,
                notes TEXT
            );
            """;

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            LOGGER.info("Database initialized successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing SQLite database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
}
