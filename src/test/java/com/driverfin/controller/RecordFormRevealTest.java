package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.util.FxTestSupport;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R8: focus given by code scrolls the form to the field. A short window makes the form scroll. */
class RecordFormRevealTest {
    private static final double SHORT_WINDOW = 420;
    private static final double TOLERANCE = 0.5;

    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "reveal.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private static ScrollPane scrollOf(RecordFormFixture form) {
        return (ScrollPane) form.node("formScroll");
    }

    private static Bounds viewportInScene(ScrollPane pane) {
        Node viewport = pane.lookup(".viewport");
        return viewport.localToScene(viewport.getBoundsInLocal());
    }

    private static void assertFullyInsideViewport(ScrollPane pane, Node node) {
        Bounds view = viewportInScene(pane);
        Bounds box = node.localToScene(node.getBoundsInLocal());
        assertTrue(box.getMinY() >= view.getMinY() - TOLERANCE, "top " + box.getMinY() + " is above " + view.getMinY());
        assertTrue(box.getMaxY() <= view.getMaxY() + TOLERANCE, "bottom " + box.getMaxY() + " is below " + view.getMaxY());
    }

    @Test
    void theFormReallyScrollsInAShortWindow() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null, SHORT_WINDOW);
            ScrollPane pane = scrollOf(form);
            assertTrue(pane.getContent().getLayoutBounds().getHeight() > pane.getViewportBounds().getHeight(),
                    "the test needs a viewport smaller than the content");
        });
    }

    @Test
    void savingWithAnErrorInTheDateScrollsBackUpToTheDate() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null, SHORT_WINDOW);
            ScrollPane pane = scrollOf(form);
            form.datePicker().setValue(null);
            pane.setVvalue(1);
            form.root.applyCss();
            form.root.layout();
            assertEquals(1.0, pane.getVvalue());

            form.save().fire();
            form.root.layout();

            assertNotEquals(1.0, pane.getVvalue(), "the form did not scroll");
            assertFullyInsideViewport(pane, form.datePicker());
            assertTrue(form.label("dateError").isVisible());
            assertFullyInsideViewport(pane, form.label("dateError"));
        });
    }

    @Test
    void savingWithAnErrorInALowFieldScrollsDownToIt() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null, SHORT_WINDOW);
            ScrollPane pane = scrollOf(form);
            form.typeValidShift();
            form.notes().setText("x".repeat(600));
            pane.setVvalue(0);
            form.root.layout();

            form.save().fire();
            form.root.layout();

            assertTrue(pane.getVvalue() > 0, "the form did not scroll down");
            assertFullyInsideViewport(pane, form.notes());
        });
    }

    @Test
    void enterWalksTheFocusAndKeepsEachFieldInView() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null, SHORT_WINDOW);
            ScrollPane pane = scrollOf(form);
            pane.setVvalue(0);
            form.root.layout();

            for (String id : new String[]{"kmEndField", "earnUberField", "earn99Field", "earnOthersField", "costFuelField"}) {
                Node previous = form.root.getScene().getFocusOwner();
                form.press(previous == null ? form.focus("kmStartField") : previous, KeyCode.ENTER);
                form.root.layout();
                assertEquals(form.node(id), form.root.getScene().getFocusOwner());
                assertFullyInsideViewport(pane, form.node(id));
            }
            assertTrue(pane.getVvalue() > 0, "the form never scrolled");
        });
    }

    /** m5: with a real window (real focus changes), leaving KM final shows "12.700,0". */
    @Test
    void kmFinalIsFormattedWhenItLosesTheRealFocus() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            Stage stage = new Stage();
            stage.setScene(form.scene);
            stage.show();
            try {
                Node kmEnd = form.focus("kmEndField");
                org.junit.jupiter.api.Assumptions.assumeTrue(kmEnd.isFocused(), "the window did not get the focus");
                FxTestSupport.type(form.field("kmEndField"), "12700");
                assertEquals("12700", form.field("kmEndField").getText());

                form.focus("timeStartField");

                assertEquals("12.700,0", form.field("kmEndField").getText());
            } finally {
                stage.hide();
            }
        });
    }
}
