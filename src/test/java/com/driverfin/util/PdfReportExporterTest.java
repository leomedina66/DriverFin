package com.driverfin.util;

import com.driverfin.model.WorkShift;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
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
}
