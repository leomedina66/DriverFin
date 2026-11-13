package com.driverfin.util;

import java.time.LocalDate;

/** The dates a shift may have: from 01/01/2000 up to one year after today (a shift can be registered ahead of time). */
public final class ShiftDateRange {
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);
    public static final int YEARS_AHEAD = 1;

    private ShiftDateRange() {}

    public static boolean contains(LocalDate date, LocalDate today) {
        return !date.isBefore(EARLIEST) && !date.isAfter(today.plusYears(YEARS_AHEAD));
    }
}
