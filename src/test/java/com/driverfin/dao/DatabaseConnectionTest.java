package com.driverfin.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConnectionTest {
    @TempDir
    Path tempDir;

    @Test
    void databasePathIsTheFileFromTheUrl() {
        Path file = tempDir.resolve("x.db");
        assertEquals(file.toAbsolutePath(), new DatabaseConnection("jdbc:sqlite:" + file).getDatabasePath());
    }

    @Test
    void inMemoryDatabaseHasNoPath() {
        assertThrows(IllegalStateException.class, () -> new DatabaseConnection("jdbc:sqlite::memory:").getDatabasePath());
    }
}
