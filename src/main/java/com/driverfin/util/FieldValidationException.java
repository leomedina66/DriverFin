package com.driverfin.util;

/** Validation error that knows which form field caused it. Still an IllegalArgumentException for old callers. */
public class FieldValidationException extends IllegalArgumentException {
    private final ShiftField field;

    public FieldValidationException(ShiftField field, String message) {
        super(message);
        this.field = field;
    }

    public ShiftField getField() {
        return field;
    }
}
