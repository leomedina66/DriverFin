package com.driverfin.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceRateTest {
    @Test
    void parsesDotAndComma() {
        assertEquals(new BigDecimal("0.30"), MaintenanceRate.parse("0.30").orElseThrow());
        assertEquals(new BigDecimal("0.30"), MaintenanceRate.parse(" 0,30 ").orElseThrow());
        assertEquals(BigDecimal.ZERO, MaintenanceRate.parse("0").orElseThrow());
    }

    @Test
    void rejectsNegativeBlankAndGarbage() {
        assertTrue(MaintenanceRate.parse("-0.1").isEmpty());
        assertTrue(MaintenanceRate.parse("").isEmpty());
        assertTrue(MaintenanceRate.parse(null).isEmpty());
        assertTrue(MaintenanceRate.parse("abc").isEmpty());
        assertTrue(MaintenanceRate.parse("1E5").isEmpty());
    }

    @Test
    void formatsWithTwoDecimalsAndComma() {
        assertEquals("0,30", MaintenanceRate.format(new BigDecimal("0.3")));
        assertEquals("0,25", MaintenanceRate.format(new BigDecimal("0.25")));
        assertEquals("1,00", MaintenanceRate.format(BigDecimal.ONE));
        assertEquals("0,13", MaintenanceRate.format(new BigDecimal("0.125")));
    }

    @Test
    void describesTheRateForTheCard() {
        assertEquals("R$\u00A00,25 por km rodado, para pneus, óleo e revisões.",
            MaintenanceRate.describe(new BigDecimal("0.25")));
    }
}
