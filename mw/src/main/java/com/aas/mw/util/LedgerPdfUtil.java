package com.aas.mw.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class LedgerPdfUtil {

    private static final float MARGIN = 36f;
    private static final float ROW_HEIGHT = 16f;
    private static final float HEADER_FONT_SIZE = 9f;
    private static final float DATA_FONT_SIZE = 8f;
    private static final float TITLE_FONT_SIZE = 12f;
    private static final float BRAND_NAME_FONT_SIZE = 14f;
    private static final float BRAND_META_FONT_SIZE = 9f;
    private static final float LOGO_MAX_WIDTH = 60f;
    private static final float LOGO_MAX_HEIGHT = 60f;
    private static final int MAX_CELL_CHARS = 20;

    private LedgerPdfUtil() {}

    public static byte[] toPdf(List<Map<String, Object>> rows, String title) throws IOException {
        return toPdf(rows, title, null);
    }

    public static byte[] toPdf(List<Map<String, Object>> rows, String title, Branding branding) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            List<String> headers = extractHeaders(rows);
            int colCount = headers.isEmpty() ? 1 : headers.size();

            // Use landscape A4 for wide tables
            PDRectangle pageSize = colCount > 4 ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4;
            float pageWidth = pageSize.getWidth();
            float pageHeight = pageSize.getHeight();
            float usableWidth = pageWidth - 2 * MARGIN;
            float colWidth = usableWidth / colCount;

            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = drawPageHeader(document, content, pageWidth, pageHeight, bold, regular, safeTitle(title), branding);

            // Draw header row
            y = drawRow(content, bold, HEADER_FONT_SIZE, MARGIN, y, colWidth, headers, true);

            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    if (y < MARGIN + ROW_HEIGHT) {
                        content.close();
                        page = new PDPage(pageSize);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = drawPageHeader(document, content, pageWidth, pageHeight, bold, regular, safeTitle(title), branding);
                        y = drawRow(content, bold, HEADER_FONT_SIZE, MARGIN, y, colWidth, headers, true);
                    }
                    List<String> cells = new ArrayList<>();
                    for (String h : headers) {
                        Object val = row == null ? null : row.get(h);
                        cells.add(val == null ? "" : String.valueOf(val));
                    }
                    y = drawRow(content, regular, DATA_FONT_SIZE, MARGIN, y, colWidth, cells, false);
                }
            }

            content.close();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static float drawRow(PDPageContentStream content, PDType1Font font, float size,
            float startX, float y, float colWidth, List<String> cells, boolean isHeader) throws IOException {
        float x = startX;
        for (String cell : cells) {
            writeText(content, font, size, x, y, truncate(cell, MAX_CELL_CHARS));
            x += colWidth;
        }
        float nextY = y - ROW_HEIGHT;
        if (isHeader) {
            // Draw a thin line under the header
            content.setLineWidth(0.5f);
            content.moveTo(startX, nextY + 4f);
            content.lineTo(startX + colWidth * cells.size(), nextY + 4f);
            content.stroke();
            nextY -= 2f;
        }
        return nextY;
    }

    private static void writeText(PDPageContentStream content, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
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

    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 2)) + "..";
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
            Branding branding) throws IOException {
        float y = pageHeight - MARGIN;
        float textStartX = MARGIN;
        if (branding != null && branding.logoBytes() != null && branding.logoBytes().length > 0) {
            try {
                PDImageXObject logo = PDImageXObject.createFromByteArray(document, branding.logoBytes(), "company-logo");
                float scale = Math.min(LOGO_MAX_WIDTH / logo.getWidth(), LOGO_MAX_HEIGHT / logo.getHeight());
                float drawWidth = Math.max(1f, logo.getWidth() * scale);
                float drawHeight = Math.max(1f, logo.getHeight() * scale);
                float imageY = y - drawHeight + 8f;
                content.drawImage(logo, MARGIN, imageY, drawWidth, drawHeight);
                textStartX = MARGIN + drawWidth + 14f;
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

        float titleY = y - 6f;
        writeText(content, bold, TITLE_FONT_SIZE, MARGIN, titleY, title);
        float dividerY = titleY - 8f;
        content.setLineWidth(0.8f);
        content.moveTo(MARGIN, dividerY);
        content.lineTo(pageWidth - MARGIN, dividerY);
        content.stroke();
        return dividerY - 12f;
    }

    private static String safeText(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "").trim();
    }

    public record Branding(String companyName, String taxId, byte[] logoBytes) {}
}
