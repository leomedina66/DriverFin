package com.driverfin.util;

/**
 * The file chosen for a backup or a report is the database the app is using. It is an
 * IllegalArgumentException for old callers; the text is fixed, so it is safe to show on screen.
 */
public class DatabaseTargetException extends IllegalArgumentException {
    public static final String USER_MESSAGE =
        "Escolha outro arquivo: este é o banco de dados que o aplicativo está usando.";

    public DatabaseTargetException() {
        super(USER_MESSAGE);
    }
}
