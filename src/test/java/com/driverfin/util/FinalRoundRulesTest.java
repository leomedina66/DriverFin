package com.driverfin.util;

import com.driverfin.dao.DatabaseConnection;
import com.driverfin.dao.DatabaseInitializer;
import com.driverfin.model.WorkShift;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules added in the final review round: ranges, rounding, safe files and messages without typed values. */
class FinalRoundRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @TempDir
    File tempDir;

    @BeforeAll
    static void needToolkit() {
        FxTestSupport.assumeToolkit();
    }

    // ---- rounding (E) -------------------------------------------------------------------------------------

    @Test
    void hoursAreRoundedHalfUpAndTheLossRuleFollowsTheRoundedMoney() {
        assertEquals("2,3", CurrencyFormat.oneDecimal(2.25));
        assertEquals("2,2", CurrencyFormat.oneDecimal(2.24));
        assertEquals("8,4", CurrencyFormat.oneDecimal(8.35));
        assertEquals("0,0", CurrencyFormat.oneDecimal(-0.04));
        assertTrue(CurrencyFormat.isLoss(new BigDecimal("-0.005")));
        assertFalse(CurrencyFormat.isLoss(new BigDecimal("-0.004")));
        assertFalse(CurrencyFormat.isLoss(BigDecimal.ZERO));
    }

    // ---- ranges (H, I) ------------------------------------------------------------------------------------

    private static WorkShift validate(LocalDate date, String kmStart, String kmEnd) {
        WorkShift shift = new WorkShift();
        WorkShiftValidator.validateAndPopulate(shift, TODAY, date, "08:00", "12:00", kmStart, kmEnd, "", "", "", "", "", "");
        return shift;
    }

    @Test
    void validatorAcceptsDatesFromTheYear2000UntilOneYearAhead() {
        assertEquals(ShiftDateRange.EARLIEST, validate(ShiftDateRange.EARLIEST, "1", "2").getDate());
        LocalDate lastDay = TODAY.plusYears(1);
        assertEquals(lastDay, validate(lastDay, "1", "2").getDate());
    }

    @Test
    void validatorRefusesDatesOutsideTheRangeOnTheDateField() {
        for (LocalDate date : List.of(LocalDate.of(1999, 12, 31), LocalDate.of(1, 1, 1), TODAY.plusYears(1).plusDays(1),
                LocalDate.of(9999, 12, 31))) {
            FieldValidationException error = assertThrows(FieldValidationException.class, () -> validate(date, "1", "2"));
            assertEquals(ShiftField.DATE, error.getField());
            assertEquals(FormMessages.DATE_OUT_OF_RANGE, error.getMessage());
        }
    }

    @Test
    void validatorRefusesKmWithMoreThanNineIntegerDigits() {
        assertEquals(999_999_999.0, validate(TODAY, "999999999", "999999999").getKmEnd());
        FieldValidationException error = assertThrows(FieldValidationException.class,
                () -> validate(TODAY, "1", "1000000000"));
        assertEquals(ShiftField.KM_END, error.getField());
        assertThrows(FieldValidationException.class, () -> validate(TODAY, "1000000000", "1000000001"));
    }

    @Test
    void formCheckReportsTheDateOutOfRangeWithTheNewText() {
        ShiftFormInput tooOld = new ShiftFormInput(LocalDate.of(1999, 1, 1), "08:00", "12:00", "1", "2", "");
        ShiftFormInput tooFar = new ShiftFormInput(TODAY.plusYears(1).plusDays(1), "08:00", "12:00", "1", "2", "");
        ShiftFormInput fine = new ShiftFormInput(TODAY, "08:00", "12:00", "1", "2", "");
        assertEquals(Optional.of(new FieldError(ShiftField.DATE, FormMessages.DATE_OUT_OF_RANGE)),
                ShiftFormCheck.checkField(ShiftField.DATE, tooOld, TODAY));
        assertEquals(Optional.of(new FieldError(ShiftField.DATE, FormMessages.DATE_OUT_OF_RANGE)),
                ShiftFormCheck.checkField(ShiftField.DATE, tooFar, TODAY));
        assertTrue(ShiftFormCheck.checkField(ShiftField.DATE, fine, TODAY).isEmpty());
        assertTrue(ShiftFormCheck.checkAll(fine, TODAY).isEmpty());
        assertEquals(1, ShiftFormCheck.checkAll(tooOld, TODAY).size());
    }

    // ---- masks (G, K) -------------------------------------------------------------------------------------

    @Test
    void longPastedTextIsRefusedEarlyWithoutBuildingANumber() {
        MaskResult digits = MoneyMask.paste("0,00", "9".repeat(21));
        assertEquals("0,00", digits.text());
        assertEquals(FormMessages.MONEY_MAX, digits.message().text());
        MaskResult words = MoneyMask.paste("0,00", "isso não é um valor em dinheiro nenhum");
        assertEquals("0,00", words.text());
        assertEquals(FormMessages.MONEY_UNREADABLE, words.message().text());
        assertEquals("1.000.000,00", MoneyMask.paste("0,00", "R$ 1.000.000,00").text());
    }

    @Test
    void moneyExceptionMessagesNeverCarryTheTypedText() {
        String typed = "12abc34";
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> MoneyMask.parseDisplay(typed));
        assertFalse(error.getMessage().contains(typed));
        IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class,
                () -> MoneyMask.parseDisplay("999.999.999.999.999.999.999,00"));
        assertFalse(tooBig.getMessage().contains("999"));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> MoneyMask.display(-1234));
        assertFalse(negative.getMessage().contains("1234"));
    }

    @Test
    void storedMoneyIsShownAsItIsEvenAboveTheCeilingOrNegative() {
        FxTestSupport.onFxThread(this::checkStoredMoney);
    }

    private void checkStoredMoney() {
        TextField field = new TextField();
        MoneyInput.install(field, message -> { });
        MoneyInput.setStored(field, 150_000_000L);
        assertEquals("1.500.000,00", field.getText());
        assertEquals(150_000_000L, MoneyInput.readCents(field).getAsLong());
        assertFalse(MoneyInput.isAllowed(150_000_000L));

        MoneyInput.setStored(field, -500L);
        assertEquals("-5,00", field.getText());
        assertTrue(MoneyInput.readCents(field).isEmpty());
        assertFalse(MoneyInput.isAllowed(-500L));
        assertNotNull(field.getAccessibleText());

        MoneyInput.setStored(field, 12_345L);
        assertEquals("123,45", field.getText());
        assertTrue(MoneyInput.isAllowed(12_345L));
        assertTrue(MoneyInput.isAllowed(MoneyMask.MAX_CENTS));
    }

    // ---- files (J, K) -------------------------------------------------------------------------------------

    private DatabaseConnection newDatabase(String name) {
        DatabaseConnection db = new DatabaseConnection("jdbc:sqlite:" + new File(tempDir, name).getAbsolutePath());
        new DatabaseInitializer(db).initDatabase();
        return db;
    }

    private static WorkShift sampleShift() {
        return new WorkShift(null, TODAY, LocalTime.of(8, 0), LocalTime.of(10, 15), 100, 140,
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    @Test
    void backupToTheLiveDatabaseThrowsTheOwnExceptionWithAFixedMessage() {
        DatabaseConnection db = newDatabase("live.db");
        DatabaseTargetException error = assertThrows(DatabaseTargetException.class,
                () -> DatabaseBackup.create(db, db.getDatabasePath()));
        assertEquals(DatabaseTargetException.USER_MESSAGE, error.getMessage());
        assertTrue(error instanceof IllegalArgumentException);
    }

    @Test
    void pdfIsNeverWrittenOverTheDatabaseFile() throws Exception {
        DatabaseConnection db = newDatabase("report-live.db");
        Path dbFile = db.getDatabasePath();
        long sizeBefore = Files.size(dbFile);

        assertThrows(DatabaseTargetException.class, () -> PdfReportExporter.exportReport(dbFile.toFile(), "Janeiro",
                List.of(sampleShift()), new BigDecimal("0.25"), dbFile));

        assertEquals(sizeBefore, Files.size(dbFile), "the database must be untouched");
    }

    @Test
    void pdfIsWrittenBesideTheTargetAndMovedAtTheEndLeavingNoTemporaryFile() throws Exception {
        DatabaseConnection db = newDatabase("report-other.db");
        File pdf = new File(tempDir, "report.pdf");
        Files.writeString(pdf.toPath(), "old report");

        PdfReportExporter.exportReport(pdf, "Janeiro", List.of(sampleShift()), new BigDecimal("0.25"), db.getDatabasePath());

        assertTrue(Files.readString(pdf.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        String[] leftovers = tempDir.list((dir, name) -> name.endsWith(".tmp"));
        assertEquals(0, leftovers == null ? 0 : leftovers.length);
    }

    @Test
    void pdfFailureKeepsTheOldReportAndLeavesNoTemporaryFile() throws Exception {
        File pdf = new File(tempDir, "keep.pdf");
        Files.writeString(pdf.toPath(), "old report");
        // A shift without a date makes the report fail in the middle of writing.
        WorkShift broken = new WorkShift();

        assertThrows(Exception.class, () -> PdfReportExporter.exportReport(pdf, "Janeiro", List.of(broken),
                new BigDecimal("0.25")));

        assertEquals("old report", Files.readString(pdf.toPath()));
        String[] leftovers = tempDir.list((dir, name) -> name.endsWith(".tmp"));
        assertEquals(0, leftovers == null ? 0 : leftovers.length);
    }

    // ---- small shared pieces ------------------------------------------------------------------------------

    @Test
    void lossStyleClassIsAddedOnceAndRemovedWhenNotNegative() {
        FxTestSupport.onFxThread(this::checkLossStyle);
    }

    private void checkLossStyle() {
        Label label = new Label();
        Styles.markNegative(label, true);
        Styles.markNegative(label, true);
        assertEquals(1, label.getStyleClass().stream().filter(Styles.NEGATIVE_VALUE::equals).count());
        Styles.markNegative(label, false);
        assertFalse(label.getStyleClass().contains(Styles.NEGATIVE_VALUE));
    }

    @Test
    void todayRevenueLineReadsLikeTheOtherTexts() {
        assertEquals("Faturamento: " + CurrencyFormat.money(new BigDecimal("182.40")) + " · 1 turno",
                DashboardTexts.todaySummary(new BigDecimal("182.40"), 1));
    }
}
