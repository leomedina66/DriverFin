package com.driverfin.controller;

import javafx.fxml.FXML;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads the FXML as text (no JavaFX toolkit) and checks that ids and handlers exist in the controllers. */
class FxmlBindingTest {
    private static final Pattern FX_ID = Pattern.compile("fx:id=\"(\\w+)\"");
    private static final Pattern HANDLER = Pattern.compile("onAction=\"#(\\w+)\"");

    private static String read(String resource) throws IOException {
        try (InputStream in = FxmlBindingTest.class.getResourceAsStream(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> missingFields(String fxml, Class<?> controller) {
        List<String> missing = new ArrayList<>();
        Matcher ids = FX_ID.matcher(fxml);
        while (ids.find()) {
            String id = ids.group(1);
            boolean found = false;
            for (Field f : controller.getDeclaredFields()) {
                found |= f.getName().equals(id);
            }
            if (!found) missing.add(id);
        }
        return missing;
    }

    private static List<String> missingHandlers(String fxml, Class<?> controller) {
        List<String> missing = new ArrayList<>();
        Matcher handlers = HANDLER.matcher(fxml);
        while (handlers.find()) {
            String name = handlers.group(1);
            boolean found = false;
            for (Method m : controller.getDeclaredMethods()) {
                found |= m.getName().equals(name);
            }
            if (!found) missing.add(name);
        }
        return missing;
    }

    /** Reverse direction: every @FXML field of the controller must have an fx:id in the FXML (else it stays null). */
    private static List<String> fieldsWithoutFxId(String fxml, Class<?> controller) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = FX_ID.matcher(fxml);
        while (matcher.find()) ids.add(matcher.group(1));

        List<String> missing = new ArrayList<>();
        for (Field f : controller.getDeclaredFields()) {
            if (f.isAnnotationPresent(FXML.class) && !ids.contains(f.getName())) missing.add(f.getName());
        }
        return missing;
    }

    @Test
    void everyDashboardControllerFieldHasAnFxId() throws IOException {
        List<String> missing = fieldsWithoutFxId(read("/com/driverfin/view/dashboard.fxml"), DashboardController.class);
        assertTrue(missing.isEmpty(), "@FXML fields without fx:id: " + missing);
    }

    @Test
    void everyRecordFormControllerFieldHasAnFxId() throws IOException {
        List<String> missing = fieldsWithoutFxId(read("/com/driverfin/view/record-form.fxml"), RecordFormController.class);
        assertTrue(missing.isEmpty(), "@FXML fields without fx:id: " + missing);
    }

    @Test
    void dashboardFxmlMatchesController() throws IOException {
        String fxml = read("/com/driverfin/view/dashboard.fxml");
        // xAxis and yAxis are only referenced inside the FXML, so they are allowed to have no field.
        List<String> missing = missingFields(fxml, DashboardController.class);
        missing.removeAll(List.of("xAxis", "yAxis", "btnBackup", "btnPdfReport", "btnNewRecord"));
        assertTrue(missing.isEmpty(), "Missing @FXML fields: " + missing);
        assertTrue(missingHandlers(fxml, DashboardController.class).isEmpty());
    }

    @Test
    void recordFormFxmlMatchesController() throws IOException {
        String fxml = read("/com/driverfin/view/record-form.fxml");
        List<String> missing = missingFields(fxml, RecordFormController.class);
        missing.removeAll(List.of("btnSave", "btnCancel"));
        assertTrue(missing.isEmpty(), "Missing @FXML fields: " + missing);
        assertTrue(missingHandlers(fxml, RecordFormController.class).isEmpty());
    }
}
