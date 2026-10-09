package com.driverfin.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;

public class DatabaseConnection {
    private final String dbUrl;

    public DatabaseConnection(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public DatabaseConnection() {
        // Default to %APPDATA%/DriverFin/driverfin.db
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            appData = System.getProperty("user.home");
        }
        File dir = new File(appData, "DriverFin");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + new File(dir, "driverfin.db").getAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }
}
