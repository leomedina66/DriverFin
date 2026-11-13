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

public class WorkShiftValidatorTest {

    private static final LocalDate DATE = LocalDate.of(2024, 1, 1);

    private WorkShift validate(String start, String end, String kmStart, String kmEnd, String uber, String notes) {
        WorkShift shift = new WorkShift();
        WorkShiftValidator.validateAndPopulate(shift, DATE, start, end, kmStart, kmEnd, uber, "", "", "", "", notes);
        return shift;
    }

    private String errorOf(String start, String end, String kmStart, String kmEnd, String uber, String notes) {
        return assertThrows(IllegalArgumentException.class,
            () -> validate(start, end, kmStart, kmEnd, uber, notes)).getMessage();
    }

    @Test
    public void acceptsValidInputWithCommaDecimals() {
        WorkShift shift = validate("08:00", "16:30", "100,5", "250", "150,50", "ok");
        assertEquals(LocalTime.of(8, 0), shift.getTimeStart());
        assertEquals(LocalTime.of(16, 30), shift.getTimeEnd());
        assertEquals(100.5, shift.getKmStart());
        assertEquals(new BigDecimal("150.50"), shift.getUberEarnings());
        assertEquals(new BigDecimal("0.00"), shift.getApp99Earnings());
    }

    @Test
    public void acceptsHourWithOneDigit() {
        WorkShift shift = validate("8:00", "9:05", "1", "2", "", "");
        assertEquals(LocalTime.of(8, 0), shift.getTimeStart());
        assertEquals(LocalTime.of(9, 5), shift.getTimeEnd());
    }

    @Test
    public void roundsMoneyHalfUpInsteadOfTruncating() {
        assertEquals(new BigDecimal("10.01"), validate("08:00", "09:00", "1", "2", "10,005", "").getUberEarnings());
        assertEquals(new BigDecimal("10.00"), validate("08:00", "09:00", "1", "2", "10,004", "").getUberEarnings());
    }

    @Test
    public void acceptsMaximumMoneyAndNotesLength() {
        validate("08:00", "09:00", "1", "2", "1000000", "a".repeat(500));
    }

    @Test
    public void rejectsInvalidTime() {
        assertTrue(errorOf("25:00", "09:00", "1", "2", "", "").contains("Horário inicial"));
        assertTrue(errorOf("08:00", "abc", "1", "2", "", "").contains("Horário final"));
        assertTrue(errorOf(null, "09:00", "1", "2", "", "").contains("Horário inicial"));
    }

    @Test
    public void rejectsInvalidKm() {
        assertTrue(errorOf("08:00", "09:00", "abc", "2", "", "").contains("KM Inicial"));
        assertTrue(errorOf("08:00", "09:00", "1", "NaN", "", "").contains("KM Final"));
        assertTrue(errorOf("08:00", "09:00", "1", "Infinity", "", "").contains("KM Final"));
        assertTrue(errorOf("08:00", "09:00", "1", "1E+30", "", "").contains("KM Final"));
        assertTrue(errorOf("08:00", "09:00", "1", "9".repeat(400), "", "").contains("grande demais"));
    }

    @Test
    public void rejectsNegativeKmAndFinalSmallerThanInitial() {
        assertTrue(errorOf("08:00", "09:00", "-1", "2", "", "").contains("negativo"));
        assertEquals("KM Final não pode ser menor que o Inicial.", errorOf("08:00", "09:00", "10", "5", "", ""));
    }

    @Test
    public void rejectsInvalidMoney() {
        assertTrue(errorOf("08:00", "09:00", "1", "2", "1E+30", "").contains("Uber"));
        assertTrue(errorOf("08:00", "09:00", "1", "2", "1.2.3", "").contains("Uber"));
        assertTrue(errorOf("08:00", "09:00", "1", "2", "abc", "").contains("Uber"));
    }

    @Test
    public void rejectsNegativeMoneyAndMoneyAboveCeiling() {
        assertTrue(errorOf("08:00", "09:00", "1", "2", "-5", "").contains("negativo"));
        assertTrue(errorOf("08:00", "09:00", "1", "2", "1000000.01", "").contains("1.000.000,00"));
    }

    @Test
    public void rejectsTooLongNotes() {
        assertTrue(errorOf("08:00", "09:00", "1", "2", "", "a".repeat(501)).contains("500"));
    }

    @Test
    public void rejectsMissingDate() {
        WorkShift shift = new WorkShift();
        assertThrows(IllegalArgumentException.class, () -> WorkShiftValidator.validateAndPopulate(
            shift, null, "08:00", "09:00", "1", "2", "", "", "", "", "", ""));
    }

    @Test
    public void failedValidationLeavesShiftUntouched() {
        WorkShift shift = new WorkShift();
        assertThrows(IllegalArgumentException.class, () -> WorkShiftValidator.validateAndPopulate(
            shift, DATE, "08:00", "09:00", "1", "2", "abc", "", "", "", "", ""));
        assertNull(shift.getDate());
        assertNull(shift.getTimeStart());
    }

    private ShiftField failingField(LocalDate date, String start, String end, String kmStart, String kmEnd,
                                    String uber, String app99, String others, String fuel, String food, String notes) {
        return assertThrows(FieldValidationException.class, () -> WorkShiftValidator.validateAndPopulate(
            new WorkShift(), date, start, end, kmStart, kmEnd, uber, app99, others, fuel, food, notes)).getField();
    }

    @Test
    public void reportsTheFieldOfEachFailure() {
        assertEquals(ShiftField.DATE, failingField(null, "08:00", "09:00", "1", "2", "", "", "", "", "", ""));
        assertEquals(ShiftField.TIME_START, failingField(DATE, "xx", "09:00", "1", "2", "", "", "", "", "", ""));
        assertEquals(ShiftField.TIME_END, failingField(DATE, "08:00", "xx", "1", "2", "", "", "", "", "", ""));
        assertEquals(ShiftField.KM_START, failingField(DATE, "08:00", "09:00", "abc", "2", "", "", "", "", "", ""));
        assertEquals(ShiftField.KM_END, failingField(DATE, "08:00", "09:00", "1", "abc", "", "", "", "", "", ""));
        assertEquals(ShiftField.KM_END, failingField(DATE, "08:00", "09:00", "5", "2", "", "", "", "", "", ""));
        assertEquals(ShiftField.EARN_UBER, failingField(DATE, "08:00", "09:00", "1", "2", "abc", "", "", "", "", ""));
        assertEquals(ShiftField.EARN_99, failingField(DATE, "08:00", "09:00", "1", "2", "", "abc", "", "", "", ""));
        assertEquals(ShiftField.EARN_OTHERS, failingField(DATE, "08:00", "09:00", "1", "2", "", "", "abc", "", "", ""));
        assertEquals(ShiftField.COST_FUEL, failingField(DATE, "08:00", "09:00", "1", "2", "", "", "", "abc", "", ""));
        assertEquals(ShiftField.COST_FOOD_OTHER, failingField(DATE, "08:00", "09:00", "1", "2", "", "", "", "", "abc", ""));
        assertEquals(ShiftField.NOTES,
            failingField(DATE, "08:00", "09:00", "1", "2", "", "", "", "", "", "a".repeat(501)));
    }
}
