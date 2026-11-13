package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MoneyMaskTest {

    private static MaskResult typeOn(String text, char key) {
        return MoneyMask.apply(new TextEdit(text, text.length(), text.length(), String.valueOf(key)));
    }

    private static String type(String keys) {
        String text = MoneyMask.ZERO;
        for (char key : keys.toCharArray()) text = typeOn(text, key).text();
        return text;
    }

    private static MaskResult backspace(String text) {
        return MoneyMask.apply(new TextEdit(text, text.length() - 1, text.length(), ""));
    }

    private static void assertPasteGives(String expected, String pasted) {
        MaskResult result = MoneyMask.paste(MoneyMask.ZERO, pasted);
        assertEquals(expected, result.text(), "pasting " + pasted);
        assertNull(result.message());
    }

    private static void assertPasteRejected(String pasted, String expectedMessage) {
        MaskResult result = MoneyMask.paste("7,00", pasted);
        assertEquals("7,00", result.text(), "pasting " + pasted);
        assertEquals(expectedMessage, result.message().text());
    }

    @Test
    public void typingPushesDigitsFromTheRight() {
        assertEquals("0,01", type("1"));
        assertEquals("0,12", type("12"));
        assertEquals("1,23", type("123"));
        assertEquals("12,34", type("1234"));
        assertEquals("123,45", type("12345"));
        assertEquals("1.234,56", type("123456"));
        assertEquals("150,00", type("15000"));
    }

    @Test
    public void leadingZerosDoNotShow() {
        assertEquals("0,05", type("005"));
    }

    @Test
    public void typingOverTheWholeSelectedValueStartsANewOne() {
        assertEquals("0,05", MoneyMask.apply(new TextEdit("150,00", 0, 6, "5")).text());
    }

    @Test
    public void backspaceRemovesTheLastDigit() {
        assertEquals("123,45", backspace("1.234,56").text());
        assertEquals("0,00", backspace("0,00").text());
        assertEquals("0,00", backspace("0,05").text());
    }

    @Test
    public void selectAllAndDeleteZeroesTheField() {
        assertEquals("0,00", MoneyMask.apply(new TextEdit("1.234,56", 0, 8, "")).text());
    }

    @Test
    public void commaAndDotAreIgnoredWithAHintOthersSilently() {
        for (char key : new char[] {',', '.'}) {
            MaskResult result = typeOn("1,23", key);
            assertEquals("1,23", result.text());
            assertEquals(FieldMessage.info(FormMessages.MONEY_COMMA_HINT), result.message());
        }
        for (char key : new char[] {'a', '-', ' '}) {
            MaskResult result = typeOn("1,23", key);
            assertEquals("1,23", result.text());
            assertNull(result.message());
        }
    }

    @Test
    public void ceilingIsAllowedButNotExceeded() {
        MaskResult over = typeOn("999.999,99", '1');
        assertEquals("999.999,99", over.text());
        assertEquals(FieldMessage.warning(FormMessages.MONEY_MAX), over.message());

        MaskResult atCeiling = typeOn("100.000,00", '0');
        assertEquals("1.000.000,00", atCeiling.text());
        assertNull(atCeiling.message());

        MaskResult beyond = typeOn("1.000.000,00", '0');
        assertEquals("1.000.000,00", beyond.text());
        assertEquals(FormMessages.MONEY_MAX, beyond.message().text());
    }

    @Test
    public void pastedValuesWithCommaAreExact() {
        assertPasteGives("1.234,56", "R$ 1.234,56");
        assertPasteGives("1.234,56", "1234,56");
        assertPasteGives("1.234,56", "1.234,56");
        assertPasteGives("150,50", "150,5");
        assertPasteGives("150,00", "150,");
    }

    @Test
    public void pastedDigitsWithoutCommaAreWholeReais() {
        assertPasteGives("123.456,00", "123456");
        assertPasteGives("150,00", "150");
    }

    @Test
    public void pastedDotsAreDecimalOrThousandsByShape() {
        assertPasteGives("12,50", "12.50");
        assertPasteGives("1.234,50", "1234.5");
        assertPasteGives("1.234,00", "1.234");
    }

    @Test
    public void pastedValuesAboveTheCeilingAreRejected() {
        assertPasteRejected("1.234.567", FormMessages.MONEY_MAX);
        assertPasteRejected("1000000,01", FormMessages.MONEY_MAX);
    }

    @Test
    public void unreadablePastesAreRejected() {
        assertPasteRejected("1234,567", FormMessages.MONEY_UNREADABLE);
        assertPasteRejected("abc", FormMessages.MONEY_UNREADABLE);
        assertPasteRejected("-5", FormMessages.MONEY_UNREADABLE);
        assertPasteRejected("", FormMessages.MONEY_UNREADABLE);
        assertPasteRejected("1,2,3", FormMessages.MONEY_UNREADABLE);
    }

    @Test
    public void ceilingPasteIsAllowed() {
        assertPasteGives("1.000.000,00", "1000000");
    }

    @Test
    public void pasteGoesThroughApplyAndReplacesTheWholeValue() {
        assertEquals("1.234,56", MoneyMask.apply(new TextEdit("7,00", 0, 4, "R$ 1.234,56")).text());
    }

    @Test
    public void displayAndPlainUseIntegerMath() {
        assertEquals("1.234,56", MoneyMask.display(123456));
        assertEquals("0,00", MoneyMask.display(0));
        assertEquals("0,07", MoneyMask.display(7));
        assertEquals("1.000.000,00", MoneyMask.display(MoneyMask.MAX_CENTS));
        assertEquals("1234,56", MoneyMask.plain(123456));
        assertEquals("0,00", MoneyMask.plain(0));
        assertThrows(IllegalArgumentException.class, () -> MoneyMask.display(-1));
    }

    @Test
    public void parseDisplayReadsOnlyTheDisplayFormat() {
        assertEquals(123456, MoneyMask.parseDisplay("1.234,56"));
        assertEquals(0, MoneyMask.parseDisplay("0,00"));
        assertEquals(0, MoneyMask.parseDisplay("  "));
        assertEquals(0, MoneyMask.parseDisplay(null));
        for (String bad : new String[] {"1234,56", "1.234", "abc", "1,5", "-1,00"}) {
            assertThrows(IllegalArgumentException.class, () -> MoneyMask.parseDisplay(bad), bad);
        }
    }

    @Test
    public void parseDisplayOfAnAbsurdlyBigNumberIsRefusedAsIllegalArgument() {
        // 19 integer digits: the cents no longer fit in a long. Must not leak an ArithmeticException.
        assertThrows(IllegalArgumentException.class, () -> MoneyMask.parseDisplay("1.000.000.000.000.000.000,00"));
        assertThrows(IllegalArgumentException.class,
            () -> MoneyMask.displayToPlain("99.999.999.999.999.999.999.999,99"));
    }

    @Test
    public void pastedThousandsGroupMustNotStartWithZero() {
        for (String pasted : new String[] {"0.500", "0.500,00", "00.500", "R$ 0.500"}) {
            MaskResult result = MoneyMask.paste("1,00", pasted);
            assertEquals("1,00", result.text(), pasted);
            assertEquals(FieldMessage.error(FormMessages.MONEY_UNREADABLE), result.message(), pasted);
        }
        assertEquals("0,50", MoneyMask.paste("1,00", "0,5").text());
        assertEquals("1.500,00", MoneyMask.paste("1,00", "1.500").text());
    }

    @Test
    public void displayToPlainFeedsTheValidator() {
        assertEquals("0,00", MoneyMask.displayToPlain("0,00"));
        assertEquals("1234,50", MoneyMask.displayToPlain("1.234,50"));

        WorkShift shift = new WorkShift();
        WorkShiftValidator.validateAndPopulate(shift, LocalDate.of(2024, 1, 1), "08:00", "09:00", "1", "2",
            MoneyMask.displayToPlain("1.234,50"), MoneyMask.displayToPlain("0,00"), "", "", "", "");
        assertEquals(new BigDecimal("1234.50"), shift.getUberEarnings());
        assertEquals(new BigDecimal("0.00"), shift.getApp99Earnings());
    }

    @Test
    public void conversionsBetweenReaisAndCents() {
        assertEquals(123450, MoneyMask.toCents(new BigDecimal("1234.5")));
        assertEquals(0, MoneyMask.toCents(null));
        assertEquals(1, MoneyMask.toCents(new BigDecimal("0.005")));
        assertEquals(new BigDecimal("1234.56"), MoneyMask.toReais(123456));
        for (String reais : new String[] {"0", "0.01", "1000000.00"}) {
            long cents = MoneyMask.toCents(new BigDecimal(reais));
            assertEquals(cents, MoneyMask.parseDisplay(MoneyMask.display(cents)));
        }
    }

    @Test
    public void caretIsAtTheEnd() {
        MaskResult result = typeOn("123,45", '6');
        assertEquals(result.text().length(), result.caret());
    }
}
