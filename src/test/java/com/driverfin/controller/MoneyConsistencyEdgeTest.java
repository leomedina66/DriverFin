package com.driverfin.controller;

import com.driverfin.model.WorkShift;
import com.driverfin.util.CurrencyFormat;
import com.driverfin.util.DashboardTexts;
import com.driverfin.util.FxTestSupport;
import com.driverfin.util.MaintenanceRate;
import com.driverfin.util.MoneyMask;
import com.driverfin.util.PdfReportExporter;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One value, typed key by key into the real form, must read the same in the field, in the SQLite row, on the
 * dashboard and in the PDF report.
 */
class MoneyConsistencyEdgeTest {
    private static final long[] CENTS = {0, 1, 5, 10, 99, 100, 999, 1999, 100001, 123456, 99999999, 100000000};

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

    private static String normal(String text) {
        return text.replace(' ', ' ').replace(' ', ' ');
    }

    private RecordFormFixture newForm(WorkShift shift) {
        RecordFormFixture form = new RecordFormFixture(fixture.dao, shift);
        form.root.applyCss();
        form.root.layout();
        return form;
    }

    private List<WorkShift> rows() {
        try {
            return fixture.dao.findAll();
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private String pdfText(List<WorkShift> shifts) throws Exception {
        File pdf = new File(tempDir, "report-" + System.nanoTime() + ".pdf");
        PdfReportExporter.exportReport(pdf, "Todos", shifts, MaintenanceRate.DEFAULT);
        PdfReader reader = new PdfReader(pdf.getAbsolutePath());
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            return " " + normal(text).replaceAll("\\s+", " ") + " ";
        } finally {
            reader.close();
        }
    }

    /** Types the shift keys into the form, saves with Ctrl+Enter and returns the stored row. */
    private WorkShift enterShift(RecordFormFixture form, String start, String end, String kmStart, String kmEnd,
                                 String uberKeys, String app99Keys, String fuelKeys, String foodKeys) {
        RecordFormJourneyTest.Keys.focus(form, form.node("timeStartField"));
        RecordFormJourneyTest.Keys.typeText(form.field("timeStartField"), start);
        RecordFormJourneyTest.Keys.focus(form, form.node("timeEndField"));
        RecordFormJourneyTest.Keys.typeText(form.field("timeEndField"), end);
        RecordFormJourneyTest.Keys.focus(form, form.node("kmStartField"));
        RecordFormJourneyTest.Keys.typeText(form.field("kmStartField"), kmStart);
        RecordFormJourneyTest.Keys.focus(form, form.node("kmEndField"));
        RecordFormJourneyTest.Keys.typeText(form.field("kmEndField"), kmEnd);
        String[][] money = {{"earnUberField", uberKeys}, {"earn99Field", app99Keys}, {"costFuelField", fuelKeys},
            {"costFoodOtherField", foodKeys}};
        for (String[] entry : money) {
            RecordFormJourneyTest.Keys.focus(form, form.node(entry[0]));
            RecordFormJourneyTest.Keys.typeText(form.field(entry[0]), entry[1]);
        }
        RecordFormJourneyTest.Keys.key(form.field("costFoodOtherField"), javafx.scene.input.KeyCode.ENTER, true);
        assertTrue(form.controller.isSaved(), "the shift must have been saved");
        return rows().get(rows().size() - 1);
    }

    @Test
    void shouldReadTheSameAmountInTheFieldTheRowTheDashboardAndThePdfForEveryAwkwardValue() throws Exception {
        for (long cents : CENTS) {
            String keys = Long.toString(cents);
            String[] boxed = new String[1];
            Exception[] failure = new Exception[1];
            FxTestSupport.onFxThread(() -> {
                fixture = new DashboardFixture(tempDir, "money-" + cents);
                RecordFormFixture form = newForm(null);
                WorkShift row = enterShift(form, "0800", "0900", "100", "100", keys, "0", "0", "0");
                String shown = MoneyMask.display(cents);
                assertEquals(shown, form.field("earnUberField").getText(), "field for " + keys);
                BigDecimal reais = BigDecimal.valueOf(cents, 2);
                assertEquals(reais, row.getUberEarnings(), "SQLite row for " + keys);

                RecordFormFixture edit = newForm(row);
                assertEquals(shown, edit.field("earnUberField").getText(), "reopened for editing: " + keys);

                fixture.open();
                String expected = "R$ " + shown;
                assertEquals(expected, normal(CurrencyFormat.money(reais)), "CurrencyFormat for " + keys);
                assertEquals(expected, normal(((Label) fixture.node("lblGrossIncome")).getText()), "KPI gross for " + keys);
                assertEquals(expected, normal(((Label) fixture.node("lblNetProfit")).getText()), "KPI net for " + keys);
                assertEquals(normal(DashboardTexts.profit(reais)), normal(((Label) fixture.node("lblTodayProfit")).getText()));
                assertEquals(expected + " / h", normal(((Label) fixture.node("lblTodayGrossPerHour")).getText()));
                boxed[0] = expected;
            });
            try {
                String pdf = pdfText(rows());
                assertTrue(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(boxed[0]) + "(?![\\d.,])").matcher(pdf).find(),
                        "PDF must show " + boxed[0] + " for " + keys + ": " + pdf);
            } catch (Exception e) {
                failure[0] = e;
            }
            if (failure[0] != null) throw failure[0];
            FxTestSupport.onFxThread(() -> {
                fixture.cleanUp();
                fixture = null;
            });
        }
    }

