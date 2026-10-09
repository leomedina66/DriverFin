package com.driverfin.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DailyRecordTest {

    private DailyRecord record;

    @BeforeEach
    public void setUp() {
        record = new DailyRecord(
                LocalDate.of(2023, 10, 1),
                LocalTime.of(8, 0), // 08:00
                LocalTime.of(16, 30), // 16:30 -> 8.5 hours
                10000.0, // kmStart
                10150.0, // kmEnd -> 150 km
                150.0,   // earnUber
                100.0,   // earn99
                50.0,    // earnOthers -> 300 gross
                40.0,    // costFuel
                20.0,    // costFood -> 60 total costs
                "Normal day"
        );
    }

    @Test
    public void testCalculateHours() {
        assertEquals(8.5, record.calculateHours(), 0.001);
    }

    @Test
    public void testCalculateHoursOvernight() {
        record.setTimeStart(LocalTime.of(22, 0));
        record.setTimeEnd(LocalTime.of(4, 30));
        // 22:00 to 04:30 is 6.5 hours
        assertEquals(6.5, record.calculateHours(), 0.001);
    }

    @Test
    public void testCalculateKm() {
        assertEquals(150.0, record.calculateKm(), 0.001);
    }

    @Test
    public void testCalculateGrossIncome() {
        assertEquals(300.0, record.calculateGrossIncome(), 0.001);
    }

    @Test
    public void testCalculateTotalCosts() {
        assertEquals(60.0, record.calculateTotalCosts(), 0.001);
    }

    @Test
    public void testCalculateMaintReserve() {
        // 150 km * 0.25 rate = 37.5
        assertEquals(37.5, record.calculateMaintReserve(0.25), 0.001);
    }

    @Test
    public void testCalculateNetProfitWithoutMaint() {
        // 300 gross - 60 costs = 240
        assertEquals(240.0, record.calculateNetProfit(), 0.001);
    }

    @Test
    public void testCalculateNetProfitWithMaint() {
        // 300 gross - 60 costs - 37.5 maint = 202.5
        assertEquals(202.5, record.calculateNetProfit(0.25), 0.001);
    }
}
