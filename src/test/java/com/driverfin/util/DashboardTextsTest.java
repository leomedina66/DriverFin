package com.driverfin.util;

import com.driverfin.model.DaySummary;
import com.driverfin.model.WorkShift;
import com.driverfin.service.SummaryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashboardTextsTest {

    private static final BigDecimal RATE = new BigDecimal("0.25");

    private static WorkShift shift(LocalTime start, LocalTime end, String uber) {
        return new WorkShift(null, LocalDate.of(2026, 11, 10), start, end, 1000.0, 1040.0, new BigDecimal(uber),
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    private static DaySummary daySummaryOf(WorkShift... shifts) {
        return SummaryService.forDay(List.of(shifts), LocalDate.of(2026, 11, 10), RATE);
    }

    @Test
    public void todayTitleHasWeekdayDayAndMonth() {
        assertEquals("HOJE - terça-feira, 10 de novembro", DashboardTexts.todayTitle(LocalDate.of(2026, 11, 10)));
        assertEquals("HOJE - domingo, 1 de março", DashboardTexts.todayTitle(LocalDate.of(2026, 3, 1)));
    }

    @Test
    public void profitShowsALossWithTheWordNotOnlyTheSign() {
        assertEquals(CurrencyFormat.money(new BigDecimal("182.40")), DashboardTexts.profit(new BigDecimal("182.40")));
        assertEquals("-" + CurrencyFormat.money(new BigDecimal("12.30")) + " (prejuízo)",
            DashboardTexts.profit(new BigDecimal("-12.30")));
    }

    @Test
    public void profitThatRoundsToZeroIsNotALoss() {
        assertEquals(CurrencyFormat.money(BigDecimal.ZERO), DashboardTexts.profit(new BigDecimal("-0.004")));
        assertEquals(CurrencyFormat.money(BigDecimal.ZERO), DashboardTexts.profit(BigDecimal.ZERO));
    }

    @Test
    public void hoursUseOneDecimalOrZero() {
        assertEquals("8,5 h", DashboardTexts.hours(8.5));
        assertEquals("0 h", DashboardTexts.hours(0));
    }

    @Test
    public void grossPerHourShowsADashWithoutHours() {
        DaySummary noHours = daySummaryOf(shift(LocalTime.of(8, 0), LocalTime.of(8, 0), "50.00"));
        assertEquals("—", DashboardTexts.grossPerHour(noHours));
        assertEquals("—", DashboardTexts.grossPerHour(daySummaryOf()));
    }

    @Test
    public void grossPerHourShowsTheRate() {
        DaySummary day = daySummaryOf(shift(LocalTime.of(8, 0), LocalTime.of(10, 0), "76.40"));
        assertEquals(CurrencyFormat.money(new BigDecimal("38.20")) + " / h", DashboardTexts.grossPerHour(day));
    }

    @Test
    public void todaySummaryHasOneLineWithSingularAndPlural() {
        BigDecimal gross = new BigDecimal("182.40");
        String money = CurrencyFormat.money(gross);
        assertEquals("Faturamento: " + money + " · 1 turno", DashboardTexts.todaySummary(gross, 1));
        assertEquals("Faturamento: " + money + " · 2 turnos", DashboardTexts.todaySummary(gross, 2));
    }

    @Test
    public void tablePlaceholderDependsOnTheSearch() {
        assertEquals("Nenhum turno neste período.", DashboardTexts.tablePlaceholder(""));
        assertEquals("Nenhum turno neste período.", DashboardTexts.tablePlaceholder("  "));
        assertEquals("Nenhum turno neste período.", DashboardTexts.tablePlaceholder(null));
        assertEquals("Nenhum turno encontrado para \"uber\".", DashboardTexts.tablePlaceholder("uber"));
    }

    @Test
    public void deleteConfirmTextShowsDateAndProfit() {
        WorkShift ws = shift(LocalTime.of(8, 0), LocalTime.of(12, 0), "100.00");
        // 100.00 gross, 40 km * 0.25 = 10.00 maintenance -> profit 90.00
        assertEquals("O turno de 10/11/2026 (lucro " + CurrencyFormat.money(new BigDecimal("90.00"))
            + ") será excluído e não dá para desfazer.", DashboardTexts.deleteConfirmText(ws, RATE));
    }

    @Test
    public void fixedTextsMatchUx() {
        assertEquals("Você ainda não registrou turno hoje.", DashboardTexts.TODAY_EMPTY);
        assertEquals("Registrar turno de hoje", DashboardTexts.TODAY_NEW_BUTTON);
        assertEquals("Bem-vindo ao DriverFin", DashboardTexts.EMPTY_TITLE);
        assertEquals("Você ainda não registrou nenhum turno. Comece pelo botão Novo Turno.", DashboardTexts.EMPTY_TEXT);
        assertEquals("Dicas: Enter edita o turno selecionado, Delete exclui. Ctrl+N cria um novo turno.",
            DashboardTexts.SHORTCUTS_HINT);
        assertEquals("Excluir turno?", DashboardTexts.DELETE_TITLE);
        assertEquals("Turno salvo.", DashboardTexts.SAVED);
        assertEquals("Turno excluído.", DashboardTexts.DELETED);
        assertEquals("Valor inválido. Use por exemplo 0,25.", DashboardTexts.MAINT_INVALID);
        assertEquals("Dinheiro que você separa a cada km rodado para pneus, óleo e revisões. "
            + "Ele é descontado do lucro.", DashboardTexts.MAINT_HELP);
    }
}
