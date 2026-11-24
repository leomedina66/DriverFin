package com.driverfin.util;

import com.driverfin.model.FinancialSummary;
import com.driverfin.model.WorkShift;
import com.driverfin.service.SummaryService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfReportExporter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERATED_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    // Same palette as the app (theme-dark.css), so the report looks like part of DriverFin.
    private static final Color TEXT = new Color(0x4a, 0x4a, 0x4a);
    private static final Color MUTED = new Color(0x76, 0x76, 0x76);
    private static final Color LINE = new Color(0xd1, 0xc9, 0xb8);
    private static final Color ROW_ALT = new Color(0xfa, 0xf9, 0xf6);
    private static final Color HEADER_BG = new Color(0xf1, 0xee, 0xe7);
    private static final Color REVENUE = new Color(0x3d, 0x61, 0x66);
    private static final Color COST = new Color(0x9a, 0x45, 0x29);
    private static final Color MAINT = new Color(0x85, 0x56, 0x0f);
    private static final Color PROFIT = new Color(0x4a, 0x66, 0x48);

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

    private static Font font(String name, float size, Color color) {
        return FontFactory.getFont(name, size, color);
    }

    private static void writeReport(File file, String period, List<WorkShift> shifts, BigDecimal maintRate) throws Exception {
        Document document = new Document(PageSize.A4, 42, 42, 44, 54);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            PdfWriter writer = PdfWriter.getInstance(document, fos);
            writer.setPageEvent(new Footer(LocalDateTime.now().format(GENERATED_FORMATTER)));
            document.addTitle("DriverFin - Relatório Financeiro");
            document.open();

            FinancialSummary summary = SummaryService.calculate(shifts, maintRate);

            addHeader(document, period);
            addSectionTitle(document, "Resumo", 14f);
            document.add(summaryCards(summary));
            document.add(averagesTable(summary));
            addSectionTitle(document, "Turnos (" + shifts.size() + ")", 18f);
            document.add(shiftsTable(shifts, maintRate, summary));

            // Must close the document (writes the PDF trailer) BEFORE the stream is closed by try-with-resources.
            document.close();
        }
    }

    private static void addHeader(Document document, String period) throws DocumentException {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{3f, 2f});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.addElement(new Paragraph("DRIVERFIN", font(FontFactory.HELVETICA_BOLD, 8.5f, REVENUE)));
        Paragraph title = new Paragraph("Relatório Financeiro", font(FontFactory.HELVETICA_BOLD, 19, TEXT));
        title.setSpacingBefore(2f);
        left.addElement(title);
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setVerticalAlignment(Element.ALIGN_BOTTOM);
        Paragraph periodLabel = new Paragraph("PERÍODO", font(FontFactory.HELVETICA, 7.5f, MUTED));
        periodLabel.setAlignment(Element.ALIGN_RIGHT);
        Paragraph periodValue = new Paragraph(period != null ? period : "Geral", font(FontFactory.HELVETICA_BOLD, 11, TEXT));
        periodValue.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(periodLabel);
        right.addElement(periodValue);
        header.addCell(right);

        // Thin line under the header: the only decoration on the page.
        PdfPCell rule = new PdfPCell();
        rule.setColspan(2);
        rule.setFixedHeight(10f);
        rule.setBorder(Rectangle.BOTTOM);
        rule.setBorderColorBottom(REVENUE);
        rule.setBorderWidthBottom(1.2f);
        header.addCell(rule);

        document.add(header);
    }

    private static void addSectionTitle(Document document, String text, float spacingBefore) throws DocumentException {
        Paragraph p = new Paragraph(text.toUpperCase(CurrencyFormat.PT_BR), font(FontFactory.HELVETICA_BOLD, 8.5f, MUTED));
        p.setSpacingBefore(spacingBefore);
        p.setSpacingAfter(6f);
        document.add(p);
    }

    /** Four totals side by side; a thin colored bar on top tells each idea apart, like the cards on screen. */
    private static PdfPTable summaryCards(FinancialSummary summary) {
        PdfPTable cards = new PdfPTable(4);
        cards.setWidthPercentage(100);
        cards.addCell(card("Faturamento bruto", summary.totalGross(), REVENUE, false));
        cards.addCell(card("Custos diretos", summary.totalCosts(), COST, false));
        cards.addCell(card("Reserva manutenção", summary.totalMaint(), MAINT, false));
        cards.addCell(card("Lucro líquido", summary.totalNet(), PROFIT, true));
        return cards;
    }

    private static PdfPCell card(String label, BigDecimal value, Color accent, boolean highlight) {
        PdfPCell cell = new PdfPCell();
        // White side borders act as a small gap between the cards.
        cell.setUseVariableBorders(true);
        cell.setBorder(Rectangle.TOP | Rectangle.LEFT | Rectangle.RIGHT);
        cell.setBorderColorTop(accent);
        cell.setBorderWidthTop(2.5f);
        cell.setBorderColorLeft(Color.WHITE);
        cell.setBorderColorRight(Color.WHITE);
        cell.setBorderWidthLeft(3f);
        cell.setBorderWidthRight(3f);
        cell.setBackgroundColor(ROW_ALT);
        cell.setPaddingTop(8f);
        cell.setPaddingBottom(10f);
        cell.setPaddingLeft(9f);
        cell.setPaddingRight(9f);
        cell.addElement(new Paragraph(label, font(FontFactory.HELVETICA, 8f, MUTED)));
        Color valueColor = CurrencyFormat.isLoss(value) ? COST : (highlight ? PROFIT : TEXT);
        Paragraph amount = new Paragraph(CurrencyFormat.money(value), font(FontFactory.HELVETICA_BOLD, 13, valueColor));
        amount.setSpacingBefore(3f);
        cell.addElement(amount);
        return cell;
    }

    private static PdfPTable averagesTable(FinancialSummary summary) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(8f);
        table.addCell(average("Lucro por hora", CurrencyFormat.perHour(summary.avgProfitPerHour())));
        table.addCell(average("Lucro por km", CurrencyFormat.perKm(summary.avgProfitPerKm())));
        table.addCell(average("Faturamento por hora", CurrencyFormat.perHour(summary.avgGrossPerHour())));
        table.addCell(average("Faturamento por km", CurrencyFormat.perKm(summary.avgGrossPerKm())));
        return table;
    }

    private static PdfPCell average(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(LINE);
        cell.setBorderWidthBottom(0.5f);
        cell.setPaddingLeft(9f);
        cell.setPaddingBottom(7f);
        cell.addElement(new Paragraph(label + " (média)", font(FontFactory.HELVETICA, 7.5f, MUTED)));
        cell.addElement(new Paragraph(value, font(FontFactory.HELVETICA_BOLD, 9.5f, TEXT)));
        return cell;
    }

    private static PdfPTable shiftsTable(List<WorkShift> shifts, BigDecimal maintRate, FinancialSummary summary)
            throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.4f, 0.9f, 1f, 1.5f, 1.5f, 1.5f});
        table.setHeaderRows(1); // repeats the header on every page

        Font headerFont = font(FontFactory.HELVETICA_BOLD, 8f, MUTED);
        String[] headers = {"Data", "Horas", "KM", "Faturamento", "Custos", "Lucro"};
        for (int i = 0; i < headers.length; i++) {
            PdfPCell cell = cell(headers[i].toUpperCase(CurrencyFormat.PT_BR), headerFont,
                i == 0 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            cell.setBackgroundColor(HEADER_BG);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColorBottom(LINE);
            cell.setPaddingTop(6f);
            cell.setPaddingBottom(7f);
            table.addCell(cell);
        }

        Font normal = font(FontFactory.HELVETICA, 9f, TEXT);
        Font profitFont = font(FontFactory.HELVETICA_BOLD, 9f, PROFIT);
        Font lossFont = font(FontFactory.HELVETICA_BOLD, 9f, COST);
        boolean alt = false;
        for (WorkShift shift : shifts) {
            BigDecimal net = shift.calculateNetProfit(maintRate);
            PdfPCell[] row = {
                cell(shift.getDate().format(DATE_FORMATTER), normal, Element.ALIGN_LEFT),
                cell(CurrencyFormat.oneDecimal(shift.calculateHours()), normal, Element.ALIGN_RIGHT),
                cell(CurrencyFormat.oneDecimal(shift.calculateKm()), normal, Element.ALIGN_RIGHT),
                cell(CurrencyFormat.money(shift.getGrossIncome()), normal, Element.ALIGN_RIGHT),
                cell(CurrencyFormat.money(shift.getTotalCosts()), normal, Element.ALIGN_RIGHT),
                cell(CurrencyFormat.money(net), CurrencyFormat.isLoss(net) ? lossFont : profitFont, Element.ALIGN_RIGHT)
            };
            for (PdfPCell c : row) {
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColorBottom(LINE);
                c.setBorderWidthBottom(0.3f);
                if (alt) c.setBackgroundColor(ROW_ALT);
                table.addCell(c);
            }
            alt = !alt;
        }

        Font totalFont = font(FontFactory.HELVETICA_BOLD, 9f, TEXT);
        PdfPCell[] totals = {
            cell("Total", totalFont, Element.ALIGN_LEFT),
            cell(CurrencyFormat.oneDecimal(summary.totalHours()), totalFont, Element.ALIGN_RIGHT),
            cell(CurrencyFormat.oneDecimal(summary.totalKm()), totalFont, Element.ALIGN_RIGHT),
            cell(CurrencyFormat.money(summary.totalGross()), totalFont, Element.ALIGN_RIGHT),
            cell(CurrencyFormat.money(summary.totalCosts()), totalFont, Element.ALIGN_RIGHT),
            cell(CurrencyFormat.money(summary.totalNet()),
                CurrencyFormat.isLoss(summary.totalNet()) ? lossFont : profitFont, Element.ALIGN_RIGHT)
        };
        for (PdfPCell c : totals) {
            c.setBorder(Rectangle.TOP);
            c.setBorderColorTop(TEXT);
            c.setBorderWidthTop(0.8f);
            c.setPaddingTop(7f);
            table.addCell(c);
        }
        return table;
    }

    private static PdfPCell cell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(5f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(6f);
        cell.setPaddingRight(6f);
        return cell;
    }

    /** Small gray footer on every page: when the report was generated and the page number. */
    private static final class Footer extends PdfPageEventHelper {
        private final String generatedAt;

        Footer(String generatedAt) {
            this.generatedAt = generatedAt;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Font footerFont = font(FontFactory.HELVETICA, 7.5f, MUTED);
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottom() - 22;

            canvas.saveState();
            canvas.setColorStroke(LINE);
            canvas.setLineWidth(0.5f);
            canvas.moveTo(document.left(), y + 11);
            canvas.lineTo(document.right(), y + 11);
            canvas.stroke();
            canvas.restoreState();

            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                new Phrase("Gerado pelo DriverFin em " + generatedAt, footerFont), document.left(), y, 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT,
                new Phrase("Página " + writer.getPageNumber(), footerFont), document.right(), y, 0);
        }
    }
}
