package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keys of the real form (UX 8, criteria 21 and 23), sent as synthetic key events. No window is opened. */
class RecordFormKeyboardTest {
    private static final List<String> ENTER_CHAIN = List.of("datePicker", "timeStartField", "timeEndField",
        "kmStartField", "kmEndField", "earnUberField", "earn99Field", "earnOthersField", "costFuelField",
        "costFoodOtherField");

    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "keys.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private RecordFormFixture newForm() {
        return new RecordFormFixture(dao, null);
    }

    @Test
    void enterMovesToTheNextFieldInTabOrder() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            for (int i = 0; i < ENTER_CHAIN.size() - 1; i++) {
                Node current = form.focus(ENTER_CHAIN.get(i));
                boolean notConsumed = form.press(current, KeyCode.ENTER);

                assertFalse(notConsumed, "Enter on " + ENTER_CHAIN.get(i) + " is used by the form");
                assertSame(form.node(ENTER_CHAIN.get(i + 1)), form.scene.getFocusOwner(),
                    "Enter on " + ENTER_CHAIN.get(i));
            }
            assertFalse(form.controller.isSaved(), "Enter before the last value field never saves");
        });
    }

    @Test
    void enterOnTheDateCommitsTheTypedTextBeforeMovingOn() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.datePicker().getEditor().setText("5/3/2026");
            form.press(form.focus("datePicker"), KeyCode.ENTER);

            assertEquals(java.time.LocalDate.of(2026, 3, 5), form.datePicker().getValue());
            assertSame(form.field("timeStartField"), form.scene.getFocusOwner());
        });
    }

    @Test
    void enterOnTheLastValueFieldSaves() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.typeValidShift();
            form.press(form.focus("costFoodOtherField"), KeyCode.ENTER);

            assertTrue(form.controller.isSaved());
        });
    }

    @Test
    void enterOnTheLastValueFieldWithErrorsDoesNotSave() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.press(form.focus("costFoodOtherField"), KeyCode.ENTER);

            assertFalse(form.controller.isSaved());
            assertSame(form.field("timeStartField"), form.scene.getFocusOwner());
        });
    }

    @Test
    void enterDoesNotWalkOverHalfWrittenMinutes() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            FxTestSupport.type(form.field("timeStartField"), "083");
            assertEquals("08:3", form.field("timeStartField").getText());

            form.press(form.focus("timeStartField"), KeyCode.ENTER);

            assertSame(form.field("timeStartField"), form.scene.getFocusOwner(), "focus stays in the time field");
            assertEquals("08:3", form.field("timeStartField").getText(), "the text is not guessed");
            assertEquals(FieldFeedback.ERROR_MARK + FormMessages.TIME_INCOMPLETE,
                form.label("timeStartError").getText());

            FxTestSupport.type(form.field("timeStartField"), "0");
            form.press(form.focus("timeStartField"), KeyCode.ENTER);

            assertSame(form.field("timeEndField"), form.scene.getFocusOwner(), "complete minutes let Enter go on");
        });
    }

    @Test
    void enterOnTheLastValueFieldDoesNotSaveWhileMinutesAreHalfWritten() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.typeValidShift();
            for (int digit = 0; digit < 2; digit++) FxTestSupport.backspace(form.field("timeEndField"));
            FxTestSupport.type(form.field("timeEndField"), "3");
            assertEquals("12:3", form.field("timeEndField").getText());

            form.press(form.focus("timeEndField"), KeyCode.ENTER);

            assertFalse(form.controller.isSaved());
            assertSame(form.field("timeEndField"), form.scene.getFocusOwner());
        });
    }

    @Test
    void enterInTheNotesAreaIsLeftToTheTextAreaAndMovesNothing() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.typeValidShift();
            Node notes = form.focus("notesArea");

            form.press(notes, KeyCode.ENTER);

            // The TextArea (its skin exists now that the form content is laid out) takes Enter as a line break.
            assertTrue(form.notes().getText().contains("\n"), "Enter must stay a line break in the notes");
            assertSame(notes, form.scene.getFocusOwner());
            assertFalse(form.controller.isSaved());
        });
    }

    @Test
    void ctrlEnterSavesFromAnywhereEvenInTheNotesArea() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.typeValidShift();
            form.notes().setText("tudo certo");
            boolean notConsumed = form.press(form.focus("notesArea"), KeyCode.ENTER, true, false);

            assertFalse(notConsumed);
            assertTrue(form.controller.isSaved());
            assertEquals("tudo certo", form.controller.getSavedShift().orElseThrow().getNotes());
        });
    }

    @Test
    void ctrlEnterFromATimeFieldSavesToo() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.typeValidShift();
            form.press(form.focus("timeStartField"), KeyCode.ENTER, true, false);

            assertTrue(form.controller.isSaved());
        });
    }

    @Test
    void escWithoutChangesClosesWithoutAsking() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.press(form.focus("timeStartField"), KeyCode.ESCAPE);

            assertEquals(1, form.closed);
            assertEquals(0, form.questionsAsked);
        });
    }

    @Test
    void escAfterAChangeAsksAndStaysOpenWhenTheAnswerIsKeepEditing() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            FxTestSupport.type(form.field("timeStartField"), "0800");
            form.discardAnswer = false;

            form.press(form.focus("timeStartField"), KeyCode.ESCAPE);

            assertEquals(1, form.questionsAsked);
            assertEquals(0, form.closed);
            assertEquals("08:00", form.field("timeStartField").getText(), "nothing is lost");
        });
    }

    @Test
    void escAfterAChangeClosesWhenTheUserDiscards() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.notes().setText("mudei");
            form.discardAnswer = true;

            form.press(form.focus("notesArea"), KeyCode.ESCAPE);

            assertEquals(1, form.questionsAsked);
            assertEquals(1, form.closed);
        });
    }

    @Test
    void changingAndRestoringTheTextCountsAsNoChange() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.notes().setText("a");
            form.notes().setText("");

            form.cancel().fire();

            assertEquals(0, form.questionsAsked);
            assertEquals(1, form.closed);
        });
    }

    @Test
    void everyKindOfFieldCountsAsChanged() {
        for (String id : List.of("timeStartField", "timeEndField", "kmStartField", "kmEndField", "earnUberField",
                "earn99Field", "earnOthersField", "costFuelField", "costFoodOtherField", "notesArea", "datePicker")) {
            FxTestSupport.onFxThread(() -> {
                RecordFormFixture form = newForm();
                switch (id) {
                    case "notesArea" -> form.notes().setText("x");
                    case "datePicker" -> form.datePicker().setValue(java.time.LocalDate.of(2020, 1, 1));
                    case "earnUberField", "earn99Field", "earnOthersField", "costFuelField", "costFoodOtherField" ->
                        FxTestSupport.type(form.field(id), "5");
                    default -> FxTestSupport.type(form.field(id), "1");
                }
                form.cancel().fire();
                assertEquals(1, form.questionsAsked, id);
            });
        }
    }

    @Test
    void cancelButtonAsksTheSameQuestionAsEsc() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            FxTestSupport.type(form.field("kmEndField"), "12");

            form.cancel().fire();

            assertEquals(1, form.questionsAsked);
            assertEquals(0, form.closed);
        });
    }

    @Test
    void enterOnAFocusedButtonActivatesIt() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.press(form.focus("btnCancel"), KeyCode.ENTER);
            assertEquals(1, form.closed, "Enter on Cancelar cancels");

            RecordFormFixture other = newForm();
            other.typeValidShift();
            other.press(other.focus("btnSave"), KeyCode.ENTER);
            assertTrue(other.controller.isSaved(), "Enter on Salvar Turno saves");
        });
    }

    @Test
    void altSSavesAndAltCCancels() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            form.typeValidShift();
            form.press(form.focus("kmEndField"), KeyCode.S, false, true);
            assertTrue(form.controller.isSaved());

            RecordFormFixture other = newForm();
            other.press(other.focus("timeStartField"), KeyCode.C, false, true);
            assertEquals(1, other.closed);
        });
    }

    @Test
    void windowCloseRequestAsksLikeCancel() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = newForm();
            Stage stage = new Stage();
            stage.setScene(form.scene);

            WindowEvent untouched = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(untouched);
            assertFalse(untouched.isConsumed(), "nothing changed: the window may close");
            assertEquals(0, form.questionsAsked);

            FxTestSupport.type(form.field("timeStartField"), "0800");
            form.discardAnswer = false;
            WindowEvent changed = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(changed);
            assertTrue(changed.isConsumed(), "the user chose to keep editing: the window stays");
            assertEquals(1, form.questionsAsked);

            form.discardAnswer = true;
            WindowEvent discarded = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
            stage.getOnCloseRequest().handle(discarded);
            assertFalse(discarded.isConsumed());
        });
    }
}
