package com.driverfin.controller;

import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.DashboardTexts;
import com.driverfin.util.FxTestSupport;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Second review round on the dashboard: the table layout and the state shown when the shifts cannot be read. */
class DashboardSecondRoundTest {
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

    @Test
    void actionsColumnComesRightAfterDateAndTheColumnMenuIsAvailable() {
        FxTestSupport.onFxThread(() -> {
            fixture = new DashboardFixture(tempDir, "columns").open();
            TableView<WorkShift> table = fixture.node("tableRecords");
            List<TableColumn<WorkShift, ?>> columns = table.getColumns();
            assertEquals(fixture.node("colDate"), columns.get(0));
            assertEquals(fixture.node("colActions"), columns.get(1));
            assertTrue(table.isTableMenuButtonVisible());
        });
    }

    @Test
    void tableCardGrowsWithTheWindowHeight() {
        FxTestSupport.onFxThread(() -> {
            fixture = new DashboardFixture(tempDir, "grow");
            fixture.saveShift(LocalDate.now(), "100.00", "20.00");
            fixture.open();
            ScrollPane scroll = (ScrollPane) fixture.scene.getRoot().lookup(".scroll-pane");
            assertTrue(scroll.isFitToHeight(), "extra window height must go to the content");
            assertEquals(javafx.scene.layout.Priority.ALWAYS,
                    javafx.scene.layout.VBox.getVgrow(fixture.node("tableCard")));
        });
    }

    @Test
    void failedFirstLoadShowsANeutralErrorInsteadOfTheWelcomeText() {
        FxTestSupport.onFxThread(() -> {
            fixture = new DashboardFixture(tempDir, "failing", db -> new WorkShiftDAO(db) {
                @Override
                public List<WorkShift> findAll() throws SQLException {
                    throw new SQLException("disk unreadable");
                }
            }).open();

            assertTrue(fixture.visible("emptyStateBox"));
            assertEquals(DashboardTexts.LOAD_FAILED_TITLE, fixture.<Label>node("emptyStateTitle").getText());
            assertEquals(DashboardTexts.LOAD_FAILED_TEXT, fixture.<Label>node("emptyStateText").getText());
            assertFalse(fixture.visible("btnEmptyNew"));
            assertFalse(fixture.<Label>node("emptyStateText").getText().contains("ainda não registrou"));
            assertTrue(fixture.<javafx.scene.Node>node("btnNewRecord").getStyleClass().contains("btn-primary"));
        });
    }

    @Test
    void emptyDatabaseStillShowsTheWelcomeText() {
        FxTestSupport.onFxThread(() -> {
            fixture = new DashboardFixture(tempDir, "welcome").open();

            assertEquals(DashboardTexts.EMPTY_TITLE, fixture.<Label>node("emptyStateTitle").getText());
            assertEquals(DashboardTexts.EMPTY_TEXT, fixture.<Label>node("emptyStateText").getText());
            assertTrue(fixture.visible("btnEmptyNew"));
        });
    }
}
