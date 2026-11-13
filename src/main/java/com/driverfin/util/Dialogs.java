package com.driverfin.util;

import javafx.scene.control.Alert;

/** One place to show simple message dialogs with the app style. */
public final class Dialogs {
    private Dialogs() {}

    public static void show(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Styles.apply(alert.getDialogPane());
        alert.showAndWait();
    }
}
