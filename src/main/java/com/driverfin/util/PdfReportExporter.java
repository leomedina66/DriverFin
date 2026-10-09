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
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class PdfReportExporter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    public static void exportReport(File file, String period, List<WorkShift> shifts, BigDecimal maintRate) throws Exception {
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
            
            addSummaryRow(summaryTable, "Faturamento Bruto:", CURRENCY_FORMAT.format(summary.totalGross()), boldFont);
            addSummaryRow(summaryTable, "Custos Diretos:", CURRENCY_FORMAT.format(summary.totalCosts()), boldFont);
            addSummaryRow(summaryTable, "Reserva de Manutenção:", CURRENCY_FORMAT.format(summary.totalMaint()), boldFont);
            addSummaryRow(summaryTable, "Lucro Líquido Real:", CURRENCY_FORMAT.format(summary.totalNet()), boldFont);
            addSummaryRow(summaryTable, "R$ / Hora (Média):", CURRENCY_FORMAT.format(summary.avgProfitPerHour()), normalFont);
            addSummaryRow(summaryTable, "R$ / KM (Média):", CURRENCY_FORMAT.format(summary.avgProfitPerKm()), normalFont);
            
            document.add(summaryTable);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 1f, 1f, 1.5f, 1.5f, 1.5f});
            
            String[] headers = {"Data", "Horas", "KM", "Bruto", "Custos", "Líquido"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, boldFont));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            for (WorkShift shift : shifts) {
                table.addCell(new Phrase(shift.getDate().format(DATE_FORMATTER), normalFont));
                table.addCell(new Phrase(String.format(new Locale("pt","BR"), "%.1f", shift.calculateHours()), normalFont));
                table.addCell(new Phrase(String.format(new Locale("pt","BR"), "%.1f", shift.calculateKm()), normalFont));
                table.addCell(new Phrase(CURRENCY_FORMAT.format(shift.getGrossIncome()), normalFont));
                table.addCell(new Phrase(CURRENCY_FORMAT.format(shift.getTotalCosts()), normalFont));
                table.addCell(new Phrase(CURRENCY_FORMAT.format(shift.calculateNetProfit(maintRate)), normalFont));
            }

            document.add(table);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
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
