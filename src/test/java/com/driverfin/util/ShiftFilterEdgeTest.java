package com.driverfin.util;

import com.driverfin.model.WorkShift;
import com.driverfin.util.ShiftFilter.Period;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Edge cases for ShiftFilter: month/year boundaries, inverted ranges, nulls and search quirks. */
class ShiftFilterEdgeTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private static WorkShift shift(LocalDate date, String uber, String fuel, String notes) {
        WorkShift ws = new WorkShift();
        ws.setDate(date);
        ws.setUberEarnings(new BigDecimal(uber));
        ws.setCostFuel(new BigDecimal(fuel));
        ws.setNotes(notes);
        return ws;
    }

    private static WorkShift shiftOn(LocalDate date) {
        return shift(date, "100.00", "10.00", "");
    }

    private static Predicate<WorkShift> search(String text) {
        return ShiftFilter.build(Period.ALL, null, null, text, TODAY);
    }

    private static Predicate<WorkShift> periodAt(Period period, LocalDate today) {
        return ShiftFilter.build(period, null, null, "", today);
    }

    // ---- periods ----

    @Test
    void lastMonthOnJanuaryFirstIsWholeDecember() {
        Predicate<WorkShift> f = periodAt(Period.LAST_MONTH, LocalDate.of(2027, 1, 1));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 12, 1))));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 12, 31))));
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 11, 30))));
        assertFalse(f.test(shiftOn(LocalDate.of(2027, 1, 1))));
    }

    @Test
    void lastMonthFromMarch31IsFebruaryNotMarch() {
        Predicate<WorkShift> f = periodAt(Period.LAST_MONTH, LocalDate.of(2026, 3, 31));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 2, 28))));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 2, 1))));
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 3, 1))));
    }

    @Test
    void lastMonthInLeapYearIncludesFebruary29() {
        Predicate<WorkShift> f = periodAt(Period.LAST_MONTH, LocalDate.of(2028, 3, 31));
        assertTrue(f.test(shiftOn(LocalDate.of(2028, 2, 29))));
        assertFalse(f.test(shiftOn(LocalDate.of(2028, 3, 1))));
    }

    @Test
    void lastSevenDaysCrossesYearBoundary() {
        Predicate<WorkShift> f = periodAt(Period.LAST_7_DAYS, LocalDate.of(2027, 1, 3));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 12, 28))));
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 12, 27))));
    }

    @Test
    void thisMonthOnLastDayOfYearStopsAtDecember31() {
        Predicate<WorkShift> f = periodAt(Period.THIS_MONTH, LocalDate.of(2026, 12, 31));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 12, 1))));
        assertTrue(f.test(shiftOn(LocalDate.of(2026, 12, 31))));
        assertFalse(f.test(shiftOn(LocalDate.of(2027, 1, 1))));
    }

    @Test
    void customRangeWithEndBeforeStartMatchesNothing() {
        Predicate<WorkShift> f = ShiftFilter.build(Period.CUSTOM,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 5), "", TODAY);
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 3, 5))));
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 3, 7))));
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 3, 10))));
    }

    @Test
    void customRangeWithBothDatesNullMatchesEveryDatedShift() {
        Predicate<WorkShift> f = ShiftFilter.build(Period.CUSTOM, null, null, "", TODAY);
        assertTrue(f.test(shiftOn(LocalDate.of(1990, 1, 1))));
        assertTrue(f.test(shiftOn(LocalDate.of(2999, 1, 1))));
    }

    @Test
    void customRangeWithOnlyEndExcludesLaterDates() {
        Predicate<WorkShift> f = ShiftFilter.build(Period.CUSTOM, null, LocalDate.of(2026, 3, 5), "", TODAY);
        assertTrue(f.test(shiftOn(LocalDate.of(2000, 1, 1))));
        assertFalse(f.test(shiftOn(LocalDate.of(2026, 3, 6))));
    }

    @Test
    void shiftWithoutDateIsRejectedByEveryPeriodExceptAll() {
        WorkShift undated = shiftOn(null);
        for (Period p : Period.values()) {
            boolean expected = p == Period.ALL;
            assertEquals(expected, ShiftFilter.build(p, null, null, "", TODAY).test(undated), "period " + p);
        }
    }

    @Test
    void filteringEmptyListGivesEmptyResult() {
        List<WorkShift> result = List.<WorkShift>of().stream().filter(periodAt(Period.TODAY, TODAY)).toList();
        assertTrue(result.isEmpty());
    }

    @Test
    void nullPeriodIsRejectedWithException() {
        assertThrows(NullPointerException.class, () -> ShiftFilter.build(null, null, null, "", TODAY));
    }

    @Test
    void allPeriodWorksEvenWithoutToday() {
        assertTrue(ShiftFilter.build(Period.ALL, null, null, "", null).test(shiftOn(TODAY)));
    }

    // ---- search ----

    @Test
    void searchWithOnlySpacesMatchesEverything() {
        assertTrue(search("   ").test(shift(TODAY, "100", "10", "x")));
    }

    @Test
    void searchWithNullMatchesEverything() {
        assertTrue(search(null).test(shift(TODAY, "100", "10", null)));
    }

    @Test
    void searchIgnoresCaseOfAccentedLetters() {
        assertTrue(search("AÇÃO").test(shift(TODAY, "100", "10", "ação")));
        assertTrue(search("ação").test(shift(TODAY, "100", "10", "AÇÃO")));
    }

    @Test
    void searchIgnoresAccentsInBothDirections() {
        assertTrue(search("acao").test(shift(TODAY, "100", "10", "ação")));
        assertTrue(search("AÇÃO").test(shift(TODAY, "100", "10", "acao")));
        assertTrue(search("sao paulo").test(shift(TODAY, "100", "10", "Viagem a São Paulo")));
        assertFalse(search("acao").test(shift(TODAY, "100", "10", "ocasião")));
    }

    @Test
    void searchMatchesIsoDateAndFullBrazilianDate() {
        WorkShift ws = shiftOn(LocalDate.of(2026, 3, 5));
        assertTrue(search("2026-03-05").test(ws));
        assertTrue(search("05/03/2026").test(ws));
        assertFalse(search("2026-03-06").test(ws));
        assertFalse(search("06/03/2026").test(ws));
    }

    @Test
    void searchTreatsRegexCharactersAsPlainText() {
        WorkShift ws = shift(TODAY, "100", "10", "a+b (teste) [x] ^$ \\d");
        assertTrue(search("a+b").test(ws));
        assertTrue(search("(teste)").test(ws));
        assertTrue(search("[x]").test(ws));
        assertTrue(search("^$").test(ws));
        assertTrue(search("\\d").test(ws));
        assertFalse(search("a.b").test(ws));
        assertFalse(search("(").test(shift(TODAY, "100", "10", "sem")));
    }

    @Test
    void searchMatchesCostValueWithComma() {
        WorkShift ws = shift(TODAY, "100.00", "37.50", "");
        assertTrue(search("37,5").test(ws));
        assertFalse(search("38").test(ws));
    }

    @Test
    void searchCommaInNotesStillMatchesNotes() {
        assertTrue(search("olá, mundo").test(shift(TODAY, "100", "10", "Olá, mundo")));
    }

    @Test
    void searchMatchesValueAsShownOnScreen() {
        WorkShift ws = shift(TODAY, "1234.50", "0", "");
        assertTrue(search("1.234,50").test(ws));
        assertTrue(search("R$ 1.234,50").test(ws));
        assertTrue(search("r$1.234,50").test(ws));
        assertTrue(search("1234,50").test(ws));
        assertTrue(search("1234.50").test(ws));
        assertTrue(search("1.234").test(ws));
        assertFalse(search("1.235,50").test(ws));
    }

    @Test
    void searchMatchesCostValueAsShownOnScreen() {
        assertTrue(search("R$ 2.500,00").test(shift(TODAY, "100", "2500", "")));
    }

    @Test
    void searchWithOnlyPunctuationOrCurrencySymbolDoesNotMatchEverything() {
        WorkShift ws = shift(TODAY, "100.00", "10.00", "");
        assertFalse(search(".").test(ws));
        assertFalse(search(",").test(ws));
        assertFalse(search("R$").test(ws));
        assertFalse(search("R$ .").test(ws));
    }

    @Test
    void searchOnNumberAlsoMatchesDatePartsSoResultsMayBeBroad() {
        // "03" is in the date 15/03/2026 even though the user may mean a value.
        assertTrue(search("03").test(shift(TODAY, "100.00", "10.00", "")));
    }

    @Test
    void describeCustomWithOpenEndsUsesPlaceholders() {
        assertEquals("início a hoje", ShiftFilter.describe(Period.CUSTOM, null, null));
        assertEquals("01/01/2027 a hoje", ShiftFilter.describe(Period.CUSTOM, LocalDate.of(2027, 1, 1), null));
    }
}
