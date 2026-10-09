package com.driverfin.service;

import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SummaryServiceTest {

    @Test
    public void testCalculateEmpty() {
        FinancialSummary summary = SummaryService.calculate(Collections.emptyList(), new BigDecimal("0.25"));
        assertEquals(BigDecimal.ZERO, summary.totalGross());
        assertEquals(BigDecimal.ZERO, summary.totalCosts());
        assertEquals(BigDecimal.ZERO, summary.totalNet());
    }
}
