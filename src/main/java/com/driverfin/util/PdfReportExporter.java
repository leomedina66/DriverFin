package com.driverfin.util;

import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import com.driverfin.service.SummaryService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfReportExporter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Same as {@link #exportReport(File, String, List, BigDecimal)}, but refuses to write over the database file.
     *
     * @throws DatabaseTargetException when {@code file} is the database in use
     */
    public static void exportReport(File file, String period, List<WorkShift> shifts, BigDecimal maintRate,
                                    Path databaseFile) throws Exception {
        SafeFileWrite.requireNotTheDatabase(file.toPath().toAbsolutePath(), databaseFile);
        exportReport(file, period, shifts, maintRate);
    }

    /** Writes beside the target first and moves the finished file over it, so a failure never leaves half a PDF. */
    public static void exportReport(File file, String period, List<WorkShift> shifts, BigDecimal maintRate) throws Exception {
        Path target = file.toPath().toAbsolutePath();
        Path temp = SafeFileWrite.tempSibling(target);
        try {
            writeReport(temp.toFile(), period, shifts, maintRate);
            SafeFileWrite.moveReplacing(temp, target);
        } finally {
            SafeFileWrite.deleteTemp(temp);
        }
    }

    private static void writeReport(File file, String period, List<WorkShift> shifts, BigDecimal maintRate) throws Exception {
        Document document = new Document(PageSize.A4);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, fos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

            Paragraph title = new Paragraph("DriverFin - Relatório Financeiro", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("Período: " + (period != null ? period : "Geral"), subtitleFont));
            document.add(new Paragraph(" "));

            FinancialSummary summary = SummaryService.calculate(shifts, maintRate);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingBefore(10f);
            summaryTable.setSpacingAfter(20f);
            
            addSummaryRow(summaryTable, "Faturamento Bruto:", CurrencyFormat.money(summary.totalGross()), boldFont);
            addSummaryRow(summaryTable, "Custos Diretos:", CurrencyFormat.money(summary.totalCosts()), boldFont);
            addSummaryRow(summaryTable, "Reserva de Manutenção:", CurrencyFormat.money(summary.totalMaint()), boldFont);
            addSummaryRow(summaryTable, "Lucro Líquido:", CurrencyFormat.money(summary.totalNet()), boldFont);
            addSummaryRow(summaryTable, "Lucro / Hora (Média):", CurrencyFormat.money(summary.avgProfitPerHour()), normalFont);
            addSummaryRow(summaryTable, "Lucro / KM (Média):", CurrencyFormat.money(summary.avgProfitPerKm()), normalFont);
            addSummaryRow(summaryTable, "Faturamento / Hora (Média):", CurrencyFormat.money(summary.avgGrossPerHour()), normalFont);
            addSummaryRow(summaryTable, "Faturamento / KM (Média):", CurrencyFormat.money(summary.avgGrossPerKm()), normalFont);
            
            document.add(summaryTable);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 1f, 1f, 1.5f, 1.5f, 1.5f});
            
            String[] headers = {"Data", "Horas", "KM", "Faturamento", "Custos", "Lucro"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, boldFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (WorkShift shift : shifts) {
                table.addCell(new Phrase(shift.getDate().format(DATE_FORMATTER), normalFont));
                table.addCell(new Phrase(CurrencyFormat.oneDecimal(shift.calculateHours()), normalFont));
                table.addCell(new Phrase(CurrencyFormat.oneDecimal(shift.calculateKm()), normalFont));
                table.addCell(new Phrase(CurrencyFormat.money(shift.getGrossIncome()), normalFont));
                table.addCell(new Phrase(CurrencyFormat.money(shift.getTotalCosts()), normalFont));
                table.addCell(new Phrase(CurrencyFormat.money(shift.calculateNetProfit(maintRate)), normalFont));
            }

            document.add(table);
            // Must close the document (writes the PDF trailer) BEFORE the stream is closed by try-with-resources.
            document.close();
        }
    }

    private static void addSummaryRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
