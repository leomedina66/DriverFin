package com.driverfin.util;

import java.util.function.Consumer;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

/** JavaFX adapter: connects a TextField to {@link KmMask}. It only copies data; the rules live in the mask. */
public final class KmInput {
    private static final String PROGRAMMATIC_KEY = "km-input.programmatic";

    private KmInput() {}

    /**
     * Installs the filter. On focus lost the text gets the display form ("12450" -> "12.450,0"); all the text is
     * selected on focus. Messages (or null to clear them) go to onMessage.
     */
    public static void install(TextField field, Consumer<FieldMessage> onMessage) {
        field.setTextFormatter(new TextFormatter<String>(change -> filter(change, onMessage)));
        FieldFocus.selectAllOnFocus(field, null);
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) formatOnExit(field, onMessage);
        });
    }

    /**
     * Shows a ready display text such as "12.450,0" (edit mode, suggested KM) without the paste rules turning it
     * back into the typing form "12450,0".
     */
    public static void setDisplayed(TextField field, String displayText) {
        field.getProperties().put(PROGRAMMATIC_KEY, Boolean.TRUE);
        try {
            field.setText(displayText);
        } finally {
            field.getProperties().remove(PROGRAMMATIC_KEY);
        }
    }

    private static TextFormatter.Change filter(TextFormatter.Change change, Consumer<FieldMessage> onMessage) {
        if (!change.isContentChange()) return change;
        if (change.getControl().getProperties().containsKey(PROGRAMMATIC_KEY)) return change;
        TextEdit edit = new TextEdit(change.getControlText(), change.getRangeStart(), change.getRangeEnd(),
            change.getText());
        MaskResult result = KmMask.apply(edit);
        change.setRange(0, change.getControlText().length());
        change.setText(result.text());
        change.selectRange(result.caret(), result.caret());
        onMessage.accept(result.message());
        return change;
    }

    private static void formatOnExit(TextField field, Consumer<FieldMessage> onMessage) {
        MaskResult result = KmMask.formatOnExit(field.getText());
        if (!result.text().equals(field.getText())) setDisplayed(field, result.text());
        onMessage.accept(result.message());
    }
}
