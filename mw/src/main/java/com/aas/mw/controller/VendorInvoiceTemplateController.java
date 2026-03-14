package com.aas.mw.controller;

import com.aas.mw.dto.DownloadedFile;
import com.aas.mw.dto.UploadedFileInfo;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.client.ErpNextClient;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.OcrService;
import com.aas.mw.service.VendorInvoiceTemplateCatalog;
import com.aas.mw.service.VendorInvoiceTemplate;
import com.aas.mw.service.VendorInvoiceTemplateParser;
import com.aas.mw.service.VendorPdfParser;
import com.aas.mw.service.InvoiceTemplateModelService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vendors")
public class VendorInvoiceTemplateController {

    private static final String SUPPLIER = "Supplier";

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService fileService;
    private final OcrService ocrService;
    private final VendorInvoiceTemplateCatalog templateCatalog;
    private final VendorInvoiceTemplateParser templateParser;
    private final VendorPdfParser heuristicParser;
    private final ObjectMapper objectMapper;
    private final InvoiceTemplateModelService invoiceTemplateModelService;

    public VendorInvoiceTemplateController(
            ErpNextClient erpNextClient,
            ErpNextFileService fileService,
            OcrService ocrService,
            VendorInvoiceTemplateCatalog templateCatalog,
            VendorInvoiceTemplateParser templateParser,
            VendorPdfParser heuristicParser,
            ObjectMapper objectMapper,
            InvoiceTemplateModelService invoiceTemplateModelService) {
        this.erpNextClient = erpNextClient;
        this.fileService = fileService;
        this.ocrService = ocrService;
        this.templateCatalog = templateCatalog;
        this.templateParser = templateParser;
        this.heuristicParser = heuristicParser;
        this.objectMapper = objectMapper;
        this.invoiceTemplateModelService = invoiceTemplateModelService;
    }

