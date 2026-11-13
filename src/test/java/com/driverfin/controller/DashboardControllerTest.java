package com.driverfin.controller;

import com.driverfin.model.WorkShift;
import com.driverfin.util.DashboardTexts;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MaintenanceRate;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the real dashboard.fxml with its controller (no window) and checks what the user would see. */
class DashboardControllerTest {
    private static final String[] HIDDEN_WHEN_EMPTY =
        {"todayCard", "filtersBar", "maintBar", "kpiGrid", "chartCard", "tableCard"};

    @TempDir
    File tempDir;

    private DashboardFixture fixture;

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

    @Test
    void emptyDatabaseShowsOnlyTheWelcomeBox() {
        FxTestSupport.onFxThread(() -> {
            newFixture("empty").open();
            assertTrue(fixture.visible("emptyStateBox"));
            for (String section : HIDDEN_WHEN_EMPTY) assertFalse(fixture.visible(section), section + " must be hidden");
            assertEquals(DashboardTexts.EMPTY_TITLE, text("emptyStateTitle"));
            assertEquals(DashboardTexts.EMPTY_TEXT, text("emptyStateText"));
        });
    }

    @Test
    void shiftTodayShowsTheProfitAndHidesTheWelcomeBox() {
        FxTestSupport.onFxThread(() -> {
            newFixture("today");
            WorkShift shift = fixture.saveShift(today(), "100.00", "20.00");
            fixture.open();
            assertFalse(fixture.visible("emptyStateBox"));
            for (String section : HIDDEN_WHEN_EMPTY) assertTrue(fixture.visible(section), section + " must be visible");
            assertEquals(DashboardTexts.profit(shift.calculateNetProfit(MaintenanceRate.DEFAULT)), text("lblTodayProfit"));
            assertTrue(fixture.<Label>node("lblTodayProfit").getStyleClass().contains("today-profit"));
            assertEquals(DashboardTexts.hours(4.0), text("lblTodayHours"));
            assertTrue(fixture.visible("todayValuesBox"));
            assertFalse(fixture.visible("lblTodayEmptyText"));
            assertFalse(fixture.visible("btnTodayNew"));
        });
    }

    @Test
    void twoShiftsTodayShowTheCountInTheSingleSummaryLine() {
        FxTestSupport.onFxThread(() -> {
            newFixture("two");
            fixture.saveShift(today(), "100.00", "0.00");
            fixture.saveShift(today(), "50.00", "0.00");
            fixture.open();
            assertEquals(DashboardTexts.todaySummary(new java.math.BigDecimal("150.00"), 2), text("lblTodayGross"));
            assertTrue(fixture.visible("lblTodayGross"));
        });
    }

    @Test
    void onlyOlderShiftsShowTheInviteInsteadOfZeros() {
        FxTestSupport.onFxThread(() -> {
            newFixture("yesterday");
            fixture.saveShift(today().minusDays(1), "100.00", "20.00");
            fixture.open();
            assertTrue(fixture.visible("todayCard"));
            assertFalse(fixture.visible("todayValuesBox"));
            assertTrue(fixture.visible("lblTodayEmptyText"));
            assertEquals(DashboardTexts.TODAY_EMPTY, text("lblTodayEmptyText"));
            assertTrue(fixture.visible("btnTodayNew"));
            assertEquals(DashboardTexts.TODAY_NEW_BUTTON, fixture.<javafx.scene.control.Button>node("btnTodayNew").getText());
        });
    }

    @Test
    void lossTodayUsesTheLossStyleAndSaysSo() {
        FxTestSupport.onFxThread(() -> {
            newFixture("loss");
            fixture.saveShift(today(), "10.00", "50.00");
            fixture.open();
            Label profit = fixture.node("lblTodayProfit");
            assertTrue(profit.getStyleClass().contains("today-profit-negative"));
            assertFalse(profit.getStyleClass().contains("today-profit"));
            assertTrue(profit.getText().contains("(prejuízo)"), profit.getText());
        });
    }

    @Test
    void emptyTablePlaceholderFollowsTheSearchText() {
        FxTestSupport.onFxThread(() -> {
            newFixture("placeholder");
            fixture.saveShift(today(), "100.00", "20.00");
            fixture.open();
            TextField search = fixture.node("searchField");
            search.setText("zzz");
            Label placeholder = (Label) table().getPlaceholder();
            assertEquals(DashboardTexts.tablePlaceholder("zzz"), placeholder.getText());
            search.clear();
            assertEquals(DashboardTexts.tablePlaceholder(""), placeholder.getText());
        });
    }

