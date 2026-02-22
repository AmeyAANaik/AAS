package com.aas.mw.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OcrService {

    private final Tesseract tesseract;
    private final int dpi;

    public OcrService(
            @Value("${ocr.tesseract.datapath:}") String datapath,
            @Value("${ocr.tesseract.language:eng}") String language,
            @Value("${ocr.tesseract.dpi:300}") int dpi) {
        this.tesseract = new Tesseract();
        if (datapath != null && !datapath.isBlank()) {
            this.tesseract.setDatapath(datapath);
        }
        this.tesseract.setLanguage(language);
        this.dpi = dpi <= 0 ? 300 : dpi;
    }

    public String extractTextFromPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF content is required for OCR.");
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            String text = extractText(document);
            if (text != null && !text.isBlank()) {
                return text;
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> pages = new ArrayList<>();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImageWithDPI(page, dpi, ImageType.RGB);
                pages.add(doOcr(image));
            }
            return String.join("\n", pages);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read vendor PDF for OCR.", ex);
        }
    }

    private String extractText(PDDocument document) throws IOException {
        if (document == null || document.getNumberOfPages() == 0) {
            return "";
        }
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        if (text == null) {
            return "";
        }
        // If there's meaningful text, prefer it over image OCR (faster + more accurate for text PDFs).
        String normalized = text.trim();
        return normalized.length() >= 20 ? normalized : "";
    }

    public boolean healthCheck() {
        BufferedImage image = new BufferedImage(220, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 220, 60);
        graphics.setColor(Color.BLACK);
        graphics.drawString("OCR", 20, 35);
        graphics.dispose();
        String text = doOcr(image);
        return text != null && !text.isBlank();
    }

    private String doOcr(BufferedImage image) {
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException ex) {
            throw new IllegalStateException("OCR failed on vendor PDF.", ex);
        }
    }
}
