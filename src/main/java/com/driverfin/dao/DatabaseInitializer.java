package com.driverfin.dao;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
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
        String createTableSql = "CREATE TABLE IF NOT EXISTS work_shifts (" +
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

        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            LOGGER.info("Database initialized successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error initializing SQLite database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public void migrateOldData() {
        File oldDbFile = new File("driverfin.db");
        if (!oldDbFile.exists()) return;
        
        String oldDbUrl = "jdbc:sqlite:" + oldDbFile.getAbsolutePath();
        String attachSql = "ATTACH DATABASE '" + oldDbFile.getAbsolutePath().replace("\\", "/") + "' AS old_db";
        String migrateSql = "INSERT INTO work_shifts (date, time_start, time_end, km_start, km_end, uber_earnings, app99_earnings, other_earnings, cost_fuel, food_and_other_costs, notes) " +
                            "SELECT date, time_start, time_end, km_start, km_end, " +
                            "CAST(earn_uber * 100 AS INTEGER), CAST(earn_99 * 100 AS INTEGER), CAST(earn_others * 100 AS INTEGER), " +
                            "CAST(cost_fuel * 100 AS INTEGER), CAST(cost_food_other * 100 AS INTEGER), notes " +
                            "FROM old_db.daily_records";
        
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(attachSql);
            // Check if old table exists
            try (ResultSet rs = stmt.executeQuery("SELECT name FROM old_db.sqlite_master WHERE type='table' AND name='daily_records'")) {
                if (rs.next()) {
                    // Check if already migrated
                    try (ResultSet rsDest = stmt.executeQuery("SELECT count(*) FROM work_shifts")) {
                        if (rsDest.next() && rsDest.getInt(1) == 0) {
                            stmt.execute(migrateSql);
                            LOGGER.info("Data migrated successfully.");
                        }
                    }
                }
            }
            stmt.execute("DETACH DATABASE old_db");
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error during migration", e);
        }
    }
}
