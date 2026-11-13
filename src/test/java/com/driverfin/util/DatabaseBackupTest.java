package com.driverfin.util;

import com.driverfin.dao.DatabaseConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupTest {
    @TempDir
    Path tempDir;

    private DatabaseConnection databaseWithRows(String fileName, String... values) throws SQLException {
        DatabaseConnection database = new DatabaseConnection("jdbc:sqlite:" + tempDir.resolve(fileName));
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE item (name TEXT)");
            for (String value : values) {
                statement.execute("INSERT INTO item VALUES ('" + value + "')");
            }
        }
        return database;
    }

    private static List<String> readRows(Path databaseFile) throws SQLException {
        DatabaseConnection database = new DatabaseConnection("jdbc:sqlite:" + databaseFile);
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name FROM item ORDER BY name")) {
            List<String> names = new java.util.ArrayList<>();
            while (rows.next()) names.add(rows.getString(1));
            return names;
        }
    }

    private long fileCount() throws IOException {
        try (Stream<Path> files = Files.list(tempDir)) {
            return files.count();
        }
    }

    @Test
    void backupContainsAllRows() throws Exception {
        DatabaseConnection database = databaseWithRows("live.db", "a", "b");
        Path backup = tempDir.resolve("backup.db");

        DatabaseBackup.create(database, backup);

        assertEquals(List.of("a", "b"), readRows(backup));
        assertEquals(List.of("a", "b"), readRows(tempDir.resolve("live.db")));
    }

    @Test
    void backupToTheLiveDatabaseFileIsRefusedAndKeepsTheData() throws Exception {
        DatabaseConnection database = databaseWithRows("live.db", "a", "b");
        Path live = tempDir.resolve("live.db");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DatabaseBackup.create(database, live));

        assertTrue(error.getMessage().contains("outro arquivo"), error.getMessage());
        assertEquals(List.of("a", "b"), readRows(live));
    }

    @Test
    void backupToTheLiveFileThroughADifferentPathSpellingIsRefused() throws Exception {
        DatabaseConnection database = databaseWithRows("live.db", "a");
        Path sameFile = tempDir.resolve("sub").resolve("..").resolve("live.db");
        Files.createDirectories(tempDir.resolve("sub"));

        assertThrows(IllegalArgumentException.class, () -> DatabaseBackup.create(database, sameFile));
        assertEquals(List.of("a"), readRows(tempDir.resolve("live.db")));
    }

    @Test
    void backupToAFileBesideTheDatabaseThatStartsWithItsNameIsRefused() throws Exception {
        DatabaseConnection database = databaseWithRows("live.db", "a");
        for (String sibling : List.of("live.db-journal", "live.db-wal", "live.db-shm", "live.db.bak-v0", "LIVE.DB-WAL")) {
            Path target = tempDir.resolve(sibling);

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> DatabaseBackup.create(database, target), sibling);

            assertEquals(DatabaseTargetException.USER_MESSAGE, error.getMessage());
            assertTrue(Files.notExists(target), sibling + " must not be created");
        }
        assertEquals(List.of("a"), readRows(tempDir.resolve("live.db")));
    }

    @Test
    void backupWithAnotherNameBesideTheDatabaseOrTheSameNameInAnotherFolderIsAllowed() throws Exception {
        DatabaseConnection database = databaseWithRows("live.db", "a");
        Path elsewhere = Files.createDirectories(tempDir.resolve("other")).resolve("live.db");

        DatabaseBackup.create(database, tempDir.resolve("live-copy.db"));
        DatabaseBackup.create(database, elsewhere);

        assertEquals(List.of("a"), readRows(tempDir.resolve("live-copy.db")));
        assertEquals(List.of("a"), readRows(elsewhere));
    }

    @Test
    void existingBackupIsReplacedWithTheNewContent() throws Exception {
        Path backup = tempDir.resolve("backup.db");
        DatabaseBackup.create(databaseWithRows("old.db", "old"), backup);
        assertEquals(List.of("old"), readRows(backup));

        DatabaseBackup.create(databaseWithRows("new.db", "new1", "new2"), backup);

        assertEquals(List.of("new1", "new2"), readRows(backup));
    }

    @Test
    void failedBackupKeepsThePreviousBackupAndLeavesNoTemporaryFile() throws Exception {
        Path backup = tempDir.resolve("backup.db");
        DatabaseBackup.create(databaseWithRows("old.db", "old"), backup);
        // The database file does not exist as a valid SQLite database: VACUUM INTO must fail.
        Path broken = tempDir.resolve("broken.db");
        Files.writeString(broken, "this is not a sqlite database, just text that is long enough to be rejected");
        DatabaseConnection brokenDatabase = new DatabaseConnection("jdbc:sqlite:" + broken);
        long filesBefore = fileCount();

        assertThrows(SQLException.class, () -> DatabaseBackup.create(brokenDatabase, backup));

        assertEquals(List.of("old"), readRows(backup));
        assertEquals(filesBefore, fileCount());
    }
}
