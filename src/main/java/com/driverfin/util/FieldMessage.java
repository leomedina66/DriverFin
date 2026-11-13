package com.driverfin.util;

/** A short message shown under a form field. */
public record FieldMessage(MessageKind kind, String text) {

    public static FieldMessage error(String text) {
        return new FieldMessage(MessageKind.ERROR, text);
    }

    public static FieldMessage warning(String text) {
        return new FieldMessage(MessageKind.WARNING, text);
    }

    public static FieldMessage info(String text) {
        return new FieldMessage(MessageKind.INFO, text);
    }

    /** ERROR > WARNING > INFO; if both have the same kind, returns a; null-safe (null when both are null). */
    public static FieldMessage strongest(FieldMessage a, FieldMessage b) {
        if (a == null) return b;
        if (b == null) return a;
        // Enum order is ERROR, WARNING, INFO: the lower ordinal is the stronger kind.
        return b.kind().ordinal() < a.kind().ordinal() ? b : a;
    }
}
