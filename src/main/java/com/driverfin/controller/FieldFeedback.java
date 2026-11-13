package com.driverfin.controller;

import com.driverfin.util.FieldMessage;
import com.driverfin.util.MessageKind;
import javafx.scene.control.Control;
import javafx.scene.control.Label;

import java.util.Arrays;
import java.util.List;

/**
 * Shows one message under a form field: text, style class by kind, red border for errors and the help text read
 * by screen readers. No rules here: the controller decides which message to show.
 */
final class FieldFeedback {
    static final String INPUT_ERROR_CLASS = "input-error";
    /** The text starts with a mark so that the kind is never given by color alone (UI.md). */
    static final String ERROR_MARK = "! ";
    static final String WARNING_MARK = "⚠ ";

    private static final List<String> KIND_CLASSES =
        Arrays.stream(MessageKind.values()).map(MessageKind::styleClass).toList();

    /** Shows the message in the label; null clears it (the label is hidden and takes no space). */
    void show(Label message, Control field, FieldMessage text) {
        show(message, text);
        field.getStyleClass().remove(INPUT_ERROR_CLASS);
        if (text == null) {
            field.setAccessibleHelp(null);
            return;
        }
        if (text.kind() == MessageKind.ERROR) field.getStyleClass().add(INPUT_ERROR_CLASS);
        field.setAccessibleHelp(text.text());
    }

    /** Same, for a note that belongs to no single field (turn note, rate warning): only the label changes. */
    void show(Label message, FieldMessage text) {
        message.getStyleClass().removeAll(KIND_CLASSES);
        if (text == null) {
            message.setText("");
            setShown(message, false);
            return;
        }
        message.setText(markFor(text.kind()) + text.text());
        message.getStyleClass().add(text.kind().styleClass());
        setShown(message, true);
    }

    private static String markFor(MessageKind kind) {
        return switch (kind) {
            case ERROR -> ERROR_MARK;
            case WARNING -> WARNING_MARK;
            case INFO -> "";
        };
    }

    private static void setShown(Label label, boolean shown) {
        label.setVisible(shown);
        label.setManaged(shown);
    }
}
