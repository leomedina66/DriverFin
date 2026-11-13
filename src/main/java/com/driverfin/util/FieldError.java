package com.driverfin.util;

/** An error that belongs to one form field. */
public record FieldError(ShiftField field, String message) {}
