package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * The real dashboard.fxml with its controller, built like MainApp does (a controller factory that hands over the DAO)
 * but on a temporary database and a temporary preferences node. A Scene is created around it and its CSS is applied,
 * so the table, the chart and the key filters exist; no window is opened. Run everything on the JavaFX thread.
 */
final class DashboardFixture {
    private static final String FXML = "/com/driverfin/view/dashboard.fxml";

    WorkShiftDAO dao;
    final Preferences prefs;
    private final DatabaseConnection db;
    /** What day the screen believes it is; a test can set another clock before open(). */
    Clock clock = Clock.systemDefaultZone();
    DashboardController controller;
    Scene scene;
    private boolean keyReachedTheScene;

    DashboardFixture(File tempDir, String name) {
        this(tempDir, name, WorkShiftDAO::new);
    }

    /** With another DAO over the same database, for example one that fails to read. */
    DashboardFixture(File tempDir, String name, Function<DatabaseConnection, WorkShiftDAO> daoFactory) {
        db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, name + ".db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = daoFactory.apply(db);
        prefs = Preferences.userRoot().node("driverfin-test-" + name);
    }

    /** Builds the screen on the data saved so far. */
    DashboardFixture open() {
        FXMLLoader loader = new FXMLLoader(DashboardController.class.getResource(FXML));
        loader.setControllerFactory(type -> {
            if (type == DashboardController.class) return new DashboardController(dao, db, prefs, clock);
            throw new IllegalStateException("Unexpected controller " + type);
        });
        try {
            Parent root = loader.load();
            controller = loader.getController();
            scene = new Scene(root);
            scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> keyReachedTheScene = true);
            // Creates the skins: the ScrollPane only puts its content in the scene graph after that.
            root.applyCss();
            root.layout();
        } catch (IOException e) {
            throw new AssertionError("dashboard.fxml did not load", e);
        }
        return this;
    }

    void cleanUp() {
        try {
            prefs.removeNode();
        } catch (BackingStoreException e) {
            throw new AssertionError("Could not remove the test preferences", e);
        }
    }

    WorkShift saveShift(LocalDate date, String uber, String fuel) {
        WorkShift shift = new WorkShift(null, date, LocalTime.of(8, 0), LocalTime.of(12, 0), 100.0, 160.0,
                new BigDecimal(uber), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(fuel), BigDecimal.ZERO, "");
        try {
            dao.save(shift);
        } catch (SQLException e) {
            throw new AssertionError("Could not save the test shift", e);
        }
        return shift;
    }

    @SuppressWarnings("unchecked")
    <T> T node(String fxId) {
        try {
            var field = DashboardController.class.getDeclaredField(fxId);
            field.setAccessible(true);
            return (T) field.get(controller);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("No @FXML field " + fxId, e);
        }
    }

    boolean visible(String fxId) {
        Node node = node(fxId);
        return node.isVisible() && node.isManaged();
    }

    /**
     * Presses a key on the node like the keyboard would. Returns true when nobody consumed it on the way up
     * (a consumed event never reaches the handler the fixture put on the Scene).
     */
    boolean press(Node target, KeyCode code, boolean ctrl) {
        keyReachedTheScene = false;
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, ctrl, false, false));
        return keyReachedTheScene;
    }
}
