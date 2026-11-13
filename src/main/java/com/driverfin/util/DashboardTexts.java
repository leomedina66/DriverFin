package com.driverfin.util;

import com.driverfin.model.DaySummary;
import com.driverfin.model.WorkShift;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Every text of the dashboard S-1 to S-4, copied from the UX document. */
public final class DashboardTexts {
    public static final String TODAY_EMPTY = "Você ainda não registrou turno hoje.";
    public static final String TODAY_NEW_BUTTON = "Registrar turno de hoje";
    public static final String EMPTY_TITLE = "Bem-vindo ao DriverFin";
    public static final String EMPTY_TEXT = "Você ainda não registrou nenhum turno. Comece pelo botão Novo Turno.";
    public static final String LOAD_FAILED_TITLE = "Não foi possível carregar os turnos";
    public static final String LOAD_FAILED_TEXT = "Feche e abra o aplicativo para tentar de novo.";
    public static final String SHORTCUTS_HINT =
        "Dicas: Enter edita o turno selecionado, Delete exclui. Ctrl+N cria um novo turno.";
    public static final String DELETE_TITLE = "Excluir turno?";
    public static final String SAVED = "Turno salvo.";
    public static final String DELETED = "Turno excluído.";
    public static final String MAINT_INVALID = "Valor inválido. Use por exemplo 0,25.";
    public static final String MAINT_HELP =
        "Dinheiro que você separa a cada km rodado para pneus, óleo e revisões. Ele é descontado do lucro.";

    private static final String NO_VALUE = "—";
    private static final String NO_SHIFTS_IN_PERIOD = "Nenhum turno neste período.";
    private static final int MONEY_DECIMALS = 2;
    private static final DateTimeFormatter TITLE_DATE =
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", CurrencyFormat.PT_BR);
    private static final DateTimeFormatter SHIFT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DashboardTexts() {}

    /** Example: "HOJE - terça-feira, 10 de novembro". */
    public static String todayTitle(LocalDate day) {
        return "HOJE - " + TITLE_DATE.format(day);
    }

    /** Example: "R$ 182,40", or "-R$ 12,30 (prejuízo)": a loss is never shown by color alone. */
    public static String profit(BigDecimal net) {
        BigDecimal rounded = net.setScale(MONEY_DECIMALS, RoundingMode.HALF_UP);
        if (rounded.signum() >= 0) return CurrencyFormat.money(rounded);
        return "-" + CurrencyFormat.money(rounded.negate()) + " (prejuízo)";
    }

    /** The single summary line of the today card. Example: "Faturamento: R$ 182,40 · 2 turnos" or "... · 1 turno". */
    public static String todaySummary(BigDecimal gross, int shiftCount) {
        String shifts = shiftCount == 1 ? "1 turno" : shiftCount + " turnos";
        return "Faturamento: " + CurrencyFormat.money(gross) + " · " + shifts;
    }

    /** Example: "8,5 h", or "0 h". */
    public static String hours(double hours) {
        return hours == 0 ? "0 h" : CurrencyFormat.oneDecimal(hours) + " h";
    }

    /** Example: "R$ 38,20 / h", or a dash when the day has no hours. */
    public static String grossPerHour(DaySummary day) {
        if (day.totals().totalHours() == 0) return NO_VALUE;
        return CurrencyFormat.perHour(day.totals().avgGrossPerHour());
    }

    /** Text of the empty table: the period has no shifts, or the search found none. */
    public static String tablePlaceholder(String searchText) {
        String search = searchText == null ? "" : searchText.strip();
        if (search.isEmpty()) return NO_SHIFTS_IN_PERIOD;
        return "Nenhum turno encontrado para \"" + search + "\".";
    }

    public static String deleteConfirmText(WorkShift ws, BigDecimal maintRate) {
        return "O turno de " + SHIFT_DATE.format(ws.getDate()) + " (lucro "
            + CurrencyFormat.money(ws.calculateNetProfit(maintRate))
            + ") será excluído e não dá para desfazer.";
    }
}
