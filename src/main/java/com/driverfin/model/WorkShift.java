package com.driverfin.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public class WorkShift {
    private Long id;
    private LocalDate date;
    private LocalTime timeStart;
    private LocalTime timeEnd;
    private double kmStart;
    private double kmEnd;
    private BigDecimal uberEarnings = BigDecimal.ZERO;
    private BigDecimal app99Earnings = BigDecimal.ZERO;
    private BigDecimal otherEarnings = BigDecimal.ZERO;
    private BigDecimal costFuel = BigDecimal.ZERO;
    private BigDecimal foodAndOtherCosts = BigDecimal.ZERO;
    private String notes;

    public WorkShift() {}

    public WorkShift(Long id, LocalDate date, LocalTime timeStart, LocalTime timeEnd,
                     double kmStart, double kmEnd, BigDecimal uberEarnings, BigDecimal app99Earnings,
                     BigDecimal otherEarnings, BigDecimal costFuel, BigDecimal foodAndOtherCosts, String notes) {
        this.id = id;
        this.date = date;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.kmStart = kmStart;
        this.kmEnd = kmEnd;
        this.uberEarnings = uberEarnings;
        this.app99Earnings = app99Earnings;
        this.otherEarnings = otherEarnings;
        this.costFuel = costFuel;
        this.foodAndOtherCosts = foodAndOtherCosts;
        this.notes = notes;
    }

    public double calculateHours() {
        if (timeStart == null || timeEnd == null) return 0.0;
        Duration duration = Duration.between(timeStart, timeEnd);
        if (duration.isNegative()) duration = duration.plusDays(1);
        return duration.toMinutes() / 60.0;
    }

    public double calculateKm() {
        return Math.max(0.0, kmEnd - kmStart);
    }

    public BigDecimal getGrossIncome() {
        return uberEarnings.add(app99Earnings).add(otherEarnings);
    }

    public BigDecimal getTotalCosts() {
        return costFuel.add(foodAndOtherCosts);
    }

    public BigDecimal calculateMaintReserve(BigDecimal maintenanceRatePerKm) {
        return maintenanceRatePerKm.multiply(BigDecimal.valueOf(calculateKm()));
    }

    public BigDecimal calculateNetProfit(BigDecimal maintenanceRatePerKm) {
        return getGrossIncome().subtract(getTotalCosts()).subtract(calculateMaintReserve(maintenanceRatePerKm));
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getTimeStart() { return timeStart; }
    public void setTimeStart(LocalTime timeStart) { this.timeStart = timeStart; }
    public LocalTime getTimeEnd() { return timeEnd; }
    public void setTimeEnd(LocalTime timeEnd) { this.timeEnd = timeEnd; }
    public double getKmStart() { return kmStart; }
    public void setKmStart(double kmStart) { this.kmStart = kmStart; }
    public double getKmEnd() { return kmEnd; }
    public void setKmEnd(double kmEnd) { this.kmEnd = kmEnd; }
    public BigDecimal getUberEarnings() { return uberEarnings; }
    public void setUberEarnings(BigDecimal uberEarnings) { this.uberEarnings = uberEarnings; }
    public BigDecimal getApp99Earnings() { return app99Earnings; }
    public void setApp99Earnings(BigDecimal app99Earnings) { this.app99Earnings = app99Earnings; }
    public BigDecimal getOtherEarnings() { return otherEarnings; }
    public void setOtherEarnings(BigDecimal otherEarnings) { this.otherEarnings = otherEarnings; }
    public BigDecimal getCostFuel() { return costFuel; }
    public void setCostFuel(BigDecimal costFuel) { this.costFuel = costFuel; }
    public BigDecimal getFoodAndOtherCosts() { return foodAndOtherCosts; }
    public void setFoodAndOtherCosts(BigDecimal foodAndOtherCosts) { this.foodAndOtherCosts = foodAndOtherCosts; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkShift workShift = (WorkShift) o;
        if (id == null || workShift.id == null) return false;
        return Objects.equals(id, workShift.id);
    }

    @Override
    public int hashCode() {
        return 31; // Constant hashCode for mutable entities with DB-generated IDs
    }
}
