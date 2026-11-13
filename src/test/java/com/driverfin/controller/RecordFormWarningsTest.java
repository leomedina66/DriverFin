package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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

/** UX 4 and 5, criteria 7, 14 and 15: soft warnings in the form. They never block saving. */
class RecordFormWarningsTest {
    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "warn.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private static void assertWarning(Label label, String text) {
        assertTrue(label.isVisible() && label.isManaged(), "should be visible: " + text);
        assertEquals(FieldFeedback.WARNING_MARK + text, label.getText());
        assertTrue(label.getStyleClass().contains("field-warning"));
    }

    private static void assertHidden(Label label) {
        assertFalse(label.isVisible(), label.getId() + " should be hidden");
        assertFalse(label.isManaged(), label.getId() + " should take no space");
    }

    private static String money(String reais) {
        return CurrencyFormat.money(new BigDecimal(reais));
    }

    private static void replaceAll(TextField field, String text) {
        field.replaceText(0, field.getLength(), text);
    }

    @Test
    void turnCrossingMidnightShowsTheHours() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("timeStartField"), "2200");
            assertHidden(form.label("turnNote"));

            FxTestSupport.type(form.field("timeEndField"), "0600");

            assertWarning(form.label("turnNote"), FormMessages.crossesMidnight("8,0"));
        });
    }

    @Test
    void equalTimesShowTheEqualTimesNoteAndANormalShiftHasNone() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("timeStartField"), "0800");
            FxTestSupport.type(form.field("timeEndField"), "0800");
            assertWarning(form.label("turnNote"), FormMessages.EQUAL_TIMES);

            replaceAll(form.field("timeEndField"), "17:00");

            assertHidden(form.label("turnNote"));
        });
    }

    @Test
    void halfWrittenTimeGivesNoNote() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("timeStartField"), "2200");
            FxTestSupport.type(form.field("timeEndField"), "06");

            assertHidden(form.label("turnNote"));
        });
    }

    @Test
    void highValueWarningFollowsTheValueOfEachMoneyField() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("costFuelField"), "499999");
            assertEquals("4.999,99", form.field("costFuelField").getText());
            assertHidden(form.label("costFuelMsg"));

            replaceAll(form.field("costFuelField"), "5.000,00");
            assertWarning(form.label("costFuelMsg"), FormMessages.highValue(money("5000.00")));
            assertHidden(form.label("earnUberMsg"));

            FxTestSupport.backspace(form.field("costFuelField"));
            assertEquals("500,00", form.field("costFuelField").getText());
            assertHidden(form.label("costFuelMsg"));
        });
    }

    @Test
    void highValueWarningStaysWhenTheCommaHintComesAndTheMaximumMessageWins() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("earnUberField"), "500000");
            FxTestSupport.typeAtEnd(form.field("earnUberField"), ",");
            assertWarning(form.label("earnUberMsg"), FormMessages.highValue(money("5000.00")));

            // Digits are pushed in until the next one would pass R$ 1.000.000,00 and is refused.
            FxTestSupport.type(form.field("earnUberField"), "99999999");
            assertEquals("500.000,99", form.field("earnUberField").getText());
            assertEquals(FieldFeedback.WARNING_MARK + FormMessages.MONEY_MAX, form.label("earnUberMsg").getText());

            FxTestSupport.typeAtEnd(form.field("earnUberField"), "x");
            assertWarning(form.label("earnUberMsg"), FormMessages.highValue(money("500000.99")));
        });
    }

    @Test
    void commaHintAloneIsStillShownAsInfo() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.typeAtEnd(form.field("earn99Field"), ",");

            assertEquals(FormMessages.MONEY_COMMA_HINT, form.label("earn99Msg").getText());
            assertTrue(form.label("earn99Msg").getStyleClass().contains("field-info"));
        });
    }

    @Test
    void unusualRateIsWarnedFromTheLiveCalculation() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            form.typeValidShift();
            // 1.234,50 in 4 h = 308,625 per hour: above 300.
            assertWarning(form.label("rateWarn"), FormMessages.unusualRate(money("308.63")));

            replaceAll(form.field("earnUberField"), "100,00");
            assertHidden(form.label("rateWarn"));

            replaceAll(form.field("earnUberField"), "5,00");
            assertWarning(form.label("rateWarn"), FormMessages.unusualRate(money("1.25")));
        });
    }

    @Test
    void noRateWarningWhileTheCalculationIsUnavailable() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("earnUberField"), "123450");

            assertEquals("—", form.label("lblCalcGross").getText());
            assertHidden(form.label("rateWarn"));
        });
    }

    @Test
    void savingWorksWithEveryWarningVisible() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            FxTestSupport.type(form.field("timeStartField"), "2200");
            FxTestSupport.type(form.field("timeEndField"), "0600");
            FxTestSupport.type(form.field("kmStartField"), "100");
            FxTestSupport.type(form.field("kmEndField"), "300");
            FxTestSupport.type(form.field("earnUberField"), "500000");
            assertWarning(form.label("turnNote"), FormMessages.crossesMidnight("8,0"));
            assertWarning(form.label("earnUberMsg"), FormMessages.highValue(money("5000.00")));
            assertWarning(form.label("rateWarn"), FormMessages.unusualRate(money("625.00")));

            form.save().fire();

            assertTrue(form.controller.isSaved());
        });
    }

    @Test
    void editModeShowsTheWarningsOfTheSavedValuesRightAway() {
        FxTestSupport.onFxThread(() -> {
            WorkShift shift = new WorkShift(3L, LocalDate.of(2026, 3, 5), LocalTime.of(22, 0), LocalTime.of(6, 0),
                100.0, 200.0, new BigDecimal("6000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "");
            RecordFormFixture form = new RecordFormFixture(dao, shift);

            assertWarning(form.label("turnNote"), FormMessages.crossesMidnight("8,0"));
            assertWarning(form.label("earnUberMsg"), FormMessages.highValue(money("6000.00")));
            assertWarning(form.label("rateWarn"), FormMessages.unusualRate(money("750.00")));
        });
    }
}
