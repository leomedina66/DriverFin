package com.driverfin.service;

import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SummaryServiceEdgeTest {
    private static final BigDecimal RATE = new BigDecimal("0.25");

    private static WorkShift shift(String start, String end, double kmStart, double kmEnd, String uber, String fuel) {
        return new WorkShift(null, LocalDate.of(2024, 1, 1), LocalTime.parse(start), LocalTime.parse(end),
                kmStart, kmEnd, new BigDecimal(uber), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(fuel), BigDecimal.ZERO, "");
    }

    private static void assertZero(BigDecimal v) {
        assertEquals(0, BigDecimal.ZERO.compareTo(v), "expected zero but was " + v);
    }

    @Test
    void grossPerHourIsZeroWhenAllShiftsHaveZeroHours() {
        FinancialSummary s = SummaryService.calculate(List.of(shift("08:00", "08:00", 0, 10, "100", "0")), RATE);
        assertZero(s.avgGrossPerHour());
        assertZero(s.avgProfitPerHour());
        assertEquals(new BigDecimal("10.00"), s.avgGrossPerKm());
    }

    @Test
    void grossPerKmIsZeroWhenAllShiftsHaveZeroKm() {
        FinancialSummary s = SummaryService.calculate(List.of(shift("08:00", "10:00", 100, 100, "100", "0")), RATE);
        assertZero(s.avgGrossPerKm());
        assertZero(s.avgProfitPerKm());
        assertEquals(new BigDecimal("50.00"), s.avgGrossPerHour());
    }

    @Test
    void zeroKmAndZeroHoursGiveNoDivisionError() {
        FinancialSummary s = SummaryService.calculate(List.of(shift("08:00", "08:00", 5, 5, "100", "0")), RATE);
        assertZero(s.avgGrossPerHour());
        assertZero(s.avgGrossPerKm());
        assertEquals(new BigDecimal("100.00"), s.totalNet());
    }

    @Test
    void shiftWithKmEndBelowStartCountsAsZeroKmNotNegative() {
        // Data that bypassed the validator (e.g. old database rows).
        FinancialSummary s = SummaryService.calculate(List.of(shift("08:00", "09:00", 100, 90, "50", "0")), RATE);
        assertZero(s.totalMaint());
        assertEquals(0.0, s.totalKm(), 0.0);
    }

    @Test
    void overnightShiftsAreSummedAsPositiveHours() {
        FinancialSummary s = SummaryService.calculate(List.of(
                shift("22:00", "02:00", 0, 10, "40", "0"),
                shift("23:30", "00:30", 10, 20, "20", "0")), RATE);
        assertEquals(5.0, s.totalHours(), 0.0001);
        assertEquals(new BigDecimal("12.00"), s.avgGrossPerHour());
    }

    @Test
    void thirdsOfAnHourAccumulateWithoutDrift() {
        List<WorkShift> shifts = new ArrayList<>();
        for (int i = 0; i < 300; i++) shifts.add(shift("08:00", "08:20", 0, 1, "10", "0"));
        FinancialSummary s = SummaryService.calculate(shifts, RATE);
        assertEquals(100.0, s.totalHours(), 1e-6);
        assertEquals(new BigDecimal("3000.00"), s.totalGross());
        assertEquals(new BigDecimal("30.00"), s.avgGrossPerHour());
    }

    @Test
    void hundredsOfShiftsGiveExactMoneyTotals() {
        List<WorkShift> shifts = new ArrayList<>();
        for (int i = 0; i < 500; i++) shifts.add(shift("08:00", "10:00", 1000.1 + i, 1010.3 + i, "33.33", "1.11"));
        FinancialSummary s = SummaryService.calculate(shifts, RATE);
        assertEquals(new BigDecimal("16665.00"), s.totalGross());
        assertEquals(new BigDecimal("555.00"), s.totalCosts());
        assertEquals(1000.0, s.totalHours(), 1e-6);
        // each shift is 10.2 km (exact decimal difference of the double text values) -> 5100 km
        assertEquals(5100.0, s.totalKm(), 1e-6);
        assertEquals(new BigDecimal("1275.00"), s.totalMaint());
    }

    @Test
    void zeroMaintenanceRateGivesZeroMaintenance() {
        FinancialSummary s = SummaryService.calculate(List.of(shift("08:00", "10:00", 0, 100, "100", "10")), BigDecimal.ZERO);
        assertZero(s.totalMaint());
        assertEquals(new BigDecimal("90.00"), s.totalNet());
    }

    @Test
    void negativeNetProfitIsKeptNegative() {
        FinancialSummary s = SummaryService.calculate(List.of(shift("08:00", "09:00", 0, 100, "10", "50")), RATE);
        assertEquals(new BigDecimal("-65.00"), s.totalNet());
        assertEquals(new BigDecimal("-65.00"), s.avgProfitPerHour());
    }

    /**
     * 100 shifts of 0.3 km at R$ 0,25/km: each reserve is 0.075 -> 0.08 (HALF_UP).
     * The card total must equal the sum of the table column (R$ 8,00), not the 30 km total (R$ 7,50).
     */
    @Test
    void maintenanceTotalEqualsSumOfPerShiftReserves() {
        List<WorkShift> shifts = new ArrayList<>();
        for (int i = 0; i < 100; i++) shifts.add(shift("08:00", "09:00", 100, 100.3, "10", "0"));

        BigDecimal perShiftSum = BigDecimal.ZERO;
        for (WorkShift w : shifts) perShiftSum = perShiftSum.add(w.calculateMaintReserve(RATE));
        BigDecimal summaryMaint = SummaryService.calculate(shifts, RATE).totalMaint();

        assertEquals(new BigDecimal("8.00"), perShiftSum);
        assertEquals(perShiftSum, summaryMaint);
    }

    @Test
    void netProfitInSummaryEqualsSumOfShiftNetProfits() {
        List<WorkShift> shifts = new ArrayList<>();
        for (int i = 0; i < 100; i++) shifts.add(shift("08:00", "09:00", 100, 100.3, "10", "0"));

        BigDecimal perShiftNet = BigDecimal.ZERO;
        for (WorkShift w : shifts) perShiftNet = perShiftNet.add(w.calculateNetProfit(RATE));

        assertEquals(new BigDecimal("992.00"), perShiftNet);
        assertEquals(perShiftNet, SummaryService.calculate(shifts, RATE).totalNet());
    }
}
