package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class VendorPdfParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)");
    private static final Pattern LETTER_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern HSN_LABELED_PATTERN = Pattern.compile("(?i)\\b(?:hsn|sac)\\b\\s*[:\\-]?\\s*(\\d{4,10})\\b");
    private static final Pattern DATE_DD_MM_YYYY_PATTERN = Pattern.compile("\\b(\\d{1,2})[\\-/.](\\d{1,2})[\\-/.](\\d{2,4})\\b");
    private static final Pattern DATE_YYYY_MM_DD_PATTERN = Pattern.compile("\\b(\\d{4})[\\-/.](\\d{1,2})[\\-/.](\\d{1,2})\\b");
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "gst", "gstin", "pan", "cin", "hsn", "sac", "invoice", "bill", "tax", "total", "subtotal");

    public List<ParsedItem> parseItems(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ParsedItem> items = new ArrayList<>();
        String pendingName = null;
        for (String rawLine : text.replace('\f', '\n').split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (isHeaderOrTotal(line)) {
                continue;
            }
            String normalized = normalizeLine(line);
            if (looksLikeStandaloneName(normalized)) {
                pendingName = normalized;
                continue;
            }

            ParsedItem item = parseLine(normalized);
            if (item == null) {
                item = parseGstNumericRow(normalized, pendingName);
            }
            if (item != null) {
                items.add(item);
                pendingName = null;
            }
        }
        return deduplicateItems(items);
    }

    public String extractBillDate(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\f', '\n');
        for (String rawLine : normalized.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String lowered = line.toLowerCase(Locale.ROOT);
            if ((lowered.contains("invoice") && lowered.contains("date"))
                    || (lowered.contains("bill") && lowered.contains("date"))
                    || lowered.startsWith("date")) {
                Optional<LocalDate> parsed = extractFirstDateFromText(line);
                if (parsed.isPresent()) {
                    return parsed.get().toString();
                }
            }
        }
        return extractFirstDateFromText(normalized).map(LocalDate::toString).orElse("");
    }

    public ParseDiagnostics diagnose(String text) {
        if (text == null) {
            return new ParseDiagnostics(0, 0, 0);
        }
        String normalized = text.replace('\f', '\n');
        String[] lines = normalized.split("\\r?\\n");
        int candidates = 0;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || isHeaderOrTotal(line)) {
                continue;
            }
            if (countNumberTokens(line) >= 2 && LETTER_PATTERN.matcher(line).find()) {
                candidates++;
            }
        }
        return new ParseDiagnostics(normalized.length(), lines.length, candidates);
    }

    private ParsedItem parseLine(String line) {
        String normalized = normalizeLine(line);
        Matcher matcher = NUMBER_PATTERN.matcher(normalized);
        List<NumberMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new NumberMatch(matcher.start(), matcher.end(), matcher.group(1)));
        }
        if (matches.size() < 2) {
            return null;
        }
        int qtyIndex = matches.size() >= 3 ? matches.size() - 3 : 0;
        String name = normalized.substring(0, matches.get(qtyIndex).start()).trim();
        name = stripLeadingIndex(name);
        name = stripTrailingColumnLabels(name);
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
        if (qty > 100_000) {
            return null;
        }
        if (qty <= 0) {
            return null;
        }
        if (amount <= 0 && rate > 0) {
            amount = rate * qty;
        }
        if (rate <= 0 && amount > 0) {
            rate = amount / qty;
        }
        if (rate <= 0 && amount <= 0) {
            return null;
        }
        if (qty > 0 && rate > 0 && amount > 0) {
            double expected = qty * rate;
            double denom = Math.max(1.0, amount);
            double score = Math.abs(expected - amount) / denom;
            // Avoid parsing header/address/date lines that just happen to contain 3 numbers.
            if (score > 0.25) {
                return null;
            }
        }
        return new ParsedItem(name, qty, rate, amount);
    }

    private ParsedItem parseGstNumericRow(String line, String pendingName) {
        String normalized = normalizeLine(line);
        List<String> tokens = List.of(normalized.split(" "));
        List<String> numericTokens = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (NUMBER_PATTERN.matcher(token).matches()) {
                numericTokens.add(token);
            }
        }
        if (numericTokens.size() < 4) {
            return null;
        }

        String srToken = numericTokens.get(0);
        double srNo = parseNumber(srToken);
        if (!isLikelySerialNumber(srToken, srNo)) {
            return null;
        }

        String hsnToken = findLikelyHsnToken(numericTokens);
        if (hsnToken == null) {
            return null;
        }

        List<Double> middle = new ArrayList<>();
        boolean skippedFirst = false;
        for (String token : numericTokens) {
            if (!skippedFirst) {
                skippedFirst = true;
                continue;
            }
            if (token.equals(hsnToken)) {
                continue;
            }
            middle.add(parseNumber(token));
        }
        if (middle.size() < 2) {
            return null;
        }

        ParsedNumbers parsed = inferQtyRateAmount(middle);
        if (parsed == null || parsed.qty <= 0) {
            return null;
        }
        if (parsed.qty > 100_000) {
            return null;
        }

        String name = pendingName == null ? "" : pendingName.trim();
        if (name.isBlank()) {
            name = buildFallbackName(hsnToken, (int) Math.round(srNo));
        }
        if (isInvalidItemName(name)) {
            return null;
        }
        return new ParsedItem(name, parsed.qty, parsed.rate, parsed.amount, normalizeHsn(hsnToken));
    }

    private ParsedNumbers inferQtyRateAmount(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Double> candidates = values.stream().filter(v -> v != null && Double.isFinite(v) && v >= 0).toList();
        if (candidates.size() < 2) {
            return null;
        }

        if (candidates.size() == 2) {
            double qty = candidates.get(0);
            double amount = candidates.get(1);
            if (qty <= 0 && amount > 0) {
                qty = 1;
            }
            double rate = qty > 0 ? amount / qty : amount;
            return new ParsedNumbers(qty, rate, amount);
        }

        double bestScore = Double.POSITIVE_INFINITY;
        ParsedNumbers best = null;
        for (int i = 0; i < candidates.size(); i++) {
            double qty = candidates.get(i);
            if (qty <= 0) {
                continue;
            }
            for (int j = 0; j < candidates.size(); j++) {
                if (j == i) {
                    continue;
                }
                double rate = candidates.get(j);
                if (rate <= 0) {
                    continue;
                }
                for (int k = 0; k < candidates.size(); k++) {
                    if (k == i || k == j) {
                        continue;
                    }
                    double amount = candidates.get(k);
                    if (amount <= 0) {
                        continue;
                    }
                    double expected = qty * rate;
                    double denom = Math.max(1.0, amount);
                    double score = Math.abs(expected - amount) / denom;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new ParsedNumbers(qty, rate, amount);
                    }
                }
            }
        }

        if (best != null && bestScore <= 0.05) {
            return best;
        }

        double qty = candidates.get(0);
        double amount = candidates.get(1);
        double rate = qty > 0 ? amount / qty : amount;
        if (candidates.size() >= 3 && candidates.get(2) > 0) {
            rate = candidates.get(2);
            if (amount <= 0) {
                amount = rate * qty;
            }
        }
        return new ParsedNumbers(qty, rate, amount);
    }

    private boolean isLikelySerialNumber(String token, double value) {
        if (token == null) {
            return false;
        }
        String cleaned = token.replace(",", "").trim();
        if (!DIGITS_ONLY_PATTERN.matcher(cleaned).matches()) {
            return false;
        }
        if (!Double.isFinite(value) || value <= 0) {
            return false;
        }
        return value <= 9999;
    }

    private String findLikelyHsnToken(List<String> numericTokens) {
        if (numericTokens == null || numericTokens.isEmpty()) {
            return null;
        }
        for (int idx = numericTokens.size() - 1; idx >= 0; idx--) {
            String token = numericTokens.get(idx);
            String digits = token == null ? "" : token.replaceAll("\\D", "");
            if (digits.length() >= 4 && digits.length() <= 10 && DIGITS_ONLY_PATTERN.matcher(digits).matches()) {
                return token;
            }
        }
        return null;
    }

    private String buildFallbackName(String hsnToken, int srNo) {
        String digits = hsnToken == null ? "" : hsnToken.replaceAll("\\D", "");
        String hsn = digits.isBlank() ? "UNKNOWN" : digits;
        if (srNo > 0) {
            return "HSN " + hsn + " (Line " + srNo + ")";
        }
        return "HSN " + hsn;
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
        if (!lowered.startsWith("hsn ") && BLOCKED_KEYWORDS.stream().anyMatch(lowered::contains)) {
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

    private String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        return line
                .replace('\u00A0', ' ')
                .replace("₹", " ")
                .replace("Rs.", " ")
                .replace("INR", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean looksLikeStandaloneName(String normalizedLine) {
        if (normalizedLine == null) {
            return false;
        }
        String line = normalizedLine.trim();
        if (line.isEmpty() || isHeaderOrTotal(line)) {
            return false;
        }
        if (!LETTER_PATTERN.matcher(line).find()) {
            return false;
        }
        return countNumberTokens(line) < 2;
    }

    private String stripLeadingIndex(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        cleaned = cleaned.replaceAll("^(?:\\(?\\d+\\)?[\\).:-]?\\s+)+", "");
        return cleaned.trim();
    }

    private String stripTrailingColumnLabels(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        for (int i = 0; i < 4; i++) {
            String next = cleaned.replaceAll("(?i)\\s*\\b(qty|quantity|rate|amount)\\b\\s*[:\\-]*\\s*$", "").trim();
            if (next.equals(cleaned)) {
                break;
            }
            cleaned = next;
        }
        return cleaned;
    }

    private double parseNumber(String value) {
        try {
            if (value == null) {
                return 0.0;
            }
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private int countNumberTokens(String line) {
        Matcher matcher = NUMBER_PATTERN.matcher(line == null ? "" : line);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private Optional<LocalDate> extractFirstDateFromText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = text.replace('\u00A0', ' ').trim();
        Matcher ymd = DATE_YYYY_MM_DD_PATTERN.matcher(normalized);
        if (ymd.find()) {
            LocalDate date = safeDate(
                    parseIntSafe(ymd.group(1)),
                    parseIntSafe(ymd.group(2)),
                    parseIntSafe(ymd.group(3)));
            if (date != null) {
                return Optional.of(date);
            }
        }

        Matcher dmy = DATE_DD_MM_YYYY_PATTERN.matcher(normalized);
        while (dmy.find()) {
            Integer first = parseIntSafe(dmy.group(1));
            Integer second = parseIntSafe(dmy.group(2));
            Integer year = parseYearSafe(dmy.group(3));
            if (first == null || second == null || year == null) {
                continue;
            }
            int day = first;
            int month = second;
            if (day <= 12 && month <= 12) {
                // Ambiguous. Prefer dd/MM for Indian invoices.
            } else if (month > 12 && day <= 12) {
                int tmp = day;
                day = month;
                month = tmp;
            }
            LocalDate date = safeDate(year, month, day);
            if (date != null) {
                return Optional.of(date);
            }
        }
        return Optional.empty();
    }

    private Integer parseIntSafe(String value) {
        try {
            if (value == null || value.isBlank()) {
                return null;
            }
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private Integer parseYearSafe(String yearToken) {
        Integer year = parseIntSafe(yearToken);
        if (year == null) {
            return null;
        }
        if (year < 100) {
            return 2000 + year;
        }
        return year;
    }

    private LocalDate safeDate(Integer year, Integer month, Integer day) {
        if (year == null || month == null || day == null) {
            return null;
        }
        try {
            if (year < 1900 || year > 2100) {
                return null;
            }
            return LocalDate.of(year, month, day);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<ParsedItem> deduplicateItems(List<ParsedItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? List.of() : items;
        }
        Map<String, MutableParsedItem> merged = new LinkedHashMap<>();
        for (ParsedItem item : items) {
            if (item == null) {
                continue;
            }
            String hsn = normalizeHsn(item.hsn());
            String name = item.name() == null ? "" : item.name().trim();
            if (hsn.isBlank()) {
                hsn = extractHsnFromName(name);
            }
            String key = buildDedupKey(name, hsn);
            MutableParsedItem current = merged.get(key);
            if (current == null) {
                merged.put(key, new MutableParsedItem(name, hsn, item.qty(), item.rate(), item.amount()));
            } else {
                current.qty += item.qty();
                current.rate = Math.max(current.rate, item.rate());
                current.amount = Math.max(current.amount, item.amount());
                if (name.length() > current.name.length()) {
                    current.name = name;
                }
                if (current.hsn.isBlank() && !hsn.isBlank()) {
                    current.hsn = hsn;
                }
            }
        }
        List<ParsedItem> result = new ArrayList<>(merged.size());
        for (MutableParsedItem item : merged.values()) {
            double qty = item.qty;
            double rate = item.rate;
            double amount = item.amount;
            if (qty > 0 && amount <= 0 && rate > 0) {
                amount = rate * qty;
            }
            if (qty > 0 && rate <= 0 && amount > 0) {
                rate = amount / qty;
            }
            result.add(new ParsedItem(item.name, qty, rate, amount, item.hsn.isBlank() ? null : item.hsn));
        }
        return result;
    }

    private String buildDedupKey(String name, String hsn) {
        String cleanedName = normalizeForKey(removeHsnSegment(name));
        String cleanedHsn = normalizeHsn(hsn);
        if (!cleanedHsn.isBlank()) {
            if (cleanedName.isBlank()) {
                return "hsn:" + cleanedHsn;
            }
            return cleanedName + "|hsn:" + cleanedHsn;
        }
        return cleanedName.isBlank() ? normalizeForKey(name) : cleanedName;
    }

    private String extractHsnFromName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        Matcher matcher = HSN_LABELED_PATTERN.matcher(name);
        if (matcher.find()) {
            return normalizeHsn(matcher.group(1));
        }
        return "";
    }

    private String removeHsnSegment(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return HSN_LABELED_PATTERN.matcher(name).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private String normalizeHsn(String token) {
        if (token == null) {
            return "";
        }
        String digits = token.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return "";
        }
        if (digits.length() < 4 || digits.length() > 10) {
            return "";
        }
        return DIGITS_ONLY_PATTERN.matcher(digits).matches() ? digits : "";
    }

    private String normalizeForKey(String value) {
        if (value == null) {
            return "";
        }
        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record NumberMatch(int start, int end, String value) {
    }

    private record ParsedNumbers(double qty, double rate, double amount) {
    }

    private static final class MutableParsedItem {
        private String name;
        private String hsn;
        private double qty;
        private double rate;
        private double amount;

        private MutableParsedItem(String name, String hsn, double qty, double rate, double amount) {
            this.name = name == null ? "" : name;
            this.hsn = hsn == null ? "" : hsn;
            this.qty = qty;
            this.rate = rate;
            this.amount = amount;
        }
    }

    public record ParseDiagnostics(int textLength, int lineCount, int candidateLines) {
    }
}
