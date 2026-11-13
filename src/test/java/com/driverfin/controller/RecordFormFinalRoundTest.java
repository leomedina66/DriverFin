package com.driverfin.controller;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.dao.WorkShiftDAO;
import com.driverfin.model.WorkShift;
import com.driverfin.util.FormMessages;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MoneyMask;
import com.driverfin.util.Styles;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corrections of the final review round on the shift form (the real FXML, no window). */
class RecordFormFinalRoundTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    @TempDir
    File tempDir;

    private WorkShiftDAO dao;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    @BeforeEach
    void createDatabase() {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, "final.db").getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        dao = new WorkShiftDAO(db);
    }

    private static WorkShift shift(String uber, String notes) {
        return new WorkShift(null, DAY, LocalTime.of(8, 0), LocalTime.of(12, 0), 100.0, 160.0, new BigDecimal(uber),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, notes);
    }

    private List<WorkShift> rows() {
        try {
            return dao.findAll();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean hasClass(Node node, String styleClass) {
        return node.getStyleClass().contains(styleClass);
    }

    // ---- notes without a value (B) ------------------------------------------------------------------------

    @Test
    void shiftWithoutNotesOpensAndEscWithoutChangesAsksNothing() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, shift("100.00", null));
            assertEquals("", form.notes().getText());

            form.press(form.field("timeStartField"), KeyCode.ESCAPE);

            assertEquals(0, form.questionsAsked, "nothing changed, so nothing to ask");
            assertEquals(1, form.closed);
        });
    }

    // ---- Tab in the notes (A) -----------------------------------------------------------------------------

    @Test
    void shiftTabLeavesTheNotesBackwardsWithoutWritingAnything() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            form.notes().setText("abc");
            form.focus("notesArea");

            Event.fireEvent(form.notes(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB,
                    true, false, false, false));

            assertEquals("abc", form.notes().getText());
            assertSame(form.node("costFoodOtherField"), form.scene.getFocusOwner());
        });
    }

    // ---- stored values outside the allowed range (F) ------------------------------------------------------

    @Test
    void storedValueAboveTheCeilingIsShownAsItIsWithAnErrorAndSavingIsBlocked() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, savedLegacy("1500000.00"));
            TextField uber = form.field("earnUberField");
            assertEquals("1.500.000,00", uber.getText());
            Label message = form.label("earnUberMsg");
            assertTrue(message.isVisible());
            assertTrue(message.getText().contains(FormMessages.STORED_MONEY_OUT_OF_RANGE));
            assertTrue(hasClass(uber, FieldFeedback.INPUT_ERROR_CLASS));

            form.save().fire();

            assertFalse(form.controller.isSaved(), "the stored amount must be corrected before saving");
            assertEquals("1500000.00", rows().get(0).getUberEarnings().toPlainString());
        });
    }

    @Test
    void correctingTheStoredValueClearsTheErrorAndAllowsSaving() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, savedLegacy("1500000.00"));
            form.field("earnUberField").setText(MoneyMask.ZERO);
            FxTestSupport.type(form.field("earnUberField"), "15000");

            assertFalse(form.label("earnUberMsg").getText().contains(FormMessages.STORED_MONEY_OUT_OF_RANGE));

            form.save().fire();

            assertTrue(form.controller.isSaved());
            assertEquals(new BigDecimal("150.00"), rows().get(0).getUberEarnings());
        });
    }

    @Test
    void storedNegativeValueDoesNotStopTheFormFromOpeningButBlocksSaving() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, savedLegacy("-5.00"));
            assertEquals("-5,00", form.field("earnUberField").getText());
            assertTrue(form.label("earnUberMsg").isVisible());
            assertEquals("—", form.label("lblCalcNet").getText(), "the calculation shows dashes, not an exception");

            form.save().fire();

            assertFalse(form.controller.isSaved());
        });
    }

    /** The DAO does not check the range, so a value like an old or hand-edited database can be stored. */
    private WorkShift savedLegacy(String uber) {
        try {
            dao.save(shift(uber, "x"));
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
        return rows().get(0);
    }

    // ---- discard question and hint (L) --------------------------------------------------------------------

    @Test
    void typingTheSameKmAgainInAnotherFormDoesNotAskToDiscard() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, savedLegacy("10.00"));
            assertEquals("100,0", form.field("kmStartField").getText());

            form.field("kmStartField").setText("100");

            assertTrue(form.controller.mayClose());
            assertEquals(0, form.questionsAsked);
        });
    }

    @Test
    void aRealChangeStillAsksToDiscard() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, savedLegacy("10.00"));
            form.field("kmStartField").setText("101");

            assertFalse(form.controller.mayClose());
            assertEquals(1, form.questionsAsked);
        });
    }

    @Test
    void suggestedKmHintStaysAvailableToScreenReadersAfterTheMessagesAreCleared() {
        FxTestSupport.onFxThread(() -> {
            savedLegacy("10.00");
            RecordFormFixture form = new RecordFormFixture(dao, null);
            assertTrue(form.label("kmStartHint").isVisible());
            assertEquals(FormMessages.KM_SUGGESTED_HINT, form.field("kmStartField").getAccessibleHelp());
        });
    }

    // ---- loss style in the calculation block (Q) ----------------------------------------------------------

    @Test
    void calculatedProfitGetsTheLossStyleOnlyWhileItIsNegative() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            form.typeValidShift();
            Label net = form.label("lblCalcNet");
            assertFalse(hasClass(net, Styles.NEGATIVE_VALUE));

            FxTestSupport.type(form.field("costFuelField"), "99999999");
            assertTrue(hasClass(net, Styles.NEGATIVE_VALUE));
            FxTestSupport.type(form.field("costFuelField"), "1");
            assertEquals(1, net.getStyleClass().stream().filter(Styles.NEGATIVE_VALUE::equals).count());

            form.field("costFuelField").setText(MoneyMask.ZERO);
            assertFalse(hasClass(net, Styles.NEGATIVE_VALUE));
        });
    }

    // ---- layout (O) ---------------------------------------------------------------------------------------

    private static boolean isInside(Node node, Class<?> container) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (container.isInstance(parent)) return true;
        }
        return false;
    }

    @Test
    void formFitsAShortScreenWithTheButtonsFixedOutsideTheScrollingContent() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            assertTrue(form.root.prefHeight(-1) <= 700, "window height " + form.root.prefHeight(-1));
            assertTrue(isInside(form.field("kmEndField"), javafx.scene.control.ScrollPane.class));
            assertTrue(isInside(form.node("notesArea"), javafx.scene.control.ScrollPane.class));
            assertFalse(isInside(form.save(), javafx.scene.control.ScrollPane.class), "Salvar must stay fixed");
            assertFalse(isInside(form.cancel(), javafx.scene.control.ScrollPane.class), "Cancelar must stay fixed");
        });
    }

    @Test
    void dateAndTheTwoTimesShareOneRowInTabOrder() {
        FxTestSupport.onFxThread(() -> {
            RecordFormFixture form = new RecordFormFixture(dao, null);
            Node row = form.datePicker().getParent().getParent();
            assertSame(row, form.field("timeStartField").getParent().getParent());
            assertSame(row, form.field("timeEndField").getParent().getParent());
            List<Node> cells = ((javafx.scene.layout.GridPane) row).getChildren();
            assertSame(form.datePicker().getParent(), cells.get(0));
            assertSame(form.field("timeStartField").getParent(), cells.get(1));
            assertSame(form.field("timeEndField").getParent(), cells.get(2));
        });
    }

    // ---- date reading (H) ---------------------------------------------------------------------------------

    @Test
    void dateConverterReadsFourDigitYearsWithoutSignAndRefusesTheRest() {
        StringConverter<LocalDate> converter = RecordFormController.newDateConverter();
        assertEquals(LocalDate.of(2026, 11, 9), converter.fromString("9/11/2026"));
        assertEquals(LocalDate.of(2026, 11, 9), converter.fromString("09/11/2026"));
        for (String text : new String[]{"+10000", "1/1/+10000", "1/1/-0005", "1/1/0000", "1/1/10000", "1/1/26", "31/2/2026"}) {
            assertNull(converter.fromString(text), "\"" + text + "\" is not a date");
        }
    }
}
