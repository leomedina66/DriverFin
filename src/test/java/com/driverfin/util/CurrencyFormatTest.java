package com.driverfin.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CurrencyFormatTest {
    /** The JDK separates "R$" and the number with a non-breaking space; normalize for comparison. */
    private static String normalized(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }

    @Test
    void formatsMoneyWithBrazilianSeparators() {
        assertEquals("R$ 1.234,50", normalized(CurrencyFormat.money(new BigDecimal("1234.5"))));
    }

    @Test
    void formatsZeroAndNegative() {
        assertEquals("R$ 0,00", normalized(CurrencyFormat.money(BigDecimal.ZERO)));
        assertEquals("-R$ 5,00", normalized(CurrencyFormat.money(new BigDecimal("-5"))));
    }

    @Test
    void formatsPerHourAndPerKm() {
        assertEquals("R$ 25,00 / h", normalized(CurrencyFormat.perHour(new BigDecimal("25"))));
        assertEquals("R$ 1,75 / km", normalized(CurrencyFormat.perKm(new BigDecimal("1.75"))));
    }

    @Test
    void formatsOneDecimal() {
        assertEquals("8,5", CurrencyFormat.oneDecimal(8.5));
        assertEquals("1.200,0", CurrencyFormat.oneDecimal(1200));
    }
}