    @Test
    void unreadableRateKeepsThePreviousRateAndShowsTheMessage() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-invalid").open();
            TextField rate = fixture.node("maintRateField");
            rate.setText("abc");
            rate.fireEvent(new ActionEvent());
            assertEquals("0,25", rate.getText());
            assertTrue(fixture.visible("lblMaintError"));
            assertEquals(DashboardTexts.MAINT_INVALID, text("lblMaintError"));
            assertEquals("0.25", fixture.prefs.get("maintenance_rate", null));
            assertEquals(MaintenanceRate.describe(MaintenanceRate.DEFAULT), text("lblMaintSubtext"));
        });
    }

    @Test
    void validRateIsFormattedStoredAndDescribed() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-valid").open();
            TextField rate = fixture.node("maintRateField");
            rate.setText("0,3");
            rate.fireEvent(new ActionEvent());
            assertEquals("0,30", rate.getText());
            assertFalse(fixture.visible("lblMaintError"));
            assertEquals("0.30", fixture.prefs.get("maintenance_rate", null));
            assertEquals("R$\u00A00,30 por km rodado, para pneus, óleo e revisões.", text("lblMaintSubtext"));
            assertEquals(DashboardTexts.MAINT_HELP, text("lblMaintHelp"));
        });
    }

    @Test
    void rateIsNotStoredWhileTypingOnly() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-typing").open();
            fixture.<TextField>node("maintRateField").setText("0,4");
            assertNull(fixture.prefs.get("maintenance_rate", null));
        });
    }

    @Test
    void firstSavedShiftHidesTheWelcomeBoxAndSelectsTheRow() {
        FxTestSupport.onFxThread(() -> {
            newFixture("first").open();
            WorkShift saved = fixture.saveShift(today(), "100.00", "20.00");
            fixture.controller.showSavedShift(saved);
            assertFalse(fixture.visible("emptyStateBox"));
            assertTrue(fixture.visible("tableCard"));
            assertEquals(saved.getId(), table().getSelectionModel().getSelectedItem().getId());
            assertEquals(DashboardTexts.SAVED, text("lblStatus"));
            assertTrue(fixture.visible("lblStatus"));
        });
    }

    @Test
    void savedShiftHiddenByTheFilterLeavesNoSelection() {
        FxTestSupport.onFxThread(() -> {
            newFixture("hidden").saveShift(today(), "100.00", "20.00");
            fixture.open();
            fixture.<TextField>node("searchField").setText("nothing matches this");
            WorkShift saved = fixture.saveShift(today(), "50.00", "0.00");
            fixture.controller.showSavedShift(saved);
            assertNull(table().getSelectionModel().getSelectedItem());
            assertEquals(DashboardTexts.SAVED, text("lblStatus"));
        });
    }

    @Test
    void deleteKeyOnTheTableDeletesAndSelectsTheNextRow() {
        FxTestSupport.onFxThread(() -> {
            newFixture("delete");
            fixture.saveShift(today(), "30.00", "0.00");
            fixture.saveShift(today().minusDays(1), "20.00", "0.00");
            fixture.saveShift(today().minusDays(2), "10.00", "0.00");
            fixture.open();
            AtomicInteger asked = new AtomicInteger();
            fixture.controller.setDeleteConfirmer(ws -> asked.incrementAndGet() > 0);
            TableView<WorkShift> table = table();
            WorkShift middle = table.getItems().get(1);
            WorkShift last = table.getItems().get(2);
            table.getSelectionModel().select(middle);

            boolean passedOn = fixture.press(table, KeyCode.DELETE, false);

            assertFalse(passedOn, "Delete on the table is handled");
            assertEquals(1, asked.get());
            assertEquals(2, table.getItems().size());
            assertEquals(last.getId(), table.getSelectionModel().getSelectedItem().getId());
            assertEquals(DashboardTexts.DELETED, text("lblStatus"));
        });
    }

    @Test
    void deletingTheLastRowSelectsThePreviousOne() {
        FxTestSupport.onFxThread(() -> {
            newFixture("delete-last");
            fixture.saveShift(today(), "30.00", "0.00");
            fixture.saveShift(today().minusDays(1), "20.00", "0.00");
            fixture.open();
            fixture.controller.setDeleteConfirmer(ws -> true);
            TableView<WorkShift> table = table();
            WorkShift first = table.getItems().get(0);
            fixture.controller.handleDelete(table.getItems().get(1));
            assertEquals(first.getId(), table.getSelectionModel().getSelectedItem().getId());
        });
    }

    @Test
    void cancellingTheDeletionKeepsEverything() {
        FxTestSupport.onFxThread(() -> {
            newFixture("delete-cancel");
            fixture.saveShift(today(), "30.00", "0.00");
            fixture.open();
            fixture.controller.setDeleteConfirmer(ws -> false);
            fixture.controller.handleDelete(table().getItems().get(0));
            assertEquals(1, table().getItems().size());
            assertFalse(fixture.visible("lblStatus"));
        });
    }

    @Test
    void deletingTheOnlyShiftBringsTheWelcomeBoxBack() {
        FxTestSupport.onFxThread(() -> {
            newFixture("delete-all");
            fixture.saveShift(today(), "30.00", "0.00");
            fixture.open();
            fixture.controller.setDeleteConfirmer(ws -> true);
            fixture.controller.handleDelete(table().getItems().get(0));
            assertTrue(fixture.visible("emptyStateBox"));
            assertFalse(fixture.visible("tableCard"));
        });
    }

    @Test
    void escapeInTheSearchFieldClearsTheTextAndThenFocusesTheTable() {
        FxTestSupport.onFxThread(() -> {
            newFixture("escape");
            fixture.saveShift(today(), "30.00", "0.00");
            fixture.open();
            TextField search = fixture.node("searchField");
            search.setText("abc");
            assertFalse(fixture.press(search, KeyCode.ESCAPE, false));
            assertEquals("", search.getText());
            assertFalse(fixture.press(search, KeyCode.ESCAPE, false));
            assertSame(table(), fixture.scene.getFocusOwner());
        });
    }

    @Test
    void windowHintsAreFilled() {
        FxTestSupport.onFxThread(() -> {
            newFixture("hints").open();
            assertEquals(DashboardTexts.SHORTCUTS_HINT, text("lblShortcutsHint"));
            assertTrue(fixture.<Label>node("lblShortcutsHint").getStyleClass().contains("shortcut-hint"));
        });
    }
}
