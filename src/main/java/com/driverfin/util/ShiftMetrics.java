package com.driverfin.util;

import com.driverfin.model.WorkShift;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Per-shift profit ratios (the gross ratios live in WorkShift). */
public final class ShiftMetrics {
    private static final int MONEY_SCALE = 2;

    private ShiftMetrics() {}

    public static BigDecimal netProfitPerHour(WorkShift shift, BigDecimal maintRate) {
        return divideOrZero(shift.calculateNetProfit(maintRate), BigDecimal.valueOf(shift.calculateHours()));
    }

    public static BigDecimal netProfitPerKm(WorkShift shift, BigDecimal maintRate) {
        return divideOrZero(shift.calculateNetProfit(maintRate), shift.calculateKmExact());
    }

    private static BigDecimal divideOrZero(BigDecimal value, BigDecimal divisor) {
        if (divisor.signum() <= 0) return BigDecimal.ZERO.setScale(MONEY_SCALE);
        return value.divide(divisor, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
