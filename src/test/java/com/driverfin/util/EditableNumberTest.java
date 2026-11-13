package com.driverfin.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditableNumberTest {
    @Test
    void usesDecimalCommaAndNoThousandsSeparator() {
        assertEquals("1234,50", CurrencyFormat.editableNumber(new BigDecimal("1234.50")));
        assertEquals("0,25", CurrencyFormat.editableNumber(MaintenanceRate.DEFAULT));
    }

    @Test
    void neverUsesScientificNotationForLargeKmValues() {
        assertEquals("12000000", CurrencyFormat.editableNumber(BigDecimal.valueOf(1.2E7)));
    }

    @Test
    void textShownInTheFormCanBeReadBackByTheValidator() {
        String shown = CurrencyFormat.editableNumber(new BigDecimal("1234.50"));
        com.driverfin.model.WorkShift shift = new com.driverfin.model.WorkShift();
        WorkShiftValidator.validateAndPopulate(shift, java.time.LocalDate.of(2024, 1, 1), "08:00", "09:00",
                CurrencyFormat.editableNumber(BigDecimal.valueOf(123.456)), "500", shown, "", "", "", "", "");
        assertEquals(new BigDecimal("1234.50"), shift.getUberEarnings());
        assertEquals(123.456, shift.getKmStart(), 0.0);
        assertTrue(MaintenanceRate.parse(CurrencyFormat.editableNumber(MaintenanceRate.DEFAULT)).isPresent());
    }
}
