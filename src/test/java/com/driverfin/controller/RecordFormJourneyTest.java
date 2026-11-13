package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MaintenanceRate;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole trips through the real record-form.fxml: real KEY_PRESSED / KEY_TYPED events (the skins handle the typing),
 * Enter and Ctrl+Enter, and what is left in the SQLite file afterwards.
 */
class RecordFormJourneyTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "journey.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    /** Synthetic keyboard and focus, shared with the hostile-input tests. */
    static final class Keys {
        private static final Method SET_FOCUSED = findSetFocused();

        private Keys() {}

        private static Method findSetFocused() {
            try {
                Method method = Node.class.getDeclaredMethod("setFocused", boolean.class);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Node.setFocused not reachable", e);
            }
        }

        /** Without a window nothing is "focused", so the focus-lost rules (tidy-up on exit) never run: flip it by hand. */
        static void setFocused(Node node, boolean focused) {
            try {
                SET_FOCUSED.invoke(node, focused);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        /** One key typed: pressed, typed (what the TextField skin turns into text) and released. */
        static void typeChar(Node target, char c) {
            String text = String.valueOf(c);
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, text, codeOf(c),
                    false, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_TYPED, text, "", KeyCode.UNDEFINED,
                    false, false, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, text, codeOf(c),
                    false, false, false, false));
        }

        static void typeText(Node target, String keys) {
            for (char c : keys.toCharArray()) typeChar(target, c);
        }

        static void key(Node target, KeyCode code, boolean ctrl) {
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code,
                    false, ctrl, false, false));
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.CHAR_UNDEFINED, "", code,
                    false, ctrl, false, false));
        }

        static void key(Node target, KeyCode code) {
            key(target, code, false);
        }

        private static KeyCode codeOf(char c) {
            if (c >= '0' && c <= '9') return KeyCode.valueOf("DIGIT" + c);
            if (c == ',') return KeyCode.COMMA;
            if (c == '.') return KeyCode.PERIOD;
            if (c == '-') return KeyCode.MINUS;
            if (c == ' ') return KeyCode.SPACE;
            if (Character.isLetter(c) && c < 128) return KeyCode.valueOf(String.valueOf(Character.toUpperCase(c)));
            return KeyCode.UNDEFINED;
        }

        /** Moves the focus like Tab or a click would, including the "arrived" rules (select all, caret at 0,00). */
        static void focus(RecordFormFixture form, Node node) {
            for (String id : FOCUSABLE) {
                Node other = form.node(id);
                if (other != node) setFocused(other, false);
            }
            node.requestFocus();
            setFocused(node, true);
            select(node);
        }

        /** After a key moved the focus by itself (Enter): copies the scene focus owner to the focused flags. */
        static void syncFocus(RecordFormFixture form) {
            Node owner = form.scene.getFocusOwner();
            for (String id : FOCUSABLE) {
                Node node = form.node(id);
                if (node != owner) setFocused(node, false);
            }
            if (owner != null && FOCUSABLE.contains(owner.getId())) {
                setFocused(owner, true);
                select(owner);
            }
        }

        static void enter(RecordFormFixture form) {
            Node owner = form.scene.getFocusOwner();
            key(owner, KeyCode.ENTER);
            syncFocus(form);
        }

        private static void select(Node node) {
            if (!(node instanceof TextInputControl text)) return;
            if (text.getText().equals("0,00")) text.positionCaret(text.getLength());
            else text.selectAll();
        }

        static final List<String> FOCUSABLE = List.of("timeStartField", "timeEndField", "kmStartField", "kmEndField",
                "earnUberField", "earn99Field", "earnOthersField", "costFuelField", "costFoodOtherField", "notesArea");
    }

    private final class Form extends RecordFormFixture {
        Form(WorkShift shift) {
            super(dao, shift);
            root.applyCss();
            root.layout();
        }

        /** Gives the focus to the field and types the keys one by one. */
        void typeInto(String id, String keys) {
            Keys.focus(this, node(id));
            Keys.typeText(node(id), keys);
        }

        void ctrlEnter() {
            Keys.key(scene.getFocusOwner() == null ? field("timeStartField") : scene.getFocusOwner(),
                    KeyCode.ENTER, true);
        }
    }

    private List<WorkShift> rows() {
        try {
            return dao.findAll();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private static String shown(BigDecimal money) {
        return CurrencyFormat.money(money).replace(' ', ' ');
    }

    // ---- journeys -----------------------------------------------------------------------------------------

    @Test
    void shouldSaveAWholeShiftTypedKeyByKeyWithEnterAndStoreExactCents() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.datePicker().setValue(DAY);

            form.typeInto("timeStartField", "0800");
            assertEquals("08:00", form.field("timeStartField").getText());
            Keys.enter(form);
            assertSame(form.field("timeEndField"), form.scene.getFocusOwner());

            Keys.typeText(form.field("timeEndField"), "1730");
            assertEquals("17:30", form.field("timeEndField").getText());
            Keys.enter(form);
            assertSame(form.field("kmStartField"), form.scene.getFocusOwner());

            Keys.typeText(form.field("kmStartField"), "12450");
            Keys.enter(form);
            assertEquals("12.450,0", form.field("kmStartField").getText());

            Keys.typeText(form.field("kmEndField"), "12610,5");
            Keys.enter(form);
            assertEquals("12.610,5", form.field("kmEndField").getText());

            Keys.typeText(form.field("earnUberField"), "15000");
            assertEquals("150,00", form.field("earnUberField").getText());
            Keys.enter(form);
            Keys.typeText(form.field("earn99Field"), "2550");
            Keys.enter(form);
            assertSame(form.field("earnOthersField"), form.scene.getFocusOwner());
            Keys.enter(form);
            Keys.typeText(form.field("costFuelField"), "4399");
            Keys.enter(form);
            assertSame(form.field("costFoodOtherField"), form.scene.getFocusOwner());
            Keys.typeText(form.field("costFoodOtherField"), "1250");

            assertTrue(rows().isEmpty(), "nothing is saved before the last Enter");
            Keys.enter(form);

            assertTrue(form.controller.isSaved());
            List<WorkShift> stored = rows();
            assertEquals(1, stored.size());
            WorkShift row = stored.get(0);
            assertEquals(DAY, row.getDate());
            assertEquals(LocalTime.of(8, 0), row.getTimeStart());
            assertEquals(LocalTime.of(17, 30), row.getTimeEnd());
            assertEquals(12450.0, row.getKmStart());
            assertEquals(12610.5, row.getKmEnd());
            assertEquals(new BigDecimal("150.00"), row.getUberEarnings());
            assertEquals(new BigDecimal("25.50"), row.getApp99Earnings());
            assertEquals(new BigDecimal("0.00"), row.getOtherEarnings());
            assertEquals(new BigDecimal("43.99"), row.getCostFuel());
            assertEquals(new BigDecimal("12.50"), row.getFoodAndOtherCosts());
        });
    }

    @Test
    void shouldReopenASavedShiftWithEveryFieldFilledAndSaveItUnchangedOnTheSameRow() {
        FxTestSupport.onFxThread(() -> {
            Form first = new Form(null);
            first.datePicker().setValue(DAY);
            first.typeInto("timeStartField", "0800");
            first.typeInto("timeEndField", "1730");
            first.typeInto("kmStartField", "12450");
            first.typeInto("kmEndField", "12610,5");
            first.typeInto("earnUberField", "15000");
            first.typeInto("earn99Field", "2550");
            first.typeInto("costFuelField", "4399");
            first.typeInto("costFoodOtherField", "1250");
            first.ctrlEnter();
            WorkShift saved = rows().get(0);

            Form edit = new Form(saved);
            assertEquals(FormMessages.TITLE_EDIT, edit.label("lblFormTitle").getText());
            assertEquals(DAY, edit.datePicker().getValue());
            assertEquals("08:00", edit.field("timeStartField").getText());
            assertEquals("17:30", edit.field("timeEndField").getText());
            assertEquals("12.450,0", edit.field("kmStartField").getText());
            assertEquals("12.610,5", edit.field("kmEndField").getText());
            assertEquals("150,00", edit.field("earnUberField").getText());
            assertEquals("25,50", edit.field("earn99Field").getText());
            assertEquals("0,00", edit.field("earnOthersField").getText());
            assertEquals("43,99", edit.field("costFuelField").getText());
            assertEquals("12,50", edit.field("costFoodOtherField").getText());
            assertEquals("R$ 175,50", edit.label("lblCalcGross").getText().replace(' ', ' '));
            assertEquals("R$ 56,49", edit.label("lblCalcCosts").getText().replace(' ', ' '));
            assertFalse(edit.label("kmStartHint").isVisible(), "editing never suggests the KM");

            edit.ctrlEnter();

            List<WorkShift> after = rows();
            assertEquals(1, after.size(), "saving an unchanged edit must not add a row");
            assertEquals(saved.getId(), after.get(0).getId());
            assertEquals(saved.getKmStart(), after.get(0).getKmStart());
            assertEquals(saved.getKmEnd(), after.get(0).getKmEnd());
            assertEquals(0, saved.getUberEarnings().compareTo(after.get(0).getUberEarnings()));
            assertEquals(0, saved.getCostFuel().compareTo(after.get(0).getCostFuel()));
            assertEquals(saved.getTimeEnd(), after.get(0).getTimeEnd());
        });
    }

    @Test
    void shouldKeepTheShiftIdAndChangeOnlyTheEditedFieldWhenEditingOneValue() {
        FxTestSupport.onFxThread(() -> {
            Form first = new Form(null);
            first.datePicker().setValue(DAY);
            first.typeInto("timeStartField", "0800");
            first.typeInto("timeEndField", "1200");
            first.typeInto("kmStartField", "100");
            first.typeInto("kmEndField", "200");
            first.typeInto("earnUberField", "10000");
            first.ctrlEnter();
            WorkShift saved = rows().get(0);

            Form edit = new Form(saved);
            Keys.focus(edit, edit.node("earnUberField"));
            Keys.typeText(edit.field("earnUberField"), "9999");   // select-all, so this replaces 100,00
            assertEquals("99,99", edit.field("earnUberField").getText());
            edit.ctrlEnter();

            List<WorkShift> after = rows();
            assertEquals(1, after.size());
            assertEquals(saved.getId(), after.get(0).getId());
            assertEquals(new BigDecimal("99.99"), after.get(0).getUberEarnings());
            assertEquals(LocalTime.of(8, 0), after.get(0).getTimeStart());
        });
    }

    // ---- save twice ---------------------------------------------------------------------------------------

    /**
     * Q-DOUBLE-SAVE: after a successful save the controller keeps accepting Enter / Ctrl+Enter and inserts the same
     * new shift again (currentShift stays null, so the id is null again). Only the window being hidden protects it.
     */
    @Test
    void shouldWriteOneRowWhenCtrlEnterIsPressedTwiceInARow() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeValidShift();
            Keys.key(form.field("timeStartField"), KeyCode.ENTER, true);
            Keys.key(form.field("timeStartField"), KeyCode.ENTER, true);
            assertEquals(1, rows().size(), "a second Ctrl+Enter after the save wrote the shift again");
        });
    }

    @Test
    void shouldWriteOneRowWhenEnterOnTheLastValueFieldIsPressedTwiceInARow() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeValidShift();
            form.press(form.focus("costFoodOtherField"), KeyCode.ENTER);
            form.press(form.field("costFoodOtherField"), KeyCode.ENTER);
            assertEquals(1, rows().size(), "a second Enter after the save wrote the shift again");
        });
    }

    @Test
    void shouldWriteOneRowWhenTheSaveButtonIsFiredTwiceInARow() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeValidShift();
            form.save().fire();
            form.save().fire();
            assertEquals(1, rows().size());
        });
    }

    // ---- errors and recovery ------------------------------------------------------------------------------

    @Test
    void shouldShowFieldMessagesWithoutSavingThenSaveOnceTheUserFixesTheFields() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.datePicker().setValue(DAY);
            form.typeInto("timeStartField", "0800");
            form.typeInto("timeEndField", "173");           // minutes half written
            form.ctrlEnter();

            assertTrue(rows().isEmpty());
            assertTrue(form.label("timeEndError").isVisible());
            assertTrue(form.label("timeEndError").getText().contains(FormMessages.TIME_INCOMPLETE));
            assertTrue(form.label("kmStartError").getText().contains(FormMessages.KM_START_REQUIRED));
            assertTrue(form.label("kmEndError").getText().contains(FormMessages.KM_END_REQUIRED));
            assertSame(form.field("timeEndField"), form.scene.getFocusOwner(), "focus goes to the first wrong field");

            Keys.typeText(form.field("timeEndField"), "0");   // "17:30"
            assertFalse(form.label("timeEndError").isVisible(), "the message goes away while the user fixes it");
            form.typeInto("kmStartField", "10");
            form.typeInto("kmEndField", "20");
            form.ctrlEnter();

            assertEquals(1, rows().size());
            assertEquals(LocalTime.of(17, 30), rows().get(0).getTimeEnd());
            assertEquals(new BigDecimal("0.00"), rows().get(0).getUberEarnings());
        });
    }

    @Test
    void shouldRejectKmEndBelowKmStartUnderKmEndAndKeepTheTypedValue() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeInto("timeStartField", "0800");
            form.typeInto("timeEndField", "1200");
            form.typeInto("kmStartField", "500");
            form.typeInto("kmEndField", "499,9");
            form.ctrlEnter();

            assertTrue(rows().isEmpty());
            assertTrue(form.label("kmEndError").getText().contains(FormMessages.kmEndBelowStart("500,0")));
            assertSame(form.field("kmEndField"), form.scene.getFocusOwner());
            assertEquals("499,9", form.field("kmEndField").getText(), "the typed value is kept");
        });
    }

    @Test
    void shouldAcceptKmEndEqualToKmStartWithoutAnyMessage() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeInto("timeStartField", "0800");
            form.typeInto("timeEndField", "1200");
            form.typeInto("kmStartField", "500");
            form.typeInto("kmEndField", "500");
            form.ctrlEnter();

            assertEquals(1, rows().size());
            assertFalse(form.label("kmEndError").isVisible());
            assertEquals(500.0, rows().get(0).getKmEnd());
        });
    }

    // ---- times --------------------------------------------------------------------------------------------

    @Test
    void shouldSaveAShiftThatCrossesMidnightWithTheRightHours() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.datePicker().setValue(DAY);
            form.typeInto("timeStartField", "2200");
            form.typeInto("timeEndField", "0200");
            form.typeInto("kmStartField", "100");
            form.typeInto("kmEndField", "140");
            form.typeInto("earnUberField", "10000");

            assertTrue(form.label("turnNote").getText().contains(FormMessages.crossesMidnight("4,0")));
            assertEquals("4,0 h", form.label("lblCalcHours").getText());
            form.ctrlEnter();

            WorkShift row = rows().get(0);
            assertEquals(DAY, row.getDate(), "the shift belongs to the day it started");
            assertEquals(4.0, row.calculateHours());
        });
    }

    @Test
    void shouldSaveAShiftWithEqualTimesAndShowOnlyTheNoteAndNoRateWarning() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeInto("timeStartField", "0900");
            form.typeInto("timeEndField", "0900");
            form.typeInto("kmStartField", "100");
            form.typeInto("kmEndField", "100");
            form.typeInto("earnUberField", "10000");

            assertTrue(form.label("turnNote").getText().contains(FormMessages.EQUAL_TIMES));
            assertFalse(form.label("rateWarn").isVisible(), "0 hours: no division, no warning");
            form.ctrlEnter();

            assertEquals(1, rows().size());
            assertEquals(0.0, rows().get(0).calculateHours());
        });
    }

    // ---- warnings never block -----------------------------------------------------------------------------

    @Test
    void shouldSaveWithHighValueAndUnusualRateWarningsVisible() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeInto("timeStartField", "0800");
            form.typeInto("timeEndField", "0900");
            form.typeInto("kmStartField", "100");
            form.typeInto("kmEndField", "110");
            form.typeInto("earnUberField", "500000");      // R$ 5.000,00 in 1 hour

            assertTrue(form.label("earnUberMsg").isVisible());
            assertTrue(form.label("earnUberMsg").getText().contains("R$"));
            assertTrue(form.label("rateWarn").isVisible());
            form.ctrlEnter();

            assertEquals(1, rows().size());
            assertEquals(new BigDecimal("5000.00"), rows().get(0).getUberEarnings());
        });
    }

    @Test
    void shouldNotWarnAtFourNinetyNineNinetyNineAndWarnAtFiveThousand() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            Keys.focus(form, form.node("earnUberField"));
            Keys.typeText(form.field("earnUberField"), "499999");
            assertFalse(form.label("earnUberMsg").isVisible(), "R$ 4.999,99 is below the limit");
            Keys.typeChar(form.field("earnUberField"), '9');
            assertEquals("49.999,99", form.field("earnUberField").getText());
            assertTrue(form.label("earnUberMsg").isVisible());
            Keys.key(form.field("earnUberField"), KeyCode.BACK_SPACE);
            assertEquals("4.999,99", form.field("earnUberField").getText());
            assertFalse(form.label("earnUberMsg").isVisible(), "the warning leaves when the value gets usual again");
        });
    }

    // ---- cancel -------------------------------------------------------------------------------------------

    @Test
    void shouldCloseWithoutAskingWhenNothingWasTypedAndAskWhenAKeyWasTyped() {
        FxTestSupport.onFxThread(() -> {
            Form untouched = new Form(null);
            Keys.key(untouched.field("timeStartField"), KeyCode.ESCAPE);
            assertEquals(1, untouched.closed);
            assertEquals(0, untouched.questionsAsked);

            Form touched = new Form(null);
            touched.typeInto("earnUberField", "1");
            Keys.key(touched.field("earnUberField"), KeyCode.ESCAPE);
            assertEquals(1, touched.questionsAsked);
            assertEquals(0, touched.closed, "answer was Continuar editando");
            touched.discardAnswer = true;
            Keys.key(touched.field("earnUberField"), KeyCode.ESCAPE);
            assertEquals(1, touched.closed);
            assertTrue(rows().isEmpty(), "cancel never writes");
        });
    }

    @Test
    void shouldTreatTypingAndErasingAMoneyDigitAsNoChange() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeInto("earnUberField", "7");
            Keys.key(form.field("earnUberField"), KeyCode.BACK_SPACE);
            assertEquals("0,00", form.field("earnUberField").getText());
            Keys.key(form.field("earnUberField"), KeyCode.ESCAPE);
            assertEquals(0, form.questionsAsked);
            assertEquals(1, form.closed);
        });
    }

    // ---- notes --------------------------------------------------------------------------------------------

    @Test
    void shouldKeepNotesWithAccentsAndLineBreaksAndRefuseMoreThanFiveHundredCharacters() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeValidShift();
            TextArea notes = form.notes();
            notes.setText("Trânsito intenso\nabastecimento ✓");
            form.ctrlEnter();
            assertEquals("Trânsito intenso\nabastecimento ✓", rows().get(0).getNotes());

            Form tooLong = new Form(null);
            tooLong.typeValidShift();
            tooLong.notes().setText("x".repeat(501));
            tooLong.ctrlEnter();
            assertEquals(1, rows().size(), "501 characters must not be saved");
            assertTrue(tooLong.label("notesError").getText().contains(FormMessages.notesTooLong(1)));
            assertSame(tooLong.notes(), tooLong.scene.getFocusOwner());

            tooLong.notes().setText("x".repeat(500));
            tooLong.ctrlEnter();
            assertEquals(2, rows().size(), "exactly 500 characters is fine");
        });
    }

    // ---- what the screen computes vs what is stored -------------------------------------------------------

    @Test
    void shouldShowInTheLiveCalculationWhatTheSavedShiftWillGive() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeInto("timeStartField", "0815");
            form.typeInto("timeEndField", "1745");
            form.typeInto("kmStartField", "1000");
            form.typeInto("kmEndField", "1123,4");
            form.typeInto("earnUberField", "12345");
            form.typeInto("earn99Field", "1");
            form.typeInto("costFuelField", "9999");
            String hoursShown = form.label("lblCalcHours").getText();
            String netShown = form.label("lblCalcNet").getText();
            form.ctrlEnter();

            WorkShift row = rows().get(0);
            assertEquals(CurrencyFormat.oneDecimal(row.calculateHours()) + " h", hoursShown);
            assertEquals(CurrencyFormat.money(row.calculateNetProfit(MaintenanceRate.DEFAULT)), netShown);
        });
    }

    @Test
    void shouldHandleTwoShiftsOnTheSameDayAndSuggestTheKmOfTheOneThatStartedLater() {
        FxTestSupport.onFxThread(() -> {
            Form morning = new Form(null);
            morning.datePicker().setValue(DAY);
            morning.typeInto("timeStartField", "0800");
            morning.typeInto("timeEndField", "1200");
            morning.typeInto("kmStartField", "100");
            morning.typeInto("kmEndField", "150");
            morning.ctrlEnter();

            Form evening = new Form(null);
            evening.datePicker().setValue(DAY);
            evening.typeInto("timeStartField", "1800");
            evening.typeInto("timeEndField", "2200");
            evening.typeInto("kmStartField", "150");
            evening.typeInto("kmEndField", "300");
            evening.ctrlEnter();

            // saved last but started earlier on the same day: must not win the suggestion
            Form lateEntry = new Form(null);
            lateEntry.datePicker().setValue(DAY);
            lateEntry.typeInto("timeStartField", "0600");
            lateEntry.typeInto("timeEndField", "0700");
            lateEntry.typeInto("kmStartField", "90");
            lateEntry.typeInto("kmEndField", "99");
            lateEntry.ctrlEnter();

            assertEquals(3, rows().size());
            Form next = new Form(null);
            assertEquals("300,0", next.field("kmStartField").getText());
            assertTrue(next.label("kmStartHint").isVisible());
        });
    }

    @Test
    void shouldNotShowTheSuggestionAsTypedKeysWhenTheFirstDigitReplacesIt() {
        FxTestSupport.onFxThread(() -> {
            Form first = new Form(null);
            first.typeValidShift();
            first.ctrlEnter();

            Form next = new Form(null);
            assertEquals("200,5", next.field("kmStartField").getText().replace(".", ""));
            TextField km = next.field("kmStartField");
            Keys.focus(next, km);          // selects the suggestion, like the real focus does
            Keys.typeChar(km, '7');
            assertEquals("7", km.getText(), "the first digit replaces the suggestion");
            assertFalse(next.label("kmStartHint").isVisible());
        });
    }
}
