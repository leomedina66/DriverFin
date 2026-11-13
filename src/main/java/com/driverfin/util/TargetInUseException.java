package com.driverfin.util;

import java.io.IOException;
import java.nio.file.FileSystemException;

/**
 * The file chosen for a backup or a report cannot be replaced, usually because another program has it open.
 * The text is fixed, so it is safe to show on screen.
 */
public class TargetInUseException extends IOException {
    public static final String USER_MESSAGE =
        "Feche o arquivo, se estiver aberto em outro programa, e tente novamente.";

    public TargetInUseException(FileSystemException cause) {
        super(USER_MESSAGE, cause);
    }
}
