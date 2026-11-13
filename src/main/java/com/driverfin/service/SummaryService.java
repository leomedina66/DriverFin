package com.driverfin.service;

import com.driverfin.model.DaySummary;
import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class SummaryService {
    private static final int MONEY_SCALE = 2;

    public static FinancialSummary calculate(List<WorkShift> shifts, BigDecimal maintRate) {
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;
        BigDecimal totalMaint = BigDecimal.ZERO;
        BigDecimal totalKm = BigDecimal.ZERO;
        double totalHours = 0.0;

        for (WorkShift shift : shifts) {
            totalGross = totalGross.add(shift.getGrossIncome());
            totalCosts = totalCosts.add(shift.getTotalCosts());
            // Sum of the per-shift (already rounded) reserves, so the card equals the table column total.
            totalMaint = totalMaint.add(shift.calculateMaintReserve(maintRate));
            totalKm = totalKm.add(shift.calculateKmExact());
            totalHours += shift.calculateHours();
        }

        totalGross = totalGross.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        totalCosts = totalCosts.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        totalMaint = totalMaint.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalNet = totalGross.subtract(totalCosts).subtract(totalMaint);
        BigDecimal hours = BigDecimal.valueOf(totalHours);

        return new FinancialSummary(
            totalGross, totalCosts, totalMaint, totalNet,
            divideOrZero(totalNet, hours), divideOrZero(totalNet, totalKm),
            divideOrZero(totalGross, hours), divideOrZero(totalGross, totalKm),
            totalHours, totalKm.doubleValue()
        );
    }

    /** Totals of the shifts whose date is the given day. A shift crossing midnight counts on its start date. */
    public static DaySummary forDay(List<WorkShift> shifts, LocalDate day, BigDecimal maintRate) {
        List<WorkShift> ofDay = shifts.stream().filter(shift -> day.equals(shift.getDate())).toList();
        return new DaySummary(ofDay.size(), calculate(ofDay, maintRate));
    }

    private static BigDecimal divideOrZero(BigDecimal value, BigDecimal divisor) {
        if (divisor.signum() <= 0) return BigDecimal.ZERO.setScale(MONEY_SCALE);
        return value.divide(divisor, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
