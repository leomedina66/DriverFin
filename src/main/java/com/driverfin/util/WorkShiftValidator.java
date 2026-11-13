package com.driverfin.util;

import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

public class WorkShiftValidator {
    static final int MAX_NOTES_LENGTH = 500;
    static final BigDecimal MAX_MONEY = new BigDecimal("1000000.00");
    /** KM values must stay below this (nine integer digits, like the KM mask). */
    static final double KM_LIMIT = 1_000_000_000d;
    private static final int MONEY_SCALE = 2;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT);
    // Optional minus sign, digits, optional decimal part with "." or ",". Rejects "1E+30", "NaN", etc.
    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+([.,]\\d+)?");
    // "1.000" or "1.000,50": a dot used as thousands separator, which would silently be read as a decimal point.
    private static final Pattern DOT_THOUSANDS = Pattern.compile("-?[1-9]\\d{0,2}(\\.\\d{3})+(,\\d+)?");

    /**
     * Validates every field first and only then changes the shift, so a failed validation
     * never leaves the shift half-filled. Throws IllegalArgumentException with a user-facing message.
     */
    public static void validateAndPopulate(WorkShift shift, LocalDate date, String timeStart, String timeEnd,
                                           String kmStart, String kmEnd, String uber, String app99, String others,
                                           String fuel, String food, String notes) {
        validateAndPopulate(shift, LocalDate.now(), date, timeStart, timeEnd, kmStart, kmEnd, uber, app99, others,
                fuel, food, notes);
    }

    /** Same, with the day that counts as "today" given (the date ceiling is one year after it). */
    public static void validateAndPopulate(WorkShift shift, LocalDate today, LocalDate date, String timeStart,
                                           String timeEnd, String kmStart, String kmEnd, String uber, String app99,
                                           String others, String fuel, String food, String notes) {
        if (date == null) throw new FieldValidationException(ShiftField.DATE, "A data é obrigatória.");
        if (!ShiftDateRange.contains(date, today)) {
            throw new FieldValidationException(ShiftField.DATE, FormMessages.DATE_OUT_OF_RANGE);
        }

        LocalTime start = parseTime(timeStart, "inicial", ShiftField.TIME_START);
        LocalTime end = parseTime(timeEnd, "final", ShiftField.TIME_END);

        double kmStartValue = parseKm(kmStart, "KM Inicial", ShiftField.KM_START);
        double kmEndValue = parseKm(kmEnd, "KM Final", ShiftField.KM_END);
        if (kmEndValue < kmStartValue) {
            throw new FieldValidationException(ShiftField.KM_END, "KM Final não pode ser menor que o Inicial.");
        }

        BigDecimal uberValue = parseCurrency(uber, "Uber", ShiftField.EARN_UBER);
        BigDecimal app99Value = parseCurrency(app99, "99", ShiftField.EARN_99);
        BigDecimal othersValue = parseCurrency(others, "Outros ganhos", ShiftField.EARN_OTHERS);
        BigDecimal fuelValue = parseCurrency(fuel, "Combustível", ShiftField.COST_FUEL);
        BigDecimal foodValue = parseCurrency(food, "Alimentação e outros custos", ShiftField.COST_FOOD_OTHER);

        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new FieldValidationException(ShiftField.NOTES, "As observações podem ter no máximo " + MAX_NOTES_LENGTH + " caracteres.");
        }

        shift.setDate(date);
        shift.setTimeStart(start);
        shift.setTimeEnd(end);
        shift.setKmStart(kmStartValue);
        shift.setKmEnd(kmEndValue);
        shift.setUberEarnings(uberValue);
        shift.setApp99Earnings(app99Value);
        shift.setOtherEarnings(othersValue);
        shift.setCostFuel(fuelValue);
        shift.setFoodAndOtherCosts(foodValue);
        shift.setNotes(notes);
    }

    private static LocalTime parseTime(String text, String label, ShiftField field) {
        try {
            return LocalTime.parse(text == null ? "" : text.trim(), TIME_FORMAT);
        } catch (DateTimeParseException e) {
            throw new FieldValidationException(field, "Horário " + label + " inválido. Use HH:mm, por exemplo 08:00.");
        }
    }

    private static double parseKm(String text, String label, ShiftField field) {
        String number = requirePlainNumber(text, label, "Use apenas números, por exemplo 10150,5.", field);
        // Checked on the text so that "-0" (negative zero) is rejected too.
        if (number.startsWith("-")) {
            throw new FieldValidationException(field, label + " não pode ser negativo.");
        }
        double value = Double.parseDouble(number);
        if (!Double.isFinite(value) || value >= KM_LIMIT) {
            throw new FieldValidationException(field, "Valor de " + label + " é grande demais.");
        }
        return value;
    }

    private static BigDecimal parseCurrency(String text, String label, ShiftField field) {
        if (text == null || text.trim().isEmpty()) return BigDecimal.ZERO.setScale(MONEY_SCALE);
        String number = requirePlainNumber(text, label, "Use apenas números, por exemplo 150,50.", field);
        BigDecimal value = new BigDecimal(number);
        if (value.signum() < 0) {
            throw new FieldValidationException(field, "Valor de " + label + " não pode ser negativo.");
        }
        BigDecimal rounded = value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (rounded.compareTo(MAX_MONEY) > 0) {
            throw new FieldValidationException(field, "Valor de " + label + " não pode passar de R$ 1.000.000,00.");
        }
        return rounded;
    }

    private static String requirePlainNumber(String text, String label, String hint, ShiftField field) {
        String trimmed = text == null ? "" : text.trim();
        if (DOT_THOUSANDS.matcher(trimmed).matches()) {
            throw new FieldValidationException(field, "Valor de " + label + ": use vírgula para os centavos e "
                    + "não use ponto para separar milhares (ex.: 1000,50).");
        }
        if (!PLAIN_NUMBER.matcher(trimmed).matches()) {
            throw new FieldValidationException(field, "Valor de " + label + " inválido. " + hint);
        }
        return trimmed.replace(",", ".");
    }
}
