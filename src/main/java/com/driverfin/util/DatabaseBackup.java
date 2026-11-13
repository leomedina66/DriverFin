package com.driverfin.util;

import com.driverfin.dao.DatabaseConnection;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Safe backup of the SQLite database: the old backup is replaced only after the new one is complete. */
public final class DatabaseBackup {

    private DatabaseBackup() {}

    /**
     * Writes a consistent copy of the database to {@code destination}.
     *
     * @throws DatabaseTargetException when the destination is the live database file itself
     */
    public static void create(DatabaseConnection database, Path destination) throws IOException, SQLException {
        Path target = destination.toAbsolutePath();
        SafeFileWrite.requireNotTheDatabase(target, database.getDatabasePath());

        // VACUUM INTO needs a file that does not exist yet, so it writes to a new temporary file first.
        Path temp = SafeFileWrite.tempSibling(target);
        try {
            try (Connection connection = database.getConnection();
                 PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
                statement.setString(1, temp.toString());
                statement.execute();
            }
            SafeFileWrite.moveReplacing(temp, target);
        } finally {
            SafeFileWrite.deleteTemp(temp);
        }
    }
}
