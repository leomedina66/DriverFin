package com.driverfin.controller;

import com.driverfin.model.WorkShift;
import com.driverfin.service.SummaryService;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.DashboardTexts;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MaintenanceRate;
import com.driverfin.util.ShiftFilter;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tries to break the real dashboard: odd sequences of data, keys and maintenance-rate texts. */
class DashboardEdgeTest {
    @TempDir
    File tempDir;

    private DashboardFixture fixture;
    private boolean reachedTheScene;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @AfterEach
    void cleanUp() {
        if (fixture != null) fixture.cleanUp();
    }

    private DashboardFixture newFixture(String name) {
        fixture = new DashboardFixture(tempDir, name);
        return fixture;
    }

    private static LocalDate today() {
        return LocalDate.now();
    }

    private TableView<WorkShift> table() {
        return fixture.node("tableRecords");
    }

    private String text(String fxId) {
        Label label = fixture.node(fxId);
        return label.getText();
    }

    private static String normal(String money) {
        return money.replace(' ', ' ');
    }

    /** Opens the screen and watches whether a key reached the Scene (nobody consumed it on the way). */
    private DashboardFixture openWatching() {
        fixture.open();
        fixture.scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> reachedTheScene = true);
        return fixture;
    }

    private boolean press(Node target, KeyCode code, boolean shift, boolean ctrl, boolean alt, boolean meta) {
        reachedTheScene = false;
        Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, meta));
        return reachedTheScene;
    }

    private WorkShift saveShift(LocalDate date, LocalTime start, LocalTime end, double kmStart, double kmEnd,
                                String uber, String fuel) {
        WorkShift shift = new WorkShift(null, date, start, end, kmStart, kmEnd, new BigDecimal(uber),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(fuel), BigDecimal.ZERO, "");
        try {
            fixture.dao.save(shift);
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }
        return shift;
    }

    // ---- empty -> one -> delete the last, several times -----------------------------------------------------------

    @Test
    void shouldSwitchBetweenTheWelcomeBoxAndTheDashboardEveryTimeTheOnlyShiftIsAddedAndDeleted() {
        FxTestSupport.onFxThread(() -> {
            newFixture("cycle").open();
            fixture.controller.setDeleteConfirmer(ws -> true);
            assertTrue(fixture.visible("emptyStateBox"));

            for (int round = 1; round <= 3; round++) {
                WorkShift saved = fixture.saveShift(today(), "100.00", "20.00");
                fixture.controller.showSavedShift(saved);
                assertFalse(fixture.visible("emptyStateBox"), "round " + round);
                assertTrue(fixture.visible("todayCard"));
                assertEquals(1, table().getItems().size());
                assertEquals(saved.getId(), table().getSelectionModel().getSelectedItem().getId());
                assertEquals(DashboardTexts.SAVED, text("lblStatus"));
                assertEquals(DashboardTexts.profit(saved.calculateNetProfit(MaintenanceRate.DEFAULT)), text("lblTodayProfit"));
                assertEquals(1, fixture.<javafx.scene.chart.BarChart<String, Number>>node("chartDailyEvolution")
                        .getData().get(0).getData().size());

                fixture.press(table(), KeyCode.DELETE, false);
                assertTrue(fixture.visible("emptyStateBox"), "after deleting the last shift, round " + round);
                assertFalse(fixture.visible("todayCard"));
                assertFalse(fixture.visible("tableCard"));
                assertTrue(table().getItems().isEmpty());
                assertNull(table().getSelectionModel().getSelectedItem());
                assertEquals(DashboardTexts.DELETED, text("lblStatus"));
                assertSame(fixture.<Button>node("btnEmptyNew"), fixture.scene.getFocusOwner(),
                        "focus goes to the only useful button");
            }
        });
    }

    @Test
    void shouldDoNothingOnDeleteOrEnterWhenTheTableHasNoSelectionOrTheKeyHasAModifier() {
        FxTestSupport.onFxThread(() -> {
            newFixture("no-selection");
            fixture.saveShift(today(), "30.00", "0.00");
            openWatching();
            AtomicInteger asked = new AtomicInteger();
            fixture.controller.setDeleteConfirmer(ws -> {
                asked.incrementAndGet();
                return false;
            });
            table().getSelectionModel().clearSelection();

            press(table(), KeyCode.DELETE, false, false, false, false);
            press(table(), KeyCode.ENTER, false, false, false, false);
            assertEquals(0, asked.get(), "Delete with nothing selected must not even ask");

            table().getSelectionModel().select(0);
            press(table(), KeyCode.DELETE, false, true, false, false);
            press(table(), KeyCode.DELETE, false, false, true, false);
            press(table(), KeyCode.DELETE, false, false, false, true);
            assertEquals(0, asked.get(), "Ctrl/Alt/Meta + Delete is not the Delete shortcut");
            assertEquals(1, table().getItems().size());
        });
    }

    @Test
    void shouldLetDeleteEditTheTextInTheSearchFieldAndNeverDeleteAShiftFromThere() {
        FxTestSupport.onFxThread(() -> {
            newFixture("delete-in-search");
            fixture.saveShift(today(), "30.00", "0.00");
            openWatching();
            AtomicInteger asked = new AtomicInteger();
            fixture.controller.setDeleteConfirmer(ws -> {
                asked.incrementAndGet();
                return true;
            });
            table().getSelectionModel().select(0);
            TextField search = fixture.node("searchField");
            search.setText("30");
            assertTrue(table().getItems().size() == 1, "the search still shows the shift");
            table().getSelectionModel().select(0);
            search.positionCaret(0);

            press(search, KeyCode.DELETE, false, false, false, false);

            assertEquals("0", search.getText(), "Delete in the search field removes a character");
            assertEquals(0, asked.get());
            assertEquals(1, table().getItems().size());
        });
    }

    // ---- the today card ---------------------------------------------------------------------------------------------

    @Test
    void shouldShowZeroProfitAsAPlainValueAndOneCentLossAsALoss() {
        FxTestSupport.onFxThread(() -> {
            newFixture("boundary");
            // 60 km x 0,25 = 15,00 of maintenance
            saveShift(today(), LocalTime.of(8, 0), LocalTime.of(12, 0), 100, 160, "15.00", "0.00");
            fixture.open();
            Label profit = fixture.node("lblTodayProfit");
            assertEquals("R$ 0,00", normal(profit.getText()));
            assertTrue(profit.getStyleClass().contains("today-profit"));
            assertFalse(profit.getStyleClass().contains("today-profit-negative"));
        });
        FxTestSupport.onFxThread(() -> {
            newFixture("one-cent-loss");
            saveShift(today(), LocalTime.of(8, 0), LocalTime.of(12, 0), 100, 160, "14.99", "0.00");
            fixture.open();
            Label profit = fixture.node("lblTodayProfit");
            assertEquals("-R$ 0,01 (prejuízo)", normal(profit.getText()));
            assertTrue(profit.getStyleClass().contains("today-profit-negative"));
            assertFalse(profit.getStyleClass().contains("today-profit"));
        });
    }

    @Test
    void shouldKeepASingleProfitStyleClassAfterManyReloadsAndSwitchesBetweenGainAndLoss() {
        FxTestSupport.onFxThread(() -> {
            newFixture("styles").open();
            fixture.controller.setDeleteConfirmer(ws -> true);
            for (int i = 0; i < 5; i++) {
                WorkShift loss = fixture.saveShift(today(), "1.00", "50.00");
                fixture.controller.showSavedShift(loss);
                WorkShift gain = fixture.saveShift(today(), "500.00", "0.00");
                fixture.controller.showSavedShift(gain);
                fixture.controller.handleDelete(loss);
            }
            Label profit = fixture.node("lblTodayProfit");
            assertEquals(1, Collections.frequency(profit.getStyleClass(), "today-profit"));
            assertEquals(0, Collections.frequency(profit.getStyleClass(), "today-profit-negative"));
        });
    }

    @Test
    void shouldKeepTheTodayCardWhenThePeriodOrTheSearchHidesEveryRow() {
        FxTestSupport.onFxThread(() -> {
            newFixture("today-vs-filters");
            fixture.saveShift(today(), "100.00", "20.00");
            fixture.saveShift(today().minusDays(30), "70.00", "0.00");
            fixture.open();
            String profit = text("lblTodayProfit");
            String hours = text("lblTodayHours");
            String perHour = text("lblTodayGrossPerHour");

            fixture.<TextField>node("searchField").setText("nothing like this");
            assertEquals(profit, text("lblTodayProfit"));
            assertEquals("R$ 0,00", normal(text("lblGrossIncome")), "the period KPIs follow the search");
            fixture.<TextField>node("searchField").clear();

            ComboBox<ShiftFilter.Period> period = fixture.node("periodComboBox");
            period.setValue(ShiftFilter.Period.CUSTOM);
            fixture.<DatePicker>node("startDatePicker").setValue(today().minusDays(100));
            fixture.<DatePicker>node("endDatePicker").setValue(today().minusDays(60));
            assertEquals(0, table().getItems().size());
            assertEquals(profit, text("lblTodayProfit"));
            assertEquals(hours, text("lblTodayHours"));
            assertEquals(perHour, text("lblTodayGrossPerHour"));
            assertTrue(fixture.visible("todayValuesBox"));
            assertEquals(DashboardTexts.tablePlaceholder(""), ((Label) table().getPlaceholder()).getText());
        });
    }

    @Test
    void shouldCountOnlyTodayInTheCardWhenOtherShiftsAreYesterdayTomorrowOrCrossMidnight() {
        FxTestSupport.onFxThread(() -> {
            newFixture("neighbours");
            saveShift(today().minusDays(1), LocalTime.of(22, 0), LocalTime.of(2, 0), 100, 140, "80.00", "0.00");
            saveShift(today().plusDays(1), LocalTime.of(8, 0), LocalTime.of(12, 0), 140, 200, "999.00", "0.00");
            fixture.open();
            assertFalse(fixture.visible("todayValuesBox"), "a shift started yesterday and ending after midnight is yesterday's");
            assertTrue(fixture.visible("lblTodayEmptyText"));
            assertTrue(fixture.visible("btnTodayNew"));

            saveShift(today(), LocalTime.of(23, 0), LocalTime.of(3, 0), 200, 230, "50.00", "0.00");
            fixture.open();
            assertTrue(fixture.visible("todayValuesBox"));
            assertEquals(DashboardTexts.hours(4.0), text("lblTodayHours"));
            assertEquals(DashboardTexts.profit(new BigDecimal("50.00").subtract(new BigDecimal("7.50"))),
                    text("lblTodayProfit"), "only today's 50,00 less 30 km of maintenance");
        });
    }

    @Test
    void shouldShowADashAndZeroHoursWhenTheOnlyShiftTodayHasEqualTimes() {
        FxTestSupport.onFxThread(() -> {
            newFixture("zero-hours");
            saveShift(today(), LocalTime.of(9, 0), LocalTime.of(9, 0), 100, 100, "100.00", "0.00");
            fixture.open();
            assertEquals("0 h", text("lblTodayHours"));
            assertEquals("—", text("lblTodayGrossPerHour"));
            assertEquals("R$ 100,00", normal(text("lblTodayProfit")));
        });
    }

    @Test
    void shouldGiveTheTodayCardTheSameNumbersAsTheKpisWhenTodayIsTheOnlyDay() {
        FxTestSupport.onFxThread(() -> {
            newFixture("same-numbers");
            saveShift(today(), LocalTime.of(8, 0), LocalTime.of(16, 30), 1000, 1187.5, "312.34", "41.07");
            fixture.open();
            assertEquals(text("lblNetProfit"), text("lblTodayProfit"));
            assertEquals(text("lblGrossPerHour"), text("lblTodayGrossPerHour"));
        });
    }

    // ---- midnight: the clock is read inside the controller, so the pure pieces are tested with explicit days -------

    @Test
    void shouldSplitTheDaysAtMidnightInTheSummaryAndInTheTodayPeriodFilter() {
        LocalDate day = LocalDate.of(2026, 3, 31);
        WorkShift lateNight = new WorkShift(1L, day, LocalTime.of(23, 59), LocalTime.of(0, 1), 0, 10,
                new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        WorkShift nextMorning = new WorkShift(2L, day.plusDays(1), LocalTime.of(0, 0), LocalTime.of(1, 0), 10, 20,
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        List<WorkShift> all = List.of(lateNight, nextMorning);

        assertEquals(1, SummaryService.forDay(all, day, BigDecimal.ZERO).shiftCount());
        assertEquals(new BigDecimal("10.00"), SummaryService.forDay(all, day, BigDecimal.ZERO).totals().totalGross());
        assertEquals(new BigDecimal("20.00"), SummaryService.forDay(all, day.plusDays(1), BigDecimal.ZERO).totals().totalGross());
        assertEquals(0, SummaryService.forDay(all, day.plusDays(2), BigDecimal.ZERO).shiftCount());

        assertTrue(ShiftFilter.build(ShiftFilter.Period.TODAY, null, null, "", day).test(lateNight));
        assertFalse(ShiftFilter.build(ShiftFilter.Period.TODAY, null, null, "", day).test(nextMorning));
        assertTrue(ShiftFilter.build(ShiftFilter.Period.TODAY, null, null, "", day.plusDays(1)).test(nextMorning));
        assertTrue(ShiftFilter.build(ShiftFilter.Period.LAST_7_DAYS, null, null, "", day.plusDays(6)).test(lateNight));
        assertFalse(ShiftFilter.build(ShiftFilter.Period.LAST_7_DAYS, null, null, "", day.plusDays(7)).test(lateNight));
        // month and year boundaries
        assertTrue(ShiftFilter.build(ShiftFilter.Period.THIS_MONTH, null, null, "", day).test(lateNight));
        assertFalse(ShiftFilter.build(ShiftFilter.Period.THIS_MONTH, null, null, "", day).test(nextMorning));
        assertTrue(ShiftFilter.build(ShiftFilter.Period.LAST_MONTH, null, null, "", day.plusDays(1)).test(lateNight));
        LocalDate newYear = LocalDate.of(2027, 1, 1);
        WorkShift december = new WorkShift(3L, LocalDate.of(2026, 12, 31), LocalTime.of(8, 0), LocalTime.of(9, 0), 0, 1,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        assertTrue(ShiftFilter.build(ShiftFilter.Period.LAST_MONTH, null, null, "", newYear).test(december));
    }

    // ---- shortcuts --------------------------------------------------------------------------------------------------

    @Test
    void shouldFocusAndSelectTheSearchTextWithCtrlFFromEveryFocusablePlace() {
        FxTestSupport.onFxThread(() -> {
            newFixture("ctrl-f");
            fixture.saveShift(today(), "30.00", "0.00");
            openWatching();
            TextField search = fixture.node("searchField");
            search.setText("abc");
            List<Node> places = List.of(table(), search, fixture.<Node>node("maintRateField"),
                    fixture.<Node>node("periodComboBox"), fixture.<Node>node("startDatePicker"),
                    fixture.<Node>node("endDatePicker"));
            for (Node place : places) {
                place.requestFocus();
                boolean passedOn = press(place, KeyCode.F, false, true, false, false);
                assertFalse(passedOn, "Ctrl+F is consumed from " + place.getClass().getSimpleName());
                assertSame(search, fixture.scene.getFocusOwner());
                assertEquals("abc", search.getSelectedText());
                search.deselect();
            }
        });
    }

    @Test
    void shouldIgnoreCtrlFWhenThereIsNothingToSearchAndNeverFocusTheHiddenField() {
        FxTestSupport.onFxThread(() -> {
            newFixture("ctrl-f-empty");
            openWatching();
            press(table(), KeyCode.F, false, true, false, false);
            assertNotSame(fixture.node("searchField"), fixture.scene.getFocusOwner());
        });
    }

    @Test
    void shouldTypeTheLettersOfEveryShortcutInTheSearchAndTheRateFieldsWithoutTriggeringAnything() {
        FxTestSupport.onFxThread(() -> {
            newFixture("plain-letters");
            fixture.saveShift(today(), "30.00", "0.00");
            openWatching();
            TextField search = fixture.node("searchField");
            RecordFormJourneyTest.Keys.typeText(search, "nbpfNBPF");
            assertEquals("nbpfNBPF", search.getText());
            assertEquals(DashboardTexts.tablePlaceholder("nbpfNBPF"), ((Label) table().getPlaceholder()).getText());
            // Ctrl+F is the only shortcut that is safe to fire here (the others open dialogs), so use it as the probe
            // for the modifier variants that must NOT count as the shortcut.
            table().requestFocus();
            press(table(), KeyCode.F, false, false, false, false);
            press(table(), KeyCode.F, true, false, false, false);
            press(table(), KeyCode.F, false, false, true, false);
            press(table(), KeyCode.F, false, false, false, true);
            press(table(), KeyCode.F, true, true, false, false);
            press(table(), KeyCode.F, false, true, true, false);
            assertSame(table(), fixture.scene.getFocusOwner(), "no variant of the letter F may jump to the search");
        });
    }

    /** Every kind of control the dashboard has, in one Scene, with the real shortcut installer. */
    @Test
    void shouldRunEachCtrlShortcutExactlyOnceFromEveryKindOfControlAndIgnoreOtherModifiers() {
        FxTestSupport.onFxThread(() -> {
            int[] counts = new int[4];
            TextField field = new TextField();
            TextArea area = new TextArea();
            ComboBox<String> combo = new ComboBox<>();
            combo.setEditable(true);
            DatePicker picker = new DatePicker();
            TableView<String> tableView = new TableView<>();
            Button button = new Button("b");
            VBox root = new VBox(field, area, combo, picker, tableView, button);
            DashboardShortcuts.install(root, () -> counts[0]++, () -> counts[1]++, () -> counts[2]++, () -> counts[3]++);
            Scene scene = new Scene(root);
            scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> reachedTheScene = true);
            root.applyCss();      // the skins add the editors of the combo box and the date picker to the scene graph
            root.layout();

            List<Node> controls = List.of(field, area, combo, combo.getEditor(), picker, picker.getEditor(), tableView, button);
            KeyCode[] codes = {KeyCode.N, KeyCode.B, KeyCode.P, KeyCode.F};
            for (Node control : controls) {
                int[] before = counts.clone();
                for (KeyCode code : codes) {
                    assertFalse(press(control, code, false, true, false, false),
                            "Ctrl+" + code + " must be consumed on " + control.getClass().getSimpleName());
                }
                for (int i = 0; i < 4; i++) {
                    assertEquals(before[i] + 1, counts[i], "action " + i + " from " + control.getClass().getSimpleName());
                }
                int[] mid = counts.clone();
                for (KeyCode code : codes) {
                    press(control, code, true, true, false, false);    // Ctrl+Shift
                    press(control, code, false, true, true, false);    // Ctrl+Alt (AltGr)
                    press(control, code, false, true, false, true);    // Ctrl+Meta
                    press(control, code, false, false, false, false);  // plain letter
                }
                assertEquals(List.of(mid[0], mid[1], mid[2], mid[3]), List.of(counts[0], counts[1], counts[2], counts[3]),
                        "other modifiers must not trigger on " + control.getClass().getSimpleName());
            }
        });
    }

    @Test
    void shouldWalkTheDashboardWithTabInTheOrderOfTheUxDocument() {
        FxTestSupport.onFxThread(() -> {
            newFixture("tab-with-today");
            fixture.saveShift(today(), "30.00", "0.00");
            fixture.open();
            assertEquals(List.of("btnBackup", "btnPdfReport", "btnNewRecord", "periodComboBox", "searchField",
                    "maintRateField", "tableRecords"), tabWalkUntilTable("btnBackup"));
        });
        FxTestSupport.onFxThread(() -> {
            newFixture("tab-without-today");
            fixture.saveShift(today().minusDays(3), "30.00", "0.00");
            fixture.open();
            assertEquals(List.of("btnBackup", "btnPdfReport", "btnNewRecord", "btnTodayNew", "periodComboBox",
                    "searchField", "maintRateField", "tableRecords"), tabWalkUntilTable("btnBackup"));
        });
    }

    private List<String> tabWalkUntilTable(String firstId) {
        Node first = fixture.scene.getRoot().lookup("#" + firstId);
        first.requestFocus();
        java.util.ArrayList<String> walked = new java.util.ArrayList<>(List.of(RecordFormTabOrderJourneyTest.idOf(first)));
        for (int i = 0; i < 15 && !walked.contains("tableRecords"); i++) {
            RecordFormTabOrderJourneyTest.traverse(fixture.scene, "NEXT");
            walked.add(RecordFormTabOrderJourneyTest.idOf(fixture.scene.getFocusOwner()));
        }
        return walked;
    }

    // ---- the maintenance rate ----------------------------------------------------------------------------------------

    private void commitRate(String typed) {
        TextField rate = fixture.node("maintRateField");
        rate.setText(typed);
        rate.fireEvent(new ActionEvent());
    }

    @Test
    void shouldKeepTheLastValidRateWithAMessageForEveryUnreadableText() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-hostile").open();
            TextField rate = fixture.node("maintRateField");
            for (String junk : List.of("", "   ", "-1", "-0,25", "abc", "1e3", "NaN", "Infinity", "100,01", "100.01",
                    "1000", "999999999999", "0,3,5", ",5", "5,", ".5", "٣", "٣٠٠", "R$ 0,25", "0,25 por km", "1.000,50",
                    "0x1","--1", "+1", "9".repeat(50), "😀")) {
                commitRate("0,40");
                assertEquals("0,40", rate.getText(), "setup");
                commitRate(junk);
                String shown = junk.replace("\n", "\\n");
                assertEquals("0,40", rate.getText(), "\"" + shown + "\" must bring back the last valid rate");
                assertTrue(fixture.visible("lblMaintError"), "\"" + shown + "\" needs the message");
                assertEquals("0.40", fixture.prefs.get("maintenance_rate", null), "stored rate after \"" + shown + "\"");
                assertEquals("R$\u00A00,40 por km rodado, para pneus, óleo e revisões.", text("lblMaintSubtext"));
                assertFalse(rate.getStyleClass().contains("input-error"), "the error border goes with the old text");
            }
        });
    }

    @Test
    void shouldAcceptEveryReadableRateWithTwoDecimalsAndStoreIt() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-valid-many").open();
            TextField rate = fixture.node("maintRateField");
            String[][] cases = {{"0", "0,00", "0.00"}, {"0,0", "0,00", "0.00"}, {"0.3", "0,30", "0.30"},
                {" 0,3 ", "0,30", "0.30"}, {"100", "100,00", "100.00"}, {"100,00", "100,00", "100.00"},
                {"00,30", "0,30", "0.30"}, {"1,5", "1,50", "1.50"}, {"0,05", "0,05", "0.05"}};
            for (String[] c : cases) {
                commitRate(c[0]);
                assertEquals(c[1], rate.getText(), "typed \"" + c[0] + "\"");
                assertEquals(c[2], fixture.prefs.get("maintenance_rate", null), "stored after \"" + c[0] + "\"");
                assertFalse(fixture.visible("lblMaintError"));
            }
        });
    }

    @Test
    void shouldRecalculateTheKpisAndTheTodayCardAsSoonAsTheRateIsValidAndIgnoreInvalidTexts() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-live");
            saveShift(today(), LocalTime.of(8, 0), LocalTime.of(12, 0), 100, 160, "100.00", "0.00");
            fixture.open();
            TextField rate = fixture.node("maintRateField");
            assertEquals("R$ 85,00", normal(text("lblTodayProfit")));

            rate.setText("1");
            assertEquals("R$ 40,00", normal(text("lblTodayProfit")));
            assertEquals("R$ 40,00", normal(text("lblNetProfit")));
            rate.setText("x");
            assertEquals("R$ 40,00", normal(text("lblTodayProfit")), "an unreadable text keeps the last valid rate");
            assertTrue(rate.getStyleClass().contains("input-error"));
            rate.setText("0");
            assertEquals("R$ 100,00", normal(text("lblTodayProfit")));
            assertFalse(rate.getStyleClass().contains("input-error"));

            commitRate("1,25");
            fixture.open();      // a second screen on the same data and preferences
            assertEquals("1,25", fixture.<TextField>node("maintRateField").getText(), "the stored rate comes back");
            assertEquals("R$ 25,00", normal(text("lblTodayProfit")));
        });
    }

    /**
     * The field shows two decimals; once the user leaves it the calculation must use that same rate, so
     * "R$ 0,26 por km" and a reserve of 60 km agree.
     */
    @Test
    void shouldCalculateWithTheSameRateTheFieldAndTheCardShowAfterTheUserLeavesTheField() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-rounding");
            saveShift(today(), LocalTime.of(8, 0), LocalTime.of(12, 0), 100, 160, "100.00", "0.00");
            fixture.open();
            commitRate("0,255");
            TextField rate = fixture.node("maintRateField");
            BigDecimal shown = MaintenanceRate.parse(rate.getText()).orElseThrow();
            BigDecimal expectedReserve = shown.multiply(new BigDecimal("60"));
            assertEquals(CurrencyFormat.money(expectedReserve), text("lblMaintReserve"),
                    "field says " + rate.getText() + " per km, 60 km must cost " + CurrencyFormat.money(expectedReserve));
        });
    }

    /** A pasted rate with thousands of decimals must not blow up the preferences (they refuse long values). */
    @Test
    void shouldNotThrowWhenARateWithThousandsOfDecimalsIsCommitted() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-giant").open();
            TextField rate = fixture.node("maintRateField");
            rate.setText("0," + "1".repeat(9000));
            assertDoesNotThrow(() -> rate.fireEvent(new ActionEvent()));
            assertTrue(rate.getText().matches("\\d+,\\d{2}"), "the field must hold a normal rate, got " + rate.getText().length() + " chars");
        });
    }

    // ---- selection after saving ------------------------------------------------------------------------------------

    @Test
    void shouldLeaveNothingSelectedAndAskNothingOnDeleteWhenTheSavedShiftIsHiddenByThePeriod() {
        FxTestSupport.onFxThread(() -> {
            newFixture("hidden-by-period");
            fixture.saveShift(today(), "100.00", "20.00");
            fixture.open();
            AtomicInteger asked = new AtomicInteger();
            fixture.controller.setDeleteConfirmer(ws -> {
                asked.incrementAndGet();
                return false;
            });
            fixture.<ComboBox<ShiftFilter.Period>>node("periodComboBox").setValue(ShiftFilter.Period.TODAY);
            WorkShift old = saveShift(today().minusDays(10), LocalTime.of(8, 0), LocalTime.of(9, 0), 1, 2, "10.00", "0.00");

            fixture.controller.showSavedShift(old);

            assertNull(table().getSelectionModel().getSelectedItem());
            assertEquals(1, table().getItems().size(), "only today's row is listed");
            assertEquals(DashboardTexts.SAVED, text("lblStatus"));
            fixture.press(table(), KeyCode.DELETE, false);
            assertEquals(0, asked.get());
        });
    }
}
