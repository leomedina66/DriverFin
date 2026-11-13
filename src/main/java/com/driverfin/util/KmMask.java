package com.driverfin.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Free-typing filter for KM fields: digits and one comma while typing, "12.450,0" after leaving the field.
 * Pure: it receives one {@link TextEdit} and returns the text to show.
 */
public final class KmMask {
    public static final int MAX_INTEGER_DIGITS = 9;

    private static final char DECIMAL_SEPARATOR = ',';
    private static final char THOUSANDS_SEPARATOR = '.';
    private static final int MAX_TYPED_DECIMALS = 1;
    private static final int MAX_STORED_DECIMALS = 3;
    private static final int MIN_DISPLAY_DECIMALS = 1;
    private static final int GROUP_SIZE = 3;
    private static final Pattern READABLE = Pattern.compile("(\\d*)(?:,(\\d*))?");
    private static final Pattern READABLE_GROUPED = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})+)(?:,(\\d*))?");
    // A pasted value may carry up to the stored decimals, so a displayed "100,55" can be pasted back.
    private static final Pattern PASTE_WITH_COMMA =
        Pattern.compile("(\\d+|[1-9]\\d{0,2}(?:\\.\\d{3})+),(\\d{0," + MAX_STORED_DECIMALS + "})");
    private static final Pattern PASTE_GROUPED = Pattern.compile("[1-9]\\d{0,2}(?:\\.\\d{3})+");
    // Exactly three digits after a dot look like a thousands group ("0.500"), so a dot decimal has at most two.
    private static final Pattern PASTE_DOT_DECIMAL = Pattern.compile("(\\d+)\\.(\\d{1,2})");
    private static final Pattern ONLY_DIGITS = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]");

    private KmMask() {}

    /** Typing filter (the caret stays where the user types) and paste (replaces the whole value). */
    public static MaskResult apply(TextEdit edit) {
        if (edit.isPaste()) return paste(edit);
        return type(edit);
    }

    /** Exit: blank stays; a readable value gets the display form "12.450,0"; anything else is reported. */
    public static MaskResult formatOnExit(String text) {
        String current = text == null ? "" : text;
        if (current.isBlank()) return MaskResult.unchanged(current, null);
        OptionalDouble km = parse(current);
        if (km.isEmpty()) return MaskResult.unchanged(current, FieldMessage.error(FormMessages.KM_UNREADABLE));
        return MaskResult.of(display(km.getAsDouble()), null);
    }

    /** 12450.0 -> "12.450,0"; 100.55 -> "100,55". Keeps the stored decimals, at most three. */
    public static String display(double km) {
        BigDecimal value = BigDecimal.valueOf(km).setScale(MAX_STORED_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros();
        if (value.scale() < MIN_DISPLAY_DECIMALS) value = value.setScale(MIN_DISPLAY_DECIMALS);
        String[] parts = value.abs().toPlainString().split("\\.");
        String sign = value.signum() < 0 ? "-" : "";
        return sign + groupThousands(parts[0]) + DECIMAL_SEPARATOR + parts[1];
    }

    /** "12.450,5" -> "12450,5" for the validator; blank -> "". Only removes the dots. */
    public static String toPlain(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replace(String.valueOf(THOUSANDS_SEPARATOR), "");
    }

    /** Reads the display form ("12.450,0") or the typing form ("12450,5"). Empty when blank or unreadable. */
    public static OptionalDouble parse(String text) {
        if (text == null || text.isBlank()) return OptionalDouble.empty();
        String trimmed = text.trim();
        Matcher matcher = READABLE_GROUPED.matcher(trimmed);
        if (!matcher.matches()) matcher = READABLE.matcher(trimmed);
        if (!matcher.matches()) return OptionalDouble.empty();
        String integerPart = matcher.group(1).replace(String.valueOf(THOUSANDS_SEPARATOR), "");
        String decimalPart = matcher.group(2) == null ? "" : matcher.group(2);
        if (integerPart.length() > MAX_INTEGER_DIGITS || integerPart.length() + decimalPart.length() == 0) {
            return OptionalDouble.empty();
        }
        String number = (integerPart.isEmpty() ? "0" : integerPart) + "." + (decimalPart.isEmpty() ? "0" : decimalPart);
        return OptionalDouble.of(Double.parseDouble(number));
    }

    /**
     * Typing works on the digits and the comma only: the thousands dots of a shown value ("12.450,0") are ignored,
     * so editing in the middle never leaves a misplaced dot. The result is the plain typing form ("127450,0").
     */
    private static MaskResult type(TextEdit edit) {
        String plainOld = withoutDots(edit.oldText());
        int start = withoutDotsLength(edit.oldText(), edit.start());
        int end = withoutDotsLength(edit.oldText(), edit.end());
        String kept = plainOld.substring(0, start) + plainOld.substring(end);
        if (edit.inserted().isEmpty()) return new MaskResult(kept, start, null);
        char typed = edit.inserted().charAt(0);
        char accepted = typed == THOUSANDS_SEPARATOR ? DECIMAL_SEPARATOR : typed;
        String candidate = kept.substring(0, start) + accepted + kept.substring(start);
        if (!isAcceptableWhileTyping(accepted, start, candidate)) {
            return new MaskResult(edit.oldText(), edit.start(), null);
        }
        return new MaskResult(candidate, start + 1, null);
    }

    private static String withoutDots(String text) {
        return text.replace(String.valueOf(THOUSANDS_SEPARATOR), "");
    }

    /** Length of text[0, position) once the dots are removed: where a caret lands in the dotless text. */
    private static int withoutDotsLength(String text, int position) {
        return withoutDots(text.substring(0, position)).length();
    }

    /** Checks the text that would result from typing one character at the given position. */
    private static boolean isAcceptableWhileTyping(char accepted, int position, String candidate) {
        int comma = candidate.indexOf(DECIMAL_SEPARATOR);
        int decimalDigits = comma < 0 ? 0 : candidate.length() - comma - 1;
        if (accepted == DECIMAL_SEPARATOR) {
            return comma == candidate.lastIndexOf(DECIMAL_SEPARATOR) && decimalDigits <= MAX_TYPED_DECIMALS;
        }
        if (accepted < '0' || accepted > '9') return false;
        boolean afterComma = comma >= 0 && position > comma;
        // Stored values may already have more decimals: only a digit typed after the comma is limited.
        if (afterComma) return decimalDigits <= MAX_TYPED_DECIMALS;
        return (comma < 0 ? candidate.length() : comma) <= MAX_INTEGER_DIGITS;
    }

    private static MaskResult paste(TextEdit edit) {
        String cleaned = WHITESPACE.matcher(edit.inserted()).replaceAll("");
        String editing = toEditingForm(cleaned);
        if (editing == null) {
            return MaskResult.unchanged(edit.oldText(), FieldMessage.error(FormMessages.KM_UNREADABLE));
        }
        return MaskResult.of(editing, null);
    }

    /** Typing form of a pasted text ("12450,5"), or null when it cannot be read. */
    private static String toEditingForm(String cleaned) {
        String result = null;
        Matcher withComma = PASTE_WITH_COMMA.matcher(cleaned);
        Matcher dotDecimal = PASTE_DOT_DECIMAL.matcher(cleaned);
        if (withComma.matches()) {
            result = withDecimals(withComma.group(1), withComma.group(2));
        } else if (ONLY_DIGITS.matcher(cleaned).matches() || PASTE_GROUPED.matcher(cleaned).matches()) {
            result = withDecimals(cleaned, "");
        } else if (dotDecimal.matches()) {
            result = withDecimals(dotDecimal.group(1), dotDecimal.group(2));
        }
        if (result == null) return null;
        int integerDigits = result.contains(String.valueOf(DECIMAL_SEPARATOR))
            ? result.indexOf(DECIMAL_SEPARATOR) : result.length();
        return integerDigits > MAX_INTEGER_DIGITS ? null : result;
    }

    private static String withDecimals(String integerPart, String decimalPart) {
        String digits = integerPart.replace(String.valueOf(THOUSANDS_SEPARATOR), "");
        return decimalPart.isEmpty() ? digits : digits + DECIMAL_SEPARATOR + decimalPart;
    }

    private static String groupThousands(String digits) {
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % GROUP_SIZE == 0) grouped.append(THOUSANDS_SEPARATOR);
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
