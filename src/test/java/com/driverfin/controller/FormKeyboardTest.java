package com.driverfin.controller;

import com.driverfin.util.FxTestSupport;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** FormKeyboard alone, with plain controls: the cases the real form cannot reach without a window. */
class FormKeyboardTest {

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    private static void press(Control target, KeyCode code) {
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    @Test
    void escOnlyClosesTheCalendarWhileItIsOpen() {
        FxTestSupport.onFxThread(() -> {
            TextField field = new TextField();
            Scene scene = new Scene(new VBox(field));
            int[] cancelled = {0};
            boolean[] calendarOpen = {true};
            FormKeyboard.install(scene, List.of(field), field, () -> { }, () -> cancelled[0]++, () -> calendarOpen[0]);

            press(field, KeyCode.ESCAPE);
            assertEquals(0, cancelled[0], "Esc with the calendar open must not cancel the form");

            calendarOpen[0] = false;
            press(field, KeyCode.ESCAPE);
            assertEquals(1, cancelled[0]);
        });
    }

    @Test
    void enterOnTheDateWithTheCalendarOpenIsLeftToTheCalendar() {
        FxTestSupport.onFxThread(() -> {
            DatePicker picker = new DatePicker();
            TextField next = new TextField();
            Scene scene = new Scene(new VBox(picker, next));
            boolean[] calendarOpen = {true};
            FormKeyboard.install(scene, List.of(picker, next), next, () -> { }, () -> { }, () -> calendarOpen[0]);
            picker.requestFocus();

            press(picker, KeyCode.ENTER);
            assertSame(picker, scene.getFocusOwner());

            calendarOpen[0] = false;
            press(picker, KeyCode.ENTER);
            assertSame(next, scene.getFocusOwner());
        });
    }

    @Test
    void altGrIsNotTreatedAsAltShortcut() {
        FxTestSupport.onFxThread(() -> {
            TextField field = new TextField();
            Scene scene = new Scene(new VBox(field));
            int[] saved = {0};
            FormKeyboard.install(scene, List.of(field), field, () -> saved[0]++, () -> { }, () -> false);

            // On Windows AltGr arrives as Ctrl+Alt.
            Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.S, false, true, true, false));
            assertEquals(0, saved[0]);
        });
    }
}
