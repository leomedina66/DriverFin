package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftMetricsTest {
    private static WorkShift shift(double kmEnd, int endHour) {
        WorkShift ws = new WorkShift();
        ws.setTimeStart(LocalTime.of(8, 0));
        ws.setTimeEnd(LocalTime.of(endHour, 0));
        ws.setKmStart(0);
        ws.setKmEnd(kmEnd);
        ws.setUberEarnings(new BigDecimal("200.00"));
        ws.setCostFuel(new BigDecimal("50.00"));
        return ws;
    }

    @Test
    void computesNetProfitPerHourAndKm() {
        WorkShift ws = shift(100, 12); // 4 h, 100 km, maint 0.20*100 = 20 -> net 130
        BigDecimal rate = new BigDecimal("0.20");
        assertEquals(new BigDecimal("32.50"), ShiftMetrics.netProfitPerHour(ws, rate));
        assertEquals(new BigDecimal("1.30"), ShiftMetrics.netProfitPerKm(ws, rate));
    }

    @Test
    void returnsZeroWhenNoKm() {
        assertEquals(new BigDecimal("0.00"), ShiftMetrics.netProfitPerKm(shift(0, 12), BigDecimal.ONE));
    }
}
