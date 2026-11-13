package com.driverfin.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShiftWarningsTest {

    @Test
    public void turnCrossingMidnightShowsTheHours() {
        assertEquals(Optional.of("Turno atravessa a meia-noite: 8,0 h."),
            ShiftWarnings.turnNote(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        assertEquals(Optional.of("Turno atravessa a meia-noite: 4,5 h."),
            ShiftWarnings.turnNote(LocalTime.of(23, 0), LocalTime.of(3, 30)));
    }

    @Test
    public void equalTimesGiveTheEqualTimesNote() {
        assertEquals(Optional.of(FormMessages.EQUAL_TIMES),
            ShiftWarnings.turnNote(LocalTime.of(8, 0), LocalTime.of(8, 0)));
    }

    @Test
    public void normalTurnHasNoNote() {
        assertTrue(ShiftWarnings.turnNote(LocalTime.of(8, 0), LocalTime.of(17, 0)).isEmpty());
        assertTrue(ShiftWarnings.turnNote(null, LocalTime.of(17, 0)).isEmpty());
    }

    @Test
    public void highValueStartsAtFiveThousand() {
        assertTrue(ShiftWarnings.highValue(499_999).isEmpty());
        assertEquals(Optional.of(FormMessages.highValue(CurrencyFormat.money(new BigDecimal("5000.00")))),
            ShiftWarnings.highValue(500_000));
        assertEquals(Optional.of(FormMessages.highValue(CurrencyFormat.money(new BigDecimal("5000.01")))),
            ShiftWarnings.highValue(500_001));
    }

    @Test
    public void rateBelowFiveIsUnusual() {
        assertTrue(ShiftWarnings.unusualRate(10, new BigDecimal("49.99")).isPresent());
        assertTrue(ShiftWarnings.unusualRate(10, new BigDecimal("50.00")).isEmpty());
    }

    @Test
    public void rateAboveThreeHundredIsUnusual() {
        assertTrue(ShiftWarnings.unusualRate(1, new BigDecimal("300.00")).isEmpty());
        assertTrue(ShiftWarnings.unusualRate(1, new BigDecimal("300.01")).isPresent());
    }

    @Test
    public void unusualRateMessageShowsTheFormattedRate() {
        assertEquals(Optional.of(FormMessages.unusualRate(CurrencyFormat.money(new BigDecimal("3.20")))),
            ShiftWarnings.unusualRate(10, new BigDecimal("32.00")));
    }

    @Test
    public void messageNeverShowsTheLimitItself() {
        // 4,999/h and 300,001/h are outside the usual range: the text must not read R$ 5,00/h or R$ 300,00/h.
        assertEquals(Optional.of(FormMessages.unusualRate(CurrencyFormat.money(new BigDecimal("4.99")))),
            ShiftWarnings.unusualRate(1, new BigDecimal("4.999")));
        assertEquals(Optional.of(FormMessages.unusualRate(CurrencyFormat.money(new BigDecimal("300.01")))),
            ShiftWarnings.unusualRate(1, new BigDecimal("300.001")));
        assertEquals(Optional.of(FormMessages.unusualRate(CurrencyFormat.money(new BigDecimal("3.33")))),
            ShiftWarnings.unusualRate(3, new BigDecimal("10.00")));
    }

    @Test
    public void noHoursOrNoGrossGivesNoWarning() {
        assertTrue(ShiftWarnings.unusualRate(0, new BigDecimal("100.00")).isEmpty());
        assertTrue(ShiftWarnings.unusualRate(8, BigDecimal.ZERO).isEmpty());
        assertTrue(ShiftWarnings.unusualRate(8, null).isEmpty());
    }
}
