package com.driverfin.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeMaskTest {

    /** Types each character at the end of the current text, like the keyboard does. */
    private static String type(String keys) {
        String text = "";
        for (char key : keys.toCharArray()) {
            text = typeOn(text, key);
        }
        return text;
    }

    private static String typeOn(String text, char key) {
        return TimeMask.apply(new TextEdit(text, text.length(), text.length(), String.valueOf(key))).text();
    }

    private static MaskResult backspace(String text) {
        return TimeMask.apply(new TextEdit(text, text.length() - 1, text.length(), ""));
    }

    private static MaskResult paste(String pasted) {
        return TimeMask.apply(new TextEdit("", 0, 0, pasted));
    }

    @Test
    public void typingFourDigitsBuildsTheTime() {
        assertEquals("0", type("0"));
        assertEquals("08:", type("08"));
        assertEquals("08:0", type("080"));
        assertEquals("08:00", type("0800"));
    }

    @Test
    public void firstDigitFromThreeToNineGetsALeadingZero() {
        assertEquals("08:", type("8"));
        assertEquals("08:30", type("830"));
        assertEquals("08:00", type("800"));
        assertEquals("1", type("1"));
        assertEquals("2", type("2"));
    }

    @Test
    public void impossibleDigitsAreIgnored() {
        assertEquals("2", type("24"));
        assertEquals("2", type("29"));
        assertEquals("08:", type("086"));
        assertEquals("08:00", type("08000"));
    }

    @Test
    public void colonPadsASingleDigitAndIsOtherwiseIgnored() {
        assertEquals("01:", type("1:"));
        assertEquals("08:", type("08:"));
        assertEquals("", type(":"));
        assertEquals("08:3", type("08:3:"));
    }

    @Test
    public void otherCharactersAreIgnored() {
        assertEquals("08:", type("0a8"));
        assertEquals("", type("-"));
    }

    @Test
    public void typingOverASelectedWholeTextStartsANewValue() {
        MaskResult result = TimeMask.apply(new TextEdit("08:30", 0, 5, "1"));
        assertEquals("1", result.text());
    }

    @Test
    public void backspaceRemovesTheLastDigit() {
        assertEquals("0", backspace("08:").text());
        assertEquals("08:3", backspace("08:30").text());
        assertEquals("", backspace("0").text());
    }

    @Test
    public void deletingEverythingEmptiesTheField() {
        assertEquals("", TimeMask.apply(new TextEdit("08:30", 0, 5, "")).text());
    }

    @Test
    public void pastedTextsBecomeATime() {
        assertEquals("08:30", paste("8:30").text());
        assertEquals("08:30", paste("830").text());
        assertEquals("08:30", paste("08:30").text());
        assertEquals("08:30", paste("0830").text());
        assertEquals("12:", paste("12").text());
        assertEquals("08:30", paste("08301").text());
    }

    @Test
    public void pasteReplacesTheWholeValue() {
        assertEquals("08:30", TimeMask.apply(new TextEdit("17:45", 5, 5, "08:30")).text());
    }

    @Test
    public void pastedInvalidTimeIsRejectedWithAnError() {
        MaskResult hour = TimeMask.apply(new TextEdit("07:00", 0, 5, "25:00"));
        assertEquals("07:00", hour.text());
        assertEquals(FieldMessage.error(FormMessages.TIME_INVALID), hour.message());

        MaskResult minute = paste("08:75");
        assertEquals("", minute.text());
        assertEquals(MessageKind.ERROR, minute.message().kind());
    }

    @Test
    public void pastedTextWithoutDigitsIsIgnoredSilently() {
        MaskResult result = TimeMask.apply(new TextEdit("07:00", 0, 5, "abc"));
        assertEquals("07:00", result.text());
        assertNull(result.message());
    }

    @Test
    public void completeFillsInTheObviousParts() {
        assertEquals("08:00", TimeMask.complete("08:").text());
        assertEquals("08:00", TimeMask.complete("08").text());
        assertEquals("01:00", TimeMask.complete("1").text());
        assertEquals("00:00", TimeMask.complete("0").text());
        assertEquals("02:00", TimeMask.complete("2").text());
        assertNull(TimeMask.complete("08").message());
    }

    @Test
    public void completeLeavesEmptyAndFullTextsAlone() {
        MaskResult empty = TimeMask.complete("");
        assertEquals("", empty.text());
        assertNull(empty.message());
        assertEquals("08:30", TimeMask.complete("08:30").text());
        assertNull(TimeMask.complete("08:30").message());
    }

    @Test
    public void completeReportsHalfTypedMinutesWithoutGuessing() {
        MaskResult result = TimeMask.complete("08:3");
        assertEquals("08:3", result.text());
        assertEquals(FieldMessage.error(FormMessages.TIME_INCOMPLETE), result.message());
    }

    @Test
    public void isCompleteAcceptsOnlyValidTimes() {
        assertTrue(TimeMask.isComplete("23:59"));
        assertTrue(TimeMask.isComplete("00:00"));
        assertFalse(TimeMask.isComplete("24:00"));
        assertFalse(TimeMask.isComplete("08:60"));
        assertFalse(TimeMask.isComplete("8:00"));
        assertFalse(TimeMask.isComplete("08:"));
        assertFalse(TimeMask.isComplete(null));
    }

    @Test
    public void caretIsAlwaysAtTheEnd() {
        for (String keys : new String[] {"0", "08", "0800", "830"}) {
            String text = "";
            for (char key : keys.toCharArray()) {
                MaskResult result = TimeMask.apply(new TextEdit(text, text.length(), text.length(), "" + key));
                assertEquals(result.text().length(), result.caret());
                text = result.text();
            }
        }
        assertEquals(5, paste("0830").caret());
        assertEquals(3, backspace("08:3").caret());
    }
}
