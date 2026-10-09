package com.driverfin.model;

import java.math.BigDecimal;

public record FinancialSummary(
    BigDecimal totalGross,
    BigDecimal totalCosts,
    BigDecimal totalMaint,
    BigDecimal totalNet,
    BigDecimal avgProfitPerHour,
    BigDecimal avgProfitPerKm,
    double totalHours,
    double totalKm
) {}
