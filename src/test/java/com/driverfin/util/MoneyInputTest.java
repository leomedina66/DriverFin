package com.driverfin.util;

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Exercises the real adapter on a TextField, without opening a window. Skipped when the toolkit cannot start. */
public class MoneyInputTest {

    @BeforeAll
    static void toolkit() {
        FxTestSupport.assumeToolkit();
    }

    private static FxTestSupport.TestField installed(List<FieldMessage> messages) {
        FxTestSupport.TestField field = new FxTestSupport.TestField();
        MoneyInput.install(field, messages::add);
        return field;
    }

    private static void press(FxTestSupport.TestField field, KeyCode code) {
        Event.fireEvent(field, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    @Test
    public void startsAtZero() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            assertEquals("0,00", field.getText());
            assertEquals(0, MoneyInput.cents(field));
        });
    }

    @Test
    public void typingDigitsPushesTheValue() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "12345");
            assertEquals("123,45", field.getText());
            FxTestSupport.type(field, "6");
            assertEquals("1.234,56", field.getText());
            assertEquals(123456, MoneyInput.cents(field));
            assertEquals(field.getText().length(), field.getCaretPosition());
        });
    }

    @Test
    public void backspaceAndSelectAllDelete() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "123456");
            FxTestSupport.backspace(field);
            assertEquals("123,45", field.getText());
            field.replaceText(0, field.getLength(), "");
            assertEquals("0,00", field.getText());
            FxTestSupport.backspace(field);
            assertEquals("0,00", field.getText());
        });
    }

    @Test
    public void deleteKeyZeroesTheField() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "123456");
            press(field, KeyCode.DELETE);
            assertEquals("0,00", field.getText());
        });
    }

    @Test
    public void selectedValueIsReplacedByTheFirstDigit() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "15000");
            field.replaceText(0, field.getLength(), "5");
            assertEquals("0,05", field.getText());
        });
    }

    @Test
    public void pasteReplacesTheValueAndSelectsIt() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            FxTestSupport.type(field, "99");
            field.replaceText(0, field.getLength(), "R$ 1.234,56");
            assertEquals("1.234,56", field.getText());
            assertEquals("1.234,56", field.getSelectedText());
            field.replaceText(0, field.getLength(), "123456");
            assertEquals("123.456,00", field.getText());
        });
    }

    @Test
    public void unreadablePasteKeepsTheValueAndReportsError() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = installed(messages);
            FxTestSupport.type(field, "123");
            field.replaceText(0, field.getLength(), "abc");
            assertEquals("1,23", field.getText());
            assertEquals(FormMessages.MONEY_UNREADABLE, messages.get(messages.size() - 1).text());
        });
    }

    @Test
    public void commaShowsAHintAndTheNextKeyClearsIt() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = installed(messages);
            FxTestSupport.type(field, "12");
            FxTestSupport.type(field, ",");
            assertEquals("0,12", field.getText());
            assertEquals(FieldMessage.info(FormMessages.MONEY_COMMA_HINT), messages.get(messages.size() - 1));
            FxTestSupport.type(field, "3");
            assertNull(messages.get(messages.size() - 1));
        });
    }

    @Test
    public void ceilingMessageIsClearedWhenFocusIsLost() {
        FxTestSupport.onFxThread(() -> {
            List<FieldMessage> messages = new ArrayList<>();
            FxTestSupport.TestField field = installed(messages);
            FxTestSupport.type(field, "99999999");
            FxTestSupport.type(field, "1");
            assertEquals("999.999,99", field.getText());
            assertEquals(FormMessages.MONEY_MAX, messages.get(messages.size() - 1).text());
            field.setFocusedForTest(true);
            field.setFocusedForTest(false);
            assertNull(messages.get(messages.size() - 1));
        });
    }

    @Test
    public void setCentsFillsTheFieldAndTheAccessibleText() {
        FxTestSupport.onFxThread(() -> {
            FxTestSupport.TestField field = installed(new ArrayList<>());
            MoneyInput.setCents(field, 123450);
            assertEquals("1.234,50", field.getText());
            assertEquals(123450, MoneyInput.cents(field));
            assertEquals(CurrencyFormat.money(new java.math.BigDecimal("1234.50")), field.getAccessibleText());
            MoneyInput.setCents(field, 0);
            assertEquals("0,00", field.getText());
        });
    }

    @Test
    public void focusSelectsAllExceptOnZeroWhereCaretGoesToTheEnd() {
        FxTestSupport.TestField zero = new FxTestSupport.TestField();
        FxTestSupport.TestField value = new FxTestSupport.TestField();
        FxTestSupport.onFxThread(() -> {
            MoneyInput.install(zero, m -> { });
            MoneyInput.install(value, m -> { });
            MoneyInput.setCents(value, 15000);
            zero.setFocusedForTest(true);
            value.setFocusedForTest(true);
        });
        // The selection is applied with Platform.runLater, which runs before this second task.
        FxTestSupport.onFxThread(() -> {
            assertEquals("", zero.getSelectedText());
            assertEquals(zero.getLength(), zero.getCaretPosition());
            assertEquals("150,00", value.getSelectedText());
        });
    }
}
