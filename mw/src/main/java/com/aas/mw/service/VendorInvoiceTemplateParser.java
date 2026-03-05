package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class VendorInvoiceTemplateParser {

    public List<ParsedItem> parseItems(String ocrText, VendorInvoiceTemplate template) {
        if (ocrText == null || ocrText.isBlank() || template == null) {
            return List.of();
        }
        if (template.version() != 1) {
            return List.of();
        }
        Pattern linePattern;
        try {
            linePattern = Pattern.compile(template.itemLineRegex());
        } catch (Exception ex) {
            return List.of();
        }

        List<ParsedItem> items = new ArrayList<>();
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = linePattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            try {
                String name = trim(matcher.group("name"));
                String qtyText = trim(matcher.group("qty"));
                String rateText = trim(matcher.group("rate"));
                String amountText = trim(matcher.group("amount"));
                String hsn = "";
                try {
                    hsn = trim(matcher.group("hsn"));
                } catch (IllegalArgumentException ignored) {
                    // optional
                }
                if (name.isBlank()) {
                    continue;
                }
                double qty = parseNumber(qtyText);
                double rate = parseNumber(rateText);
                double amount = parseNumber(amountText);
                if (qty <= 0) {
                    continue;
                }
                if (amount <= 0 && qty > 0 && rate > 0) {
                    amount = rate * qty;
                }
                if (rate <= 0 && qty > 0 && amount > 0) {
                    rate = amount / qty;
                }
                if (rate <= 0 || amount <= 0) {
                    continue;
                }
                items.add(new ParsedItem(name, qty, rate, amount, hsn.isBlank() ? null : hsn));
            } catch (IllegalArgumentException ex) {
                // Missing named groups or mismatch; skip the line.
            }
        }
        return items;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private double parseNumber(String raw) {
        if (raw == null) {
            return 0.0;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return 0.0;
        }
        text = text.replace(",", "");
        // Some OCR variants produce "1O.00" (O instead of 0).
        text = text.replace('O', '0').replace('o', '0');
        // Strip currency markers.
        text = text.replaceAll("(?i)inr|rs\\.?", "");
        text = text.trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
