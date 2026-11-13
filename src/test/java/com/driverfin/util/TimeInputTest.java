package com.driverfin.util;

import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real adapter on a TextField, without opening a window. Skipped when the toolkit cannot start. */
public class TimeInputTest {

    @BeforeAll
    static void toolkit() {
        FxTestSupport.assumeToolkit();
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    @Test
    public void typingDigitsShowsTheMaskedTime() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, m -> { });
            FxTestSupport.type(field, "0");
            assertEquals("0", field.getText());
            FxTestSupport.type(field, "8");
            assertEquals("08:", field.getText());
            FxTestSupport.type(field, "00");
            assertEquals("08:00", field.getText());
            assertEquals(5, field.getCaretPosition());
        });
    }

    @Test
    public void backspaceRemovesTheLastDigit() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, m -> { });
            FxTestSupport.type(field, "0830");
            FxTestSupport.backspace(field);
            assertEquals("08:3", field.getText());
            FxTestSupport.backspace(field);
            FxTestSupport.backspace(field);
            assertEquals("0", field.getText());
        });
    }

    @Test
    public void selectAllAndTypeStartsANewValue() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, m -> { });
            FxTestSupport.type(field, "0830");
            field.replaceText(0, field.getLength(), "1");
            assertEquals("1", field.getText());
            field.replaceText(0, field.getLength(), "");
            assertEquals("", field.getText());
        });
    }

    @Test
    public void pasteReplacesTheValue() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, m -> { });
            FxTestSupport.typeAtEnd(field, "8:30");
            assertEquals("08:30", field.getText());
            field.replaceText(0, field.getLength(), "0945");
            assertEquals("09:45", field.getText());
        });
    }

    @Test
    public void invalidPasteKeepsTextAndReportsError() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, messages::add);
            FxTestSupport.typeAtEnd(field, "25:00");
            assertEquals("", field.getText());
            assertEquals(FieldMessage.error(FormMessages.TIME_INVALID), messages.get(messages.size() - 1));
        });
    }

    @Test
    public void losingFocusCompletesTheTime() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, m -> { });
            field.setFocusedForTest(true);
            FxTestSupport.type(field, "08");
            assertEquals("08:", field.getText());
            field.setFocusedForTest(false);
            assertEquals("08:00", field.getText());
        });
    }

    @Test
    public void losingFocusWithHalfTypedMinutesKeepsTextAndShowsError() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, messages::add);
            field.setFocusedForTest(true);
            FxTestSupport.type(field, "083");
            field.setFocusedForTest(false);
            assertEquals("08:3", field.getText());
            assertEquals(FieldMessage.error(FormMessages.TIME_INCOMPLETE), messages.get(messages.size() - 1));
        });
    }

    @Test
    public void messageIsClearedOnTheNextKey() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, messages::add);
            FxTestSupport.type(field, "083");
            field.setFocusedForTest(true);
            field.setFocusedForTest(false);
            FxTestSupport.type(field, "0");
            assertNull(messages.get(messages.size() - 1));
        });
    }

    /** True when the key reaches the field's handlers, that is, no event filter consumed it. */
    private static boolean keyReachesField(FxTestSupport.TestField field, KeyCode code) {
        AtomicBoolean reached = new AtomicBoolean(false);
        EventHandler<KeyEvent> probe = event -> reached.set(true);
        field.addEventHandler(KeyEvent.KEY_PRESSED, probe);
        Event.fireEvent(field, key(code));
        field.removeEventHandler(KeyEvent.KEY_PRESSED, probe);
        return reached.get();
    }

    @Test
    public void tabAndEnterAreBlockedWhileMinutesAreIncomplete() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, messages::add);
            FxTestSupport.type(field, "083");

            assertFalse(keyReachesField(field, KeyCode.TAB));
            assertFalse(keyReachesField(field, KeyCode.ENTER));
            assertEquals(FieldMessage.error(FormMessages.TIME_INCOMPLETE), messages.get(messages.size() - 1));
        });
    }

    @Test
    public void tabPassesWhenTheTimeIsCompleteOrEmpty() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = new FxTestSupport.TestField();
            TimeInput.install(field, m -> { });
            assertTrue(keyReachesField(field, KeyCode.TAB));

            FxTestSupport.type(field, "0800");
            assertTrue(keyReachesField(field, KeyCode.TAB));
            assertTrue(keyReachesField(field, KeyCode.ENTER));
        });
    }
}
