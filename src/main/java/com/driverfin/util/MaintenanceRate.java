package com.driverfin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parsing of the maintenance rate (R$ per km) typed by the user or stored in preferences. */
public final class MaintenanceRate {
    public static final BigDecimal DEFAULT = new BigDecimal("0.25");
    /** Highest accepted rate in R$ per km; anything above is almost certainly a typing mistake. */
    public static final BigDecimal MAX = new BigDecimal("100");
    private static final int RATE_DECIMALS = 2;
    private static final Pattern NON_NEGATIVE_NUMBER = Pattern.compile("\\d+([.,]\\d+)?");

    private MaintenanceRate() {}

    /** The rate as it is kept and stored: two decimals, HALF_UP (0.333 -> 0.33). */
    public static BigDecimal rounded(BigDecimal rate) {
        return rate.setScale(RATE_DECIMALS, RoundingMode.HALF_UP);
    }

    /** Two decimals with a comma, example: 0.3 -> "0,30". */
    public static String format(BigDecimal rate) {
        return CurrencyFormat.editableNumber(rounded(rate));
    }

    /** Explanation for the maintenance card, example: "R$ 0,25 por km rodado, para pneus, óleo e revisões." */
    public static String describe(BigDecimal rate) {
        return "R$\u00A0" + format(rate) + " por km rodado, para pneus, óleo e revisões.";
    }

    /** Empty when the text is blank, not a plain number, negative, or above {@link #MAX}. */
    public static Optional<BigDecimal> parse(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!NON_NEGATIVE_NUMBER.matcher(trimmed).matches()) return Optional.empty();
        BigDecimal value = new BigDecimal(trimmed.replace(",", "."));
        return value.compareTo(MAX) > 0 ? Optional.empty() : Optional.of(value);
    }
}
