package com.driverfin.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Form-level checks with the exact UX messages, one per field (ADR 0001). They run before
 * {@link WorkShiftValidator}, which stays the last gate. Money fields cannot be wrong: the mask guarantees them.
 */
public final class ShiftFormCheck {
    /** The only km texts the validator accepts once the thousands dots are removed. */
    private static final Pattern PLAIN_KM = Pattern.compile("\\d+(,\\d+)?");

    private ShiftFormCheck() {}

    /** Message for one field, or empty. KM_END includes the "final >= initial" rule (only when both parse). */
    public static Optional<FieldError> checkField(ShiftField field, ShiftFormInput input) {
        return checkField(field, input, LocalDate.now());
    }

    /** Same, with the day that counts as "today" given (the date ceiling is one year after it). */
    public static Optional<FieldError> checkField(ShiftField field, ShiftFormInput input, LocalDate today) {
        Optional<String> message = switch (field) {
            case DATE -> checkDate(input.date(), today);
            case TIME_START -> checkTime(input.timeStart(), FormMessages.TIME_START_REQUIRED);
            case TIME_END -> checkTime(input.timeEnd(), FormMessages.TIME_END_REQUIRED);
            case KM_START -> checkKm(input.kmStart(), FormMessages.KM_START_REQUIRED);
            case KM_END -> checkKmEnd(input);
            case NOTES -> checkNotes(input.notes());
            case EARN_UBER, EARN_99, EARN_OTHERS, COST_FUEL, COST_FOOD_OTHER -> Optional.empty();
        };
        return message.map(text -> new FieldError(field, text));
    }

    /** All errors, sorted in the order of {@link ShiftField} (the Tab order of the form). */
    public static List<FieldError> checkAll(ShiftFormInput input) {
        return checkAll(input, LocalDate.now());
    }

    public static List<FieldError> checkAll(ShiftFormInput input, LocalDate today) {
        List<FieldError> errors = new ArrayList<>();
        for (ShiftField field : ShiftField.values()) {
            checkField(field, input, today).ifPresent(errors::add);
        }
        return errors;
    }

    /**
     * Normalisation rule: a time is judged after {@link TimeMask#complete}, the same tidy-up the form does when the
     * field is left and before saving ("08:" and "17" become "08:00" and "17:00"). So hour-only texts pass here and
     * the validator receives the completed text, never the raw one.
     */
    private static Optional<String> checkTime(String text, String requiredMessage) {
        if (isBlank(text)) return Optional.of(requiredMessage);
        MaskResult completed = TimeMask.complete(text);
        if (completed.message() != null) return Optional.of(completed.message().text());
        if (!TimeMask.isComplete(completed.text())) return Optional.of(FormMessages.TIME_INVALID);
        return Optional.empty();
    }

    private static Optional<String> checkDate(LocalDate date, LocalDate today) {
        if (date == null) return Optional.of(FormMessages.DATE_REQUIRED);
        return ShiftDateRange.contains(date, today) ? Optional.empty() : Optional.of(FormMessages.DATE_OUT_OF_RANGE);
    }

    private static Optional<String> checkKm(String text, String requiredMessage) {
        if (isBlank(text)) return Optional.of(requiredMessage);
        if (!isReadableKm(text)) return Optional.of(FormMessages.KM_UNREADABLE);
        return Optional.empty();
    }

    private static Optional<String> checkKmEnd(ShiftFormInput input) {
        Optional<String> basic = checkKm(input.kmEnd(), FormMessages.KM_END_REQUIRED);
        if (basic.isPresent()) return basic;
        if (isBlank(input.kmStart()) || !isReadableKm(input.kmStart())) return Optional.empty();
        OptionalDouble start = KmMask.parse(input.kmStart());
        OptionalDouble end = KmMask.parse(input.kmEnd());
        if (end.getAsDouble() < start.getAsDouble()) {
            return Optional.of(FormMessages.kmEndBelowStart(KmMask.display(start.getAsDouble())));
        }
        return Optional.empty();
    }

    private static Optional<String> checkNotes(String notes) {
        int over = notes == null ? 0 : notes.length() - WorkShiftValidator.MAX_NOTES_LENGTH;
        return over > 0 ? Optional.of(FormMessages.notesTooLong(over)) : Optional.empty();
    }

    private static boolean isReadableKm(String text) {
        return KmMask.parse(text).isPresent() && PLAIN_KM.matcher(KmMask.toPlain(text.trim())).matches();
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
