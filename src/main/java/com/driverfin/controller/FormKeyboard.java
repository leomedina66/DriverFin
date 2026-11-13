package com.driverfin.controller;

import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Keyboard of the shift form: Enter walks through the fields, Enter in the last value field and Ctrl+Enter save,
 * Esc cancels, Alt+S / Alt+C are the button shortcuts (the buttons' own mnemonics normally handle them; the filter
 * here is a fallback for when the mnemonic does not fire, for example without a window). It only decides which
 * action a key triggers; what save and cancel do belongs to the controller.
 */
final class FormKeyboard {

    private FormKeyboard() {}

    /**
     * Installs the form keys.
     *
     * <p>Two kinds of listener are used, on purpose. The Scene gets an event FILTER (capturing phase, runs before any
     * control) for the keys that must work from anywhere and that controls would swallow: Ctrl+Enter (a TextArea would
     * insert a line), Esc, Alt+S and Alt+C. The Enter chain is a FILTER on each field itself, registered after the
     * field's own filters (for example the time mask that blocks Enter while the minutes are incomplete), and it
     * respects an event that is already consumed, so it never walks over such a block.
     *
     * @param enterChain      fields in Tab order; Enter moves to the next one
     * @param saveOnEnter     the item of the chain where Enter saves instead of moving on
     * @param datePopupShowing true while the DatePicker calendar is open: Esc then only closes the calendar
     */
    static void install(Scene scene, List<Control> enterChain, Control saveOnEnter, Runnable save, Runnable cancel,
                        BooleanSupplier datePopupShowing) {
        install(scene, enterChain, saveOnEnter, save, cancel, datePopupShowing, Control::requestFocus);
    }

    /** Same, where {@code focusMover} gives the focus to the next field (the form also scrolls it into view). */
    static void install(Scene scene, List<Control> enterChain, Control saveOnEnter, Runnable save, Runnable cancel,
                        BooleanSupplier datePopupShowing, Consumer<Control> focusMover) {
        for (int i = 0; i < enterChain.size(); i++) {
            Control current = enterChain.get(i);
            Control next = i + 1 < enterChain.size() ? enterChain.get(i + 1) : null;
            current.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isConsumed() || !isPlainEnter(event)) return;
                if (current instanceof DatePicker && datePopupShowing.getAsBoolean()) return;
                event.consume();
                if (current instanceof DatePicker picker) commitTypedDate(picker);
                if (current == saveOnEnter) {
                    save.run();
                } else if (next != null) {
                    focusMover.accept(next);
                }
            });
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isConsumed()) return;
            if (isCtrlEnter(event)) {
                event.consume();
                save.run();
            } else if (event.getCode() == KeyCode.ESCAPE && !datePopupShowing.getAsBoolean()) {
                event.consume();
                cancel.run();
            } else if (isAlt(event, KeyCode.S)) {
                event.consume();
                save.run();
            } else if (isAlt(event, KeyCode.C)) {
                event.consume();
                cancel.run();
            } else if (isPlainEnter(event) && scene.getFocusOwner() instanceof ButtonBase button) {
                // A focused button does not react to Enter by itself in JavaFX (only to Space).
                event.consume();
                button.fire();
            }
        });
    }

    /**
     * Tab and Shift+Tab leave a TextArea instead of writing a tab character into it: Tab goes to {@code next},
     * Shift+Tab to {@code previous}. Ctrl+Tab is left alone (it is the platform's own way out).
     */
    static void leaveWithTab(TextArea area, Control next, Control previous) {
        leaveWithTab(area, next, previous, Control::requestFocus);
    }

    /** Same, where {@code focusMover} gives the focus to the chosen control. */
    static void leaveWithTab(TextArea area, Control next, Control previous, Consumer<Control> focusMover) {
        area.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.TAB || event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
                return;
            }
            event.consume();
            focusMover.accept(event.isShiftDown() ? previous : next);
        });
    }

    /** Reads the text typed in the date field (a DatePicker normally does it only when the field loses focus). */
    static void commitTypedDate(DatePicker picker) {
        String typed = picker.getEditor().getText();
        if (typed.equals(picker.getConverter().toString(picker.getValue()))) return;
        LocalDate date = picker.getConverter().fromString(typed);
        picker.setValue(date);
    }

    private static boolean isPlainEnter(KeyEvent event) {
        return event.getCode() == KeyCode.ENTER && !event.isControlDown() && !event.isAltDown()
            && !event.isMetaDown();
    }

    private static boolean isCtrlEnter(KeyEvent event) {
        return event.getCode() == KeyCode.ENTER && event.isControlDown() && !event.isAltDown();
    }

    /** Alt + letter. Ctrl+Alt is how AltGr is reported on Windows, so it is not a shortcut. */
    private static boolean isAlt(KeyEvent event, KeyCode letter) {
        return event.getCode() == letter && event.isAltDown() && !event.isControlDown();
    }
}
