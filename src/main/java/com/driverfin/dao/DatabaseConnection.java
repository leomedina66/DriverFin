package com.driverfin.dao;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;

public class DatabaseConnection {
    private static final String URL_PREFIX = "jdbc:sqlite:";

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
        this.dbUrl = URL_PREFIX + new File(dir, "driverfin.db").getAbsolutePath();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /** Location of the database file. Throws IllegalStateException when the URL does not point to a file. */
    public Path getDatabasePath() {
        if (!dbUrl.startsWith(URL_PREFIX) || dbUrl.length() == URL_PREFIX.length()
                || dbUrl.contains(":memory:")) {
            throw new IllegalStateException("The database is not stored in a file: " + dbUrl);
        }
        return Path.of(dbUrl.substring(URL_PREFIX.length())).toAbsolutePath();
    }
}
