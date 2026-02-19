package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class VendorPdfParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern LETTER_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "gst", "gstin", "pan", "cin", "hsn", "sac", "invoice", "bill", "tax", "total", "subtotal");

    public List<ParsedItem> parseItems(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ParsedItem> items = new ArrayList<>();
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (isHeaderOrTotal(line)) {
                continue;
            }
            ParsedItem item = parseLine(line);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private ParsedItem parseLine(String line) {
        Matcher matcher = NUMBER_PATTERN.matcher(line);
        List<NumberMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new NumberMatch(matcher.start(), matcher.end(), matcher.group(1)));
        }
        if (matches.size() < 2) {
            return null;
        }
        String name = line.substring(0, matches.get(0).start()).trim();
        if (name.isEmpty()) {
            return null;
        }
        if (isInvalidItemName(name)) {
            return null;
        }
        double qty;
        double rate;
        double amount;
        if (matches.size() >= 3) {
            qty = parseNumber(matches.get(matches.size() - 3).value());
            rate = parseNumber(matches.get(matches.size() - 2).value());
            amount = parseNumber(matches.get(matches.size() - 1).value());
        } else {
            qty = parseNumber(matches.get(0).value());
            amount = parseNumber(matches.get(1).value());
            rate = qty > 0 ? amount / qty : amount;
        }
        if (qty <= 0 || amount <= 0) {
            return null;
        }
        return new ParsedItem(name, qty, rate, amount);
    }

    private boolean isHeaderOrTotal(String line) {
        String lowered = line.toLowerCase();
        if (lowered.contains("total")
                || lowered.contains("subtotal")
                || lowered.contains("tax")
                || lowered.contains("grand")) {
            return true;
        }
        if (lowered.contains("gstin")
                || lowered.contains("pan")
                || lowered.contains("cin")
                || lowered.contains("hsn")
                || lowered.contains("phone")
                || lowered.contains("mobile")
                || lowered.contains("address")) {
            return true;
        }
        if (lowered.contains("invoice") || lowered.contains("purchase order")) {
            return true;
        }
        return lowered.contains("item")
                && (lowered.contains("qty") || lowered.contains("quantity"))
                && (lowered.contains("rate") || lowered.contains("amount"));
    }

    private boolean isInvalidItemName(String rawName) {
        String normalized = rawName == null ? "" : rawName.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (BLOCKED_KEYWORDS.stream().anyMatch(lowered::contains)) {
            return true;
        }
        if (!LETTER_PATTERN.matcher(normalized).find()) {
            return true;
        }
        String compactLetters = normalized.replaceAll("[^A-Za-z]", "");
        if (compactLetters.length() <= 1) {
            return true;
        }
        if (normalized.length() <= 2) {
            return true;
        }
        return normalized.equalsIgnoreCase("no")
                || normalized.equalsIgnoreCase("qty")
                || normalized.equalsIgnoreCase("rate")
                || normalized.equalsIgnoreCase("amount");
    }

    private double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private record NumberMatch(int start, int end, String value) {
    }
}
