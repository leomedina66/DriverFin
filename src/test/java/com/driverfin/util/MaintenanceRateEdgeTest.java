package com.driverfin.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceRateEdgeTest {
    @Test
    void parsesCommaAndDotToSameValue() {
        assertEquals(0, new BigDecimal("0.25").compareTo(MaintenanceRate.parse("0,25").orElseThrow()));
        assertEquals(0, new BigDecimal("0.25").compareTo(MaintenanceRate.parse("0.25").orElseThrow()));
    }

    @Test
    void rejectsWhitespaceOnlyAndInnerSpaces() {
        assertTrue(MaintenanceRate.parse("   ").isEmpty());
        assertTrue(MaintenanceRate.parse("0 25").isEmpty());
        assertTrue(MaintenanceRate.parse("0, 25").isEmpty());
    }

    @Test
    void rejectsScientificNotationNaNAndInfinity() {
        assertTrue(MaintenanceRate.parse("1e3").isEmpty());
        assertTrue(MaintenanceRate.parse("NaN").isEmpty());
        assertTrue(MaintenanceRate.parse("Infinity").isEmpty());
    }

    @Test
    void rejectsIncompleteOrSignedNumbers() {
        assertTrue(MaintenanceRate.parse(".5").isEmpty());
        assertTrue(MaintenanceRate.parse("5.").isEmpty());
        assertTrue(MaintenanceRate.parse("+0.25").isEmpty());
        assertTrue(MaintenanceRate.parse("-0").isEmpty());
        assertTrue(MaintenanceRate.parse("1.000,50").isEmpty());
        assertTrue(MaintenanceRate.parse("1,2,3").isEmpty());
    }

    @Test
    void rejectsNonAsciiDigits() {
        assertTrue(MaintenanceRate.parse("٣").isEmpty()); // Arabic-indic digit three
    }

    @Test
    void zeroRateIsAccepted() {
        assertEquals(0, BigDecimal.ZERO.compareTo(MaintenanceRate.parse("0,00").orElseThrow()));
    }

    @Test
    void rateAboveCeilingIsRejected() {
        assertTrue(MaintenanceRate.parse("9".repeat(400)).isEmpty());
        assertTrue(MaintenanceRate.parse("1000000").isEmpty());
        assertTrue(MaintenanceRate.parse("100,01").isEmpty());
    }

    @Test
    void rateExactlyAtCeilingIsAccepted() {
        assertEquals(0, MaintenanceRate.MAX.compareTo(MaintenanceRate.parse("100").orElseThrow()));
        assertTrue(MaintenanceRate.parse("100,00").isPresent());
        assertTrue(MaintenanceRate.parse("99,99").isPresent());
    }
}
