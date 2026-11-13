package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KmMaskTest {

    private static MaskResult typeAt(String text, int position, String key) {
        return KmMask.apply(new TextEdit(text, position, position, key));
    }

    private static String type(String keys) {
        String text = "";
        for (char key : keys.toCharArray()) text = typeAt(text, text.length(), String.valueOf(key)).text();
        return text;
    }

    private static void assertPasteGives(String expected, String pasted) {
        MaskResult result = KmMask.apply(new TextEdit("100", 0, 3, pasted));
        assertEquals(expected, result.text(), "pasting " + pasted);
        assertNull(result.message());
    }

    @Test
    public void typingKeepsDigitsAndOneComma() {
        assertEquals("12450", type("12450"));
        assertEquals("12450,5", type("12450,5"));
    }

    @Test
    public void dotBecomesComma() {
        assertEquals("12450,", type("12450."));
        assertEquals("12450,5", type("12450.5"));
    }

    @Test
    public void secondCommaAndSecondDecimalAreIgnored() {
        assertEquals("12450,5", type("12450,5,"));
        assertEquals("12450,5", type("12450,55"));
        assertEquals("12450,5", type("12450,5."));
    }

    @Test
    public void lettersAndMinusAreIgnored() {
        assertEquals("12", type("1a2-"));
    }

    @Test
    public void integerPartIsLimitedToNineDigits() {
        assertEquals("123456789", type("1234567890"));
        assertEquals("123456789,5", type("1234567890,5"));
    }

    @Test
    public void caretFollowsWhereTheUserTypes() {
        MaskResult result = typeAt("1250", 2, "4");
        assertEquals("12450", result.text());
        assertEquals(3, result.caret());

        MaskResult rejected = typeAt("1250", 2, "x");
        assertEquals("1250", rejected.text());
        assertEquals(2, rejected.caret());
    }

    @Test
    public void deletingKeepsTheCaretAtTheDeletedPosition() {
        MaskResult result = KmMask.apply(new TextEdit("12450", 1, 2, ""));
        assertEquals("1450", result.text());
        assertEquals(1, result.caret());
    }

    @Test
    public void storedExtraDecimalsStayButTypingAfterThemIsRefused() {
        assertEquals("100,55", typeAt("100,55", 0, "").text());
        assertEquals("1100,55", typeAt("100,55", 0, "1").text());
        assertEquals("100,55", typeAt("100,55", 6, "1").text());
    }

    @Test
    public void commaInTheMiddleThatLeavesTooManyDecimalsIsRefused() {
        assertEquals("12450", typeAt("12450", 2, ",").text());
        assertEquals("12450,", typeAt("12450", 5, ",").text());
    }

    @Test
    public void formatOnExitShowsTheDisplayForm() {
        assertEquals("12.450,0", KmMask.formatOnExit("12450").text());
        assertEquals("12.450,5", KmMask.formatOnExit("12450,5").text());
        assertEquals("12.450,0", KmMask.formatOnExit("12450,").text());
        assertEquals("12.450,0", KmMask.formatOnExit("12.450,0").text());
        assertEquals("0,5", KmMask.formatOnExit(",5").text());
    }

    @Test
    public void formatOnExitLeavesBlankAlone() {
        MaskResult result = KmMask.formatOnExit("  ");
        assertEquals("  ", result.text());
        assertNull(result.message());
        assertEquals("", KmMask.formatOnExit(null).text());
    }

    @Test
    public void formatOnExitReportsUnreadableText() {
        MaskResult result = KmMask.formatOnExit(",");
        assertEquals(",", result.text());
        assertEquals(FieldMessage.error(FormMessages.KM_UNREADABLE), result.message());
    }

    @Test
    public void pastedValuesBecomeTheEditingForm() {
        assertPasteGives("12450,5", "12.450,5");
        assertPasteGives("12450,5", "12450,5");
        assertPasteGives("12450,5", "12450.5");
        assertPasteGives("12450", "12450");
        assertPasteGives("12450", "12.450");
        assertPasteGives("12450", " 12450 ");
    }

    @Test
    public void pastedValuesKeepUpToThreeDecimals() {
        assertPasteGives("100,55", "100,55");
        assertPasteGives("12450,555", "12.450,555");
        assertPasteGives("12450,55", "12450.55");
        assertPasteGives("100,55", KmMask.display(100.55));
    }

    @Test
    public void pastedThousandsGroupMustNotStartWithZero() {
        for (String pasted : new String[] {"0.500", "0.500,5", "00.500"}) {
            MaskResult result = KmMask.apply(new TextEdit("100", 0, 3, pasted));
            assertEquals("100", result.text(), pasted);
            assertEquals(FieldMessage.error(FormMessages.KM_UNREADABLE), result.message(), pasted);
        }
        assertPasteGives("1500", "1.500");
        assertPasteGives("0,5", "0.5");
    }

    @Test
    public void editingInTheMiddleOfAShownValueIgnoresTheThousandsDots() {
        // "12.450,0" with the caret after "12." (position 3): typing 7 gives 127450,0 and the caret is after the 7.
        MaskResult typed = KmMask.apply(new TextEdit("12.450,0", 3, 3, "7"));
        assertEquals("127450,0", typed.text());
        assertEquals(3, typed.caret());

        MaskResult deleted = KmMask.apply(new TextEdit("12.450,0", 1, 2, ""));
        assertEquals("1450,0", deleted.text());
        assertEquals(1, deleted.caret());

        MaskResult selectedAll = KmMask.apply(new TextEdit("12.450,0", 0, 8, "9"));
        assertEquals("9", selectedAll.text());
    }

    @Test
    public void thousandsDotsDoNotCountAgainstTheNineDigitLimit() {
        // 9 integer digits shown with 2 dots (11 characters before the comma): a tenth digit is refused.
        assertEquals("123.456.789,0", typeAt("123.456.789,0", 0, "1").text());
        // 8 digits shown with 2 dots: the ninth is accepted although the shown text is already 10 long.
        assertEquals("112345678,0", typeAt("12.345.678,0", 0, "1").text());
    }

    @Test
    public void afterEditingAShownValueLeavingTheFieldShowsTheGroupsAgain() {
        String edited = KmMask.apply(new TextEdit("12.450,0", 3, 3, "7")).text();
        assertEquals("127.450,0", KmMask.formatOnExit(edited).text());
    }

    @Test
    public void unreadablePastesAreRejected() {
        for (String pasted : new String[] {"abc", "-5", "12450,5555", "1,2,3", "1234567890"}) {
            MaskResult result = KmMask.apply(new TextEdit("100", 0, 3, pasted));
            assertEquals("100", result.text(), pasted);
            assertEquals(FieldMessage.error(FormMessages.KM_UNREADABLE), result.message(), pasted);
        }
    }

    @Test
    public void displayKeepsStoredDecimals() {
        assertEquals("12.450,0", KmMask.display(12450.0));
        assertEquals("100,55", KmMask.display(100.55));
        assertEquals("0,0", KmMask.display(0));
        assertEquals("1.000.000,5", KmMask.display(1_000_000.5));
        assertEquals("10,123", KmMask.display(10.1234));
    }

    @Test
    public void toPlainRemovesOnlyTheDots() {
        assertEquals("12450,5", KmMask.toPlain("12.450,5"));
        assertEquals("12450", KmMask.toPlain("12450"));
        assertEquals("", KmMask.toPlain(" "));
        assertEquals("", KmMask.toPlain(null));
    }

    @Test
    public void plainTextIsAcceptedByTheValidator() {
        WorkShift shift = new WorkShift();
        WorkShiftValidator.validateAndPopulate(shift, LocalDate.of(2024, 1, 1), "08:00", "09:00",
            KmMask.toPlain("12.450,5"), KmMask.toPlain("12.500,0"), "", "", "", "", "", "");
        assertEquals(12450.5, shift.getKmStart());
        assertEquals(12500.0, shift.getKmEnd());
    }

    @Test
    public void parseReadsDisplayAndTypingForms() {
        assertEquals(12450.0, KmMask.parse("12.450,0").getAsDouble());
        assertEquals(12450.5, KmMask.parse("12450,5").getAsDouble());
        assertEquals(12450.0, KmMask.parse("12450").getAsDouble());
        assertTrue(KmMask.parse("").isEmpty());
        assertTrue(KmMask.parse(null).isEmpty());
        assertTrue(KmMask.parse("abc").isEmpty());
        assertEquals(OptionalDouble.empty(), KmMask.parse("1.2"));
    }
}
