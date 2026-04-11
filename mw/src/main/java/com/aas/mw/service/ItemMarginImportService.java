package com.aas.mw.service;

import com.aas.mw.dto.FieldsRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ItemMarginImportService {

    private final ObjectMapper objectMapper;
    private final MasterDataService masterDataService;
    private final CatalogRoutingService catalogRoutingService;
    private final String configuredPythonCommand;

    public ItemMarginImportService(
            ObjectMapper objectMapper,
            MasterDataService masterDataService,
            CatalogRoutingService catalogRoutingService,
            @Value("${app.pdf.python-command:}") String configuredPythonCommand) {
        this.objectMapper = objectMapper;
        this.masterDataService = masterDataService;
        this.catalogRoutingService = catalogRoutingService;
        this.configuredPythonCommand = configuredPythonCommand == null ? "" : configuredPythonCommand.trim();
    }

    public Map<String, Object> importMargins(MultipartFile file, String categoryId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }
        if (categoryId == null || categoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId is required to create missing items.");
        }

        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("aas-item-margin-import-");
            String originalName = file.getOriginalFilename() == null ? "import.xlsx" : file.getOriginalFilename();
            Path workbookPath = workingDir.resolve(safeFileName(originalName));
            file.transferTo(workbookPath);

            ImportSheetPayload sheetPayload = readSheet(workbookPath);
            Map<String, List<Map<String, Object>>> existingByHsn = indexItemsByHsn(masterDataService.listItems());

            List<Map<String, Object>> updated = new ArrayList<>();
            List<Map<String, Object>> created = new ArrayList<>();
            List<Map<String, Object>> skipped = new ArrayList<>();
            Map<String, Map<String, Object>> createdByHsn = new HashMap<>();

            for (ImportRow row : sheetPayload.rows()) {
                String normalizedHsn = normalizeHsn(row.vendorHsnCode());
                if (normalizedHsn.isBlank()) {
                    skipped.add(skipEntry(row, "Missing HSN code."));
                    continue;
                }

                List<Map<String, Object>> matches = existingByHsn.getOrDefault(normalizedHsn, List.of());
                if (!matches.isEmpty()) {
                    for (Map<String, Object> match : matches) {
                        String itemId = asText(match.get("name"));
                        Map<String, Object> result = unwrap(masterDataService.updateItem(itemId, fieldsRequest(Map.of(
                                "aas_margin_percent", row.marginPercent()))));
                        updated.add(Map.of(
                                "itemId", asText(result.get("name")),
                                "itemCode", asText(result.get("item_code")),
                                "itemName", firstText(result.get("item_name"), row.itemName()),
                                "vendorHsnCode", normalizedHsn,
                                "marginPercent", row.marginPercent()));
                    }
                    continue;
                }

                if (createdByHsn.containsKey(normalizedHsn)) {
                    Map<String, Object> existingCreated = createdByHsn.get(normalizedHsn);
                    Map<String, Object> createdItem = unwrap(existingCreated);
                    masterDataService.updateItem(asText(createdItem.get("name")), fieldsRequest(Map.of(
                            "aas_margin_percent", row.marginPercent())));
                    skipped.add(skipEntry(row, "Duplicate HSN in workbook. Reused newly created item."));
                    continue;
                }

                if (row.itemName() == null || row.itemName().isBlank()) {
                    skipped.add(skipEntry(row, "Cannot create item without item name."));
                    continue;
                }

                Map<String, Object> createdItem = unwrap(masterDataService.createItem(fieldsRequest(Map.of(
                        "item_name", row.itemName().trim(),
                        "item_group", categoryId,
                        "stock_uom", defaultUnit(row.measureUnit()),
                        "aas_margin_percent", row.marginPercent(),
                        "aas_vendor_hsn_code", normalizedHsn))));
                createdByHsn.put(normalizedHsn, createdItem);
                created.add(Map.of(
                        "itemId", asText(createdItem.get("name")),
                        "itemCode", asText(createdItem.get("item_code")),
                        "itemName", firstText(createdItem.get("item_name"), row.itemName()),
                        "vendorHsnCode", normalizedHsn,
                        "marginPercent", row.marginPercent()));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("fileName", originalName);
            response.put("sourceSheet", sheetPayload.sheet());
            response.put("categoryId", categoryId);
            response.put("processedRows", sheetPayload.rows().size());
            response.put("updatedCount", updated.size());
            response.put("createdCount", created.size());
            response.put("skippedCount", skipped.size());
            response.put("updated", updated);
            response.put("created", created);
            response.put("skipped", skipped);
            return response;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store the uploaded workbook for processing.", ex);
        } finally {
            deleteQuietly(workingDir);
        }
    }

    private ImportSheetPayload readSheet(Path workbookPath) {
        try {
            Path scriptPath = resolveScriptPath();
            List<String> command = List.of(
                    resolvePythonCommand(),
                    scriptPath.toString(),
                    workbookPath.toString());
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Margin import parser failed: " + summarize(output));
            }
            Map<String, Object> payload = objectMapper.readValue(output, new TypeReference<Map<String, Object>>() {});
            String error = asText(payload.get("error"));
            if (!error.isBlank()) {
                throw new IllegalStateException(error);
            }
            String sheet = asText(payload.get("sheet"));
            List<ImportRow> rows = readRows(payload.get("rows"));
            if (rows.isEmpty()) {
                throw new IllegalStateException("The workbook did not contain any valid item margin rows.");
            }
            return new ImportSheetPayload(sheet, rows);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Margin import parser was interrupted.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to run the margin import parser.", ex);
        }
    }

    private List<ImportRow> readRows(Object rowsObject) {
        if (!(rowsObject instanceof List<?> rows)) {
            return List.of();
        }
        List<ImportRow> result = new ArrayList<>();
        for (Object entry : rows) {
            if (!(entry instanceof Map<?, ?> raw)) {
                continue;
            }
            String itemName = asText(raw.get("itemName"));
            String vendorHsnCode = asText(raw.get("vendorHsnCode"));
            String measureUnit = asText(raw.get("measureUnit"));
            Double marginPercent = asDouble(raw.get("marginPercent"));
            if (vendorHsnCode.isBlank() || marginPercent == null) {
                continue;
            }
            result.add(new ImportRow(itemName, vendorHsnCode, measureUnit, marginPercent));
        }
        return result;
    }

    private Map<String, List<Map<String, Object>>> indexItemsByHsn(List<Map<String, Object>> items) {
        Map<String, List<Map<String, Object>>> indexed = new HashMap<>();
        for (Map<String, Object> item : items) {
            String normalizedHsn = normalizeHsn(firstText(item.get("aas_vendor_hsn_code"), item.get("vendor_hsn_code")));
            if (normalizedHsn.isBlank()) {
                continue;
            }
            indexed.computeIfAbsent(normalizedHsn, ignored -> new ArrayList<>()).add(item);
        }
        return indexed;
    }

    private Path resolveScriptPath() {
        Path currentDir = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                currentDir.resolve("scripts/import_item_margins.py"),
                currentDir.resolve("../scripts/import_item_margins.py").normalize());
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Margin import script not found under scripts/import_item_margins.py.");
    }

    private String resolvePythonCommand() {
        if (!configuredPythonCommand.isBlank()) {
            return configuredPythonCommand;
        }
        return "python3";
    }

    private FieldsRequest fieldsRequest(Map<String, Object> fields) {
        FieldsRequest request = new FieldsRequest();
        request.setFields(new HashMap<>(fields));
        return request;
    }

    private String normalizeHsn(String value) {
        return catalogRoutingService.normalizeCodeSegment(asText(value)).replace("_", "");
    }

    private String defaultUnit(String value) {
        String normalized = asText(value).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "Nos";
        }
        return switch (normalized) {
            case "KG", "KGS" -> "Kg";
            case "NOS", "NO", "PCS", "PC" -> "Nos";
            case "PACK", "PKT", "PKTS" -> "Pack";
            case "TIN", "TINS" -> "Tin";
            case "LTR", "LITRE", "LITER", "LT", "L" -> "Litre";
            case "BTL", "BOTTLE", "BOTTLES" -> "Bottle";
            default -> value.trim();
        };
    }

    private String safeFileName(String value) {
        String normalized = value == null ? "import.xlsx" : value.replaceAll("[^A-Za-z0-9._-]+", "_");
        return normalized.isBlank() ? "import.xlsx" : normalized;
    }

    private Map<String, Object> skipEntry(ImportRow row, String reason) {
        return Map.of(
                "itemName", row.itemName(),
                "vendorHsnCode", row.vendorHsnCode(),
                "marginPercent", row.marginPercent(),
                "reason", reason);
    }

    private String summarize(String output) {
        String compact = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "no process output";
        }
        return compact.length() > 400 ? compact.substring(0, 400) + "..." : compact;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = asText(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = asText(value).replace(",", "");
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
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

    private record ImportSheetPayload(
            String sheet,
            List<ImportRow> rows) {
    }

    private record ImportRow(
            String itemName,
            String vendorHsnCode,
            String measureUnit,
            Double marginPercent) {
    }
}
