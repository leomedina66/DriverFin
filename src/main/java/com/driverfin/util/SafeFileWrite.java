package com.driverfin.util;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The write pattern of backups and reports: the new file is written beside the target under a temporary name and
 * moved over it at the end, so an existing file is replaced only by a complete one. It never touches the database file.
 */
public final class SafeFileWrite {
    private static final Logger LOGGER = Logger.getLogger(SafeFileWrite.class.getName());
    private static final String TEMP_SUFFIX = ".tmp";

    private SafeFileWrite() {}

    /**
     * @throws DatabaseTargetException when the target is the database itself, or a file beside it whose name starts
     *                                 with the database file name (driverfin.db-journal, -wal, -shm, .bak-v0 ...)
     */
    public static void requireNotTheDatabase(Path target, Path databaseFile) throws IOException {
        if (Files.exists(target) && Files.isSameFile(target, databaseFile)) {
            throw new DatabaseTargetException();
        }
        if (isDatabaseSibling(target, databaseFile)) {
            throw new DatabaseTargetException();
        }
    }

    private static boolean isDatabaseSibling(Path target, Path databaseFile) throws IOException {
        Path targetFolder = target.toAbsolutePath().getParent();
        Path databaseFolder = databaseFile.toAbsolutePath().getParent();
        if (targetFolder == null || databaseFolder == null
                || !Files.exists(targetFolder) || !Files.exists(databaseFolder)
                || !Files.isSameFile(targetFolder, databaseFolder)) {
            return false;
        }
        // File names are compared without case: Windows and macOS see "DriverFin.DB-wal" as the same file.
        String targetName = target.getFileName().toString().toLowerCase(Locale.ROOT);
        String databaseName = databaseFile.getFileName().toString().toLowerCase(Locale.ROOT);
        return targetName.startsWith(databaseName);
    }

    /** A free temporary name in the folder of the target (hidden, unique). */
    public static Path tempSibling(Path target) {
        return target.resolveSibling("." + target.getFileName() + "." + UUID.randomUUID() + TEMP_SUFFIX);
    }

    /**
     * Moves the finished file over the target.
     *
     * @throws TargetInUseException when the target cannot be replaced because it is open in another program
     *                              or the user has no right to replace it
     */
    public static void moveReplacing(Path source, Path target) throws IOException {
        try {
            move(source, target);
        } catch (FileSystemException e) {
            // NoSuchFile, NotDirectory and the like are other problems: only "blocked" ones get the "close it" text.
            if (e instanceof AccessDeniedException || e.getClass() == FileSystemException.class) {
                throw new TargetInUseException(e);
            }
            throw e;
        }
    }

    /**
     * Removes the temporary file in a {@code finally} block. A failure here is only logged: it must never replace
     * the exception that is already on its way to the user.
     */
    public static void deleteTemp(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "A temporary file could not be removed.", e);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
