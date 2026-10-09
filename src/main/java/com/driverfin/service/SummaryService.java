package com.driverfin.service;

import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class SummaryService {
    
    public static FinancialSummary calculate(List<WorkShift> shifts, BigDecimal maintRate) {
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalCosts = BigDecimal.ZERO;
        double totalHours = 0.0;
        double totalKm = 0.0;

        for (WorkShift shift : shifts) {
            totalGross = totalGross.add(shift.getGrossIncome());
            totalCosts = totalCosts.add(shift.getTotalCosts());
            totalHours += shift.calculateHours();
            totalKm += shift.calculateKm();
        }

        BigDecimal totalMaint = maintRate.multiply(BigDecimal.valueOf(totalKm));
        BigDecimal totalNet = totalGross.subtract(totalCosts).subtract(totalMaint);

        BigDecimal avgProfitPerHour = BigDecimal.ZERO;
        if (totalHours > 0) {
            avgProfitPerHour = totalNet.divide(BigDecimal.valueOf(totalHours), 2, RoundingMode.HALF_UP);
        }

        BigDecimal avgProfitPerKm = BigDecimal.ZERO;
        if (totalKm > 0) {
            avgProfitPerKm = totalNet.divide(BigDecimal.valueOf(totalKm), 2, RoundingMode.HALF_UP);
        }

        return new FinancialSummary(
            totalGross, totalCosts, totalMaint, totalNet,
            avgProfitPerHour, avgProfitPerKm, totalHours, totalKm
        );
    }
}
