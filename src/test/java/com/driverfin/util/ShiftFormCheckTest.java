package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShiftFormCheckTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 1);

    private static ShiftFormInput input(String start, String end, String kmStart, String kmEnd, String notes) {
        return new ShiftFormInput(DATE, start, end, kmStart, kmEnd, notes);
    }

    private static ShiftFormInput valid() {
        return input("08:00", "17:00", "12.000,0", "12.450,0", "");
    }

    private static String messageOf(ShiftField field, ShiftFormInput input) {
        return ShiftFormCheck.checkField(field, input).map(FieldError::message).orElse("");
    }

    /** What the form hands to the validator: the time completed like on leaving the field ("17" -> "17:00"). */
    private static String completedTime(String text) {
        return TimeMask.complete(text).text();
    }

    @Test
    public void emptyFormGivesFiveErrorsInTabOrder() {
        List<FieldError> errors = ShiftFormCheck.checkAll(new ShiftFormInput(null, "", "", "", "", ""));
        assertEquals(List.of(
            new FieldError(ShiftField.DATE, FormMessages.DATE_REQUIRED),
            new FieldError(ShiftField.TIME_START, FormMessages.TIME_START_REQUIRED),
            new FieldError(ShiftField.TIME_END, FormMessages.TIME_END_REQUIRED),
            new FieldError(ShiftField.KM_START, FormMessages.KM_START_REQUIRED),
            new FieldError(ShiftField.KM_END, FormMessages.KM_END_REQUIRED)), errors);
    }

    @Test
    public void nullTextsCountAsEmpty() {
        assertEquals(5, ShiftFormCheck.checkAll(new ShiftFormInput(null, null, null, null, null, null)).size());
    }

    @Test
    public void validFormHasNoErrors() {
        assertTrue(ShiftFormCheck.checkAll(valid()).isEmpty());
    }

    @Test
    public void halfTypedMinutesAreIncomplete() {
        assertEquals(FormMessages.TIME_INCOMPLETE, messageOf(ShiftField.TIME_START, input("08:3", "17:00", "1", "2", "")));
        assertEquals(FormMessages.TIME_INCOMPLETE, messageOf(ShiftField.TIME_END, input("08:00", "17:3", "1", "2", "")));
    }

    @Test
    public void impossibleTimesAreInvalid() {
        assertEquals(FormMessages.TIME_INVALID, messageOf(ShiftField.TIME_START, input("25:00", "17:00", "1", "2", "")));
        assertEquals(FormMessages.TIME_INVALID, messageOf(ShiftField.TIME_END, input("08:00", "08:75", "1", "2", "")));
    }

    @Test
    public void hourOnlyTimesAreCompletable() {
        assertEquals("", messageOf(ShiftField.TIME_START, input("08", "8:", "1", "2", "")));
        assertEquals("", messageOf(ShiftField.TIME_END, input("08:00", "17:", "1", "2", "")));
    }

    @Test
    public void kmEndBelowStartShowsTheFormattedStart() {
        String expected = "O KM final precisa ser maior ou igual ao KM inicial (12.450,0).";
        assertEquals(expected, messageOf(ShiftField.KM_END, input("08:00", "17:00", "12.450,0", "12.000,0", "")));
        assertEquals(expected, messageOf(ShiftField.KM_END, input("08:00", "17:00", "12450", "12000", "")));
    }

    @Test
    public void equalKmIsAllowed() {
        assertEquals("", messageOf(ShiftField.KM_END, input("08:00", "17:00", "12.450,0", "12.450,0", "")));
    }

    @Test
    public void kmEndRuleNeedsBothValuesToBeReadable() {
        assertEquals("", messageOf(ShiftField.KM_END, input("08:00", "17:00", "", "12.000,0", "")));
        assertEquals("", messageOf(ShiftField.KM_END, input("08:00", "17:00", "abc", "12.000,0", "")));
        assertEquals(FormMessages.KM_UNREADABLE,
            messageOf(ShiftField.KM_END, input("08:00", "17:00", "12.450,0", "abc", "")));
    }

    @Test
    public void unreadableKmIsReported() {
        assertEquals(FormMessages.KM_UNREADABLE, messageOf(ShiftField.KM_START, input("08:00", "17:00", "abc", "1", "")));
        assertEquals(FormMessages.KM_UNREADABLE, messageOf(ShiftField.KM_START, input("08:00", "17:00", "-5", "1", "")));
        // The validator rejects a trailing comma, so the check must not let it through.
        assertEquals(FormMessages.KM_UNREADABLE, messageOf(ShiftField.KM_START, input("08:00", "17:00", "12450,", "1", "")));
    }

    @Test
    public void longNotesReportHowManyCharactersAreOver() {
        assertEquals("Passou 3 caracteres do limite de 500.",
            messageOf(ShiftField.NOTES, input("08:00", "17:00", "1", "2", "a".repeat(503))));
        assertEquals("", messageOf(ShiftField.NOTES, input("08:00", "17:00", "1", "2", "a".repeat(500))));
    }

    @Test
    public void moneyFieldsAreNeverChecked() {
        for (ShiftField field : new ShiftField[] {ShiftField.EARN_UBER, ShiftField.EARN_99, ShiftField.EARN_OTHERS,
                ShiftField.COST_FUEL, ShiftField.COST_FOOD_OTHER}) {
            assertEquals(Optional.empty(), ShiftFormCheck.checkField(field, valid()));
        }
    }

    @Test
    public void everythingTheCheckAcceptsTheValidatorAccepts() {
        ShiftFormInput[] samples = {
            valid(),
            input("00:00", "23:59", "0", "0", "ok"),
            input("22:00", "06:00", "100,5", "250,5", "a".repeat(500)),
            input("08:00", "08:00", "12.450,0", "12.450,0", ""),
            input("01:05", "02:10", "1.234.567,8", "1.234.568,0", null),
            // Hour-only times pass the check because the form completes them first (see completedTime).
            input("08:", "17", "12.000,0", "12.450,0", ""),
            input("8", "1", "12.000,0", "12.450,0", "")
        };
        for (ShiftFormInput sample : samples) {
            assertTrue(ShiftFormCheck.checkAll(sample).isEmpty(), sample.toString());
            assertDoesNotThrow(() -> WorkShiftValidator.validateAndPopulate(new WorkShift(), sample.date(),
                completedTime(sample.timeStart()), completedTime(sample.timeEnd()), KmMask.toPlain(sample.kmStart()),
                KmMask.toPlain(sample.kmEnd()), MoneyMask.displayToPlain("0,00"), "0,00", "0,00", "0,00", "0,00",
                sample.notes()), sample.toString());
        }
    }
}
