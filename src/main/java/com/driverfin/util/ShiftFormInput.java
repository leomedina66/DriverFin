package com.driverfin.util;

import java.time.LocalDate;

/** What the form shows, as texts (display form: "08:00", "12.450,0"), for {@link ShiftFormCheck}. */
public record ShiftFormInput(LocalDate date, String timeStart, String timeEnd,
                             String kmStart, String kmEnd, String notes) {}
