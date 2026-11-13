package com.driverfin.service;

import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;
import com.driverfin.model.DaySummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SummaryServiceTest {

    private static final BigDecimal MAINT_RATE = new BigDecimal("0.25");

    @Test
    public void testCalculateEmpty() {
        FinancialSummary summary = SummaryService.calculate(Collections.emptyList(), MAINT_RATE);
        assertZero(summary.totalGross());
        assertZero(summary.totalCosts());
        assertZero(summary.totalNet());
        assertZero(summary.avgProfitPerHour());
        assertZero(summary.avgProfitPerKm());
        assertZero(summary.avgGrossPerHour());
        assertZero(summary.avgGrossPerKm());
    }

    @Test
    public void testCalculateTwoShifts() {
        // 8h, 100km, gross 200, costs 50 | 2h, 50km, gross 100, costs 0
        WorkShift first = shift(8, 0, 16, 0, 1000.0, 1100.0, "200.00", "50.00");
        WorkShift second = shift(10, 0, 12, 0, 1100.0, 1150.0, "100.00", "0.00");

        FinancialSummary summary = SummaryService.calculate(List.of(first, second), MAINT_RATE);

        assertEquals(new BigDecimal("300.00"), summary.totalGross());
        assertEquals(new BigDecimal("50.00"), summary.totalCosts());
        assertEquals(new BigDecimal("37.50"), summary.totalMaint());
        assertEquals(new BigDecimal("212.50"), summary.totalNet());
        assertEquals(new BigDecimal("30.00"), summary.avgGrossPerHour());
        assertEquals(new BigDecimal("2.00"), summary.avgGrossPerKm());
        assertEquals(new BigDecimal("21.25"), summary.avgProfitPerHour());
        assertEquals(new BigDecimal("1.42"), summary.avgProfitPerKm());
        assertEquals(10.0, summary.totalHours(), 0.0001);
        assertEquals(150.0, summary.totalKm(), 0.0001);
    }

    private static void assertZero(BigDecimal value) {
        assertEquals(0, BigDecimal.ZERO.compareTo(value), "expected zero but was " + value);
    }

    private static WorkShift shift(int h1, int m1, int h2, int m2, double kmStart, double kmEnd,
                                   String uber, String fuel) {
        return new WorkShift(null, LocalDate.of(2024, 1, 1), LocalTime.of(h1, m1), LocalTime.of(h2, m2),
            kmStart, kmEnd, new BigDecimal(uber), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal(fuel), BigDecimal.ZERO, "");
    }

    private static WorkShift shiftOn(LocalDate date, LocalTime start, LocalTime end, String uber) {
        return new WorkShift(null, date, start, end, 1000.0, 1050.0, new BigDecimal(uber), BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    @Test
    public void forDayCountsAndSumsOnlyThatDay() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        WorkShift morning = shiftOn(today, LocalTime.of(8, 0), LocalTime.of(12, 0), "100.00");
        WorkShift evening = shiftOn(today, LocalTime.of(18, 0), LocalTime.of(20, 0), "50.00");
        WorkShift yesterday = shiftOn(today.minusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 0), "999.00");

        DaySummary day = SummaryService.forDay(List.of(morning, yesterday, evening), today, MAINT_RATE);

        assertEquals(2, day.shiftCount());
        assertEquals(SummaryService.calculate(List.of(morning, evening), MAINT_RATE), day.totals());
    }

    @Test
    public void forDayCountsAShiftCrossingMidnightOnItsStartDate() {
        LocalDate start = LocalDate.of(2026, 11, 10);
        WorkShift night = shiftOn(start, LocalTime.of(22, 0), LocalTime.of(2, 0), "80.00");

        assertEquals(1, SummaryService.forDay(List.of(night), start, MAINT_RATE).shiftCount());
        assertEquals(0, SummaryService.forDay(List.of(night), start.plusDays(1), MAINT_RATE).shiftCount());
    }

    @Test
    public void forDayWithoutShiftsIsEmpty() {
        DaySummary day = SummaryService.forDay(List.of(), LocalDate.of(2026, 11, 10), MAINT_RATE);

        assertEquals(0, day.shiftCount());
        assertZero(day.totals().totalGross());
        assertEquals(0.0, day.totals().totalHours());
    }
}