    @Test
    void shouldShowTheLiveCalculationOfTheFormAndTheDashboardTotalsWithTheSameTextForTheSameShift() {
        FxTestSupport.onFxThread(() -> {
            fixture = new DashboardFixture(tempDir, "same-shift");
            RecordFormFixture form = newForm(null);
            enterShift(form, "0800", "1730", "12450", "12610,5", "123456", "5", "1999", "1");
            // the form still shows its calculation block for what was just typed
            assertEquals("R$ 1.234,61", normal(form.label("lblCalcGross").getText()));
            assertEquals("R$ 20,00", normal(form.label("lblCalcCosts").getText()));

            fixture.open();
            assertEquals(form.label("lblCalcGross").getText(), ((Label) fixture.node("lblGrossIncome")).getText());
            assertEquals(form.label("lblCalcCosts").getText(), ((Label) fixture.node("lblTotalCosts")).getText());
            assertEquals(form.label("lblCalcMaint").getText(), ((Label) fixture.node("lblMaintReserve")).getText());
            assertEquals(form.label("lblCalcNet").getText(), ((Label) fixture.node("lblNetProfit")).getText());
            assertEquals(form.label("lblCalcNet").getText(), ((Label) fixture.node("lblTodayProfit")).getText());
            assertEquals(form.label("lblCalcHours").getText(), ((Label) fixture.node("lblTodayHours")).getText());
        });
    }

    /**
     * Q-HOURS: hours with a quarter (08:00 to 10:15 = 2,25 h) are "2,2 h" on the screen (NumberFormat rounds half to
     * even) and "2,3" in the PDF (String.format rounds half up). The same shift reads differently.
     */
    @Test
    void shouldPrintTheSameHoursInThePdfAsTheScreenShowsForAQuarterOfAnHour() throws Exception {
        WorkShift quarter = new WorkShift(1L, LocalDate.of(2026, 11, 9), LocalTime.of(8, 0), LocalTime.of(10, 15), 100, 140,
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        String onScreen = CurrencyFormat.oneDecimal(quarter.calculateHours());
        String pdf = pdfText(List.of(quarter));
        assertTrue(pdf.contains(" " + onScreen + " "),
                "screen shows " + onScreen + " h for 2,25 h but the PDF row says: " + pdf);
    }

    @Test
    void shouldPrintTheSameKmInThePdfAsTheScreenShowsForAWholeNumberOfKm() throws Exception {
        WorkShift shift = new WorkShift(1L, LocalDate.of(2026, 11, 9), LocalTime.of(8, 0), LocalTime.of(10, 0), 12450.0,
                12610.5, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
        String pdf = pdfText(List.of(shift));
        assertTrue(pdf.contains(" " + CurrencyFormat.oneDecimal(shift.calculateKm()) + " "), pdf);
        assertTrue(pdf.contains(" " + CurrencyFormat.oneDecimal(shift.calculateHours()) + " "), pdf);
    }

    /**
     * Q-LEGACY-CEILING: the field cannot show more than R$ 1.000.000,00. A stored value above that (an old or
     * hand-edited database) opens as 0,00, and saving the form writes the zero over the real amount.
     */
    @Test
    void shouldNotOpenAStoredValueAboveTheCeilingAsZeroForEditing() {
        FxTestSupport.onFxThread(() -> {
            fixture = new DashboardFixture(tempDir, "above-ceiling");
            WorkShift legacy = new WorkShift(null, LocalDate.of(2026, 11, 9), LocalTime.of(8, 0), LocalTime.of(9, 0), 1, 2,
                    new BigDecimal("1500000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
            try {
                fixture.dao.save(legacy);
            } catch (SQLException e) {
                throw new AssertionError(e);
            }
            RecordFormFixture edit = newForm(rows().get(0));
            assertNotEquals("0,00", edit.field("earnUberField").getText(),
                    "the stored R$ 1.500.000,00 must not be shown (and later saved) as zero");
        });
    }
}
