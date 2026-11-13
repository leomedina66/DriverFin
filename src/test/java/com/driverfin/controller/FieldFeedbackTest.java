package com.driverfin.controller;

import com.driverfin.util.FieldMessage;
import com.driverfin.util.FxTestSupport;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldFeedbackTest {
    private final FieldFeedback feedback = new FieldFeedback();

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @Test
    void errorShowsMarkedTextRedBorderAndHelp() {
        FxTestSupport.onFxThread(() -> {
            Label label = new Label();
            TextField field = new TextField();
            feedback.show(label, field, FieldMessage.error("Informe a hora de fim."));

            assertTrue(label.isVisible() && label.isManaged());
            assertEquals(FieldFeedback.ERROR_MARK + "Informe a hora de fim.", label.getText());
            assertTrue(label.getStyleClass().contains("field-error"));
            assertTrue(field.getStyleClass().contains(FieldFeedback.INPUT_ERROR_CLASS));
            assertEquals("Informe a hora de fim.", field.getAccessibleHelp());
        });
    }

    @Test
    void warningAndInfoDoNotMarkTheFieldAsWrong() {
        FxTestSupport.onFxThread(() -> {
            Label label = new Label();
            TextField field = new TextField();
            feedback.show(label, field, FieldMessage.warning("Valor alto."));
            assertTrue(label.getStyleClass().contains("field-warning"));
            assertTrue(label.getText().startsWith(FieldFeedback.WARNING_MARK));
            assertFalse(field.getStyleClass().contains(FieldFeedback.INPUT_ERROR_CLASS));

            feedback.show(label, field, FieldMessage.info("Dica."));
            assertEquals("Dica.", label.getText());
            assertTrue(label.getStyleClass().contains("field-info"));
            assertFalse(label.getStyleClass().contains("field-warning"));
        });
    }

    @Test
    void nullClearsEverythingAndHidesTheLabel() {
        FxTestSupport.onFxThread(() -> {
            Label label = new Label();
            TextField field = new TextField();
            feedback.show(label, field, FieldMessage.error("Erro."));
            feedback.show(label, field, null);

            assertFalse(label.isVisible());
            assertFalse(label.isManaged());
            assertEquals("", label.getText());
            assertFalse(label.getStyleClass().contains("field-error"));
            assertFalse(field.getStyleClass().contains(FieldFeedback.INPUT_ERROR_CLASS));
            assertNull(field.getAccessibleHelp());
        });
    }

    @Test
    void showingTwiceKeepsOneCopyOfEachStyleClass() {
        FxTestSupport.onFxThread(() -> {
            Label label = new Label();
            TextField field = new TextField();
            feedback.show(label, field, FieldMessage.error("A"));
            feedback.show(label, field, FieldMessage.error("B"));

            assertEquals(1, label.getStyleClass().stream().filter("field-error"::equals).count());
            assertEquals(1, field.getStyleClass().stream().filter("input-error"::equals).count());
        });
    }
}
