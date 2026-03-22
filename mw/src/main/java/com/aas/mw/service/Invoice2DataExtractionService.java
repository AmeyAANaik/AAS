package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class Invoice2DataExtractionService {

    public record Invoice2DataProfile(
            String id,
            String label,
            String inputReader,
            String yaml) {
    }

    public record ExtractionResult(
            Map<String, Object> raw,
            List<ParsedItem> items,
            String invoiceNumber,
            String invoiceDate,
            String finalAmount,
            String transportCharge) {
    }

    private final ObjectMapper objectMapper;
    private final String configuredPythonCommand;

    public Invoice2DataExtractionService(
            ObjectMapper objectMapper,
            @Value("${app.invoice2data.python-command:}") String configuredPythonCommand) {
        this.objectMapper = objectMapper;
        this.configuredPythonCommand = configuredPythonCommand == null ? "" : configuredPythonCommand.trim();
    }

    public ExtractionResult extract(byte[] pdfBytes, Invoice2DataProfile profile) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Sample PDF is required.");
        }
        if (profile == null || profile.yaml() == null || profile.yaml().isBlank()) {
            throw new IllegalArgumentException("invoice2data profile is required.");
        }

        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("aas-invoice2data-");
            Path templateDir = Files.createDirectory(workingDir.resolve("templates"));
            Path pdfPath = workingDir.resolve("invoice.pdf");
            Path outputBase = workingDir.resolve("result");
            Path outputJson = workingDir.resolve("result.json");
            Path templatePath = templateDir.resolve(profile.id() + ".yml");

            Files.writeString(templatePath, profile.yaml(), StandardCharsets.UTF_8);
            Files.write(pdfPath, pdfBytes);

            List<String> command = new ArrayList<>();
            command.add(resolvePythonCommand());
            command.add("-m");
            command.add("invoice2data.main");
            command.add("--input-reader");
            command.add(profile.inputReader() == null || profile.inputReader().isBlank() ? "pdftotext" : profile.inputReader().trim());
            command.add("--template-folder");
            command.add(templateDir.toString());
            command.add("--exclude-built-in-templates");
            command.add("--output-format");
            command.add("json");
            command.add("--output-name");
            command.add(outputBase.toString());
            command.add(pdfPath.toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("invoice2data failed for profile " + profile.id() + ": " + summarizeProcessOutput(output));
            }
            if (!Files.exists(outputJson)) {
                throw new IllegalStateException("invoice2data did not produce JSON output for profile " + profile.id() + ".");
            }

            List<Map<String, Object>> documents = objectMapper.readValue(outputJson.toFile(), new TypeReference<List<Map<String, Object>>>() {});
            if (documents == null || documents.isEmpty()) {
                throw new IllegalStateException("invoice2data did not extract any invoice data for profile " + profile.id() + ".");
            }
            Map<String, Object> raw = documents.get(0);
            List<ParsedItem> parsedItems = normalizeItems(raw.get("lines"));
            if (parsedItems.isEmpty()) {
                throw new IllegalStateException("invoice2data did not extract any line items for profile " + profile.id() + ".");
            }
            return new ExtractionResult(
                    raw,
                    parsedItems,
                    asText(raw.get("invoice_number")),
                    asText(raw.get("date")),
                    amountText(raw.get("amount")),
                    amountText(raw.get("transport")));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to prepare invoice2data runtime files.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("invoice2data execution was interrupted.", ex);
        } finally {
            deleteQuietly(workingDir);
        }
    }

    public String buildProfileJson(Invoice2DataProfileCatalogService.Profile profile, Map<String, Object> previewMetrics, String sampleFileName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "invoice2data_profile");
        payload.put("version", 1);
        payload.put("engine", Map.of(
                "type", "invoice2data",
                "inputReader", profile.inputReader()));
        payload.put("profile", Map.of(
                "id", profile.id(),
                "label", profile.label(),
                "vendorName", profile.vendorName(),
                "description", profile.description()));
        payload.put("template", Map.of(
                "name", profile.id(),
                "yaml", profile.yaml()));
        if (previewMetrics != null && !previewMetrics.isEmpty()) {
            Map<String, Object> validation = new LinkedHashMap<>(previewMetrics);
            validation.put("validatedAt", Instant.now().toString());
            if (sampleFileName != null && !sampleFileName.isBlank()) {
                validation.put("sampleFileName", sampleFileName);
            }
            payload.put("validation", validation);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to build invoice2data profile JSON.", ex);
        }
    }

    public Invoice2DataProfile parseStoredProfile(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (!"invoice2data_profile".equals(asText(map.get("kind")))) {
                return null;
            }
            Map<String, Object> engine = asMap(map.get("engine"));
            if (!"invoice2data".equals(asText(engine.get("type")))) {
                return null;
            }
            Map<String, Object> profileMap = asMap(map.get("profile"));
            Map<String, Object> templateMap = asMap(map.get("template"));
            String id = asText(profileMap.get("id"));
            String label = asText(profileMap.get("label"));
            String inputReader = asText(engine.get("inputReader"));
            String yaml = asText(templateMap.get("yaml"));
            if (id.isBlank() || yaml.isBlank()) {
                return null;
            }
            return new Invoice2DataProfile(id, label, inputReader, yaml);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<ParsedItem> normalizeItems(Object linesObj) {
        if (!(linesObj instanceof List<?> lines) || lines.isEmpty()) {
            return List.of();
        }
        List<ParsedItem> items = new ArrayList<>();
        for (Object lineObj : lines) {
            if (!(lineObj instanceof Map<?, ?> line)) {
                continue;
            }
            String itemName = compactDescription(asText(line.get("description")));
            String itemId = asText(line.get("item_id"));
            double qty = asDouble(line.get("qty"));
            double rate = asDouble(line.get("rate"));
            double amount = asDouble(line.get("amount"));
            Double gst = asNullablePercent(line.get("gst"));
            String uom = asText(line.get("uom"));
            Double mrp = asNullableDouble(line.get("mrp"));
            if (itemName.isBlank() || qty <= 0 || rate <= 0 || amount <= 0) {
                continue;
            }
            items.add(new ParsedItem(
                    itemName,
                    qty,
                    rate,
                    amount,
                    itemId.isBlank() ? null : itemId,
                    gst,
                    uom.isBlank() ? null : uom,
                    mrp));
        }
        return items;
    }

    private String compactDescription(String value) {
        return value == null ? "" : value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private String summarizeProcessOutput(String output) {
        if (output == null || output.isBlank()) {
            return "no process output";
        }
        String compact = output.replaceAll("\\s+", " ").trim();
        return compact.length() > 400 ? compact.substring(0, 400) + "..." : compact;
    }

    private String resolvePythonCommand() {
        if (!configuredPythonCommand.isBlank()) {
            return configuredPythonCommand;
        }
        Path localVenv = Path.of("/tmp/aas-pdf-eval/bin/python");
        if (Files.exists(localVenv)) {
            return localVenv.toString();
        }
        return "python3";
    }

    private double asDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim().replace(",", "");
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private Double asNullableDouble(Object value) {
        double parsed = asDouble(value);
        return parsed > 0 ? parsed : null;
    }

    private Double asNullablePercent(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim()
                .replace("%", "")
                .replace(",", "")
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

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String amountText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return String.format(Locale.US, "%.2f", number.doubleValue());
        }
        return asText(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new HashMap<>();
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
}
