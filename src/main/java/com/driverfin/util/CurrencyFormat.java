package com.driverfin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Single place that formats numbers for the screen (pt-BR). */
public final class CurrencyFormat {
    public static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final int MONEY_DECIMALS = 2;
    private static final int DECIMALS_ONE = 1;

    private CurrencyFormat() {}

    /**
     * Example: "R$ 1.234,56". Rounds HALF_UP like the rest of the app and never shows "-R$ 0,00".
     * NumberFormat is not thread-safe, so a new one is created per call.
     */
    public static String money(BigDecimal value) {
        BigDecimal rounded = value.setScale(MONEY_DECIMALS, RoundingMode.HALF_UP);
        return NumberFormat.getCurrencyInstance(PT_BR).format(rounded);
    }

    /** True when the value shows as a loss on screen: negative after rounding (so "-R$ 0,00" never counts). */
    public static boolean isLoss(BigDecimal value) {
        return value.setScale(MONEY_DECIMALS, RoundingMode.HALF_UP).signum() < 0;
    }

    public static String perHour(BigDecimal value) {
        return money(value) + " / h";
    }

    public static String perKm(BigDecimal value) {
        return money(value) + " / km";
    }

    /**
     * One decimal place, example: "8,5". Rounds HALF_UP like the rest of the app (2,25 shows "2,3"); the screen and
     * the PDF both use this method, so they always print the same number.
     */
    public static String oneDecimal(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(PT_BR);
        format.setMinimumFractionDigits(DECIMALS_ONE);
        format.setMaximumFractionDigits(DECIMALS_ONE);
        format.setRoundingMode(RoundingMode.HALF_UP);
        if (!Double.isFinite(value)) return format.format(value);
        // BigDecimal.valueOf uses the shortest decimal text of the double, so 0.25 is a real tie and rounds up.
        return format.format(BigDecimal.valueOf(value).setScale(DECIMALS_ONE, RoundingMode.HALF_UP));
    }

    /** Plain number with a decimal comma and no thousands separator, to fill an input field: "1234,50". */
    public static String editableNumber(BigDecimal value) {
        return value.toPlainString().replace('.', ',');
    }
}
