package com.driverfin.util;

import com.driverfin.model.WorkShift;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Builds the predicate used to filter shifts by period and search text. Pure logic, no JavaFX. */
public final class ShiftFilter {
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LAST_DAYS_COUNT = 7;
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    // Regular, no-break and narrow no-break spaces (NumberFormat may use any of them).
    private static final String WHITESPACE = "[\\s\\u00A0\\u202F]";

    public enum Period {
        ALL("Todos"),
        TODAY("Hoje"),
        LAST_7_DAYS("Últimos 7 dias"),
        THIS_MONTH("Este mês"),
        LAST_MONTH("Mês passado"),
        CUSTOM("Personalizado");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private ShiftFilter() {}

    /**
     * @param customStart used only for CUSTOM; null means no lower limit
     * @param customEnd   used only for CUSTOM; null means no upper limit
     * @param searchText  matches notes (ignoring case and accents), date (dd/MM/yyyy or yyyy-MM-dd) and gross/cost values; blank matches all
     */
    public static Predicate<WorkShift> build(Period period, LocalDate customStart, LocalDate customEnd,
                                             String searchText, LocalDate today) {
        Predicate<WorkShift> byPeriod = periodPredicate(period, customStart, customEnd, today);
        return shift -> byPeriod.test(shift) && matchesSearch(shift, searchText);
    }

    /** Human-readable period for report headers. */
    public static String describe(Period period, LocalDate customStart, LocalDate customEnd) {
        if (period != Period.CUSTOM) return period.toString();
        String start = customStart == null ? "início" : customStart.format(BR_DATE);
        String end = customEnd == null ? "hoje" : customEnd.format(BR_DATE);
        return start + " a " + end;
    }

    private static Predicate<WorkShift> periodPredicate(Period period, LocalDate customStart,
                                                        LocalDate customEnd, LocalDate today) {
        return switch (period) {
            case ALL -> shift -> true;
            case TODAY -> between(today, today);
            case LAST_7_DAYS -> between(today.minusDays(LAST_DAYS_COUNT - 1), today);
            case THIS_MONTH -> between(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
            case LAST_MONTH -> {
                LocalDate firstOfLast = today.withDayOfMonth(1).minusMonths(1);
                yield between(firstOfLast, firstOfLast.withDayOfMonth(firstOfLast.lengthOfMonth()));
            }
            case CUSTOM -> between(customStart, customEnd);
        };
    }

    private static Predicate<WorkShift> between(LocalDate start, LocalDate end) {
        return shift -> {
            LocalDate date = shift.getDate();
            if (date == null) return false;
            return (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end));
        };
    }

    private static boolean matchesSearch(WorkShift shift, String searchText) {
        String query = normalizeText(searchText);
        if (query.isEmpty()) return true;

        if (shift.getNotes() != null && normalizeText(shift.getNotes()).contains(query)) return true;
        if (shift.getDate() != null
                && (shift.getDate().format(BR_DATE).contains(query) || shift.getDate().toString().contains(query))) {
            return true;
        }
        return matchesValue(shift.getGrossIncome(), query) || matchesValue(shift.getTotalCosts(), query);
    }

    /** Matches what the screen shows ("1.234,50") and the plain forms "1234.50" / "1234,50". */
    private static boolean matchesValue(BigDecimal value, String query) {
        String compactQuery = query.replace("r$", "").replaceAll(WHITESPACE, "");
        if (compactQuery.chars().noneMatch(Character::isDigit)) return false;

        String shown = CurrencyFormat.money(value).replace("R$", "").replaceAll(WHITESPACE, "");
        if (shown.contains(compactQuery)) return true;

        String dotQuery = compactQuery.contains(",") ? compactQuery.replace(".", "").replace(',', '.') : compactQuery;
        return value.toPlainString().contains(dotQuery);
    }

    /** Lower case without accents, so "ACAO" finds "ação". */
    private static String normalizeText(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
