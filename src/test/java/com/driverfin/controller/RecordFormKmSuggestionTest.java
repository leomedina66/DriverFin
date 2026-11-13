package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UX 7 and criterion 19: KM inicial suggested from the last shift. Temporary database, no window. */
class RecordFormKmSuggestionTest {
    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "km.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private WorkShift shift(LocalDate date, LocalTime start, double kmStart, double kmEnd) {
        return new WorkShift(null, date, start, start.plusHours(1), kmStart, kmEnd, BigDecimal.TEN, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    private void saveShift(LocalDate date, LocalTime start, double kmStart, double kmEnd) throws Exception {
        dao.save(shift(date, start, kmStart, kmEnd));
    }

    @Test
    void newShiftGetsTheKmEndOfTheLastShiftSelectedWithTheHint() throws Exception {
        saveShift(LocalDate.of(2026, 3, 1), LocalTime.of(8, 0), 100, 12000);
        saveShift(LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), 12000, 12630);
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);

            assertEquals("12.630,0", form.field("kmStartField").getText());
            assertEquals("12.630,0", form.field("kmStartField").getSelectedText(), "typing replaces it");
            assertTrue(form.label("kmStartHint").isVisible() && form.label("kmStartHint").isManaged());
            assertEquals(FormMessages.KM_SUGGESTED_HINT, form.label("kmStartHint").getText());
            assertTrue(form.label("kmStartHint").getStyleClass().contains("field-info"));
            assertFalse(form.label("kmStartError").isVisible());
        });
    }

    @Test
    void theHintGoesAwayOnTheFirstChangeAndTypingReplacesTheSuggestion() throws Exception {
        saveShift(LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), 12000, 12630);
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);

            form.field("kmStartField").replaceSelection("1"); // the suggestion is selected

            assertEquals("1", form.field("kmStartField").getText());
            assertFalse(form.label("kmStartHint").isVisible());
            assertFalse(form.label("kmStartHint").isManaged());
        });
    }

    @Test
    void acceptingTheSuggestionAndSavingStoresIt() throws Exception {
        saveShift(LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), 12000, 12630);
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("timeStartField"), "0800");
            FxTestSupport.type(form.field("timeEndField"), "1200");
            FxTestSupport.type(form.field("kmEndField"), "12700");

            form.save().fire();

            assertTrue(form.controller.isSaved());
            assertEquals(12630.0, form.controller.getSavedShift().orElseThrow().getKmStart());
        });
    }

    @Test
    void firstEverShiftHasNoSuggestionAndNoHint() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);

            assertEquals("", form.field("kmStartField").getText());
            assertFalse(form.label("kmStartHint").isVisible());
        });
    }

    @Test
    void editingNeverSuggests() throws Exception {
        saveShift(LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), 12000, 12630);
        WorkShift older = shift(LocalDate.of(2026, 3, 1), LocalTime.of(8, 0), 100.5, 12000);
        dao.save(older);
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, older);

            assertEquals("100,5", form.field("kmStartField").getText());
            assertFalse(form.label("kmStartHint").isVisible());
        });
    }

    @Test
    void aFailureReadingTheLastShiftOpensTheFormWithoutSuggestionAndWithoutDialog() {
        DatabaseConnection broken = new DatabaseConnection("jdbc:sqlite:"
            + new File(new File(tempDir, "does-not-exist"), "x.db").getAbsolutePath());
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(new WorkShiftDAO(broken), null);

            assertEquals("", form.field("kmStartField").getText());
            assertFalse(form.label("kmStartHint").isVisible());
            assertEquals(LocalDate.now(), form.datePicker().getValue(), "the form works normally");
        });
    }

    @Test
    void theSuggestionDoesNotCountAsAChangeForTheDiscardQuestion() throws Exception {
        saveShift(LocalDate.of(2026, 3, 2), LocalTime.of(8, 0), 12000, 12630);
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture untouched = new RecordFormFixture(dao, null);
            untouched.cancel().fire();
            assertEquals(0, untouched.questionsAsked);
            assertEquals(1, untouched.closed);

            RecordFormFixture changed = new RecordFormFixture(dao, null);
            changed.field("kmStartField").replaceSelection("1");
            changed.cancel().fire();
            assertEquals(1, changed.questionsAsked);
        });
    }
}
