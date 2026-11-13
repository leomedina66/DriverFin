package com.driverfin.util;

import com.driverfin.model.WorkShift;
import com.driverfin.util.ShiftFilter.Period;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftFilterTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private static WorkShift shift(LocalDate date, String uber, String fuel, String notes) {
        WorkShift ws = new WorkShift();
        ws.setDate(date);
        ws.setUberEarnings(new BigDecimal(uber));
        ws.setCostFuel(new BigDecimal(fuel));
        ws.setNotes(notes);
        return ws;
    }

    private static WorkShift shiftOn(LocalDate date) {
        return shift(date, "100.00", "10.00", "");
    }

    private static Predicate<WorkShift> period(Period period) {
        return ShiftFilter.build(period, null, null, "", TODAY);
    }

    @Test
    void allAcceptsEverything() {
        assertTrue(period(Period.ALL).test(shiftOn(LocalDate.of(2000, 1, 1))));
    }

    @Test
    void todayAcceptsOnlyToday() {
        assertTrue(period(Period.TODAY).test(shiftOn(TODAY)));
        assertFalse(period(Period.TODAY).test(shiftOn(TODAY.minusDays(1))));
    }

    @Test
    void lastSevenDaysIncludesTodayAndSixDaysBack() {
        assertTrue(period(Period.LAST_7_DAYS).test(shiftOn(TODAY.minusDays(6))));
        assertFalse(period(Period.LAST_7_DAYS).test(shiftOn(TODAY.minusDays(7))));
        assertFalse(period(Period.LAST_7_DAYS).test(shiftOn(TODAY.plusDays(1))));
    }

    @Test
    void thisMonthCoversWholeMonth() {
        assertTrue(period(Period.THIS_MONTH).test(shiftOn(LocalDate.of(2026, 3, 1))));
        assertTrue(period(Period.THIS_MONTH).test(shiftOn(LocalDate.of(2026, 3, 31))));
        assertFalse(period(Period.THIS_MONTH).test(shiftOn(LocalDate.of(2026, 2, 28))));
    }

    @Test
    void lastMonthCoversPreviousMonthAcrossYearBoundary() {
        Predicate<WorkShift> lastMonth = ShiftFilter.build(Period.LAST_MONTH, null, null, "", LocalDate.of(2026, 1, 10));
        assertTrue(lastMonth.test(shiftOn(LocalDate.of(2025, 12, 31))));
        assertTrue(lastMonth.test(shiftOn(LocalDate.of(2025, 12, 1))));
        assertFalse(lastMonth.test(shiftOn(LocalDate.of(2026, 1, 1))));
    }

    @Test
    void customRangeIsInclusiveAndAllowsOpenEnds() {
        LocalDate start = LocalDate.of(2026, 3, 5);
        LocalDate end = LocalDate.of(2026, 3, 10);
        Predicate<WorkShift> range = ShiftFilter.build(Period.CUSTOM, start, end, "", TODAY);
        assertTrue(range.test(shiftOn(start)));
        assertTrue(range.test(shiftOn(end)));
        assertFalse(range.test(shiftOn(end.plusDays(1))));

        Predicate<WorkShift> openEnd = ShiftFilter.build(Period.CUSTOM, start, null, "", TODAY);
        assertTrue(openEnd.test(shiftOn(LocalDate.of(2030, 1, 1))));
        assertFalse(openEnd.test(shiftOn(start.minusDays(1))));
    }

    @Test
    void searchMatchesNotesIgnoringCase() {
        Predicate<WorkShift> filter = ShiftFilter.build(Period.ALL, null, null, "CHUVA", TODAY);
        assertTrue(filter.test(shift(TODAY, "100", "10", "Dia de chuva forte")));
        assertFalse(filter.test(shift(TODAY, "100", "10", "Sol")));
    }

    @Test
    void searchMatchesDateInBrazilianFormat() {
        Predicate<WorkShift> filter = ShiftFilter.build(Period.ALL, null, null, "15/03", TODAY);
        assertTrue(filter.test(shiftOn(TODAY)));
        assertFalse(filter.test(shiftOn(TODAY.plusDays(1))));
    }

    @Test
    void searchMatchesValueWithCommaOrDot() {
        WorkShift ws = shift(TODAY, "123.45", "10.00", null);
        assertTrue(ShiftFilter.build(Period.ALL, null, null, "123,45", TODAY).test(ws));
        assertTrue(ShiftFilter.build(Period.ALL, null, null, "123.45", TODAY).test(ws));
        assertFalse(ShiftFilter.build(Period.ALL, null, null, "999", TODAY).test(ws));
    }

    @Test
    void periodAndSearchAreCombined() {
        Predicate<WorkShift> filter = ShiftFilter.build(Period.TODAY, null, null, "chuva", TODAY);
        assertFalse(filter.test(shift(TODAY.minusDays(1), "100", "10", "chuva")));
        assertTrue(filter.test(shift(TODAY, "100", "10", "chuva")));
    }

    @Test
    void describeShowsCustomRange() {
        assertEquals("05/03/2026 a 10/03/2026",
                ShiftFilter.describe(Period.CUSTOM, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 10)));
        assertEquals("Hoje", ShiftFilter.describe(Period.TODAY, null, null));
    }
}
