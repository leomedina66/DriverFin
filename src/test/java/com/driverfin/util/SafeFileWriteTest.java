package com.driverfin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFileWriteTest {
    @TempDir
    Path tempDir;

    @Test
    void deleteTempRemovesTheFile() throws IOException {
        Path temp = Files.writeString(tempDir.resolve("a.tmp"), "x");

        SafeFileWrite.deleteTemp(temp);

        assertFalse(Files.exists(temp));
    }

    @Test
    void deleteTempNeverThrowsEvenWhenTheFileCannotBeRemoved() throws IOException {
        // A folder with a file inside cannot be deleted by deleteIfExists: it throws, and the helper must not.
        Path stuck = Files.createDirectory(tempDir.resolve("stuck.tmp"));
        Files.writeString(stuck.resolve("inner"), "x");

        assertDoesNotThrow(() -> SafeFileWrite.deleteTemp(stuck));
        assertTrue(Files.exists(stuck));
    }

    @Test
    void deleteTempIgnoresAFileThatDoesNotExist() {
        assertDoesNotThrow(() -> SafeFileWrite.deleteTemp(tempDir.resolve("missing.tmp")));
    }

    @Test
    void moveToAFolderThatDoesNotExistKeepsItsOwnExceptionAndIsNotReportedAsInUse() throws IOException {
        Path source = Files.writeString(tempDir.resolve("source.tmp"), "x");
        Path target = tempDir.resolve("no-such-folder").resolve("report.pdf");

        IOException error = assertThrows(IOException.class, () -> SafeFileWrite.moveReplacing(source, target));

        assertFalse(error instanceof TargetInUseException, error.toString());
    }

    @Test
    void siblingsOfTheDatabaseAreRefusedWithTheFixedMessage() throws IOException {
        Path database = Files.writeString(tempDir.resolve("driverfin.db"), "x");
        for (String name : new String[]{"driverfin.db-journal", "driverfin.db-wal", "driverfin.db-shm", "driverfin.db.bak-v0"}) {
            DatabaseTargetException error = assertThrows(DatabaseTargetException.class,
                    () -> SafeFileWrite.requireNotTheDatabase(tempDir.resolve(name), database), name);
            assertTrue(error.getMessage().equals(DatabaseTargetException.USER_MESSAGE));
        }
    }
}
