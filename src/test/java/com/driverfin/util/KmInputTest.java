package com.driverfin.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exercises the real adapter on a TextField, without opening a window. Skipped when the toolkit cannot start. */
public class KmInputTest {

    @BeforeAll
    static void toolkit() {
        FxTestSupport.assumeToolkit();
    }

    private static FxTestSupport.TestField installed(List<FieldMessage> messages) {
        FxTestSupport.TestField field = new FxTestSupport.TestField();
        KmInput.install(field, messages::add);
        return field;
    }

    @Test
    public void typingFiltersTheText() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "12a450.55");
            assertEquals("12450,5", field.getText());
            assertEquals(7, field.getCaretPosition());
        });
    }

    @Test
    public void typingInTheMiddleKeepsTheCaretThere() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "1250");
            field.replaceText(2, 2, "4");
            assertEquals("12450", field.getText());
            assertEquals(3, field.getCaretPosition());
        });
    }

    @Test
    public void editingInTheMiddleOfAShownValueStaysReadableAndRegroupsOnExit() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            KmInput.setDisplayed(field, "12.450,0");
            field.replaceText(3, 3, "7");
            assertEquals("127450,0", field.getText());
            assertEquals(3, field.getCaretPosition());

            field.setFocusedForTest(true);
            field.setFocusedForTest(false);
            assertEquals("127.450,0", field.getText());
        });
    }

    @Test
    public void backspaceDeletes() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "123");
            FxTestSupport.backspace(field);
            assertEquals("12", field.getText());
        });
    }

    @Test
    public void pasteReplacesTheWholeValue() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "99");
            field.replaceText(0, field.getLength(), "12.450,5");
            assertEquals("12450,5", field.getText());
        });
    }

    @Test
    public void unreadablePasteReportsAnError() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = installed(messages);
            FxTestSupport.type(field, "99");
            field.replaceText(0, field.getLength(), "abc");
            assertEquals("99", field.getText());
            assertEquals(FieldMessage.error(FormMessages.KM_UNREADABLE), messages.get(messages.size() - 1));
        });
    }

    @Test
    public void losingFocusFormatsTheValue() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            field.setFocusedForTest(true);
            FxTestSupport.type(field, "12450");
            field.setFocusedForTest(false);
            assertEquals("12.450,0", field.getText());
        });
    }

    @Test
    public void losingFocusWithUnreadableTextKeepsItAndShowsError() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = installed(messages);
            FxTestSupport.type(field, ",");
            field.setFocusedForTest(true);
            field.setFocusedForTest(false);
            assertEquals(",", field.getText());
            assertEquals(FieldMessage.error(FormMessages.KM_UNREADABLE), messages.get(messages.size() - 1));
        });
    }

    @Test
    public void losingFocusOnBlankClearsTheMessage() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = installed(messages);
            field.setFocusedForTest(true);
            field.setFocusedForTest(false);
            assertEquals("", field.getText());
            assertNull(messages.get(messages.size() - 1));
        });
    }

    @Test
    public void setDisplayedKeepsTheDisplayForm() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            KmInput.setDisplayed(field, "12.450,0");
            assertEquals("12.450,0", field.getText());
            FxTestSupport.type(field, "1");
            // Typing still goes through the filter: the single typed decimal is already there.
            assertEquals("12.450,0", field.getText());
        });
    }

    @Test
    public void focusSelectsAllTheText() {
        FxTestSupport.TestField field = new FxTestSupport.TestField();
        FxTestSupport.onFxThread(() -> {
            KmInput.install(field, m -> { });
            KmInput.setDisplayed(field, "12.630,0");
            field.setFocusedForTest(true);
        });
        FxTestSupport.onFxThread(() -> assertEquals("12.630,0", field.getSelectedText()));
    }
}
