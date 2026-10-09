package com.driverfin.util;

import com.driverfin.model.DailyRecord;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PDF Exporter utility class for generating formatted A4 financial reports using OpenPDF.
 */
public class PdfReportExporter {

    private static final Logger LOGGER = Logger.getLogger(PdfReportExporter.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final NumberFormat CURRENCY_FORMATTER = NumberFormat.getCurrencyInstance(PT_BR);

    // Color Palette
    private static final Color COLOR_PRIMARY = new Color(15, 23, 42);     // Slate 900
    private static final Color COLOR_ACCENT = new Color(37, 99, 235);     // Blue 600
    private static final Color COLOR_BG_HEADER = new Color(241, 245, 249); // Slate 100
    private static final Color COLOR_BORDER = new Color(226, 232, 240);    // Slate 200
    private static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);// Slate 500
    private static final Color COLOR_TEXT_DARK = new Color(30, 41, 59);   // Slate 800
    private static final Color COLOR_ZEBRA = new Color(248, 250, 252);     // Slate 50

    private static final Color COLOR_GROSS = new Color(22, 163, 74);     // Green 600
    private static final Color COLOR_COSTS = new Color(220, 38, 38);     // Red 600
    private static final Color COLOR_MAINT = new Color(217, 119, 6);     // Amber 600

    private PdfReportExporter() {
        // Private constructor for utility class
    }

    /**
     * Exports a financial statement PDF report.
     *
     * @param destinationFile File path where PDF will be saved
     * @param periodLabel     Selected period string description (e.g. "Este Mês")
     * @param records         List of DailyRecord objects to include
     * @param maintRatePerKm  Maintenance cost reserve rate (e.g. 0.20 R$/km)
     * @throws Exception if an error occurs during PDF generation
     */
    public static void exportReport(File destinationFile, String periodLabel, List<DailyRecord> records, double maintRatePerKm) throws Exception {
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);

        // Fix #10: try-with-resources garante fechamento do stream mesmo em caso de exceção
        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
            PdfWriter writer = PdfWriter.getInstance(document, fos);

            HeaderFooterPageEvent pageEvent = new HeaderFooterPageEvent();
            writer.setPageEvent(pageEvent);

            document.open();

            // 1. Title & Header
            addHeader(document, periodLabel);

            // 2. Calculations
            double totalGross = 0.0;
            double totalCosts = 0.0;
            double totalKm = 0.0;
            double totalHours = 0.0;

            if (records != null) {
                for (DailyRecord r : records) {
                    if (r != null) {
                        totalGross += r.calculateGrossIncome();
                        totalCosts += r.calculateTotalCosts();
                        totalKm += r.calculateKm();
                        totalHours += r.calculateHours();
                    }
                }
            }

            double totalMaint = totalKm * maintRatePerKm;
            double totalNet = totalGross - totalCosts - totalMaint;
            double avgProfitPerHour = totalHours > 0 ? totalNet / totalHours : 0.0;
            double avgProfitPerKm = totalKm > 0 ? totalNet / totalKm : 0.0;

            // 3. 5 KPI Summary Cards Grid Table
            addKpiSummaryGrid(document, totalGross, totalCosts, totalMaint, totalNet, avgProfitPerHour, avgProfitPerKm);

            // 4. Daily Transactions Table
            addRecordsTable(document, records, maintRatePerKm, totalGross, totalCosts, totalMaint, totalNet, totalKm, totalHours);

