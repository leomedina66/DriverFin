package com.driverfin.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live mask for a time field ("HH:mm"). Pure: it receives one {@link TextEdit} and returns the text to show.
 * The state of the field is its digits (0 to 4); the text is always rebuilt from them.
 */
public final class TimeMask {
    private static final int MAX_DIGITS = 4;
    private static final int HOUR_DIGITS = 2;
    private static final int MAX_HOUR = 23;
    private static final int MAX_MINUTE = 59;
    private static final char SEPARATOR = ':';
    private static final Pattern COMPLETE = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern HOUR_AND_MINUTES = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private TimeMask() {}

    public static MaskResult apply(TextEdit edit) {
        if (edit.isDeletion()) return delete(edit);
        if (edit.isPaste()) return paste(edit);
        if (edit.inserted().isEmpty()) return MaskResult.unchanged(edit.oldText(), null);
        return type(edit, edit.inserted().charAt(0));
    }

    /** Exit/Enter: fills in what is obvious ("8" -> "08:00") and reports minutes that are only half typed. */
    public static MaskResult complete(String text) {
        String current = text == null ? "" : text;
        String digits = digitsOf(current);
        switch (digits.length()) {
            case 0:
                return MaskResult.unchanged(current, null);
            case 1:
            case HOUR_DIGITS:
                return completeHour(current, digits);
            case HOUR_DIGITS + 1:
                return MaskResult.unchanged(current, FieldMessage.error(FormMessages.TIME_INCOMPLETE));
            default:
                return isComplete(current)
                    ? MaskResult.unchanged(current, null)
                    : MaskResult.unchanged(current, FieldMessage.error(FormMessages.TIME_INVALID));
        }
    }

    /** Exactly "HH:mm", from 00:00 to 23:59. */
    public static boolean isComplete(String text) {
        return text != null && COMPLETE.matcher(text).matches();
    }

    private static MaskResult completeHour(String current, String digits) {
        int hour = Integer.parseInt(digits);
        if (hour > MAX_HOUR) {
            return MaskResult.unchanged(current, FieldMessage.error(FormMessages.TIME_INVALID));
        }
        return MaskResult.of(format(String.format("%02d", hour) + "00"), null);
    }

    private static MaskResult delete(TextEdit edit) {
        if (edit.replacesAll()) return MaskResult.of("", null);
        String digits = digitsOf(edit.oldText());
        if (digits.isEmpty()) return MaskResult.unchanged(edit.oldText(), null);
        return MaskResult.of(format(digits.substring(0, digits.length() - 1)), null);
    }

    private static MaskResult type(TextEdit edit, char typed) {
        String digits = edit.replacesAll() ? "" : digitsOf(edit.oldText());
        if (typed >= '0' && typed <= '9') {
            return typeDigit(edit, digits, typed);
        }
        if (typed == SEPARATOR && digits.length() == 1) {
            return MaskResult.of(format("0" + digits), null);
        }
        return MaskResult.unchanged(edit.oldText(), null);
    }

    private static MaskResult typeDigit(TextEdit edit, String digits, char digit) {
        if (digits.isEmpty() && digit > '2') {
            return MaskResult.of(format("0" + digit), null);
        }
        if (!isDigitAccepted(digits, digit)) {
            return MaskResult.unchanged(edit.oldText(), null);
        }
        return MaskResult.of(format(digits + digit), null);
    }

    private static boolean isDigitAccepted(String digits, char digit) {
        switch (digits.length()) {
            case 0:
                return true;
            case 1:
                return digits.charAt(0) != '2' || digit <= '3';
            case HOUR_DIGITS:
                return digit <= '5';
            case HOUR_DIGITS + 1:
                return true;
            default:
                return false;
        }
    }

    private static MaskResult paste(TextEdit edit) {
        String pasted = edit.inserted().trim();
        Matcher withColon = HOUR_AND_MINUTES.matcher(pasted);
        String digits = withColon.matches()
            ? String.format("%02d", Integer.parseInt(withColon.group(1))) + withColon.group(2)
            : pastedDigits(pasted);
        if (digits.isEmpty()) return MaskResult.unchanged(edit.oldText(), null);
        if (!isValidPaste(digits)) {
            return MaskResult.unchanged(edit.oldText(), FieldMessage.error(FormMessages.TIME_INVALID));
        }
        return MaskResult.of(format(digits), null);
    }

    /** Only digits, first 4. Hour only (1-2 digits) stays as is; 3 digits are H + MM, so the hour gets a zero. */
    private static String pastedDigits(String pasted) {
        String digits = digitsOf(pasted);
        if (digits.length() == HOUR_DIGITS + 1) return "0" + digits;
        if (digits.length() == 1) return "0" + digits;
        return digits;
    }

    private static boolean isValidPaste(String digits) {
        if (Integer.parseInt(digits.substring(0, HOUR_DIGITS)) > MAX_HOUR) return false;
        return digits.length() <= HOUR_DIGITS || Integer.parseInt(digits.substring(HOUR_DIGITS)) <= MAX_MINUTE;
    }

    /** Digits of the text, at most four. */
    private static String digitsOf(String text) {
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '0' && c <= '9' && digits.length() < MAX_DIGITS) digits.append(c);
        }
        return digits.toString();
    }

    private static String format(String digits) {
        if (digits.length() < HOUR_DIGITS) return digits;
        String hour = digits.substring(0, HOUR_DIGITS);
        return hour + SEPARATOR + digits.substring(HOUR_DIGITS);
    }
}
