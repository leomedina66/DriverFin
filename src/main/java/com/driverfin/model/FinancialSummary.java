package com.driverfin.model;

/**
 * Value object summarizing financial metrics over a given period.
 */
public class FinancialSummary {
    private double totalGross;
    private double totalCosts;
    private double totalMaint;
    private double totalNet;
    private double avgProfitPerHour;
    private double avgProfitPerKm;
    private double totalHours;
    private double totalKm;

    public FinancialSummary() {
    }

    public FinancialSummary(double totalGross, double totalCosts, double totalMaint,
                            double totalNet, double avgProfitPerHour, double avgProfitPerKm,
                            double totalHours, double totalKm) {
        this.totalGross = totalGross;
        this.totalCosts = totalCosts;
        this.totalMaint = totalMaint;
        this.totalNet = totalNet;
        this.avgProfitPerHour = avgProfitPerHour;
        this.avgProfitPerKm = avgProfitPerKm;
        this.totalHours = totalHours;
        this.totalKm = totalKm;
    }

    // Getters and Setters

    public double getTotalGross() {
        return totalGross;
    }

    public void setTotalGross(double totalGross) {
        this.totalGross = totalGross;
    }

    public double getTotalCosts() {
        return totalCosts;
    }

    public void setTotalCosts(double totalCosts) {
        this.totalCosts = totalCosts;
    }

    public double getTotalMaint() {
        return totalMaint;
    }

    public void setTotalMaint(double totalMaint) {
        this.totalMaint = totalMaint;
    }

    public double getTotalNet() {
        return totalNet;
    }

    public void setTotalNet(double totalNet) {
        this.totalNet = totalNet;
    }

    public double getAvgProfitPerHour() {
        return avgProfitPerHour;
    }

    public void setAvgProfitPerHour(double avgProfitPerHour) {
        this.avgProfitPerHour = avgProfitPerHour;
    }

    public double getAvgProfitPerKm() {
        return avgProfitPerKm;
    }

    public void setAvgProfitPerKm(double avgProfitPerKm) {
        this.avgProfitPerKm = avgProfitPerKm;
    }

    public double getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(double totalHours) {
        this.totalHours = totalHours;
    }

    public double getTotalKm() {
        return totalKm;
    }

    public void setTotalKm(double totalKm) {
        this.totalKm = totalKm;
    }

    @Override
    public String toString() {
        return "FinancialSummary{" +
                "totalGross=" + totalGross +
                ", totalCosts=" + totalCosts +
                ", totalMaint=" + totalMaint +
                ", totalNet=" + totalNet +
                ", avgProfitPerHour=" + avgProfitPerHour +
                ", avgProfitPerKm=" + avgProfitPerKm +
                ", totalHours=" + totalHours +
                ", totalKm=" + totalKm +
                '}';
    }
}
