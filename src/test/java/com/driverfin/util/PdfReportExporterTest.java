package com.driverfin.util;

import com.driverfin.model.WorkShift;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PdfReportExporterTest {

    @TempDir
    File tempDir;

    @Test
    public void exportsCompletePdfFile() throws Exception {
        WorkShift shift = new WorkShift(null, LocalDate.of(2024, 1, 1), LocalTime.of(8, 0), LocalTime.of(16, 0),
            1000.0, 1100.0, new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("50.00"), BigDecimal.ZERO, "");
        File pdf = new File(tempDir, "report.pdf");

        PdfReportExporter.exportReport(pdf, "Janeiro", List.of(shift), new BigDecimal("0.25"));

        assertTrue(pdf.exists());
        assertTrue(pdf.length() > 0);
        String content = new String(Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
        assertTrue(content.startsWith("%PDF"), "must start with %PDF");
        assertTrue(content.stripTrailing().endsWith("%%EOF"), "must end with %%EOF (file not truncated)");
    }

    @Test
    public void emptyPeriodStillExportsSummaryAndTotals() throws Exception {
        File pdf = new File(tempDir, "empty.pdf");

        PdfReportExporter.exportReport(pdf, null, List.of(), new BigDecimal("0.25"));

        String text = pageText(pdf, 1);
        assertTrue(text.contains("Relatório Financeiro"), text);
        assertTrue(text.contains("Geral"), "null period shows as Geral: " + text);
        assertTrue(text.contains("Total"), text);
        assertTrue(text.contains("Página 1"), text);
    }

    @Test
    public void longReportRepeatsHeaderAndNumbersEveryPage() throws Exception {
        List<WorkShift> shifts = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            shifts.add(new WorkShift(null, LocalDate.of(2024, 1, 1).plusDays(i), LocalTime.of(8, 0), LocalTime.of(16, 0),
                1000.0, 1100.0, new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("50.00"), BigDecimal.ZERO, ""));
        }
        File pdf = new File(tempDir, "long.pdf");

        PdfReportExporter.exportReport(pdf, "Ano", shifts, new BigDecimal("0.25"));

        try (PdfReader reader = new PdfReader(pdf.getAbsolutePath())) {
            int pages = reader.getNumberOfPages();
            assertTrue(pages >= 2, "80 shifts must need more than one page");
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= pages; page++) {
                String text = extractor.getTextFromPage(page);
                assertTrue(text.contains("FATURAMENTO"), "table header on page " + page);
                assertTrue(text.contains("Página " + page), "page number on page " + page);
            }
            // 80 x (200 - 50 - 100 km x 0,25) = 80 x 125 = 10.000
            assertTrue(extractor.getTextFromPage(pages).contains("10.000,00"), "total profit on the last page");
        }
    }

    @Test
    public void lossShiftIsExported() throws Exception {
        WorkShift loss = new WorkShift(null, LocalDate.of(2024, 1, 1), LocalTime.of(8, 0), LocalTime.of(10, 0),
            1000.0, 1010.0, new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("80.00"), BigDecimal.ZERO, "");
        File pdf = new File(tempDir, "loss.pdf");

        PdfReportExporter.exportReport(pdf, "Janeiro", List.of(loss), new BigDecimal("0.25"));

        assertTrue(pageText(pdf, 1).contains("62,50"), "loss of R$ 62,50 must be printed");
    }

    private static String pageText(File pdf, int page) throws Exception {
        try (PdfReader reader = new PdfReader(pdf.getAbsolutePath())) {
            return new PdfTextExtractor(reader).getTextFromPage(page);
        }
    }
}
