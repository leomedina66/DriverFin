package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.util.FxTestSupport;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The keyboard-only trip through the form (UX section 8): Tab order, and getting out of the notes with Tab. */
class RecordFormTabOrderJourneyTest {
    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "tab.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private RecordFormFixture newForm() {
        RecordFormFixture form = new RecordFormFixture(dao, null);
        form.root.applyCss();
        form.root.layout();
        return form;
    }

    /** What the Tab key does in a Scene: JavaFX focus traversal, forward or backward. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void traverse(Scene scene, String direction) {
        try {
            Class directionType = Class.forName("com.sun.javafx.scene.traversal.Direction");
            Class methodType = Class.forName("com.sun.javafx.scene.traversal.TraversalMethod");
            Method traverse = Scene.class.getDeclaredMethod("traverse", Node.class, directionType, methodType);
            traverse.setAccessible(true);
            traverse.invoke(scene, scene.getFocusOwner(), Enum.valueOf(directionType, direction), Enum.valueOf(methodType, "KEY"));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("focus traversal not reachable", e);
        }
    }

    static String idOf(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n.getId() != null && !n.getId().isBlank()) return n.getId();
        }
        return String.valueOf(node);
    }

    @Test
    void shouldWalkTheFormInTheOrderOfTheUxDocumentWithTab() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.datePicker().getEditor().requestFocus();
            List<String> walked = new ArrayList<>(List.of(idOf(form.scene.getFocusOwner())));
            for (int i = 0; i < 12; i++) {
                traverse(form.scene, "NEXT");
                walked.add(idOf(form.scene.getFocusOwner()));
            }
            assertEquals(List.of("datePicker", "timeStartField", "timeEndField", "kmStartField", "kmEndField",
                    "earnUberField", "earn99Field", "earnOthersField", "costFuelField", "costFoodOtherField",
                    "notesArea", "btnCancel", "btnSave"), walked);
        });
    }

    @Test
    void shouldWalkBackwardsWithShiftTabInTheSameOrder() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.node("btnSave").requestFocus();
            List<String> walked = new ArrayList<>(List.of(idOf(form.scene.getFocusOwner())));
            for (int i = 0; i < 12; i++) {
                traverse(form.scene, "PREVIOUS");
                walked.add(idOf(form.scene.getFocusOwner()));
            }
            assertEquals(List.of("btnSave", "btnCancel", "notesArea", "costFoodOtherField", "costFuelField",
                    "earnOthersField", "earn99Field", "earnUberField", "kmEndField", "kmStartField", "timeEndField",
                    "timeStartField", "datePicker"), walked);
        });
    }

    /**
     * Q-TAB-NOTES: UX 2.5 says "to save use Tab until btnSave" from the notes. A JavaFX TextArea eats Tab and writes a
     * tab character instead, so the keyboard user is trapped in the notes (only Ctrl+Tab or Ctrl+Enter get out).
     */
    @Test
    void shouldLeaveTheNotesAreaWithTabInsteadOfWritingATabCharacter() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            TextArea notes = form.notes();
            RecordFormJourneyTest.Keys.focus(form, notes);
            RecordFormJourneyTest.Keys.typeText(notes, "abc");
            RecordFormJourneyTest.Keys.key(notes, KeyCode.TAB);
            assertEquals("abc", notes.getText(), "Tab must not become a character of the notes");
            assertSame(form.cancel(), form.scene.getFocusOwner(), "Tab from the notes goes to Cancelar");
        });
    }

    @Test
    void shouldNotTrapTheTextFieldsEitherWhenTabIsPressedInTheMiddleOfAValidValue() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            TextField time = form.field("timeStartField");
            RecordFormJourneyTest.Keys.focus(form, time);
            RecordFormJourneyTest.Keys.typeText(time, "0800");
            RecordFormJourneyTest.Keys.key(time, KeyCode.TAB);
            assertEquals("08:00", time.getText(), "Tab on a complete time is not blocked and changes nothing");
        });
    }
}
