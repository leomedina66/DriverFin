package com.driverfin.util;

/** Result of a mask. A rejected edit has text equal to the old text. The message may be null. */
public record MaskResult(String text, int caret, FieldMessage message) {

    /** Caret at the end of the text. */
    public static MaskResult of(String text, FieldMessage message) {
        return new MaskResult(text, text.length(), message);
    }

    public static MaskResult unchanged(String oldText, FieldMessage message) {
        return of(oldText, message);
    }
}
