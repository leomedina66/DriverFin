package com.driverfin.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cents mask for money fields: the user types digits and the value is pushed from right to left
 * ("1", "2", "3" shows 0,01 / 0,12 / 1,23). Money is kept as a long number of cents; text is built with
 * integer math, never with NumberFormat.
 */
public final class MoneyMask {
    public static final long MAX_CENTS = 100_000_000L;
    public static final String ZERO = "0,00";

    private static final int MAX_PASTE_LENGTH = 20;
    private static final int CENTS_PER_REAL = 100;
    private static final int DECIMAL_DIGITS = 2;
    private static final int GROUP_SIZE = 3;
    private static final char DECIMAL_SEPARATOR = ',';
    private static final char THOUSANDS_SEPARATOR = '.';
    private static final int DIGIT_BASE = 10;
    private static final Pattern DISPLAY_FORMAT = Pattern.compile("(\\d{1,3}(\\.\\d{3})*),(\\d{2})");
    private static final Pattern GROUPED_INTEGER = Pattern.compile("[1-9]\\d{0,2}(\\.\\d{3})+");
    private static final Pattern DOT_DECIMAL = Pattern.compile("(\\d+)\\.(\\d{1,2})");
    private static final Pattern WITH_COMMA = Pattern.compile("(\\d+|[1-9]\\d{0,2}(\\.\\d{3})+),(\\d{0,2})");
    private static final Pattern ONLY_DIGITS = Pattern.compile("\\d+");
    private static final Pattern IGNORED_IN_PASTE = Pattern.compile("R\\$|[\\s\\u00A0]");

    private MoneyMask() {}

    public static MaskResult apply(TextEdit edit) {
        if (edit.isDeletion()) return delete(edit);
        if (edit.isPaste()) return paste(edit.oldText(), edit.inserted());
        if (edit.inserted().isEmpty()) return MaskResult.unchanged(edit.oldText(), null);
        return type(edit, edit.inserted().charAt(0));
    }

    /** Pasting follows the reading rules of UX 5: a comma means exact value, plain digits mean whole reais. */
    public static MaskResult paste(String oldText, String pasted) {
        String cleaned = IGNORED_IN_PASTE.matcher(pasted == null ? "" : pasted).replaceAll("");
        // Far longer than any allowed value ("1.000.000,00" has 12 characters): refuse before any number is built.
        if (cleaned.length() > MAX_PASTE_LENGTH) {
            boolean looksLikeNumber = cleaned.chars().allMatch(c -> (c >= '0' && c <= '9') || c == ',' || c == '.');
            return MaskResult.unchanged(oldText, looksLikeNumber
                    ? FieldMessage.warning(FormMessages.MONEY_MAX)
                    : FieldMessage.error(FormMessages.MONEY_UNREADABLE));
        }
        BigInteger cents = readPastedCents(cleaned);
        if (cents == null) {
            return MaskResult.unchanged(oldText, FieldMessage.error(FormMessages.MONEY_UNREADABLE));
        }
        if (cents.compareTo(BigInteger.valueOf(MAX_CENTS)) > 0) {
            return MaskResult.unchanged(oldText, FieldMessage.warning(FormMessages.MONEY_MAX));
        }
        return MaskResult.of(display(cents.longValueExact()), null);
    }

    /** 123456 -> "1.234,56"; 0 -> "0,00". */
    public static String display(long cents) {
        return groupedReais(requireNonNegative(cents)) + DECIMAL_SEPARATOR + twoDigits(cents % CENTS_PER_REAL);
    }

    /** 123456 -> "1234,56"; 0 -> "0,00". */
    public static String plain(long cents) {
        return requireNonNegative(cents) / CENTS_PER_REAL + String.valueOf(DECIMAL_SEPARATOR)
            + twoDigits(cents % CENTS_PER_REAL);
    }

