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
        List<String> lines = new ArrayList<>();
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }

        // Some invoices (especially scanned PDFs) break a single item row across multiple OCR lines.
        // To keep the template format simple (single regex), try matching on short windows of adjacent lines.
        for (int i = 0; i < lines.size(); i++) {
            boolean matched = false;
            // Prefer larger windows first to avoid partial/duplicate matches.
            for (int win = 4; win >= 1; win--) {
                if (i + win > lines.size()) {
                    continue;
                }
                String candidate = join(lines, i, i + win);
                ParsedItem item = tryParseCandidate(candidate, linePattern);
                if (item != null) {
                    items.add(item);
                    i = i + win - 1; // advance past consumed lines
                    matched = true;
                    break;
                }
            }
            if (matched) {
                continue;
            }
        }
        return items;
    }

    private ParsedItem tryParseCandidate(String candidate, Pattern linePattern) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        Matcher matcher = linePattern.matcher(candidate);
        if (!matcher.find()) {
            return null;
        }
        try {
            String name = firstGroup(matcher, "name", "item_name", "itemName");
            // Strip leading serial numbers (common on invoices: "1 ITEM NAME ...").
            name = name.replaceFirst("^\\d+\\s+", "").trim();
            String qtyText = firstGroup(matcher, "qty", "quantity");
            String rateText = firstGroup(matcher, "rate", "rate_after_tax");
            String amountText = firstGroup(matcher, "amount", "total", "total_value_after_tax", "totalValueAfterTax");
            String hsn = firstGroup(matcher, "hsn", "item_id", "itemId", "hsn_code", "hsnCode", "id");
            String gstText = firstGroup(matcher, "gst", "tax", "tax_percent", "taxPercent", "gst_percent", "gstPercent");
            if (name.isBlank()) {
                return null;
            }
            if (!containsLetters(name) || isHeaderOrMeta(name)) {
                return null;
            }
            // Guard against regex accidentally spanning multiple items (common when OCR merges rows).
            // If the captured name includes an HSN-like long digit sequence, it's almost certainly wrong.
            if (name.matches(".*\\b\\d{4,10}\\b.*")) {
                return null;
            }
            // If it has too many numeric tokens, it likely swallowed qty/rate/amount from adjacent rows.
            if (countNumericTokens(name) > 4) {
                return null;
            }
            double qty = parseNumber(qtyText);
            double rate = parseNumber(rateText);
            double amount = parseNumber(amountText);
            double gstPercent = parseNumber(gstText);
            if (qty <= 0) {
                qty = 1.0;
            }
            if (amount <= 0 && qty > 0 && rate > 0) {
                amount = rate * qty;
            }
            if (rate <= 0 && qty > 0 && amount > 0) {
                rate = amount / qty;
            }
            if (rate <= 0 && amount > 0 && gstPercent > 0) {
                rate = amount;
            }
            // If both rate and amount are present but inconsistent (common when rate includes tax but amount is taxable),
            // keep amount as source of truth and normalize rate so ERPNext won't recompute a different amount.
            if (qty > 0 && rate > 0 && amount > 0) {
                double expected = Math.round(qty * rate * 100.0) / 100.0;
                if (Math.abs(expected - amount) > 0.5) {
                    rate = amount / qty;
                }
            }
            if (rate <= 0 || amount <= 0) {
                return null;
            }
            Double gstPercentValue = gstPercent > 0 ? gstPercent : null;
            return new ParsedItem(name, qty, rate, amount, hsn.isBlank() ? null : hsn, gstPercentValue);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int countNumericTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String token : value.trim().split("\\s+")) {
            if (token.matches("\\d+(?:\\.\\d+)?")) {
                count++;
            }
        }
        return count;
    }

    private boolean containsLetters(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeaderOrMeta(String name) {
        String lowered = name.toLowerCase();
        // Reject common invoice metadata/header rows that may accidentally match numeric patterns.
        String[] blocked = {
                "hsn", "sac", "qty", "rate", "amount", "tax", "total", "value", "invoice", "bill",
                "gst", "gstin", "pan", "fssai", "sr", "particular", "receiver", "seller", "buyer"
        };
        for (String word : blocked) {
            if (lowered.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String join(List<String> lines, int start, int endExclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < endExclusive; i++) {
            if (i > start) {
                sb.append(' ');
            }
            sb.append(lines.get(i));
        }
        return sb.toString().trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstGroup(Matcher matcher, String... names) {
        for (String name : names) {
            try {
                String value = trim(matcher.group(name));
                if (!value.isBlank()) {
                    return value;
                }
            } catch (IllegalArgumentException ignored) {
                // try next alias
            }
        }
        return "";
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
