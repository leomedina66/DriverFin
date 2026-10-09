package com.driverfin.model;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Model class representing a daily work record for a ride-hailing driver.
 */
public class DailyRecord {
    private Long id;
    private LocalDate date;
    private LocalTime timeStart;
    private LocalTime timeEnd;
    private double kmStart;
    private double kmEnd;
    private double earnUber;
    private double earn99;
    private double earnOthers;
    private double costFuel;
    private double costFoodOther;
    private String notes;

    public DailyRecord() {
    }

    public DailyRecord(Long id, LocalDate date, LocalTime timeStart, LocalTime timeEnd,
                       double kmStart, double kmEnd, double earnUber, double earn99,
                       double earnOthers, double costFuel, double costFoodOther, String notes) {
        this.id = id;
        this.date = date;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.kmStart = kmStart;
        this.kmEnd = kmEnd;
        this.earnUber = earnUber;
        this.earn99 = earn99;
        this.earnOthers = earnOthers;
        this.costFuel = costFuel;
        this.costFoodOther = costFoodOther;
        this.notes = notes;
    }

    public DailyRecord(LocalDate date, LocalTime timeStart, LocalTime timeEnd,
                       double kmStart, double kmEnd, double earnUber, double earn99,
                       double earnOthers, double costFuel, double costFoodOther, String notes) {
        this(null, date, timeStart, timeEnd, kmStart, kmEnd, earnUber, earn99, earnOthers, costFuel, costFoodOther, notes);
    }

    // Calculation Methods

    /**
     * Calculates total hours worked in decimal format (e.g., 8.5 hours).
     * Handles night shifts crossing midnight.
     */
    public double calculateHours() {
        if (timeStart == null || timeEnd == null) {
            return 0.0;
        }
        Duration duration = Duration.between(timeStart, timeEnd);
        if (duration.isNegative()) {
            duration = duration.plusDays(1);
        }
        return duration.toMinutes() / 60.0;
    }

    /**
     * Calculates distance driven in kilometers.
     */
    public double calculateKm() {
        return Math.max(0.0, kmEnd - kmStart);
    }

    /**
     * Calculates gross income (total earnings across all platforms).
     */
    public double calculateGrossIncome() {
        return earnUber + earn99 + earnOthers;
    }

    /**
     * Calculates total direct costs (fuel + food/other expenses).
     */
    public double calculateTotalCosts() {
        return costFuel + costFoodOther;
    }

    /**
     * Calculates maintenance reserve based on rate per kilometer driven.
     * @param rate Cost factor per km (e.g. R$ 0.20/km)
     */
    public double calculateMaintReserve(double rate) {
        return calculateKm() * rate;
    }

    /**
     * Calculates net profit before maintenance reserve.
     */
    public double calculateNetProfit() {
        return calculateGrossIncome() - calculateTotalCosts();
    }

    /**
     * Calculates net profit after deducting maintenance reserve.
     * @param maintRate Cost factor per km for maintenance
     */
    public double calculateNetProfit(double maintRate) {
        return calculateGrossIncome() - calculateTotalCosts() - calculateMaintReserve(maintRate);
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(LocalTime timeStart) {
        this.timeStart = timeStart;
    }

    public LocalTime getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(LocalTime timeEnd) {
        this.timeEnd = timeEnd;
    }

    public double getKmStart() {
        return kmStart;
    }

    public void setKmStart(double kmStart) {
        this.kmStart = kmStart;
    }

    public double getKmEnd() {
        return kmEnd;
    }

    public void setKmEnd(double kmEnd) {
        this.kmEnd = kmEnd;
    }

    public double getEarnUber() {
        return earnUber;
    }

    public void setEarnUber(double earnUber) {
        this.earnUber = earnUber;
    }

    public double getEarn99() {
        return earn99;
    }

    public void setEarn99(double earn99) {
        this.earn99 = earn99;
    }

    public double getEarnOthers() {
        return earnOthers;
    }

    public void setEarnOthers(double earnOthers) {
        this.earnOthers = earnOthers;
    }

    public double getCostFuel() {
        return costFuel;
    }

    public void setCostFuel(double costFuel) {
        this.costFuel = costFuel;
    }

    public double getCostFoodOther() {
        return costFoodOther;
    }

    public void setCostFoodOther(double costFoodOther) {
        this.costFoodOther = costFoodOther;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DailyRecord record = (DailyRecord) o;
        // Registros sem ID (novos) só são iguais por identidade de referência
        if (id == null || record.id == null) return false;
        return Objects.equals(id, record.id);
    }

    @Override
    public int hashCode() {
        // Registros sem ID usam identidade de objeto para hashCode
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "DailyRecord{" +
                "id=" + id +
                ", date=" + date +
                ", timeStart=" + timeStart +
                ", timeEnd=" + timeEnd +
                ", kmStart=" + kmStart +
                ", kmEnd=" + kmEnd +
                ", earnUber=" + earnUber +
                ", earn99=" + earn99 +
                ", earnOthers=" + earnOthers +
                ", costFuel=" + costFuel +
                ", costFoodOther=" + costFoodOther +
                ", notes='" + notes + '\'' +
                '}';
    }
}
