package com.driverfin.controller;

import com.driverfin.model.WorkShift;
import com.driverfin.util.DashboardTexts;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MaintenanceRate;
import com.driverfin.util.Styles;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corrections of the final review round on the dashboard: maintenance rate, day change, loss style, layout. */
class DashboardFinalRoundTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    /** A clock a test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advanceTo(Instant instant) {
            this.now = instant;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

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

    private String text(String fxId) {
        Label label = fixture.node(fxId);
        return label.getText();
    }

    private boolean hasLossStyle(String fxId) {
        Node node = fixture.node(fxId);
        return node.getStyleClass().contains(Styles.NEGATIVE_VALUE);
    }

    private static long count(Node node, String styleClass) {
        return node.getStyleClass().stream().filter(styleClass::equals).count();
    }

    // ---- maintenance rate (C) -----------------------------------------------------------------------------

    @Test
    void invalidRateGoesBackToTheConfirmedRateNotToANumberTypedOnTheWay() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-confirmed").open();
            TextField rate = fixture.node("maintRateField");
            for (String typed : new String[]{"2", "20", "200"}) rate.setText(typed);
            rate.fireEvent(new ActionEvent());

            assertEquals("0,25", rate.getText());
            assertTrue(fixture.visible("lblMaintError"));
            assertEquals(DashboardTexts.MAINT_INVALID, text("lblMaintError"));
            assertEquals("0.25", fixture.prefs.get("maintenance_rate", null));
            assertEquals(MaintenanceRate.describe(MaintenanceRate.DEFAULT), text("lblMaintSubtext"));
        });
    }

    @Test
    void textWithTrailingJunkGoesBackToTheConfirmedRate() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-junk").open();
            TextField rate = fixture.node("maintRateField");
            rate.setText("0,5");
            rate.setText("0,5x");
            rate.fireEvent(new ActionEvent());

            assertEquals("0,25", rate.getText());
            assertEquals("0.25", fixture.prefs.get("maintenance_rate", null));
            assertEquals(MaintenanceRate.describe(MaintenanceRate.DEFAULT), text("lblMaintSubtext"));
        });
    }

    @Test
    void confirmedRateIsTheOneAnInvalidTextFallsBackToLater() {
        FxTestSupport.onFxThread(() -> {
            newFixture("rate-second").open();
            TextField rate = fixture.node("maintRateField");
            rate.setText("0,4");
            rate.fireEvent(new ActionEvent());
            rate.setText("9999");
            rate.fireEvent(new ActionEvent());

            assertEquals("0,40", rate.getText());
            assertEquals("0.40", fixture.prefs.get("maintenance_rate", null));
        });
    }

    // ---- day change (N) -----------------------------------------------------------------------------------

    @Test
    void todayCardBecomesTomorrowWhenTheClockPassesMidnight() {
        FxTestSupport.onFxThread(() -> {
            newFixture("midnight");
            fixture.saveShift(DAY, "100.00", "20.00");
            MovableClock clock = new MovableClock(Instant.parse("2026-11-10T23:50:00Z"));
            fixture.clock = clock;
            fixture.open();
            assertEquals(DashboardTexts.todayTitle(DAY), text("lblTodayTitle"));
            assertTrue(fixture.visible("todayValuesBox"));

            clock.advanceTo(Instant.parse("2026-11-11T00:01:00Z"));
            fixture.controller.refreshDayIfChanged();

            assertEquals(DashboardTexts.todayTitle(DAY.plusDays(1)), text("lblTodayTitle"));
            assertFalse(fixture.visible("todayValuesBox"), "the shift of yesterday is not today's shift");
            assertTrue(fixture.visible("btnTodayNew"));
        });
    }

    @Test
    void sameDayLeavesTheScreenAlone() {
        FxTestSupport.onFxThread(() -> {
            newFixture("same-day");
            fixture.saveShift(DAY, "100.00", "20.00");
            MovableClock clock = new MovableClock(Instant.parse("2026-11-10T08:00:00Z"));
            fixture.clock = clock;
            fixture.open();

            clock.advanceTo(Instant.parse("2026-11-10T23:59:00Z"));
            fixture.controller.refreshDayIfChanged();

            assertEquals(DashboardTexts.todayTitle(DAY), text("lblTodayTitle"));
            assertTrue(fixture.visible("todayValuesBox"));
        });
    }

    // ---- loss style (Q) -----------------------------------------------------------------------------------

    @Test
    void lossGetsTheNegativeStyleOnceAndLosesItWhenTheProfitComesBack() {
        FxTestSupport.onFxThread(() -> {
            newFixture("loss");
            fixture.saveShift(LocalDate.now(), "10.00", "100.00");
            fixture.open();
            for (String id : new String[]{"lblNetProfit", "lblProfitPerHour", "lblProfitPerKm", "lblTodayProfit"}) {
                assertTrue(hasLossStyle(id), id + " must show the loss style");
            }
            assertFalse(hasLossStyle("lblGrossIncome"), "revenue is not a loss");

            WorkShift gain = fixture.saveShift(LocalDate.now(), "5000.00", "0.00");
            fixture.controller.showSavedShift(gain);
            for (String id : new String[]{"lblNetProfit", "lblProfitPerHour", "lblProfitPerKm", "lblTodayProfit"}) {
                assertFalse(hasLossStyle(id), id + " must lose the loss style");
            }
        });
    }

    @Test
    void lossStyleIsNeverAccumulatedOnRepeatedRefreshes() {
        FxTestSupport.onFxThread(() -> {
            newFixture("loss-once");
            fixture.saveShift(LocalDate.now(), "10.00", "100.00");
            fixture.open();
            TextField rate = fixture.node("maintRateField");
            for (String typed : new String[]{"0,30", "0,35", "0,40"}) {
                rate.setText(typed);
            }
            Label net = fixture.node("lblNetProfit");
            assertEquals(1, count(net, Styles.NEGATIVE_VALUE));
        });
    }

    // ---- one primary action (R) and the today revenue line (S) --------------------------------------------

    @Test
    void headerButtonIsSecondaryWhileTheEmptyStateHasTheMainButton() {
        FxTestSupport.onFxThread(() -> {
            newFixture("btn-empty").open();
            Node header = fixture.node("btnNewRecord");
            assertTrue(header.getStyleClass().contains("btn-outline"));
            assertFalse(header.getStyleClass().contains("btn-primary"));
        });
    }

    @Test
    void headerButtonIsSecondaryWhileTheTodayCardAsksToRegisterAShift() {
        FxTestSupport.onFxThread(() -> {
            newFixture("btn-card");
            fixture.saveShift(LocalDate.now().minusDays(3), "100.00", "20.00");
            fixture.open();
            assertTrue(fixture.visible("btnTodayNew"));
            Node header = fixture.node("btnNewRecord");
            assertTrue(header.getStyleClass().contains("btn-outline"));
            assertEquals(0, count(header, "btn-primary"));
        });
    }

    @Test
    void headerButtonIsPrimaryAgainWhenThereIsAShiftToday() {
        FxTestSupport.onFxThread(() -> {
            newFixture("btn-today");
            fixture.saveShift(LocalDate.now(), "100.00", "20.00");
            fixture.open();
            Node header = fixture.node("btnNewRecord");
            assertEquals(1, count(header, "btn-primary"));
            assertEquals(0, count(header, "btn-outline"));
        });
    }

    @Test
    void todayCardShowsTheRevenueOfTheDayWhenThereIsAShift() {
        FxTestSupport.onFxThread(() -> {
            newFixture("today-gross");
            fixture.saveShift(LocalDate.now(), "100.00", "20.00");
            fixture.open();
            assertTrue(fixture.visible("lblTodayGross"));
            assertEquals(DashboardTexts.todaySummary(new java.math.BigDecimal("100.00"), 1), text("lblTodayGross"));
        });
    }

    @Test
    void todayRevenueLineIsHiddenWithoutAShiftToday() {
        FxTestSupport.onFxThread(() -> {
            newFixture("today-gross-none");
            fixture.saveShift(LocalDate.now().minusDays(2), "100.00", "20.00");
            fixture.open();
            assertFalse(fixture.visible("lblTodayGross"));
        });
    }

    // ---- table layout (P) ---------------------------------------------------------------------------------

    @Test
    void tableScrollsSidewaysInsteadOfSqueezingColumnsAndKeepsReadableMinimums() {
        FxTestSupport.onFxThread(() -> {
            newFixture("table-layout").open();
            TableView<WorkShift> table = fixture.node("tableRecords");
            assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
            assertMinimum("colDate", 100);
            assertMinimum("colGross", 110);
            assertMinimum("colNet", 110);
            assertMinimum("colActions", 150);
            for (String id : new String[]{"colHours", "colKm", "colCosts", "colMaint", "colGrossPerHour", "colGrossPerKm"}) {
                assertMinimum(id, 85);
            }
        });
    }

    private void assertMinimum(String columnId, double minimum) {
        TableColumn<?, ?> column = fixture.node(columnId);
        assertTrue(column.getMinWidth() >= minimum, columnId + " minimum width is " + column.getMinWidth());
        assertTrue(column.getPrefWidth() >= column.getMinWidth(), columnId + " preferred width below its minimum");
    }
}
