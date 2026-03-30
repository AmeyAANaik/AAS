package com.aas.mw.service;

import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.NativeLayoutSample;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.NativeLayoutTable;
import com.aas.mw.dto.NativeLayoutTableRow;
import com.aas.mw.dto.NativeMappedInvoiceRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NativeLayoutInvoiceService {

    private static final Pattern MULTISPACE = Pattern.compile("\\s{2,}");
    private static final Pattern SUMMARY_LABEL = Pattern.compile(
            "(?i)\\b(total|grand total|bill amount|transport|rounding off|round off|cgst|sgst|igst|taxable value)\\b");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("([0-9][0-9,]*\\.\\d{2})");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DMY_DASH_PATTERN = Pattern.compile("\\b(\\d{1,2}-[A-Za-z]{3}-\\d{2,4})\\b");

    private final ObjectMapper objectMapper;
    private final String configuredPythonCommand;

    public NativeLayoutInvoiceService(
            ObjectMapper objectMapper,
            @Value("${app.pdf.python-command:}") String configuredPythonCommand) {
        this.objectMapper = objectMapper;
        this.configuredPythonCommand = configuredPythonCommand == null ? "" : configuredPythonCommand.trim();
    }

    public NativeLayoutSample extractSample(String fileName, String fileUrl, byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Sample PDF is required.");
        }
        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("aas-native-layout-");
            Path pdfPath = workingDir.resolve("invoice.pdf");
            Path textPath = workingDir.resolve("invoice-layout.txt");
            Files.write(pdfPath, pdfBytes);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "pdftotext",
                    "-layout",
                    pdfPath.toString(),
                    textPath.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0 && !Files.exists(textPath)) {
                throw new IllegalStateException("Unable to extract native PDF layout text: " + summarize(output));
            }
            String layoutText = Files.exists(textPath)
                    ? Files.readString(textPath, StandardCharsets.UTF_8)
                    : "";
            String normalized = normalizeLayoutText(layoutText);
            List<String> lines = normalized.isBlank() ? List.of() : List.of(normalized.split("\\r?\\n"));
            List<NativeLayoutTable> tables = detectTables(lines);
            List<String> summaryLines = detectSummaryLines(lines);
            List<String> summaryLabels = detectSummaryLabels(summaryLines);
            int pageCount = countPages(layoutText);
            return new NativeLayoutSample(
                    fileName == null ? "" : fileName,
                    fileUrl == null ? "" : fileUrl,
                    normalized,
                    pageCount <= 0 ? 1 : pageCount,
                    tables,
                    summaryLabels,
                    summaryLines);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to prepare PDF for native layout extraction.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Native PDF layout extraction was interrupted.", ex);
        } finally {
            deleteQuietly(workingDir);
        }
    }

    public String buildMappingPromptInput(
            String vendorId,
            String vendorName,
            NativeLayoutSample sample,
            List<InvoiceTemplateModelProperties.TemplateField> itemFields,
            List<InvoiceTemplateModelProperties.TemplateField> summaryFields) {
        NativeLayoutTable primaryTable = mergeCompatibleTables(sample.tables(), "");
        List<String> headers = primaryTable == null ? List.of() : primaryTable.headers();
        int totalTableRows = primaryTable == null ? 0 : primaryTable.rows().size();
        List<NativeLayoutTableRow> rows = primaryTable == null ? List.of() : primaryTable.rows();
        List<String> rawTableLines = primaryTable == null ? List.of() : primaryTable.rawLines();
        return """
                {
                  "vendorId": "%s",
                  "vendorName": "%s",
                  "requiredItemFields": %s,
                  "requiredSummaryFields": %s,
                  "tableHeaders": %s,
                  "totalTableRows": %d,
                  "sampleRows": %s,
                  "summaryLabels": %s,
                  "summaryLines": %s,
                  "tablePreviewLines": %s,
                  "layoutPreview": %s
                }
                """.formatted(
                blankSafe(vendorId),
                blankSafe(vendorName),
                describeFieldKeys(itemFields),
                describeFieldKeys(summaryFields),
                headers,
                totalTableRows,
                describeRows(rows),
                sample.summaryLabels(),
                sample.summaryLines().stream().limit(10).toList(),
                rawTableLines,
                buildLayoutPreview(sample.layoutText()));
    }

    public String buildDeterministicRulePromptInput(
            String vendorId,
            String vendorName,
            NativeLayoutSample sample,
            List<InvoiceFieldMappingService.FieldMapping> itemMappings,
            List<InvoiceFieldMappingService.FieldMapping> summaryMappings) {
        List<Map<String, Object>> candidateBlocks = new ArrayList<>();
        for (NativeLayoutTable table : sample.tables()) {
            candidateBlocks.add(Map.of(
                    "blockId", table.tableId(),
                    "blockType", "table_candidate",
                    "headers", table.headers(),
                    "sampleRows", table.rows().stream().map(NativeLayoutTableRow::cells).toList(),
                    "rawPreview", table.rawLines()));
        }
        candidateBlocks.add(Map.of(
                "blockId", "summary_1",
                "blockType", "summary_lines_candidate",
                "lines", sample.summaryLines().stream().limit(20).toList()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vendorId", blankSafe(vendorId));
        payload.put("vendorName", blankSafe(vendorName));
        payload.put("layoutMapping", Map.of(
                "itemMappings", toMappingPreview(itemMappings),
                "summaryMappings", toMappingPreview(summaryMappings)));
        payload.put("candidateBlocks", candidateBlocks);
        payload.put("summaryLabels", sample.summaryLabels());
        payload.put("layoutPreview", buildLayoutPreview(sample.layoutText()));
        payload.put("summaryPreview", sample.summaryLines().stream().limit(20).toList());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to build deterministic rule prompt input.", ex);
        }
    }

    public List<NativeMappedInvoiceRow> mapRows(
            NativeLayoutTable table,
            List<InvoiceFieldMappingService.FieldMapping> itemMappings,
            InvoiceFieldMappingService.RowRules rowRules,
            Map<String, String> fieldParsingRules,
            Map<String, String> parsingHints) {
        if (table == null || table.rows().isEmpty() || itemMappings == null || itemMappings.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> indexes = headerIndexes(table.headers());
        List<NativeMappedInvoiceRow> rows = new ArrayList<>();
        for (NativeLayoutTableRow row : filterRows(table.rows(), rowRules)) {
            List<String> alignedCells = alignRowCells(table.headers(), row.cells());
            Map<String, String> sourceValues = new LinkedHashMap<>();
            for (InvoiceFieldMappingService.FieldMapping mapping : itemMappings) {
                if (!mapping.present()) {
                    continue;
                }
                Integer index = indexes.get(normalizeHeader(mapping.sourceLabel()));
                String value = index == null || index < 0 || index >= alignedCells.size()
                        ? findValueByLooseHeader(table.headers(), alignedCells, mapping.sourceLabel())
                        : alignedCells.get(index);
                sourceValues.put(mapping.targetField(), value);
            }
            QtyUom qtyUom = parseQtyUom(sourceValues.get("qty"), sourceValues.get("uom"));
            Double gstPercent = parseValueByRule(sourceValues.get("gst"), ruleFor(fieldParsingRules, "gst"));
            Double mappedRate = parseValueByRule(sourceValues.get("rate"), ruleFor(fieldParsingRules, "rate"));
            RatePair ratePair = resolveRatePair(
                    table.headers(),
                    alignedCells,
                    fieldParsingRules,
                    parsingHints,
                    gstPercent,
                    mappedRate);
            rows.add(new NativeMappedInvoiceRow(
                    textValue(sourceValues.get("item_name")),
                    textValue(sourceValues.get("item_id")),
                    qtyUom.qty(),
                    qtyUom.uom(),
                    mappedRate,
                    parseValueByRule(sourceValues.get("mrp"), ruleFor(fieldParsingRules, "mrp")),
                    gstPercent,
                    parseValueByRule(sourceValues.get("total"), ruleFor(fieldParsingRules, "total")),
                    ratePair.beforeTax(),
                    ratePair.afterTax(),
                    sourceValues,
                    row.rawText()));
        }
        return rows.stream()
                .filter(row -> row.itemName() != null && !row.itemName().isBlank())
                .toList();
    }

    private List<String> alignRowCells(List<String> headers, List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        List<String> aligned = new ArrayList<>(cells);
        if (headers == null || headers.isEmpty()) {
            return aligned;
        }
        if (aligned.size() >= headers.size()) {
            return aligned;
        }

        int serialIndex = indexOfHeader(headers, "sl");
        int descriptionIndex = indexOfHeader(headers, "description of goods");
        if (serialIndex >= 0 && descriptionIndex == serialIndex + 1 && !aligned.isEmpty()) {
            splitLeadingSerialAndDescription(aligned);
        }

        int gstIndex = indexOfHeader(headers, "gst");
        int quantityIndex = indexOfHeader(headers, "quantity");
        if (gstIndex >= 0 && quantityIndex == gstIndex + 1) {
            splitCombinedTaxAndQty(aligned, gstIndex, headers.size());
        }

        return aligned;
    }

    private int indexOfHeader(List<String> headers, String targetHeader) {
        String normalizedTarget = normalizeHeader(targetHeader);
        for (int index = 0; index < headers.size(); index++) {
            if (normalizeHeader(headers.get(index)).equals(normalizedTarget)) {
                return index;
            }
        }
        return -1;
    }

    private void splitLeadingSerialAndDescription(List<String> cells) {
        if (cells.isEmpty()) {
            return;
        }
        String first = blankSafe(cells.getFirst());
        java.util.regex.Matcher matcher = Pattern.compile("^(\\d+)\\s+(.*)$").matcher(first);
        if (!matcher.matches()) {
            return;
        }
        String serial = matcher.group(1).trim();
        String description = matcher.group(2).trim();
        if (description.isBlank()) {
            return;
        }
        cells.set(0, serial);
        cells.add(1, description);
    }

    private void splitCombinedTaxAndQty(List<String> cells, int gstIndex, int headerCount) {
        if (gstIndex < 0 || gstIndex >= cells.size()) {
            return;
        }
        if (cells.size() >= headerCount) {
            return;
        }
        String combined = blankSafe(cells.get(gstIndex));
        java.util.regex.Matcher matcher = Pattern.compile(
                        "^([0-9]+(?:\\.[0-9]+)?\\s*%?)\\s+([0-9][0-9,]*\\.?[0-9]*\\s+[A-Za-z]+.*)$")
                .matcher(combined);
        if (!matcher.matches()) {
            return;
        }
        cells.set(gstIndex, matcher.group(1).trim());
        cells.add(gstIndex + 1, matcher.group(2).trim());
    }

    public String buildProfileJson(
            String profileId,
            String label,
            String vendorName,
            String description,
            Map<String, Object> fieldMapping,
            Map<String, Object> deterministicRules,
            Map<String, Object> validation,
            String sampleFileName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "native_layout_mapping");
        payload.put("version", 1);
        payload.put("engine", Map.of(
                "type", "native_layout",
                "reader", "pdftotext_layout"));
        payload.put("profile", Map.of(
                "id", blankSafe(profileId).trim(),
                "label", blankSafe(label).trim(),
                "vendorName", blankSafe(vendorName).trim(),
                "description", blankSafe(description).trim()));
        if (fieldMapping != null && !fieldMapping.isEmpty()) {
            payload.put("fieldMapping", fieldMapping);
        }
        if (deterministicRules != null && !deterministicRules.isEmpty()) {
            payload.put("deterministicRules", deterministicRules);
        }
        if (validation != null && !validation.isEmpty()) {
            Map<String, Object> validationCopy = new LinkedHashMap<>(validation);
            if (sampleFileName != null && !sampleFileName.isBlank()) {
                validationCopy.put("sampleFileName", sampleFileName);
            }
            payload.put("validation", validationCopy);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to build native layout mapping profile JSON.", ex);
        }
    }

    public StoredProfile parseStoredProfile(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (!"native_layout_mapping".equals(asText(map.get("kind")))) {
                return null;
            }
            Map<String, Object> profileMap = asMap(map.get("profile"));
            Map<String, Object> fieldMapping = asMap(map.get("fieldMapping"));
            if (fieldMapping.isEmpty()) {
                return null;
            }
            Map<String, Object> deterministicRules = asMap(map.get("deterministicRules"));
            return new StoredProfile(
                    asText(profileMap.get("id")),
                    asText(profileMap.get("label")),
                    asText(profileMap.get("vendorName")),
                    asText(profileMap.get("description")),
                    readMappings(fieldMapping.get("itemMappings")),
                    readMappings(fieldMapping.get("summaryMappings")),
                    asText(fieldMapping.get("notes")),
                    asText(fieldMapping.get("generatorType")),
                    asText(fieldMapping.get("generatorModel")),
                    asText(deterministicRules.get("primaryItemTableBlockId")),
                    asText(deterministicRules.get("gstSummaryBlockId")),
                    readStringMap(deterministicRules.get("summaryFieldRoles")),
                    readStringMap(deterministicRules.get("parsingHints")),
                    readStringMap(deterministicRules.get("fieldParsingRules")),
                    readStringList(deterministicRules.get("skipLabels")),
                    readStringList(deterministicRules.get("headerContinuationLabels")));
        } catch (Exception ex) {
            return null;
        }
    }

    public ExtractionResult extract(byte[] pdfBytes, StoredProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("native layout profile is required.");
        }
        NativeLayoutSample sample = extractSample("", "", pdfBytes);
        NativeLayoutTable table = mergeCompatibleTables(sample.tables(), profile.primaryItemTableBlockId());
        List<NativeMappedInvoiceRow> mappedRows = mapRows(
                table,
                applyPreferredRateColumn(profile.itemMappings(), profile.parsingHints()),
                new InvoiceFieldMappingService.RowRules(profile.skipLabels(), profile.headerContinuationLabels()),
                profile.fieldParsingRules(),
                profile.parsingHints());
        List<ParsedItem> parsedItems = mappedRows.stream()
                .filter(row -> row.itemName() != null && row.qty() != null && row.rate() != null && row.total() != null)
                .map(row -> new ParsedItem(
                        row.itemName(),
                        row.qty(),
                        row.rate(),
                        row.total(),
                        row.itemId(),
                        row.gstPercent(),
                        row.uom(),
                        row.mrp(),
                        row.rateBeforeTax(),
                        row.rateAfterTax()))
                .toList();
        List<Integer> extractedSerials = mappedRows.stream()
                .filter(row -> row.itemName() != null && row.qty() != null && row.rate() != null && row.total() != null)
                .map(NativeLayoutInvoiceService::extractLeadingSerial)
                .filter(Objects::nonNull)
                .toList();
        String invoiceNumber = firstNonBlank(
                contextualInvoiceNumber(sample.layoutText()),
                firstMatch(sample.layoutText(), Pattern.compile("(?im)invoice\\s*no\\.?\\s*[:#-]?\\s*([A-Za-z0-9\\-/]+)")));
        String invoiceDate = firstNonBlank(
                contextualInvoiceDate(sample.layoutText()),
                firstMatch(sample.layoutText(), Pattern.compile("(?im)(?:invoice\\s+date|dated|date)\\s*[:#-]?\\s*(\\d{4}-\\d{2}-\\d{2})")),
                firstMatch(sample.layoutText(), Pattern.compile("(?im)(?:invoice\\s+date|dated|date)\\s*[:#-]?\\s*(\\d{1,2}-[A-Za-z]{3}-\\d{2,4})")),
                firstRegex(sample.layoutText(), ISO_DATE_PATTERN),
                firstRegex(sample.layoutText(), DMY_DASH_PATTERN));
        String finalAmount = summaryAmount(sample, profile, "final_bill_amount");
        String transportCharge = summaryAmount(sample, profile, "transport_charge");
        return new ExtractionResult(
                parsedItems,
                invoiceNumber,
                invoiceDate,
                finalAmount,
                transportCharge,
                sample.layoutText(),
                table == null ? List.of() : table.rawLines(),
                extractedSerials);
    }

    private static Integer extractLeadingSerial(NativeMappedInvoiceRow row) {
        if (row == null || row.rawText() == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("^\\s*(\\d{1,3})\\b").matcher(row.rawText());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<NativeLayoutTable> detectTables(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<NativeLayoutTable> tables = new ArrayList<>();
        int tableCounter = 1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!looksLikeHeader(line)) {
                continue;
            }
            List<String> headers = parseCells(line);
            List<String> rawLines = new ArrayList<>();
            rawLines.add(line);
            List<NativeLayoutTableRow> rows = new ArrayList<>();
            int rowNumber = 1;
            for (int cursor = index + 1; cursor < lines.size(); cursor++) {
                String raw = lines.get(cursor);
                if (raw.isBlank()) {
                    if (!rows.isEmpty()) {
                        break;
                    }
                    continue;
                }
                if (looksLikeHeader(raw) && !rows.isEmpty()) {
                    break;
                }
                if (SUMMARY_LABEL.matcher(raw).find() && !rows.isEmpty()) {
                    break;
                }
                List<String> cells = parseCells(raw);
                if (cells.size() < Math.max(3, Math.min(headers.size(), 4))) {
                    if (!rows.isEmpty() && looksLikeContinuationLine(raw)) {
                        NativeLayoutTableRow continued = appendContinuation(headers, rows.getLast(), raw);
                        rows.set(rows.size() - 1, continued);
                        rawLines.add(raw);
                        continue;
                    }
                    if (!rows.isEmpty()) {
                        break;
                    }
                    continue;
                }
                rawLines.add(raw);
                rows.add(new NativeLayoutTableRow(rowNumber++, cells, raw.trim()));
            }
            if (!rows.isEmpty()) {
                tables.add(new NativeLayoutTable("table-" + tableCounter++, headers, rows, rawLines));
            }
        }
        return tables;
    }

    private boolean looksLikeContinuationLine(String raw) {
        String text = blankSafe(raw).trim();
        if (text.isBlank()) {
            return false;
        }
        if (looksLikeHeader(text) || SUMMARY_LABEL.matcher(text).find()) {
            return false;
        }
        // A line that starts with a serial number (e.g. "32 SFK SABUDANA") is a new item row, not a continuation.
        // Previously this rejected any line containing 2+ digit numbers, which incorrectly broke tables
        // at continuation lines like "OIL 13kg", "POWDER 500GM", "680GMS".
        if (text.matches("^\\d{1,3}\\s+.*")) {
            return false;
        }
        return true;
    }

    private NativeLayoutTableRow appendContinuation(
            List<String> headers,
            NativeLayoutTableRow row,
            String raw) {
        List<String> cells = new ArrayList<>(row.cells());
        if (cells.isEmpty()) {
            return row;
        }
        int descriptionIndex = descriptionIndex(headers, cells);
        String continuation = blankSafe(raw).trim();
        if (continuation.isBlank()) {
            return row;
        }
        String base = blankSafe(cells.get(descriptionIndex)).trim();
        cells.set(descriptionIndex, (base + " " + continuation).trim());
        return new NativeLayoutTableRow(row.rowNumber(), cells, (row.rawText() + " " + continuation).trim());
    }

    private int descriptionIndex(List<String> headers, List<String> cells) {
        int particularsIndex = indexOfMatchingHeader(headers, "particulars");
        if (particularsIndex >= 0 && particularsIndex < cells.size()) {
            return particularsIndex;
        }
        int descriptionIndex = indexOfMatchingHeader(headers, "description of goods");
        if (descriptionIndex >= 0 && descriptionIndex < cells.size()) {
            return descriptionIndex;
        }
        return Math.min(cells.size() - 1, 1);
    }

    private int indexOfMatchingHeader(List<String> headers, String needle) {
        String normalizedNeedle = normalizeHeader(needle);
        for (int index = 0; index < headers.size(); index++) {
            String normalizedHeader = normalizeHeader(headers.get(index));
            if (normalizedHeader.equals(normalizedNeedle)
                    || normalizedHeader.contains(normalizedNeedle)
                    || normalizedNeedle.contains(normalizedHeader)) {
                return index;
            }
        }
        return -1;
    }

    private boolean looksLikeHeader(String line) {
        String lowered = blankSafe(line).toLowerCase(Locale.ROOT);
        return (lowered.contains("description of goods") || lowered.contains("item description") || lowered.contains("particulars"))
                && (lowered.contains("hsn") || lowered.contains("sac"))
                && (lowered.contains("qty") || lowered.contains("quantity"));
    }

    private List<String> parseCells(String line) {
        String normalized = blankSafe(line).replace('\t', ' ').trim();
        if (normalized.contains("|")) {
            return List.of(normalized.split("\\s*\\|\\s*"));
        }
        return List.of(MULTISPACE.split(normalized));
    }

    private List<String> detectSummaryLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> summaryLines = new ArrayList<>();
        for (String line : lines) {
            if (SUMMARY_LABEL.matcher(line).find()) {
                summaryLines.add(line.trim());
            }
        }
        return summaryLines;
    }

    private List<String> detectSummaryLabels(List<String> lines) {
        Set<String> labels = new LinkedHashSet<>();
        for (String line : lines) {
            String lowered = line.toLowerCase(Locale.ROOT);
            if (lowered.contains("transport")) {
                labels.add("Transport");
            }
            if (lowered.contains("grand total") || lowered.startsWith("total") || lowered.contains("bill amount")) {
                labels.add("Total");
            }
            if (lowered.contains("taxable value")) {
                labels.add("Taxable Value");
            }
            if (lowered.contains("cgst")) {
                labels.add("CGST");
            }
            if (lowered.contains("sgst")) {
                labels.add("SGST");
            }
            if (lowered.contains("igst")) {
                labels.add("IGST");
            }
            if (lowered.contains("round off") || lowered.contains("rounding off")) {
                labels.add("Round Off");
            }
        }
        return new ArrayList<>(labels);
    }

    private Map<String, Integer> headerIndexes(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            indexes.put(normalizeHeader(headers.get(index)), index);
        }
        return indexes;
    }

    private String normalizeHeader(String value) {
        return blankSafe(value).replaceAll("[^A-Za-z0-9]+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private String findValueByLooseHeader(List<String> headers, List<String> cells, String header) {
        String normalizedTarget = normalizeHeader(header);
        if (normalizedTarget.isBlank()) {
            return "";
        }
        for (int index = 0; index < headers.size() && index < cells.size(); index++) {
            String normalizedHeader = normalizeHeader(headers.get(index));
            if (normalizedHeader.equals(normalizedTarget)
                    || normalizedHeader.contains(normalizedTarget)
                    || normalizedTarget.contains(normalizedHeader)) {
                return cells.get(index);
            }
        }
        return "";
    }

    private List<NativeLayoutTableRow> filterRows(
            List<NativeLayoutTableRow> rows,
            InvoiceFieldMappingService.RowRules rowRules) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        if (rowRules == null) {
            return rows;
        }
        List<String> skipLabels = normalizeLabels(rowRules.skipLabels());
        List<String> headerLabels = normalizeLabels(rowRules.headerContinuationLabels());
        List<NativeLayoutTableRow> filtered = new ArrayList<>();
        for (NativeLayoutTableRow row : rows) {
            String joined = normalizeRowText(row);
            if (joined.isBlank()) {
                continue;
            }
            if (containsAny(joined, skipLabels)) {
                continue;
            }
            if (containsAny(joined, headerLabels) && row.cells().size() <= 2) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private String normalizeRowText(NativeLayoutTableRow row) {
        return row.cells().stream()
                .filter(Objects::nonNull)
                .map(this::normalizeHeader)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private List<String> normalizeLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        return labels.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeHeader)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean containsAny(String joined, List<String> labels) {
        for (String label : labels) {
            if (!label.isBlank() && joined.contains(label)) {
                return true;
            }
        }
        return false;
    }

    private QtyUom parseQtyUom(String qtyText, String uomText) {
        String qtyValue = blankSafe(qtyText);
        String explicitUom = textValue(uomText);
        if (qtyValue.isBlank()) {
            return new QtyUom(null, explicitUom);
        }
        String cleaned = qtyValue.replace(',', ' ').trim();
        String[] parts = cleaned.split("\\s+");
        if (parts.length >= 2) {
            Double qty = parseAmount(parts[0]);
            String uom = explicitUom != null && !explicitUom.isBlank() ? explicitUom : parts[1];
            return new QtyUom(qty, textValue(uom));
        }
        return new QtyUom(parseAmount(cleaned), explicitUom);
    }

    private Double parseAmount(String raw) {
        String text = blankSafe(raw)
                .replace("%", "")
                .replace(",", "")
                .replace("₹", "")
                .replace("ī", "")
                .trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseValueByRule(String raw, String rule) {
        String normalizedRule = blankSafe(rule).toLowerCase(Locale.ROOT);
        return switch (normalizedRule) {
            case "integer" -> parseInteger(raw);
            case "percentage", "decimal_amount", "number_with_uom", "text", "" -> parseAmount(raw);
            default -> parseAmount(raw);
        };
    }

    private Double parseInteger(String raw) {
        Double value = parseAmount(raw);
        return value == null ? null : (double) value.intValue();
    }

    private String textValue(String value) {
        String text = blankSafe(value);
        return text.isBlank() ? null : text;
    }

    private String normalizeLayoutText(String text) {
        return blankSafe(text).replace('\f', '\n').trim();
    }

    private int countPages(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return 0;
        }
        int pages = 1;
        for (int index = 0; index < rawText.length(); index++) {
            if (rawText.charAt(index) == '\f') {
                pages++;
            }
        }
        return pages;
    }

    private String describeFields(List<InvoiceTemplateModelProperties.TemplateField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "(none)";
        }
        return fields.stream()
                .map(field -> field.getKey() + " (required=" + field.isRequired() + ", aliases="
                        + String.join("/", field.getSourceAliases()) + ")")
                .toList()
                .toString();
    }

    private String describeFieldKeys(List<InvoiceTemplateModelProperties.TemplateField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "[]";
        }
        return fields.stream()
                .map(InvoiceTemplateModelProperties.TemplateField::getKey)
                .toList()
                .toString();
    }

    private String describeRows(List<NativeLayoutTableRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "(none)";
        }
        List<String> described = new ArrayList<>();
        for (NativeLayoutTableRow row : rows) {
            described.add(row.rowNumber() + ". " + row.cells());
        }
        return described.toString();
    }

    private List<String> buildLayoutPreview(String layoutText) {
        if (!hasText(layoutText)) {
            return List.of();
        }
        return normalizedNonEmptyLines(layoutText).stream().limit(20).toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<String> normalizedNonEmptyLines(String text) {
        if (!hasText(text)) {
            return List.of();
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private String summarize(String output) {
        String compact = blankSafe(output).replaceAll("\\s+", " ").trim();
        if (compact.length() <= 400) {
            return compact;
        }
        return compact.substring(0, 400) + "...";
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
    }

    private List<InvoiceFieldMappingService.FieldMapping> readMappings(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        List<InvoiceFieldMappingService.FieldMapping> mappings = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            mappings.add(new InvoiceFieldMappingService.FieldMapping(
                    asText(map.get("targetField")),
                    asText(map.get("sourceLabel")),
                    readBoolean(map.get("required")),
                    readBoolean(map.get("present")),
                    normalizeConfidence(asText(map.get("confidence")))));
        }
        return mappings;
    }

    private Map<String, String> readStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = asText(entry.getKey());
            if (!key.isBlank()) {
                result.put(key, asText(entry.getValue()));
            }
        }
        return result;
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            String text = asText(item);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return "true".equalsIgnoreCase(asText(value));
    }

    private String normalizeConfidence(String value) {
        return switch (blankSafe(value).trim().toLowerCase(Locale.ROOT)) {
            case "high", "medium", "low" -> value.trim().toLowerCase(Locale.ROOT);
            default -> "low";
        };
    }

    private String summaryAmount(NativeLayoutSample sample, StoredProfile profile, String targetField) {
        String label = profile.summaryFieldRoles().getOrDefault(targetField, "");
        if (label.isBlank()) {
            label = profile.summaryMappings().stream()
                    .filter(mapping -> targetField.equals(mapping.targetField()) && mapping.present())
                    .map(InvoiceFieldMappingService.FieldMapping::sourceLabel)
                    .findFirst()
                    .orElse("");
        }
        if (label.isBlank()) {
            label = "final_bill_amount".equals(targetField) ? "Total" : "Transport";
        }
        List<String> matches = new ArrayList<>();
        for (String line : sample.summaryLines()) {
            if (line.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))) {
                java.util.regex.Matcher matcher = AMOUNT_PATTERN.matcher(line.replace("ī", "").replace("₹", ""));
                while (matcher.find()) {
                    matches.add(matcher.group(1));
                }
            }
        }
        if (matches.isEmpty()) {
            return "";
        }
        if ("final_bill_amount".equals(targetField)) {
            if ("bill amount".equalsIgnoreCase(label)) {
                List<Double> nonZero = matches.stream()
                        .map(this::parseAmount)
                        .filter(Objects::nonNull)
                        .filter(value -> value > 0)
                        .toList();
                if (!nonZero.isEmpty()) {
                    return String.format(Locale.ROOT, "%.2f", nonZero.getLast());
                }
            }
            return matches.stream()
                    .map(this::parseAmount)
                    .filter(java.util.Objects::nonNull)
                    .max(Double::compareTo)
                    .map(value -> String.format(Locale.ROOT, "%.2f", value))
                    .orElse("");
        }
        return matches.getLast().replace(",", "");
    }

    private NativeLayoutTable choosePrimaryTable(List<NativeLayoutTable> tables, String preferredTableId) {
        if (tables == null || tables.isEmpty()) {
            return null;
        }
        if (hasText(preferredTableId)) {
            for (NativeLayoutTable table : tables) {
                if (preferredTableId.equalsIgnoreCase(table.tableId())) {
                    return table;
                }
            }
        }
        return tables.getFirst();
    }

    private NativeLayoutTable mergeCompatibleTables(List<NativeLayoutTable> tables, String preferredTableId) {
        NativeLayoutTable primary = choosePrimaryTable(tables, preferredTableId);
        if (primary == null) {
            return null;
        }
        String primarySignature = headerSignature(primary.headers());
        if (primarySignature.isBlank()) {
            return primary;
        }

        List<NativeLayoutTableRow> mergedRows = new ArrayList<>();
        List<String> mergedRawLines = new ArrayList<>();
        int rowNumber = 1;
        for (NativeLayoutTable candidate : tables) {
            if (!primarySignature.equals(headerSignature(candidate.headers()))) {
                continue;
            }
            if (mergedRawLines.isEmpty()) {
                mergedRawLines.addAll(candidate.rawLines());
            } else {
                List<String> candidateRawLines = candidate.rawLines();
                if (!candidateRawLines.isEmpty()) {
                    String headerLine = candidateRawLines.getFirst();
                    boolean alreadyHasHeader = mergedRawLines.stream().anyMatch(line -> line.equals(headerLine));
                    if (alreadyHasHeader) {
                        mergedRawLines.addAll(candidateRawLines.stream().skip(1).toList());
                    } else {
                        mergedRawLines.addAll(candidateRawLines);
                    }
                }
            }
            for (NativeLayoutTableRow row : candidate.rows()) {
                mergedRows.add(new NativeLayoutTableRow(rowNumber++, row.cells(), row.rawText()));
            }
        }
        return new NativeLayoutTable(primary.tableId(), primary.headers(), mergedRows, mergedRawLines);
    }

    private String headerSignature(List<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        return headers.stream()
                .map(this::normalizeHeader)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private List<InvoiceFieldMappingService.FieldMapping> applyPreferredRateColumn(
            List<InvoiceFieldMappingService.FieldMapping> mappings,
            Map<String, String> parsingHints) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        String preferredRate = parsingHints == null ? "" : blankSafe(parsingHints.get("preferredRateColumn"));
        if (preferredRate.isBlank()) {
            return mappings;
        }
        List<InvoiceFieldMappingService.FieldMapping> adjusted = new ArrayList<>();
        for (InvoiceFieldMappingService.FieldMapping mapping : mappings) {
            if ("rate".equals(mapping.targetField()) && mapping.present()) {
                adjusted.add(new InvoiceFieldMappingService.FieldMapping(
                        mapping.targetField(),
                        preferredRate,
                        mapping.required(),
                        true,
                        mapping.confidence()));
            } else {
                adjusted.add(mapping);
            }
        }
        return adjusted;
    }

    private String ruleFor(Map<String, String> fieldParsingRules, String key) {
        return fieldParsingRules == null ? "" : blankSafe(fieldParsingRules.get(key));
    }

    private RatePair resolveRatePair(
            List<String> headers,
            List<String> cells,
            Map<String, String> fieldParsingRules,
            Map<String, String> parsingHints,
            Double gstPercent,
            Double mappedRate) {
        String explicitBeforeLabel = parsingHints == null ? "" : blankSafe(parsingHints.get("rateBeforeTaxLabel"));
        String explicitAfterLabel = parsingHints == null ? "" : blankSafe(parsingHints.get("rateAfterTaxLabel"));
        String preferredRateLabel = parsingHints == null ? "" : blankSafe(parsingHints.get("preferredRateColumn"));
        Double qty = parseValueByRule(findValueByLooseHeader(headers, cells, "qty"), ruleFor(fieldParsingRules, "qty"));
        Double total = parseValueByRule(findValueByLooseHeader(headers, cells, "total"), ruleFor(fieldParsingRules, "total"));
        List<String> rateHeaders = headers == null
                ? List.of()
                : headers.stream().filter(this::looksLikeRateHeader).toList();
        boolean hasMultipleRateHeaders = rateHeaders.size() > 1;

        String beforeLabel = explicitBeforeLabel;
        String afterLabel = explicitAfterLabel;
        if (hasMultipleRateHeaders) {
            if (beforeLabel.isBlank()) {
                beforeLabel = inferRateLabel(headers, false);
            }
            if (afterLabel.isBlank()) {
                afterLabel = inferRateLabel(headers, true);
            }
        }

        Double rateBeforeTax = parseValueByRule(findValueByLooseHeader(headers, cells, beforeLabel), ruleFor(fieldParsingRules, "rate"));
        Double rateAfterTax = parseValueByRule(findValueByLooseHeader(headers, cells, afterLabel), ruleFor(fieldParsingRules, "rate"));

        if (mappedRate != null && (!hasMultipleRateHeaders || (rateBeforeTax == null && rateAfterTax == null))) {
            RateClassification inferred = classifySingleRate(mappedRate, qty, gstPercent, total);
            if (inferred == RateClassification.AFTER_TAX) {
                rateAfterTax = mappedRate;
            } else if (inferred == RateClassification.BEFORE_TAX) {
                rateBeforeTax = mappedRate;
            } else if (isTaxInclusiveRateLabel(preferredRateLabel)) {
                rateAfterTax = mappedRate;
            } else {
                rateBeforeTax = mappedRate;
            }
        }

        if (rateBeforeTax == null && rateAfterTax != null) {
            rateBeforeTax = deriveExclusiveRate(rateAfterTax, gstPercent);
        }
        if (rateAfterTax == null && rateBeforeTax != null) {
            rateAfterTax = deriveInclusiveRate(rateBeforeTax, gstPercent);
        }

        return new RatePair(rateBeforeTax, rateAfterTax);
    }

    private String inferRateLabel(List<String> headers, boolean inclusive) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        for (String header : headers) {
            if (!looksLikeRateHeader(header)) {
                continue;
            }
            if (inclusive == isTaxInclusiveRateLabel(header)) {
                return header;
            }
        }
        return "";
    }

    private boolean looksLikeRateHeader(String header) {
        String normalized = normalizeHeader(header);
        return normalized.contains("rate");
    }

    private boolean isTaxInclusiveRateLabel(String header) {
        String normalized = normalizeHeader(header);
        return normalized.contains("after tax")
                || normalized.contains("incl of tax")
                || normalized.contains("incl tax")
                || normalized.contains("inclusive");
    }

    private Double deriveExclusiveRate(Double inclusiveRate, Double gstPercent) {
        if (inclusiveRate == null) {
            return null;
        }
        if (gstPercent == null || gstPercent <= 0) {
            return inclusiveRate;
        }
        return roundAmount(inclusiveRate / (1 + (gstPercent / 100.0)));
    }

    private Double deriveInclusiveRate(Double exclusiveRate, Double gstPercent) {
        if (exclusiveRate == null) {
            return null;
        }
        if (gstPercent == null || gstPercent <= 0) {
            return exclusiveRate;
        }
        return roundAmount(exclusiveRate * (1 + (gstPercent / 100.0)));
    }

    private Double roundAmount(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private RateClassification classifySingleRate(
            Double rate,
            Double qty,
            Double gstPercent,
            Double total) {
        if (rate == null || qty == null || total == null || rate <= 0 || qty <= 0 || total <= 0) {
            return RateClassification.UNKNOWN;
        }
        double inclusiveAmount = roundAmount(rate * qty);
        double exclusiveAmount = roundAmount(rate * qty * (1 + (safePercent(gstPercent) / 100.0)));
        double diffInclusive = Math.abs(total - inclusiveAmount);
        double diffExclusive = Math.abs(total - exclusiveAmount);
        if (diffInclusive <= 0.5 && diffInclusive + 0.01 < diffExclusive) {
            return RateClassification.AFTER_TAX;
        }
        if (diffExclusive <= 0.5 && diffExclusive + 0.01 < diffInclusive) {
            return RateClassification.BEFORE_TAX;
        }
        return RateClassification.UNKNOWN;
    }

    private double safePercent(Double gstPercent) {
        return gstPercent == null || gstPercent < 0 ? 0.0 : gstPercent;
    }

    private List<Map<String, Object>> toMappingPreview(List<InvoiceFieldMappingService.FieldMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .map(mapping -> Map.<String, Object>of(
                        "targetField", mapping.targetField(),
                        "sourceLabel", mapping.sourceLabel(),
                        "present", mapping.present(),
                        "confidence", mapping.confidence()))
                .toList();
    }

    private String contextualInvoiceNumber(String layoutText) {
        List<String> lines = normalizedNonEmptyLines(layoutText);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).toLowerCase(Locale.ROOT);
            if (!line.contains("invoice no")) {
                continue;
            }
            if (index + 1 < lines.size()) {
                String next = extractInvoiceNumberCandidate(lines.get(index + 1));
                if (hasText(next)) {
                    return next;
                }
            }
            String current = extractInvoiceNumberCandidate(lines.get(index));
            if (hasText(current) && !"e-way".equalsIgnoreCase(current)) {
                return current;
            }
        }
        return "";
    }

    private String contextualInvoiceDate(String layoutText) {
        List<String> lines = normalizedNonEmptyLines(layoutText);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).toLowerCase(Locale.ROOT);
            if (!(line.contains("dated") || line.contains("invoice date") || line.contains("date"))) {
                continue;
            }
            if (index + 1 < lines.size()) {
                String next = extractDateCandidate(lines.get(index + 1));
                if (hasText(next)) {
                    return next;
                }
            }
            String current = extractDateCandidate(lines.get(index));
            if (hasText(current)) {
                return current;
            }
        }
        return "";
    }

    private String extractInvoiceNumberCandidate(String line) {
        String text = blankSafe(line);
        String datedPrefix = prefixBeforeDate(text);
        if (hasText(datedPrefix)) {
            String beforeDateToken = lastInvoiceLikeToken(datedPrefix);
            if (hasText(beforeDateToken)) {
                return beforeDateToken;
            }
        }
        Matcher matcher = Pattern.compile("\\b([A-Za-z]*\\d[A-Za-z0-9\\-/]*)\\b").matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            String lowered = token.toLowerCase(Locale.ROOT);
            if (lowered.contains("way")) {
                continue;
            }
            if (token.matches("\\d{1,6}") || token.contains("/") || token.contains("-")) {
                return token;
            }
        }
        return "";
    }

    private String extractDateCandidate(String line) {
        String iso = firstRegex(line, ISO_DATE_PATTERN);
        if (hasText(iso)) {
            return iso;
        }
        return firstRegex(line, DMY_DASH_PATTERN);
    }

    private String prefixBeforeDate(String line) {
        Matcher iso = ISO_DATE_PATTERN.matcher(blankSafe(line));
        if (iso.find()) {
            return line.substring(0, iso.start());
        }
        Matcher dmy = DMY_DASH_PATTERN.matcher(blankSafe(line));
        if (dmy.find()) {
            return line.substring(0, dmy.start());
        }
        return "";
    }

    private String lastInvoiceLikeToken(String text) {
        Matcher matcher = Pattern.compile("\\b([A-Za-z]*\\d[A-Za-z0-9\\-/]*)\\b").matcher(blankSafe(text));
        String candidate = "";
        while (matcher.find()) {
            String token = matcher.group(1).trim();
            String lowered = token.toLowerCase(Locale.ROOT);
            if (lowered.contains("way")) {
                continue;
            }
            if (token.matches("\\d{1,6}") || token.contains("/") || token.contains("-")) {
                candidate = token;
            }
        }
        return candidate;
    }

    private String firstMatch(String text, Pattern pattern) {
        if (text == null || text.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = pattern.matcher(text);
        return matcher.find() ? blankSafe(matcher.group(1)).trim() : "";
    }

    private String firstRegex(String text, Pattern pattern) {
        if (text == null || text.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(candidate -> {
                        try {
                            Files.deleteIfExists(candidate);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private record QtyUom(Double qty, String uom) {
    }

    public record StoredProfile(
            String profileId,
            String label,
            String vendorName,
            String description,
            List<InvoiceFieldMappingService.FieldMapping> itemMappings,
            List<InvoiceFieldMappingService.FieldMapping> summaryMappings,
            String notes,
            String generatorType,
            String generatorModel,
            String primaryItemTableBlockId,
            String gstSummaryBlockId,
            Map<String, String> summaryFieldRoles,
            Map<String, String> parsingHints,
            Map<String, String> fieldParsingRules,
            List<String> skipLabels,
            List<String> headerContinuationLabels) {
    }

    public record ExtractionResult(
            List<ParsedItem> items,
            String invoiceNumber,
            String invoiceDate,
            String finalAmount,
            String transportCharge,
            String layoutText,
            List<String> rawTableLines,
            List<Integer> extractedSerials) {
    }

    private record RatePair(
            Double beforeTax,
            Double afterTax) {
    }

    private enum RateClassification {
        BEFORE_TAX,
        AFTER_TAX,
        UNKNOWN
    }
}
