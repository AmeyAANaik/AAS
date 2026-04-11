package com.aas.mw.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class InvoiceSummaryExtractor {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{2})");

    private InvoiceSummaryExtractor() {
    }

    public static Extraction extractFinalAmount(String text, String configuredRegex) {
        if (text == null || text.isBlank()) {
            return new Extraction("", "");
        }
        Extraction configured = extractByRegex(text, configuredRegex);
        if (configured.hasAmount()) {
            return configured;
        }
        Extraction contextual = extractNearAmountChargeable(text);
        if (contextual.hasAmount()) {
            return contextual;
        }
        return extractGenericTotalLine(text);
    }

    public static double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        String normalized = raw.replace(",", "").trim();
        normalized = normalized.replace('O', '0').replace('o', '0');
        normalized = normalized.replaceAll("(?i)inr|rs\\.?", "").trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static Extraction extractByRegex(String text, String regex) {
        if (regex == null || regex.isBlank()) {
            return new Extraction("", "");
        }
        try {
            var matcher = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
            String amount = "";
            String matchedText = "";
            while (matcher.find()) {
                for (String group : List.of(
                        "finalBillAmount",
                        "final_bill_amount",
                        "amount",
                        "total",
                        "grandTotal",
                        "grand_total",
                        "invoiceTotal",
                        "invoice_total")) {
                    try {
                        String candidate = asText(matcher.group(group));
                        if (!candidate.isBlank() && parseAmount(candidate) > 0) {
                            amount = extractBestAmount(candidate);
                            matchedText = asText(matcher.group());
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                String whole = asText(matcher.group());
                String extracted = extractBestAmount(whole);
                if (!extracted.isBlank()) {
                    amount = extracted;
                    matchedText = whole;
                }
            }
            return new Extraction(amount, matchedText);
        } catch (Exception ex) {
            return new Extraction("", "");
        }
    }

    private static Extraction extractNearAmountChargeable(String text) {
        List<String> lines = normalizedLines(text);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).toLowerCase();
            if (!line.startsWith("amount chargeable")) {
                continue;
            }
            for (int cursor = Math.max(0, index - 4); cursor < index; cursor++) {
                String candidate = lines.get(cursor);
                if (!candidate.toLowerCase().startsWith("total")) {
                    continue;
                }
                if (candidate.startsWith("Total:")) {
                    continue;
                }
                String amount = extractBestAmount(candidate);
                if (!amount.isBlank()) {
                    return new Extraction(amount, candidate);
                }
            }
        }
        return new Extraction("", "");
    }

    private static Extraction extractGenericTotalLine(String text) {
        for (String line : normalizedLines(text)) {
            String lowered = line.toLowerCase();
            if (!lowered.startsWith("total")) {
                continue;
            }
            if (lowered.startsWith("total:")) {
                continue;
            }
            String amount = extractBestAmount(line);
            if (!amount.isBlank()) {
                return new Extraction(amount, line);
            }
        }
        return new Extraction("", "");
    }

    private static List<String> normalizedLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String extractBestAmount(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        var matcher = AMOUNT_PATTERN.matcher(text);
        String last = "";
        while (matcher.find()) {
            String candidate = matcher.group();
            if (parseAmount(candidate) > 0) {
                last = candidate;
            }
        }
        return last;
    }

    private static String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record Extraction(String amount, String matchedText) {
        public boolean hasAmount() {
            return amount != null && !amount.isBlank();
        }
    }
}
