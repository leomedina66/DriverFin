package com.driverfin.util;

import java.util.function.Consumer;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/** JavaFX adapter: connects a TextField to {@link TimeMask}. It only copies data; the rules live in the mask. */
public final class TimeInput {

    private TimeInput() {}

    /**
     * Installs the mask. On focus lost the text is completed ("8" -> "08:00"). Tab and Enter are blocked while the
     * minutes are half typed. Every message (or null to clear it) goes to onMessage.
     */
    public static void install(TextField field, Consumer<FieldMessage> onMessage) {
        field.setTextFormatter(new TextFormatter<String>(change -> filter(change, onMessage)));
        FieldFocus.selectAllOnFocus(field, null);
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) completeOnExit(field, onMessage);
        });
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> blockLeavingWhileIncomplete(field, event, onMessage));
    }

    private static TextFormatter.Change filter(TextFormatter.Change change, Consumer<FieldMessage> onMessage) {
        if (!change.isContentChange()) return change;
        TextEdit edit = new TextEdit(change.getControlText(), change.getRangeStart(), change.getRangeEnd(),
            change.getText());
        MaskResult result = TimeMask.apply(edit);
        change.setRange(0, change.getControlText().length());
        change.setText(result.text());
        change.selectRange(result.caret(), result.caret());
        onMessage.accept(result.message());
        return change;
    }

    private static void completeOnExit(TextField field, Consumer<FieldMessage> onMessage) {
        MaskResult result = TimeMask.complete(field.getText());
        if (!result.text().equals(field.getText())) field.setText(result.text());
        onMessage.accept(result.message());
    }

    private static void blockLeavingWhileIncomplete(TextField field, KeyEvent event,
                                                    Consumer<FieldMessage> onMessage) {
        if (event.getCode() != KeyCode.TAB && event.getCode() != KeyCode.ENTER) return;
        FieldMessage message = TimeMask.complete(field.getText()).message();
        if (message == null) return;
        onMessage.accept(message);
        event.consume();
    }
}
