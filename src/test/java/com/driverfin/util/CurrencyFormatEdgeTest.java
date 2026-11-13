package com.driverfin.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyFormatEdgeTest {
    private static String n(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }

    @Test
    void formatsMillionsWithTwoThousandSeparators() {
        assertEquals("R$ 1.000.000,00", n(CurrencyFormat.money(new BigDecimal("1000000"))));
    }

    @Test
    void formatsEnormousNumbersWithoutScientificNotation() {
        assertEquals("R$ 1.000.000.000.000.000.000,00", n(CurrencyFormat.money(new BigDecimal("1e18"))));
    }

    @Test
    void formatsNegativeWithThousands() {
        assertEquals("-R$ 1.234,56", n(CurrencyFormat.money(new BigDecimal("-1234.56"))));
    }

    @Test
    void keepsTwoDecimalsForWholeAndShortValues() {
        assertEquals("R$ 0,50", n(CurrencyFormat.money(new BigDecimal("0.5"))));
        assertEquals("R$ 7,00", n(CurrencyFormat.money(new BigDecimal("7"))));
    }

    /** App rounds money HALF_UP everywhere; the screen must agree. 10.005 -> 10,01. */
    @Test
    void roundsHalfUpAtThirdDecimalLikeTheRestOfTheApp() {
        assertEquals("R$ 10,01", n(CurrencyFormat.money(new BigDecimal("10.005"))));
    }

    @Test
    void roundsHalfUpAtFiveThousandthsFromZero() {
        assertEquals("R$ 0,01", n(CurrencyFormat.money(new BigDecimal("0.005"))));
    }

    @Test
    void roundsHalfUpOnEvenSecondDecimal() {
        assertEquals("R$ 0,03", n(CurrencyFormat.money(new BigDecimal("0.025"))));
    }

    @Test
    void tinyNegativeShowsNoNegativeZero() {
        // -0.004 rounds to zero; a "-R$ 0,00" would confuse the user.
        assertEquals("R$ 0,00", n(CurrencyFormat.money(new BigDecimal("-0.004"))));
    }

    @Test
    void moneyOfNullThrows() {
        assertThrows(RuntimeException.class, () -> CurrencyFormat.money(null));
    }

    @Test
    void oneDecimalHandlesZeroNegativeAndRounding() {
        assertEquals("0,0", CurrencyFormat.oneDecimal(0));
        assertEquals("-8,5", CurrencyFormat.oneDecimal(-8.5));
        assertEquals("1.234.567,9", CurrencyFormat.oneDecimal(1234567.89));
        assertEquals("0,1", CurrencyFormat.oneDecimal(0.06));
    }

    @Test
    void oneDecimalOfNaNAndInfinityDoesNotThrow() {
        // Documents the current behavior only: no exception for non-finite values.
        CurrencyFormat.oneDecimal(Double.NaN);
        CurrencyFormat.oneDecimal(Double.POSITIVE_INFINITY);
    }
}
