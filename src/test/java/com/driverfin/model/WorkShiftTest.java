package com.driverfin.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorkShiftTest {

    private WorkShift shift;

    @BeforeEach
    public void setUp() {
        shift = new WorkShift(
                null,
                LocalDate.of(2023, 10, 1),
                LocalTime.of(8, 0),
                LocalTime.of(16, 30),
                10000.0,
                10150.0,
                new BigDecimal("150.00"),
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                new BigDecimal("40.00"),
                new BigDecimal("20.00"),
                "Normal day"
        );
    }

    @Test
    public void testCalculateHours() {
        assertEquals(8.5, shift.calculateHours(), 0.001);
    }

    @Test
    public void testCalculateHoursOvernight() {
        shift.setTimeStart(LocalTime.of(22, 0));
        shift.setTimeEnd(LocalTime.of(4, 30));
        assertEquals(6.5, shift.calculateHours(), 0.001);
    }

    @Test
    public void testCalculateKm() {
        assertEquals(150.0, shift.calculateKm(), 0.001);
    }

    @Test
    public void testGetGrossIncome() {
        assertEquals(new BigDecimal("300.00"), shift.getGrossIncome());
    }

    @Test
    public void testGetTotalCosts() {
        assertEquals(new BigDecimal("60.00"), shift.getTotalCosts());
    }

    @Test
    public void testCalculateMaintReserve() {
        assertEquals(new BigDecimal("37.500"), shift.calculateMaintReserve(new BigDecimal("0.25")));
    }

    @Test
    public void testCalculateNetProfitWithMaint() {
        assertEquals(new BigDecimal("202.500"), shift.calculateNetProfit(new BigDecimal("0.25")));
    }
}