            document.close();
        }
        LOGGER.info("PDF report exported successfully to " + destinationFile.getAbsolutePath());
    }

    private static void addHeader(Document document, String periodLabel) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, COLOR_PRIMARY);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 10, COLOR_TEXT_MUTED);
        Font badgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_ACCENT);

        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{70, 30});
        headerTable.setSpacingAfter(15);

        // Left Cell: Title and Subtitle
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("DriverFin", titleFont));
        leftCell.addElement(new Paragraph("Relatório Financeiro & Demonstrativo de Desempenho", subFont));
        headerTable.addCell(leftCell);

        // Right Cell: Period Badge & Timestamp
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        Paragraph badge = new Paragraph("Período: " + (periodLabel != null ? periodLabel : "Todos"), badgeFont);
        badge.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(badge);

        Paragraph timestamp = new Paragraph("Gerado em: " + LocalDateTime.now().format(DATETIME_FORMATTER), subFont);
        timestamp.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(timestamp);

        headerTable.addCell(rightCell);
        document.add(headerTable);

        // Thin separator line
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setFixedHeight(2);
        lineCell.setBackgroundColor(COLOR_ACCENT);
        lineCell.setBorder(Rectangle.NO_BORDER);
        line.addCell(lineCell);
        line.setSpacingAfter(15);
        document.add(line);
    }

    private static void addKpiSummaryGrid(Document document, double gross, double costs, double maint, double net, double profitPerHour, double profitPerKm) throws DocumentException {
        PdfPTable kpiTable = new PdfPTable(5);
        kpiTable.setWidthPercentage(100);
        kpiTable.setWidths(new float[]{20, 20, 20, 20, 20});
        kpiTable.setSpacingAfter(20);

        addKpiCard(kpiTable, "RECEITA BRUTA", formatCurrency(gross), "Ganhos Totais", COLOR_GROSS);
        addKpiCard(kpiTable, "CUSTOS DIRETO", formatCurrency(costs), "Combustível + Outros", COLOR_COSTS);
        addKpiCard(kpiTable, "RESERVA MANUT.", formatCurrency(maint), "Estimativa de Desgaste", COLOR_MAINT);
        addKpiCard(kpiTable, "LUCRO LÍQUIDO", formatCurrency(net), "Resultado Final", net >= 0 ? COLOR_GROSS : COLOR_COSTS);
        
        String effText = String.format(PT_BR, "R$ %,.2f/h\nR$ %,.2f/km", profitPerHour, profitPerKm);
        addKpiCard(kpiTable, "EFICIÊNCIA", effText, "Médias do Período", COLOR_ACCENT);

        document.add(kpiTable);
    }

    private static void addKpiCard(PdfPTable table, String title, String value, String subtext, Color valueColor) {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, COLOR_TEXT_MUTED);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, valueColor);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 7, COLOR_TEXT_MUTED);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_BG_HEADER);
        cell.setBorderColor(COLOR_BORDER);
        cell.setBorderWidth(1f);
        cell.setPadding(8);

        Paragraph pTitle = new Paragraph(title, titleFont);
        pTitle.setSpacingAfter(3);
        cell.addElement(pTitle);

        Paragraph pVal = new Paragraph(value, valueFont);
        pVal.setSpacingAfter(3);
        cell.addElement(pVal);

        Paragraph pSub = new Paragraph(subtext, subFont);
        cell.addElement(pSub);

        table.addCell(cell);
    }

    private static void addRecordsTable(Document document, List<DailyRecord> records, double maintRatePerKm,
                                        double totalGross, double totalCosts, double totalMaint, double totalNet,
                                        double totalKm, double totalHours) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY);
        Paragraph sectionTitle = new Paragraph("Detalhamento dos Turnos Diários", sectionFont);
        sectionTitle.setSpacingAfter(10);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{14, 11, 12, 16, 16, 15, 16});

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, COLOR_TEXT_DARK);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_PRIMARY);

        String[] headers = {"Data", "Horas", "KM Total", "Bruto", "Custos", "Manutenção", "Líquido"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(COLOR_PRIMARY);
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBorderColor(COLOR_PRIMARY);
            table.addCell(cell);
        }

        boolean alternate = false;
        if (records != null && !records.isEmpty()) {
            for (DailyRecord r : records) {
                if (r == null) continue;

                Color rowBg = alternate ? COLOR_ZEBRA : Color.WHITE;
                alternate = !alternate;

                addStyledCell(table, r.getDate() != null ? r.getDate().format(DATE_FORMATTER) : "-", cellFont, rowBg, Element.ALIGN_CENTER);
                addStyledCell(table, String.format(PT_BR, "%.1f h", r.calculateHours()), cellFont, rowBg, Element.ALIGN_CENTER);
                addStyledCell(table, String.format(PT_BR, "%.1f km", r.calculateKm()), cellFont, rowBg, Element.ALIGN_CENTER);
                addStyledCell(table, formatCurrency(r.calculateGrossIncome()), cellFont, rowBg, Element.ALIGN_RIGHT);
                addStyledCell(table, formatCurrency(r.calculateTotalCosts()), cellFont, rowBg, Element.ALIGN_RIGHT);
                addStyledCell(table, formatCurrency(r.calculateMaintReserve(maintRatePerKm)), cellFont, rowBg, Element.ALIGN_RIGHT);
                addStyledCell(table, formatCurrency(r.calculateNetProfit(maintRatePerKm)), cellFont, rowBg, Element.ALIGN_RIGHT);
            }
        } else {
            PdfPCell emptyCell = new PdfPCell(new Phrase("Nenhum registro encontrado para o período selecionado.", cellFont));
            emptyCell.setColspan(7);
            emptyCell.setPadding(10);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(emptyCell);
        }

        // Totals Row
        Color totalsBg = COLOR_BG_HEADER;
        addStyledCell(table, "TOTAL DO PERÍODO", totalFont, totalsBg, Element.ALIGN_LEFT);
        addStyledCell(table, String.format(PT_BR, "%.1f h", totalHours), totalFont, totalsBg, Element.ALIGN_CENTER);
        addStyledCell(table, String.format(PT_BR, "%.1f km", totalKm), totalFont, totalsBg, Element.ALIGN_CENTER);
        addStyledCell(table, formatCurrency(totalGross), totalFont, totalsBg, Element.ALIGN_RIGHT);
        addStyledCell(table, formatCurrency(totalCosts), totalFont, totalsBg, Element.ALIGN_RIGHT);
        addStyledCell(table, formatCurrency(totalMaint), totalFont, totalsBg, Element.ALIGN_RIGHT);
        addStyledCell(table, formatCurrency(totalNet), totalFont, totalsBg, Element.ALIGN_RIGHT);

        document.add(table);
    }

    private static void addStyledCell(PdfPTable table, String text, Font font, Color bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setBorderColor(COLOR_BORDER);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static String formatCurrency(double amount) {
        return CURRENCY_FORMATTER.format(amount);
    }

    /**
     * Page Event Helper to draw Footer page numbers (Page X of Y).
     */
    private static class HeaderFooterPageEvent extends PdfPageEventHelper {
        private PdfTemplate totalPagesTemplate;
        private BaseFont baseFont;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPagesTemplate = writer.getDirectContent().createTemplate(30, 16);
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error initializing font for PDF header/footer", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            cb.saveState();

            String text = String.format("DriverFin  |  Demonstrativo Financeiro  |  Página %d de ", writer.getPageNumber());
            float textWidth = baseFont != null ? baseFont.getWidthPoint(text, 8) : 150;
            float pageWidth = document.getPageSize().getWidth();
            float x = (pageWidth - textWidth) / 2;
            float y = document.bottomMargin() - 15;

            if (baseFont != null) {
                cb.beginText();
                cb.setFontAndSize(baseFont, 8);
                cb.setColorFill(COLOR_TEXT_MUTED);
                cb.setTextMatrix(x, y);
                cb.showText(text);
                cb.endText();

                cb.addTemplate(totalPagesTemplate, x + textWidth, y);
            }

            cb.restoreState();
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            if (baseFont != null && totalPagesTemplate != null) {
                totalPagesTemplate.beginText();
                totalPagesTemplate.setFontAndSize(baseFont, 8);
                totalPagesTemplate.setColorFill(COLOR_TEXT_MUTED);
                totalPagesTemplate.showText(String.valueOf(writer.getPageNumber()));
                totalPagesTemplate.endText();
            }
        }
    }
}
