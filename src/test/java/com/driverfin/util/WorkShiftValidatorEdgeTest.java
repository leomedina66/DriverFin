package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkShiftValidatorEdgeTest {
    private static final LocalDate DATE = LocalDate.of(2024, 1, 1);

    private static WorkShift fill(String start, String end, String kmStart, String kmEnd, String uber, String notes) {
        WorkShift shift = new WorkShift();
        WorkShiftValidator.validateAndPopulate(shift, DATE, start, end, kmStart, kmEnd, uber, null, null, null, null, notes);
        return shift;
    }

    private static void assertRejected(String start, String end, String kmStart, String kmEnd, String uber) {
        assertThrows(IllegalArgumentException.class, () -> fill(start, end, kmStart, kmEnd, uber, ""));
    }

    @Test
    void overnightShiftHasPositiveHours() {
        WorkShift s = fill("22:00", "02:00", "1", "2", "", "");
        assertEquals(4.0, s.calculateHours(), 0.0001);
    }

    @Test
    void sameStartAndEndTimeGivesZeroHoursNotTwentyFour() {
        WorkShift s = fill("08:00", "08:00", "1", "2", "100", "");
        assertEquals(0.0, s.calculateHours(), 0.0001);
        assertEquals(0, BigDecimal.ZERO.compareTo(s.calculateGrossPerHour()));
    }

    @Test
    void midnightEndCountsAsEndOfDay() {
        assertEquals(2.0, fill("22:00", "00:00", "1", "2", "", "").calculateHours(), 0.0001);
    }

    @Test
    void kmEqualToStartIsAccepted() {
        WorkShift s = fill("08:00", "09:00", "100", "100", "", "");
        assertEquals(0.0, s.calculateKm(), 0.0);
    }

    @Test
    void kmEndSlightlyBelowStartIsRejected() {
        assertRejected("08:00", "09:00", "100,5", "100,4", "");
    }

    @Test
    void hourWithOneAndTwoDigitsGiveSameTime() {
        assertEquals(fill("8:00", "9:00", "1", "2", "", "").getTimeStart(),
                fill("08:00", "9:00", "1", "2", "", "").getTimeStart());
    }

    @Test
    void timeWithSurroundingSpacesIsTrimmed() {
        assertEquals(LocalTime.of(8, 0), fill(" 8:00 ", "09:00", "1", "2", "", "").getTimeStart());
    }

    @Test
    void rejectsMalformedTimes() {
        assertRejected("08:0", "09:00", "1", "2", "");
        assertRejected("8.00", "09:00", "1", "2", "");
        assertRejected("08:60", "09:00", "1", "2", "");
        assertRejected("", "09:00", "1", "2", "");
        assertRejected("   ", "09:00", "1", "2", "");
        assertRejected("08:00", null, "1", "2", "");
    }

    @Test
    void time2400IsRejectedButBothEndsOfTheDayAreAccepted() {
        assertRejected("24:00", "09:00", "1", "2", "");
        assertRejected("08:00", "24:00", "1", "2", "");
        assertEquals(LocalTime.MIDNIGHT, fill("00:00", "09:00", "1", "2", "", "").getTimeStart());
        assertEquals(LocalTime.of(23, 59), fill("23:59", "09:00", "1", "2", "", "").getTimeStart());
    }

    @Test
    void notesOf500CharactersPassAnd501Fail() {
        assertEquals(500, fill("08:00", "09:00", "1", "2", "", "n".repeat(500)).getNotes().length());
        assertThrows(IllegalArgumentException.class, () -> fill("08:00", "09:00", "1", "2", "", "n".repeat(501)));
    }

    @Test
    void nullNotesAreAccepted() {
        assertNull(fill("08:00", "09:00", "1", "2", "", null).getNotes());
    }

    @Test
    void dotThousandsSeparatorIsRejectedWithClearMessage() {
        String moneyMessage = assertThrows(IllegalArgumentException.class,
                () -> fill("08:00", "09:00", "1", "2", "1.000", "")).getMessage();
        assertTrue(moneyMessage.contains("vírgula") && moneyMessage.contains("milhares"), moneyMessage);

        String kmMessage = assertThrows(IllegalArgumentException.class,
                () -> fill("08:00", "09:00", "1.000", "2000", "", "")).getMessage();
        assertTrue(kmMessage.contains("milhares"), kmMessage);
        assertRejected("08:00", "09:00", "1", "2", "12.345.678");
    }

    @Test
    void brazilianThousandsWithCommaDecimalIsRejectedWithTheSameMessage() {
        String message = assertThrows(IllegalArgumentException.class,
                () -> fill("08:00", "09:00", "1", "2", "1.000,50", "")).getMessage();
        assertTrue(message.contains("milhares"), message);
    }

    @Test
    void ordinaryDecimalsAreStillAccepted() {
        assertEquals(new BigDecimal("12.50"), fill("08:00", "09:00", "1", "2", "12.5", "").getUberEarnings());
        assertEquals(new BigDecimal("12.50"), fill("08:00", "09:00", "1", "2", "12,5", "").getUberEarnings());
        assertEquals(new BigDecimal("1000.50"), fill("08:00", "09:00", "1", "2", "1000,50", "").getUberEarnings());
        assertEquals(new BigDecimal("1.50"), fill("08:00", "09:00", "1", "2", "1.5", "").getUberEarnings());
        assertEquals(new BigDecimal("1234.57"), fill("08:00", "09:00", "1", "2", "1234.567", "").getUberEarnings());
        // A leading zero can only be a decimal part, never a thousands group.
        assertEquals(new BigDecimal("0.01"), fill("08:00", "09:00", "1", "2", "0.005", "").getUberEarnings());
    }

    @Test
    void commaAndDotGiveSameMoney() {
        assertEquals(fill("08:00", "09:00", "1", "2", "12,34", "").getUberEarnings(),
                fill("08:00", "09:00", "1", "2", "12.34", "").getUberEarnings());
    }

    @Test
    void rejectsMoneyFormsThatAreNotPlainNumbers() {
        assertRejected("08:00", "09:00", "1", "2", ".5");
        assertRejected("08:00", "09:00", "1", "2", "5.");
        assertRejected("08:00", "09:00", "1", "2", "R$ 10");
        assertRejected("08:00", "09:00", "1", "2", "1 000");
        assertRejected("08:00", "09:00", "1", "2", "+5");
    }

    @Test
    void negativeZeroMoneyBecomesZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(fill("08:00", "09:00", "1", "2", "-0", "").getUberEarnings()));
    }

    @Test
    void moneyJustAboveCeilingThatRoundsDownIsAccepted() {
        assertEquals(new BigDecimal("1000000.00"), fill("08:00", "09:00", "1", "2", "1000000.004", "").getUberEarnings());
    }

    @Test
    void moneyJustAboveCeilingThatRoundsUpIsRejected() {
        assertRejected("08:00", "09:00", "1", "2", "1000000.005");
    }

    @Test
    void hugeMoneyIsRejectedWithoutCrash() {
        assertRejected("08:00", "09:00", "1", "2", "9".repeat(400));
    }

    @Test
    void negativeZeroKmIsRejected() {
        assertRejected("08:00", "09:00", "-0", "5", "");
        assertRejected("08:00", "09:00", "0", "-0,0", "");
    }

    /** Notes limit counts UTF-16 chars, so 251 emojis (502 chars) are rejected although the user typed 251 symbols. */
    @Test
    void notesLimitCountsEmojiAsTwoCharsToday() {
        String emojis = "😀".repeat(251);
        assertTrue(emojis.codePointCount(0, emojis.length()) <= 500);
        assertThrows(IllegalArgumentException.class, () -> fill("08:00", "09:00", "1", "2", "", emojis));
    }

    @Test
    void failedKmValidationDoesNotTouchShiftEvenWhenTimesAreValid() {
        WorkShift s = new WorkShift();
        assertThrows(IllegalArgumentException.class, () -> WorkShiftValidator.validateAndPopulate(
                s, DATE, "08:00", "09:00", "10", "5", "100", "", "", "", "", "x"));
        assertNull(s.getTimeStart());
        assertNull(s.getNotes());
    }
}
