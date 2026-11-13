package com.driverfin.controller;

import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MaintenanceRate;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.io.IOException;

/**
 * The real record-form.fxml with its controller and a Scene around it (no window). Closing and the discard
 * question are replaced by counters, because a dialog would block the test and a hidden window cannot be observed.
 * Every method must run on the JavaFX thread.
 */
class RecordFormFixture {
    private static final String FXML = "/com/driverfin/view/record-form.fxml";

    final RecordFormController controller;
    final Parent root;
    final Scene scene;

    /** How many times the form was asked to close, and how many times it asked "Descartar alterações?". */
    int closed;
    int questionsAsked;
    boolean discardAnswer;

    private boolean lastKeyReachedTheScene;

    RecordFormFixture(WorkShiftDAO dao, WorkShift shift) {
        this(dao, shift, 0);
    }

    /** With a scene of this height (0 = the natural size of the form): a short window makes the form scroll. */
    RecordFormFixture(WorkShiftDAO dao, WorkShift shift, double sceneHeight) {
        try {
            FXMLLoader loader = new FXMLLoader(RecordFormController.class.getResource(FXML));
            root = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            throw new AssertionError("record-form.fxml did not load", e);
        }
        controller.setCloser(() -> closed++);
        controller.setDiscardAsker(() -> {
            questionsAsked++;
            return discardAnswer;
        });
        controller.initData(shift, MaintenanceRate.DEFAULT, dao);
        scene = sceneHeight > 0 ? new Scene(root, root.prefWidth(-1), sceneHeight) : new Scene(root);
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> lastKeyReachedTheScene = true);
        // The ScrollPane puts its content in the scene graph only once its skin exists; lookups need that.
        root.applyCss();
        root.layout();
    }

    TextField field(String id) { return (TextField) root.lookup("#" + id); }
    TextArea notes() { return (TextArea) root.lookup("#notesArea"); }
    Label label(String id) { return (Label) root.lookup("#" + id); }
    DatePicker datePicker() { return (DatePicker) root.lookup("#datePicker"); }
    Button save() { return (Button) root.lookup("#btnSave"); }
    Button cancel() { return (Button) root.lookup("#btnCancel"); }
    Node node(String id) { return root.lookup("#" + id); }

    void typeValidShift() {
        FxTestSupport.type(field("timeStartField"), "0800");
        FxTestSupport.type(field("timeEndField"), "1200");
        FxTestSupport.type(field("kmStartField"), "100");
        FxTestSupport.type(field("kmEndField"), "200,5");
        FxTestSupport.type(field("earnUberField"), "123450");
    }

    /** Presses a key on the node like the keyboard would. Returns true when nobody consumed it on the way up. */
    boolean press(Node target, KeyCode code, boolean ctrl, boolean alt) {
        lastKeyReachedTheScene = false;
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, ctrl, alt, false));
        return lastKeyReachedTheScene;
    }

    boolean press(Node target, KeyCode code) {
        return press(target, code, false, false);
    }

    /** Gives the focus to the node and returns it, so a key can be sent to it next. */
    Node focus(String id) {
        Node node = node(id);
        node.requestFocus();
        return node;
    }
}