    @PostMapping(value = "/{id}/invoice-template/sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSamplePdf(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "templateJson", required = false) String templateJson,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "ERPNext session not found. Please log out and log in again (middleware restart clears ERP session cache).",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sample PDF file is required."));
        }

        // Upload the sample file to ERPNext and store the link on Supplier.
        UploadedFileInfo info = fileService.uploadFile(SUPPLIER, id, "invoice_template.pdf", file, sessionCookie);

        // Auto-detect which built-in template fits best by OCRing the sample.
        String chosenKey = VendorInvoiceTemplateCatalog.KEY_HEURISTIC_V1;
        int detectedItems = 0;
        String ocrText = "";
        try {
            byte[] pdfBytes = file.getBytes();
            ocrText = ocrService.extractTextFromPdf(pdfBytes);
            if (ocrText != null && !ocrText.isBlank()) {
                // Try table parser first; otherwise fallback to heuristic.
                var table = templateCatalog.byKey(VendorInvoiceTemplateCatalog.KEY_TABLE_V1);
                if (table.isPresent()) {
                    int count = templateParser.parseItems(ocrText, table.get()).size();
                    if (count > 0) {
                        chosenKey = VendorInvoiceTemplateCatalog.KEY_TABLE_V1;
                        detectedItems = count;
                    }
                }
                if (detectedItems == 0) {
                    detectedItems = heuristicParser.parseItems(ocrText).size();
                }
            }
        } catch (Exception ignored) {
            chosenKey = VendorInvoiceTemplateCatalog.KEY_HEURISTIC_V1;
            detectedItems = 0;
        }

        TemplateValidation validation = validateTemplate(id, templateJson, ocrText, chosenKey);

        Map<String, Object> update = new HashMap<>();
        if (info.fileUrl() != null) {
            update.put("aas_invoice_template_sample_pdf", info.fileUrl());
        }
        update.put("aas_invoice_template_key", chosenKey);
        Map<String, Object> supplier = erpNextClient.updateResource(SUPPLIER, id, update);

        Map<String, Object> response = new HashMap<>();
        response.put("vendor", supplier);
        response.put("file", Map.of(
                "fileName", info.fileName(),
                "fileUrl", info.fileUrl(),
                "fileId", info.fileId()));
        response.put("templateChosen", Map.of(
                "key", chosenKey,
                "detectedItems", detectedItems));
        response.put("validation", validation.asMap());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/invoice-template/sample")
    public ResponseEntity<byte[]> downloadSamplePdf(@PathVariable String id) {
        Map<String, Object> supplier = unwrapResource(erpNextClient.getResource(SUPPLIER, id));
        String fileUrl = asText(supplier.get("aas_invoice_template_sample_pdf"));
        if (fileUrl.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        DownloadedFile file = fileService.downloadFile(fileUrl);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(mediaType)
                .body(file.bytes());
    }

    @DeleteMapping("/{id}/invoice-template")
    public ResponseEntity<Map<String, Object>> clearTemplate(
            @PathVariable String id,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "ERPNext session not found. Please log out and log in again (middleware restart clears ERP session cache).",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("aas_invoice_template_enabled", 0);
        update.put("aas_invoice_template_key", "");
        update.put("aas_invoice_template_json", "");
        update.put("aas_invoice_template_sample_pdf", "");
        update.put("disabled", 1);
        Map<String, Object> supplier = erpNextClient.updateResource(SUPPLIER, id, update);
        return ResponseEntity.ok(Map.of("vendor", supplier));
    }

    private TemplateValidation validateTemplate(String vendorId, String templateJson, String ocrText, String fallbackKey) {
        String sampleText = ocrText == null ? "" : ocrText;
        String effectiveTemplateJson = templateJson == null ? "" : templateJson.trim();
        if (effectiveTemplateJson.isBlank()) {
            Map<String, Object> supplier = unwrapResource(erpNextClient.getResource(SUPPLIER, vendorId));
            effectiveTemplateJson = asText(supplier.get("aas_invoice_template_json"));
        }

        List<ParsedItem> parsedItems = List.of();
        String parserSource = "heuristic";
        boolean configured = false;
        boolean used = false;
        Optional<VendorInvoiceTemplate> template = parseVendorTemplate(effectiveTemplateJson);
        if (template.isPresent() && !sampleText.isBlank()) {
            configured = true;
            parserSource = "vendor_json";
            parsedItems = templateParser.parseItems(sampleText, template.get());
            used = !parsedItems.isEmpty();
        }
        if (parsedItems.isEmpty() && !sampleText.isBlank()) {
            var builtin = templateCatalog.byKey(fallbackKey);
            if (!configured && builtin.isPresent()) {
                configured = true;
                parserSource = fallbackKey;
            }
            if (builtin.isPresent()) {
                parsedItems = templateParser.parseItems(sampleText, builtin.get());
                used = !parsedItems.isEmpty();
                if (used) {
                    parserSource = fallbackKey;
                }
            }
        }
        if (parsedItems.isEmpty() && !sampleText.isBlank()) {
            parsedItems = heuristicParser.parseItems(sampleText);
            if (!parsedItems.isEmpty()) {
                parserSource = "heuristic";
            }
        }
        return buildValidation(sampleText, configured, used, parserSource, parsedItems, template.orElse(null));
    }

    private TemplateValidation buildValidation(
            String ocrText,
            boolean configured,
            boolean used,
            String parserSource,
            List<ParsedItem> parsedItems,
            VendorInvoiceTemplate template) {
        Set<String> parsedColumns = new LinkedHashSet<>();
        for (ParsedItem item : parsedItems) {
            if (item == null) {
                continue;
            }
            if (hasText(item.name())) {
                parsedColumns.add("item_name");
            }
            if (hasText(item.hsn())) {
                parsedColumns.add("item_id");
            }
            if (item.qty() > 0) {
                parsedColumns.add("qty");
            }
            if (item.rate() > 0) {
                parsedColumns.add("rate");
            }
            if (item.gstPercent() != null && item.gstPercent() > 0) {
                parsedColumns.add("gst");
            }
            if (item.amount() > 0) {
                parsedColumns.add("total");
            }
        }
        List<String> requiredColumns = invoiceTemplateModelService.requiredItemKeys();
        List<String> missingColumns = new ArrayList<>();
        for (String column : requiredColumns) {
            if (!parsedColumns.contains(column)) {
                missingColumns.add(column);
            }
        }
        String finalBillAmount = extractFinalAmount(ocrText, template);
        List<String> requiredSummaryFields = invoiceTemplateModelService.requiredSummaryKeys();
        List<String> parsedSummaryFields = new ArrayList<>();
        if (!finalBillAmount.isBlank()) {
            parsedSummaryFields.add("final_bill_amount");
        }
        List<String> missingSummaryFields = new ArrayList<>();
        for (String field : requiredSummaryFields) {
            if (!parsedSummaryFields.contains(field)) {
                missingSummaryFields.add(field);
            }
        }
        List<Map<String, Object>> preview = parsedItems.stream()
                .limit(3)
                .map(item -> Map.<String, Object>of(
                        "item_name", item.name(),
                        "item_id", item.hsn() == null ? "" : item.hsn(),
                        "qty", item.qty(),
                        "rate", item.rate(),
                        "gst", item.gstPercent() == null ? "" : item.gstPercent(),
                        "total", item.amount()))
                .toList();
        VendorPdfParser.ParseDiagnostics diagnostics = heuristicParser.diagnose(ocrText);
        boolean activationReady = configured && used && !parsedItems.isEmpty() && missingColumns.isEmpty() && missingSummaryFields.isEmpty();
        return new TemplateValidation(
                configured,
                used,
                parserSource,
                parsedItems.size(),
                requiredColumns,
                new ArrayList<>(parsedColumns),
                missingColumns,
                requiredSummaryFields,
                parsedSummaryFields,
                missingSummaryFields,
                finalBillAmount,
                activationReady,
                diagnostics.lineCount(),
                buildOcrPreview(ocrText),
                preview);
    }

    private Optional<VendorInvoiceTemplate> parseVendorTemplate(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> parserMap = map;
            Object parserObj = map.get("parser");
            if (parserObj instanceof Map<?, ?> parserCandidate) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) parserCandidate;
                parserMap = casted;
            }
            Object versionObj = parserMap.get("version");
            Object regexObj = parserMap.get("itemLineRegex");
            if (versionObj == null || regexObj == null) {
                return Optional.empty();
            }
            int version = versionObj instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(versionObj.toString().trim());
            String itemLineRegex = String.valueOf(regexObj).trim();
            String billDateRegex = parserMap.get("billDateRegex") == null ? null : String.valueOf(parserMap.get("billDateRegex"));
            String finalAmountRegex = parserMap.get("finalAmountRegex") == null ? null : String.valueOf(parserMap.get("finalAmountRegex"));
            if (version <= 0 || itemLineRegex.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new VendorInvoiceTemplate(version, itemLineRegex, billDateRegex, finalAmountRegex));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String extractFinalAmount(String ocrText, VendorInvoiceTemplate template) {
        if (ocrText == null || ocrText.isBlank() || template == null) {
            return "";
        }
        String regex = asText(template.finalAmountRegex());
        if (regex.isBlank()) {
            return "";
        }
        try {
            var matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.MULTILINE).matcher(ocrText);
            String lastPositive = "";
            while (matcher.find()) {
            for (String group : List.of("finalBillAmount", "final_bill_amount", "amount", "total", "grandTotal", "grand_total", "invoiceTotal", "invoice_total")) {
                    try {
                        String value = asText(matcher.group(group));
                        if (!value.isBlank() && parseAmount(value) > 0) {
                            lastPositive = value;
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                String whole = asText(matcher.group());
                if (!whole.isBlank() && parseAmount(whole) > 0) {
                    lastPositive = whole;
                }
            }
            return lastPositive;
        } catch (Exception ex) {
            return "";
        }
    }

    private List<String> buildOcrPreview(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        List<String> tail = new ArrayList<>();
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (lines.size() < 30) {
                lines.add(line);
            }
            tail.add(line);
            if (tail.size() > 12) {
                tail.remove(0);
            }
        }
        lines.add("--- OCR TAIL ---");
        lines.addAll(tail);
        return lines;
    }

    private double parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        String text = raw.replace(",", "").trim();
        text = text.replace('O', '0').replace('o', '0');
        text = text.replaceAll("(?i)inr|rs\\.?", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private record TemplateValidation(
            boolean configured,
            boolean used,
            String parserSource,
            int detectedItems,
            List<String> requiredColumns,
            List<String> parsedColumns,
            List<String> missingColumns,
            List<String> requiredSummaryFields,
            List<String> parsedSummaryFields,
            List<String> missingSummaryFields,
            String finalBillAmount,
            boolean activationReady,
            int ocrLineCount,
            List<String> ocrPreview,
            List<Map<String, Object>> previewItems) {

        private Map<String, Object> asMap() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("configured", configured);
            payload.put("used", used);
            payload.put("parserSource", parserSource);
            payload.put("detectedItems", detectedItems);
            payload.put("requiredColumns", requiredColumns);
            payload.put("parsedColumns", parsedColumns);
            payload.put("missingColumns", missingColumns);
            payload.put("requiredSummaryFields", requiredSummaryFields);
            payload.put("parsedSummaryFields", parsedSummaryFields);
            payload.put("missingSummaryFields", missingSummaryFields);
            payload.put("finalBillAmount", finalBillAmount);
            payload.put("activationReady", activationReady);
            payload.put("ocrLineCount", ocrLineCount);
            payload.put("ocrPreview", ocrPreview);
            payload.put("previewItems", previewItems);
            return payload;
        }
    }
}
