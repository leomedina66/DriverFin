package com.driverfin.util;

import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public class WorkShiftValidator {
    public static void validateAndPopulate(WorkShift shift, LocalDate date, String timeStart, String timeEnd,
                                           String kmStart, String kmEnd, String uber, String app99, String others,
                                           String fuel, String food, String notes) {
        if (date == null) throw new IllegalArgumentException("A data é obrigatória.");
        shift.setDate(date);
        
        try {
            shift.setTimeStart(LocalTime.parse(timeStart));
            shift.setTimeEnd(LocalTime.parse(timeEnd));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Horário inválido. Use HH:mm.");
        }

        try {
            shift.setKmStart(Double.parseDouble(kmStart.replace(",", ".")));
            shift.setKmEnd(Double.parseDouble(kmEnd.replace(",", ".")));
            if (shift.getKmEnd() < shift.getKmStart()) {
                throw new IllegalArgumentException("KM Final não pode ser menor que o Inicial.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valores de KM inválidos.");
        }

        try {
            shift.setUberEarnings(parseCurrency(uber));
            shift.setApp99Earnings(parseCurrency(app99));
            shift.setOtherEarnings(parseCurrency(others));
            shift.setCostFuel(parseCurrency(fuel));
            shift.setFoodAndOtherCosts(parseCurrency(food));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valores financeiros inválidos. Evite múltiplos pontos.");
        }
        
        shift.setNotes(notes);
    }
    
    private static BigDecimal parseCurrency(String val) {
        if (val == null || val.trim().isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(val.trim().replace(",", "."));
    }
}
