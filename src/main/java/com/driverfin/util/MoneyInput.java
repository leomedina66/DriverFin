package com.driverfin.util;

import java.util.OptionalLong;
import java.util.function.Consumer;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** JavaFX adapter: connects a TextField to {@link MoneyMask}. It only copies data; the rules live in the mask. */
public final class MoneyInput {
    private static final String PROGRAMMATIC_KEY = "money-input.programmatic";
    private static final String NEGATIVE_SIGN = "-";

    private MoneyInput() {}

    /**
     * Installs the cents mask: the field starts at "0,00" and never becomes empty, the Delete key zeroes it, all
     * the text is selected on focus (caret at the end on "0,00"). Transient messages go to onMessage and are
     * cleared (null) on the next key and when the focus is lost.
     */
    public static void install(TextField field, Consumer<FieldMessage> onMessage) {
        field.setText(MoneyMask.ZERO);
        field.setTextFormatter(new TextFormatter<String>(change -> filter(change, onMessage)));
        FieldFocus.selectAllOnFocus(field, MoneyMask.ZERO);
        field.textProperty().addListener((obs, oldText, newText) -> updateAccessibleText(field));
        updateAccessibleText(field);
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) onMessage.accept(null);
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.DELETE) return;
            // Delete at the end of the text produces no Change, so the formatter cannot see it.
            field.setText(MoneyMask.ZERO);
            onMessage.accept(null);
            event.consume();
        });
    }

    /** Cents of the field. Throws IllegalArgumentException when the text is not a money text (see {@link #readCents}). */
    public static long cents(TextField field) {
        return MoneyMask.parseDisplay(field.getText());
    }

    /** Cents of the field, or empty when its text cannot be read (a stored negative value shown for correction). */
    public static OptionalLong readCents(TextField field) {
        try {
            return OptionalLong.of(cents(field));
        } catch (IllegalArgumentException unreadable) {
            return OptionalLong.empty();
        }
    }

    public static void setCents(TextField field, long cents) {
        field.setText(MoneyMask.display(cents));
    }

    /**
     * Shows a value that came from the database even when it is outside what the mask allows (above the maximum, or
     * negative, as "-5,00"), so the real amount is visible instead of a silent zero. The mask filter is skipped; the
     * first key typed afterwards goes through the normal rules again.
     */
    public static void setStored(TextField field, long cents) {
        field.getProperties().put(PROGRAMMATIC_KEY, Boolean.TRUE);
        try {
            field.setText(storedText(cents));
        } finally {
            field.getProperties().remove(PROGRAMMATIC_KEY);
        }
    }

    /** True when the amount can be typed in the field: from zero to {@link MoneyMask#MAX_CENTS}. */
    public static boolean isAllowed(long cents) {
        return cents >= 0 && cents <= MoneyMask.MAX_CENTS;
    }

    private static String storedText(long cents) {
        if (cents >= 0) return MoneyMask.display(cents);
        return NEGATIVE_SIGN + MoneyMask.display(-Math.max(cents, -Long.MAX_VALUE));
    }

    private static TextFormatter.Change filter(TextFormatter.Change change, Consumer<FieldMessage> onMessage) {
        if (!change.isContentChange()) return change;
        if (change.getControl().getProperties().containsKey(PROGRAMMATIC_KEY)) return change;
        TextEdit edit = new TextEdit(change.getControlText(), change.getRangeStart(), change.getRangeEnd(),
            change.getText());
        MaskResult result = MoneyMask.apply(edit);
        change.setRange(0, change.getControlText().length());
        change.setText(result.text());
        if (edit.isPaste() && !result.text().equals(edit.oldText())) {
            change.selectRange(0, result.text().length());
        } else {
            change.selectRange(result.caret(), result.caret());
        }
        onMessage.accept(result.message());
        return change;
    }

    private static void updateAccessibleText(TextField field) {
        OptionalLong cents = readCents(field);
        field.setAccessibleText(cents.isPresent() ? CurrencyFormat.money(MoneyMask.toReais(cents.getAsLong()))
            : field.getText());
    }
}
