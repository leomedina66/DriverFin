package com.driverfin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

/** Soft warnings for unusual shift data (UX 4 and 5). They never block saving. */
public final class ShiftWarnings {
    public static final long HIGH_VALUE_CENTS = 500_000L;
    private static final BigDecimal MIN_USUAL_RATE_PER_HOUR = new BigDecimal("5.00");
    private static final BigDecimal MAX_USUAL_RATE_PER_HOUR = new BigDecimal("300.00");
    private static final int RATE_PRECISION = 10;
    private static final int MONEY_SCALE = 2;

    private ShiftWarnings() {}

    /** End before start: the shift crosses midnight. Equal times: 0 h. Anything else: no note. */
    public static Optional<String> turnNote(LocalTime start, LocalTime end) {
        if (start == null || end == null) return Optional.empty();
        if (end.equals(start)) return Optional.of(FormMessages.EQUAL_TIMES);
        if (end.isAfter(start)) return Optional.empty();
        double hours = Duration.between(start, end).plusDays(1).toMinutes() / 60.0;
        return Optional.of(FormMessages.crossesMidnight(CurrencyFormat.oneDecimal(hours)));
    }

    public static Optional<String> highValue(long cents) {
        if (cents < HIGH_VALUE_CENTS) return Optional.empty();
        return Optional.of(FormMessages.highValue(CurrencyFormat.money(MoneyMask.toReais(cents))));
    }

    /** Gross per hour below R$ 5,00 or above R$ 300,00. Compared without rounding, so 4,999 is still below. */
    public static Optional<String> unusualRate(double hours, BigDecimal gross) {
        if (hours <= 0 || gross == null || gross.signum() <= 0) return Optional.empty();
        BigDecimal perHour = gross.divide(BigDecimal.valueOf(hours), RATE_PRECISION, RoundingMode.HALF_UP);
        boolean tooLow = perHour.compareTo(MIN_USUAL_RATE_PER_HOUR) < 0;
        boolean tooHigh = perHour.compareTo(MAX_USUAL_RATE_PER_HOUR) > 0;
        if (!tooLow && !tooHigh) return Optional.empty();
        // Round away from the limit, so the text never shows the limit itself (4,999 must not read "R$ 5,00/h").
        BigDecimal shown = perHour.setScale(MONEY_SCALE, tooLow ? RoundingMode.FLOOR : RoundingMode.CEILING);
        return Optional.of(FormMessages.unusualRate(CurrencyFormat.money(shown)));
    }
}
