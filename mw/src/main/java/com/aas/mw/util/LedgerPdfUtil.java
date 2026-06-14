package com.aas.mw.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class LedgerPdfUtil {

    private static final float MARGIN = 36f;
    private static final float HEADER_FONT_SIZE = 9.5f;
    private static final float DATA_FONT_SIZE = 8.5f;
    private static final float TITLE_FONT_SIZE = 16f;
    private static final float SUBTITLE_FONT_SIZE = 9f;
    private static final float BRAND_NAME_FONT_SIZE = 16f;
    private static final float BRAND_META_FONT_SIZE = 9f;
    private static final float LOGO_MAX_WIDTH = 60f;
    private static final float LOGO_MAX_HEIGHT = 60f;
    private static final float HEADER_CELL_PADDING_X = 6f;
    private static final float CELL_PADDING_X = 5f;
    private static final float CELL_PADDING_Y = 4f;
    private static final float LINE_GAP = 3f;
    private static final DecimalFormat MONEY = new DecimalFormat("0.00");
    private static final Map<String, Float> COLUMN_WIDTH_HINTS = buildColumnWidthHints();

    private LedgerPdfUtil() {}

    public static byte[] toPdf(List<Map<String, Object>> rows, String title) throws IOException {
        return toPdf(rows, title, null, null);
    }

    public static byte[] toPdf(List<Map<String, Object>> rows, String title, Branding branding) throws IOException {
        return toPdf(rows, title, branding, null);
    }

    public static byte[] toPdf(
            List<Map<String, Object>> rows,
            String title,
            Branding branding,
            ReportContext reportContext) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            List<String> headers = extractHeaders(rows);
            int colCount = headers.isEmpty() ? 1 : headers.size();
            PDRectangle pageSize = colCount > 4
                    ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                    : PDRectangle.A4;
            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();
            float usableWidth = pageWidth - 2 * MARGIN;
            List<ColumnSpec> columns = resolveColumns(headers, usableWidth, colCount);

            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = drawPageHeader(document, content, pageWidth, pageHeight, bold, regular, safeTitle(title), branding, reportContext);
            y = drawSummaryBand(content, bold, regular, MARGIN, y, usableWidth, rows, headers);
            y = drawHeaderRow(content, bold, MARGIN, y, columns);

            if (rows != null) {
                boolean shaded = false;
                for (Map<String, Object> row : rows) {
                    List<Cell> cells = buildCells(columns, row, regular);
                    float rowHeight = computeRowHeight(cells);
                    if (y - rowHeight < MARGIN + 14f) {
                        content.close();
                        page = new PDPage(pageSize);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = drawPageHeader(document, content, pageWidth, pageHeight, bold, regular, safeTitle(title), branding, reportContext);
                        y = drawSummaryBand(content, bold, regular, MARGIN, y, usableWidth, rows, headers);
                        y = drawHeaderRow(content, bold, MARGIN, y, columns);
                    }
                    y = drawDataRow(content, regular, MARGIN, y, columns, cells, shaded);
                    shaded = !shaded;
                }
            }

            content.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static float drawSummaryBand(
            PDPageContentStream content,
            PDType1Font bold,
            PDType1Font regular,
            float startX,
            float y,
            float width,
            List<Map<String, Object>> rows,
            List<String> headers) throws IOException {
        float bandHeight = 34f;
        float gap = 6f;
        float boxWidth = (width - (gap * 2f)) / 3f;
        double closingBalance = resolveLastNumeric(rows, "runningBalance");
        drawInfoBox(content, bold, regular, startX, y, boxWidth, bandHeight, "Rows", String.valueOf(rows == null ? 0 : rows.size()));
        drawInfoBox(content, bold, regular, startX + boxWidth + gap, y, boxWidth, bandHeight, "Columns", String.valueOf(headers == null ? 0 : headers.size()));
        drawInfoBox(content, bold, regular, startX + ((boxWidth + gap) * 2f), y, boxWidth, bandHeight, "Closing Balance", formatNumber(closingBalance));
        return y - bandHeight - 14f;
    }

    private static void drawInfoBox(
            PDPageContentStream content,
            PDType1Font bold,
            PDType1Font regular,
            float x,
            float y,
            float width,
            float height,
            String label,
            String value) throws IOException {
        float bottomY = y - height;
        setNonStrokingRgb(content, 248, 250, 252);
        content.addRect(x, bottomY, width, height);
        content.fill();
        setStrokingRgb(content, 203, 213, 225);
        content.addRect(x, bottomY, width, height);
        content.stroke();
        writeText(content, regular, 8f, x + 8f, y - 10f, safeText(label).toUpperCase());
        writeText(content, bold, 11f, x + 8f, y - 24f, safeText(value));
    }

    private static float drawHeaderRow(
            PDPageContentStream content,
            PDType1Font bold,
            float startX,
            float y,
            List<ColumnSpec> columns) throws IOException {
        float rowHeight = 24f;
        float x = startX;
        float bottomY = y - rowHeight;
        setNonStrokingRgb(content, 30, 41, 59);
        content.addRect(startX, bottomY, totalWidth(columns), rowHeight);
        content.fill();
        setStrokingRgb(content, 203, 213, 225);
        for (ColumnSpec column : columns) {
            content.addRect(x, bottomY, column.width(), rowHeight);
            content.stroke();
            writeText(content, bold, HEADER_FONT_SIZE, x + HEADER_CELL_PADDING_X, y - 15f, column.displayName(), 255, 255, 255);
            x += column.width();
        }
        return bottomY;
    }

    private static float drawDataRow(
            PDPageContentStream content,
            PDType1Font regular,
            float startX,
            float y,
            List<ColumnSpec> columns,
            List<Cell> cells,
            boolean shaded) throws IOException {
        float rowHeight = computeRowHeight(cells);
        float bottomY = y - rowHeight;
        float x = startX;
        if (shaded) {
            setNonStrokingRgb(content, 248, 250, 252);
            content.addRect(startX, bottomY, totalWidth(columns), rowHeight);
            content.fill();
        }
        setStrokingRgb(content, 226, 232, 240);
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec column = columns.get(index);
            Cell cell = cells.get(index);
            content.addRect(x, bottomY, column.width(), rowHeight);
            content.stroke();
            drawCellText(content, regular, x, y, column, cell, rowHeight);
            x += column.width();
        }
        return bottomY;
    }

    private static void drawCellText(
            PDPageContentStream content,
            PDFont font,
            float x,
            float topY,
            ColumnSpec column,
            Cell cell,
            float rowHeight) throws IOException {
        float lineHeight = DATA_FONT_SIZE + LINE_GAP;
        float textTop = topY - CELL_PADDING_Y - DATA_FONT_SIZE;
        List<String> lines = cell.lines();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lines.get(lineIndex);
            float textWidth = widthOf(font, DATA_FONT_SIZE, line);
            float drawX = column.numeric()
                    ? x + column.width() - CELL_PADDING_X - textWidth
                    : x + CELL_PADDING_X;
            float drawY = textTop - (lineIndex * lineHeight);
            if (drawY < (topY - rowHeight) + 3f) {
                break;
            }
            writeText(content, font, DATA_FONT_SIZE, drawX, drawY, line);
        }
    }

    private static List<String> extractHeaders(List<Map<String, Object>> rows) {
        Set<String> set = new LinkedHashSet<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row != null) {
                    set.addAll(row.keySet());
                }
            }
        }
        return new ArrayList<>(set);
    }

    private static String safeTitle(String title) {
        return title == null ? "Ledger Export" : title.replaceAll("[^\\x20-\\x7E]", "");
    }

    private static float drawPageHeader(
            PDDocument document,
            PDPageContentStream content,
            float pageWidth,
            float pageHeight,
            PDType1Font bold,
            PDType1Font regular,
            String title,
            Branding branding,
            ReportContext reportContext) throws IOException {
        float y = pageHeight - MARGIN;
        float textStartX = MARGIN;
        float reservedLogoWidth = 0f;
        if (branding != null && branding.logoBytes() != null && branding.logoBytes().length > 0) {
            try {
                PDImageXObject logo = PDImageXObject.createFromByteArray(document, branding.logoBytes(), "company-logo");
                float scale = Math.min(LOGO_MAX_WIDTH / logo.getWidth(), LOGO_MAX_HEIGHT / logo.getHeight());
                float drawWidth = Math.max(1f, logo.getWidth() * scale);
                float drawHeight = Math.max(1f, logo.getHeight() * scale);
                float imageY = y - drawHeight + 8f;
                content.drawImage(logo, MARGIN, imageY, drawWidth, drawHeight);
                textStartX = MARGIN + drawWidth + 14f;
                reservedLogoWidth = drawWidth + 14f;
            } catch (Exception ignored) {
                textStartX = MARGIN;
            }
        }

        String companyName = branding == null ? "" : safeText(branding.companyName());
        if (!companyName.isBlank()) {
            writeText(content, bold, BRAND_NAME_FONT_SIZE, textStartX, y, companyName);
            y -= BRAND_NAME_FONT_SIZE + 4f;
        }
        String companyTaxId = branding == null ? "" : safeText(branding.taxId());
        if (!companyTaxId.isBlank()) {
            writeText(content, regular, BRAND_META_FONT_SIZE, textStartX, y, "GSTIN: " + companyTaxId);
            y -= BRAND_META_FONT_SIZE + 6f;
        }

        float titleStartX = reservedLogoWidth > 0f ? textStartX : MARGIN;
        float titleY = y - 4f;
        writeText(content, bold, TITLE_FONT_SIZE, titleStartX, titleY, title);
        float detailY = titleY - 16f;

        String branchName = reportContext == null ? "" : safeText(reportContext.branchName());
        if (!branchName.isBlank()) {
            writeText(content, regular, SUBTITLE_FONT_SIZE, titleStartX, detailY, "Branch: " + branchName);
            detailY -= 12f;
        }

        String categoryName = reportContext == null ? "" : safeText(reportContext.categoryName());
        if (!categoryName.isBlank()) {
            writeText(content, regular, SUBTITLE_FONT_SIZE, titleStartX, detailY, "Category: " + categoryName);
            detailY -= 12f;
        }

        String periodLine = formatPeriodLine(reportContext);
        if (!periodLine.isBlank()) {
            writeText(content, regular, SUBTITLE_FONT_SIZE, titleStartX, detailY, periodLine);
            detailY -= 12f;
        }

        writeText(content, regular, SUBTITLE_FONT_SIZE, titleStartX, detailY, "Generated on " + LocalDate.now());
        float dividerY = detailY - 10f;
        content.setLineWidth(0.8f);
        content.moveTo(MARGIN, dividerY);
        content.lineTo(pageWidth - MARGIN, dividerY);
        content.stroke();
        return dividerY - 12f;
    }

    private static void writeText(PDPageContentStream content, PDFont font, float size, float x, float y, String text)
            throws IOException {
        writeText(content, font, size, x, y, text, 17, 24, 39);
    }

    private static void writeText(
            PDPageContentStream content,
            PDFont font,
            float size,
            float x,
            float y,
            String text,
            int red,
            int green,
            int blue) throws IOException {
        String safe = safeText(text);
        if (safe.isBlank()) {
            return;
        }
        content.beginText();
        setNonStrokingRgb(content, red, green, blue);
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(safe);
        content.endText();
    }

    private static void setNonStrokingRgb(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setNonStrokingColor(rgb(red), rgb(green), rgb(blue));
    }

    private static void setStrokingRgb(PDPageContentStream content, int red, int green, int blue) throws IOException {
        content.setStrokingColor(rgb(red), rgb(green), rgb(blue));
    }

    private static float rgb(int value) {
        return Math.max(0, Math.min(255, value)) / 255f;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "").trim();
    }

    private static List<ColumnSpec> resolveColumns(List<String> headers, float usableWidth, int colCount) {
        if (headers == null || headers.isEmpty()) {
            return List.of(new ColumnSpec("value", "Value", usableWidth, false));
        }
        double totalHint = 0.0;
        for (String header : headers) {
            totalHint += COLUMN_WIDTH_HINTS.getOrDefault(header, 1f);
        }
        if (totalHint <= 0) {
            totalHint = Math.max(1, colCount);
        }
        List<ColumnSpec> specs = new ArrayList<>();
        for (String header : headers) {
            float hint = COLUMN_WIDTH_HINTS.getOrDefault(header, 1f);
            float width = (float) ((hint / totalHint) * usableWidth);
            specs.add(new ColumnSpec(header, humanizeHeader(header), width, isNumericHeader(header)));
        }
        return specs;
    }

    private static List<Cell> buildCells(List<ColumnSpec> columns, Map<String, Object> row, PDFont font) throws IOException {
        List<Cell> cells = new ArrayList<>();
        for (ColumnSpec column : columns) {
            Object raw = row == null ? null : row.get(column.key());
            String text = formatValue(column.key(), raw);
            float maxWidth = Math.max(20f, column.width() - (CELL_PADDING_X * 2f));
            cells.add(new Cell(wrapText(text, font, DATA_FONT_SIZE, maxWidth)));
        }
        return cells;
    }

    private static float computeRowHeight(List<Cell> cells) {
        int lines = 1;
        for (Cell cell : cells) {
            lines = Math.max(lines, cell.lines().size());
        }
        return (lines * (DATA_FONT_SIZE + LINE_GAP)) + (CELL_PADDING_Y * 2f) + 2f;
    }

    private static List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        String safe = safeText(text);
        if (safe.isBlank()) {
            return List.of("-");
        }
        List<String> lines = new ArrayList<>();
        String[] words = safe.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(font, fontSize, candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (widthOf(font, fontSize, word) <= maxWidth) {
                current.append(word);
            } else {
                lines.addAll(splitLongToken(word, font, fontSize, maxWidth));
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("-") : lines;
    }

    private static List<String> splitLongToken(String token, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> parts = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (char ch : token.toCharArray()) {
            String candidate = piece + String.valueOf(ch);
            if (widthOf(font, fontSize, candidate) <= maxWidth || piece.isEmpty()) {
                piece.append(ch);
                continue;
            }
            parts.add(piece + "…");
            piece.setLength(0);
            piece.append(ch);
        }
        if (!piece.isEmpty()) {
            parts.add(piece.toString());
        }
        return parts;
    }

    private static float widthOf(PDFont font, float fontSize, String text) throws IOException {
        String safe = safeText(text);
        return font.getStringWidth(safe) / 1000f * fontSize;
    }

    private static String formatValue(String header, Object value) {
        if (value == null) {
            return "";
        }
        if (isNumericHeader(header) && value instanceof Number number) {
            return MONEY.format(number.doubleValue());
        }
        if (isNumericHeader(header)) {
            try {
                return MONEY.format(Double.parseDouble(String.valueOf(value)));
            } catch (NumberFormatException ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static boolean isNumericHeader(String header) {
        String normalized = header == null ? "" : header.trim().toLowerCase();
        return normalized.contains("amount")
                || normalized.contains("debit")
                || normalized.contains("credit")
                || normalized.contains("balance")
                || normalized.contains("change")
                || normalized.contains("total");
    }

    private static String humanizeHeader(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String value = key
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        String[] tokens = value.split(" ");
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (out.length() > 0) {
                out.append(' ');
            }
            if (token.length() <= 2) {
                out.append(token.toUpperCase());
            } else {
                out.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
            }
        }
        return out.toString();
    }

    private static String formatNumber(double value) {
        return MONEY.format(value);
    }

    private static String formatPeriodLine(ReportContext reportContext) {
        if (reportContext == null) {
            return "";
        }
        String fromDate = safeText(reportContext.fromDate());
        String toDate = safeText(reportContext.toDate());
        if (!fromDate.isBlank() && !toDate.isBlank()) {
            return "Start Date: " + fromDate + "    End Date: " + toDate;
        }
        if (!fromDate.isBlank()) {
            return "Start Date: " + fromDate;
        }
        if (!toDate.isBlank()) {
            return "End Date: " + toDate;
        }
        return "";
    }

    private static double resolveLastNumeric(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty() || key == null || key.isBlank()) {
            return 0.0;
        }
        double last = 0.0;
        for (Map<String, Object> row : rows) {
            if (row == null || row.get(key) == null) {
                continue;
            }
            try {
                last = Double.parseDouble(String.valueOf(row.get(key)));
            } catch (NumberFormatException ignored) {
                // Keep last valid number.
            }
        }
        return last;
    }

    private static float totalWidth(List<ColumnSpec> columns) {
        float total = 0f;
        for (ColumnSpec column : columns) {
            total += column.width();
        }
        return total;
    }

    private static Map<String, Float> buildColumnWidthHints() {
        Map<String, Float> hints = new HashMap<>();
        hints.put("date", 1.0f);
        hints.put("voucherType", 1.4f);
        hints.put("voucherNo", 1.45f);
        hints.put("reference", 1.5f);
        hints.put("debit", 1.0f);
        hints.put("credit", 1.0f);
        hints.put("netChange", 1.1f);
        hints.put("runningBalance", 1.2f);
        hints.put("branchId", 1.1f);
        hints.put("branchName", 1.5f);
        hints.put("category", 1.4f);
        hints.put("amount", 1.1f);
        return hints;
    }

    private record ColumnSpec(String key, String displayName, float width, boolean numeric) {}

    private record Cell(List<String> lines) {}

    public record ReportContext(String title, String branchName, String categoryName, String fromDate, String toDate) {}

    public record Branding(String companyName, String taxId, byte[] logoBytes) {}
}
