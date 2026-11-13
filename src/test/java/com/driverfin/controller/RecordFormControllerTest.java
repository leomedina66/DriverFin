package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.ShiftField;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loads the real record-form.fxml with its controller (no window) and drives it like the user would. */
class RecordFormControllerTest {
    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "form.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    /** The loaded form (see {@link RecordFormFixture}) on the temporary database of this test. */
    private final class Form extends RecordFormFixture {
        Form(WorkShift shift) {
            super(dao, shift);
        }
    }

    @Test
    void fxmlLoadsAndEveryFxmlFieldIsInjected() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            for (Field field : RecordFormController.class.getDeclaredFields()) {
                if (!field.isAnnotationPresent(FXML.class)) continue;
                try {
                    field.setAccessible(true);
                    assertNotNull(field.get(form.controller), "@FXML field not injected: " + field.getName());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        });
    }

    @Test
    void newFormStartsWithTodayNoMessagesAndFocusOnStartTime() {
        Form[] holder = new Form[1];
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            holder[0] = form;
            assertEquals(LocalDate.now(), form.datePicker().getValue());
            assertEquals(FormMessages.TITLE_NEW, form.label("lblFormTitle").getText());
            assertEquals("0,00", form.field("earnUberField").getText());
            for (String id : List.of("dateError", "timeStartError", "timeEndError", "kmStartError", "kmEndError",
                    "kmStartHint", "earnUberMsg", "notesError", "turnNote", "rateWarn")) {
                assertFalse(form.label(id).isVisible(), id + " must start hidden");
                assertFalse(form.label(id).isManaged(), id + " must take no space");
            }
            assertTrue(form.label("moneyHint").isVisible());
            assertEquals(FormMessages.MONEY_HINT, form.label("moneyHint").getText());
        });
        FxTestSupport.onFxThread(() ->
            assertSame(holder[0].field("timeStartField"), holder[0].scene.getFocusOwner()));
    }

    @Test
    void typingZeroEightHundredGivesEightOClock() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            FxTestSupport.type(form.field("timeStartField"), "0800");
            assertEquals("08:00", form.field("timeStartField").getText());
        });
    }

    @Test
    void saveWithEverythingFilledSavesAndExposesTheShift() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeValidShift();
            form.save().fire();

            assertTrue(form.controller.isSaved());
            WorkShift saved = form.controller.getSavedShift().orElseThrow();
            assertNotNull(saved.getId());
            try {
                List<WorkShift> stored = dao.findAll();
                assertEquals(1, stored.size());
                WorkShift row = stored.get(0);
                assertEquals(LocalDate.now(), row.getDate());
                assertEquals(LocalTime.of(8, 0), row.getTimeStart());
                assertEquals(LocalTime.of(12, 0), row.getTimeEnd());
                assertEquals(100.0, row.getKmStart());
                assertEquals(200.5, row.getKmEnd());
                assertEquals(0, new BigDecimal("1234.50").compareTo(row.getUberEarnings()));
            } catch (java.sql.SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void saveWithFocusStillInsideAHalfWrittenTimeCompletesIt() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.typeValidShift();
            for (int digit = 0; digit < 4; digit++) FxTestSupport.backspace(form.field("timeEndField"));
            FxTestSupport.type(form.field("timeEndField"), "9");
            assertEquals("09:", form.field("timeEndField").getText());

            form.save().fire();

            assertTrue(form.controller.getSavedShift().isPresent());
            assertEquals(LocalTime.of(9, 0), form.controller.getSavedShift().get().getTimeEnd());
        });
    }

    @Test
    void saveWithEmptyFormShowsEveryMessageFocusesTheFirstAndDoesNotSave() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.save().fire();

            assertFalse(form.controller.isSaved());
            assertFalse(form.save().isDisabled());
            assertShown(form.label("timeStartError"), FormMessages.TIME_START_REQUIRED);
            assertShown(form.label("timeEndError"), FormMessages.TIME_END_REQUIRED);
            assertShown(form.label("kmStartError"), FormMessages.KM_START_REQUIRED);
            assertShown(form.label("kmEndError"), FormMessages.KM_END_REQUIRED);
            assertFalse(form.label("dateError").isVisible());
            assertTrue(form.field("timeStartField").getStyleClass().contains("input-error"));
            assertSame(form.field("timeStartField"), form.scene.getFocusOwner());
            try {
                assertTrue(dao.findAll().isEmpty());
            } catch (java.sql.SQLException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void saveWithoutDateAlsoShowsTheDateMessageAndFocusesTheDate() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.datePicker().setValue(null);
            form.save().fire();

            assertShown(form.label("dateError"), FormMessages.DATE_REQUIRED);
            assertShown(form.label("timeStartError"), FormMessages.TIME_START_REQUIRED);
            assertSame(form.datePicker(), form.scene.getFocusOwner());
        });
    }

    @Test
    void errorsClearOnTheNextSaveOnceFixed() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.save().fire();
            form.typeValidShift();
            assertFalse(form.label("timeStartError").isVisible(), "typing a valid value clears the message");

            form.save().fire();

            assertTrue(form.controller.isSaved());
        });
    }

    @Test
    void untouchedFieldShowsNothingWhenLeft() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            form.controller.checkOnExit(ShiftField.TIME_START);
            assertFalse(form.label("timeStartError").isVisible());
        });
    }

    @Test
    void touchedHalfWrittenTimeShowsItsMessageOnExitAndClearsWhenFixed() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            FxTestSupport.type(form.field("timeStartField"), "083");
            form.controller.checkOnExit(ShiftField.TIME_START);
            assertShown(form.label("timeStartError"), FormMessages.TIME_INCOMPLETE);

            FxTestSupport.type(form.field("timeStartField"), "0");

            assertEquals("08:30", form.field("timeStartField").getText());
            assertFalse(form.label("timeStartError").isVisible());
        });
    }

    @Test
    void kmEndBelowStartIsShownOnLeavingKmStartAndClearsWhenStartIsLowered() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            FxTestSupport.type(form.field("kmEndField"), "12000");
            FxTestSupport.type(form.field("kmStartField"), "12450");
            form.controller.checkOnExit(ShiftField.KM_START);
            assertShown(form.label("kmEndError"), FormMessages.kmEndBelowStart("12.450,0"));

            form.field("kmStartField").replaceText(0, form.field("kmStartField").getLength(), "11000");

            assertFalse(form.label("kmEndError").isVisible());
        });
    }

    @Test
    void editModeShowsTheSavedValuesInDisplayFormat() {
        FxTestSupport.onFxThread(() -> {
            WorkShift shift = new WorkShift(7L, LocalDate.of(2026, 3, 5), LocalTime.of(8, 0), LocalTime.of(12, 30),
                100.55, 12450.0, new BigDecimal("1234.50"), BigDecimal.ZERO, new BigDecimal("0.05"),
                new BigDecimal("40.00"), BigDecimal.ZERO, "nota");
            Form form = new Form(shift);

            assertEquals(FormMessages.TITLE_EDIT, form.label("lblFormTitle").getText());
            assertEquals(LocalDate.of(2026, 3, 5), form.datePicker().getValue());
            assertEquals("08:00", form.field("timeStartField").getText());
            assertEquals("12:30", form.field("timeEndField").getText());
            assertEquals("100,55", form.field("kmStartField").getText());
            assertEquals("12.450,0", form.field("kmEndField").getText());
            assertEquals("1.234,50", form.field("earnUberField").getText());
            assertEquals("0,05", form.field("earnOthersField").getText());
            assertEquals("40,00", form.field("costFuelField").getText());
            form.controller.checkOnExit(ShiftField.KM_START);
            assertFalse(form.label("kmEndError").isVisible(), "filling the form is not the user touching it");
        });
    }

    @Test
    void editModeSavesChangesOverTheSameRow() throws Exception {
        WorkShift shift = new WorkShift(null, LocalDate.of(2026, 3, 5), LocalTime.of(8, 0), LocalTime.of(12, 0),
            100.0, 200.0, new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, "");
        dao.save(shift);
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(shift);
            form.field("earnUberField").replaceText(0, form.field("earnUberField").getLength(), "1.234,50");
            form.save().fire();
            assertTrue(form.controller.isSaved());
        });

        List<WorkShift> stored = dao.findAll();
        assertEquals(1, stored.size());
        assertEquals(0, new BigDecimal("1234.50").compareTo(stored.get(0).getUberEarnings()));
    }

    @Test
    void liveCalculationFollowsTheMaskedFields() {
        FxTestSupport.onFxThread(() -> {
            Form form = new Form(null);
            assertEquals("—", form.label("lblCalcGross").getText());
            form.typeValidShift();

            assertEquals(CurrencyFormat.oneDecimal(4.0) + " h", form.label("lblCalcHours").getText());
            assertEquals(CurrencyFormat.money(new BigDecimal("1234.50")), form.label("lblCalcGross").getText());
        });
    }

    @Test
    void dateConverterReturnsNullForUnreadableText() {
        StringConverter<LocalDate> converter = RecordFormController.newDateConverter();
        assertNull(converter.fromString("abc"));
        assertNull(converter.fromString("31/02/2026"));
        assertNull(converter.fromString(""));
        assertNull(converter.fromString(null));
        assertEquals(LocalDate.of(2026, 11, 10), converter.fromString("10/11/2026"));
        assertEquals(LocalDate.of(2026, 3, 5), converter.fromString("5/3/2026"));
        assertEquals("10/11/2026", converter.toString(LocalDate.of(2026, 11, 10)));
        assertEquals("", converter.toString(null));
    }

    private static void assertShown(Label label, String message) {
        assertTrue(label.isVisible() && label.isManaged(), "message should be visible: " + message);
        assertEquals(FieldFeedback.ERROR_MARK + message, label.getText());
    }
}
