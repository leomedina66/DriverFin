package com.driverfin.model;

/** Totals of the shifts of one day plus how many shifts there were. */
public record DaySummary(int shiftCount, FinancialSummary totals) {}