    /** "1.234,56" -> 123456; blank -> 0; anything not in the display format throws IllegalArgumentException. */
    public static long parseDisplay(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) return 0;
        Matcher matcher = DISPLAY_FORMAT.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a money text.");
        }
        String reais = matcher.group(1).replace(String.valueOf(THOUSANDS_SEPARATOR), "");
        try {
            return Math.addExact(Math.multiplyExact(Long.parseLong(reais), (long) CENTS_PER_REAL),
                Long.parseLong(matcher.group(3)));
        } catch (ArithmeticException | NumberFormatException tooBigToRead) {
            throw new IllegalArgumentException("Money text is too big.");
        }
    }

    /** The converter that feeds the validator: "1.234,50" -> "1234,50". */
    public static String displayToPlain(String text) {
        return plain(parseDisplay(text));
    }

    /** Reais to cents, HALF_UP. null counts as zero. */
    public static long toCents(BigDecimal reais) {
        if (reais == null) return 0;
        return reais.movePointRight(DECIMAL_DIGITS).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal toReais(long cents) {
        return BigDecimal.valueOf(cents, DECIMAL_DIGITS);
    }

    private static MaskResult delete(TextEdit edit) {
        if (edit.replacesAll()) return MaskResult.of(ZERO, null);
        return MaskResult.of(display(centsOf(edit.oldText()) / DIGIT_BASE), null);
    }

    private static MaskResult type(TextEdit edit, char typed) {
        if (typed >= '0' && typed <= '9') {
            long current = edit.replacesAll() ? 0 : centsOf(edit.oldText());
            long next = current * DIGIT_BASE + (typed - '0');
            if (next > MAX_CENTS) {
                return MaskResult.unchanged(edit.oldText(), FieldMessage.warning(FormMessages.MONEY_MAX));
            }
            return MaskResult.of(display(next), null);
        }
        if (typed == DECIMAL_SEPARATOR || typed == THOUSANDS_SEPARATOR) {
            return MaskResult.unchanged(edit.oldText(), FieldMessage.info(FormMessages.MONEY_COMMA_HINT));
        }
        return MaskResult.unchanged(edit.oldText(), null);
    }

    /** Cents of a text made by this mask: its digits, ignoring separators. Empty text is zero. */
    private static long centsOf(String text) {
        long cents = 0;
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9') cents = Math.min(cents * DIGIT_BASE + (c - '0'), MAX_CENTS * DIGIT_BASE);
        }
        return cents;
    }

    /** Cents of a pasted text already cleaned of "R$" and spaces, or null when it cannot be read. */
    private static BigInteger readPastedCents(String cleaned) {
        Matcher withComma = WITH_COMMA.matcher(cleaned);
        if (withComma.matches()) {
            return centsFrom(withComma.group(1), withComma.group(3));
        }
        if (ONLY_DIGITS.matcher(cleaned).matches()) {
            return centsFrom(cleaned, "");
        }
        if (GROUPED_INTEGER.matcher(cleaned).matches()) {
            return centsFrom(cleaned, "");
        }
        Matcher dotDecimal = DOT_DECIMAL.matcher(cleaned);
        if (dotDecimal.matches()) {
            return centsFrom(dotDecimal.group(1), dotDecimal.group(2));
        }
        return null;
    }

    private static BigInteger centsFrom(String integerPart, String decimalPart) {
        BigInteger reais = new BigInteger(integerPart.replace(String.valueOf(THOUSANDS_SEPARATOR), ""));
        String paddedDecimals = (decimalPart + "0".repeat(DECIMAL_DIGITS)).substring(0, DECIMAL_DIGITS);
        return reais.multiply(BigInteger.valueOf(CENTS_PER_REAL)).add(new BigInteger(paddedDecimals));
    }

    private static String groupedReais(long cents) {
        String digits = Long.toString(cents / CENTS_PER_REAL);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % GROUP_SIZE == 0) grouped.append(THOUSANDS_SEPARATOR);
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }

    private static String twoDigits(long value) {
        return value < DIGIT_BASE ? "0" + value : Long.toString(value);
    }

    private static long requireNonNegative(long cents) {
        if (cents < 0) throw new IllegalArgumentException("Money cannot be negative.");
        return cents;
    }
}
