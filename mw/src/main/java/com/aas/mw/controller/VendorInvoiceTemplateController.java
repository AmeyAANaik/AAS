package com.aas.mw.controller;

import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.DownloadedFile;
import com.aas.mw.dto.UploadedFileInfo;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.VendorInvoiceFieldMapping;
import com.aas.mw.dto.VendorInvoiceTemplateMappingRequest;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.client.ErpNextClient;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.OcrService;
import com.aas.mw.service.VendorInvoiceTemplateCatalog;
import com.aas.mw.service.VendorInvoiceTemplate;
import com.aas.mw.service.VendorInvoiceTemplateParser;
import com.aas.mw.service.InvoiceSummaryExtractor;
import com.aas.mw.service.InvoiceTemplateModelService;
import com.aas.mw.service.Invoice2DataExtractionService;
import com.aas.mw.service.Invoice2DataProfileCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
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
    private final ObjectMapper objectMapper;
    private final InvoiceTemplateModelService invoiceTemplateModelService;
    private final Invoice2DataProfileCatalogService invoice2DataProfileCatalogService;
    private final Invoice2DataExtractionService invoice2DataExtractionService;

    public VendorInvoiceTemplateController(
            ErpNextClient erpNextClient,
            ErpNextFileService fileService,
            OcrService ocrService,
            VendorInvoiceTemplateCatalog templateCatalog,
            VendorInvoiceTemplateParser templateParser,
            ObjectMapper objectMapper,
            InvoiceTemplateModelService invoiceTemplateModelService,
            Invoice2DataProfileCatalogService invoice2DataProfileCatalogService,
            Invoice2DataExtractionService invoice2DataExtractionService) {
        this.erpNextClient = erpNextClient;
        this.fileService = fileService;
        this.ocrService = ocrService;
        this.templateCatalog = templateCatalog;
        this.templateParser = templateParser;
        this.objectMapper = objectMapper;
        this.invoiceTemplateModelService = invoiceTemplateModelService;
        this.invoice2DataProfileCatalogService = invoice2DataProfileCatalogService;
        this.invoice2DataExtractionService = invoice2DataExtractionService;
    }

    @GetMapping("/invoice-template-profiles")
    public ResponseEntity<Map<String, Object>> listInvoiceTemplateProfiles() {
        return ResponseEntity.ok(Map.of("profiles", invoice2DataProfileCatalogService.describeProfiles()));
    }

    @PostMapping(value = "/{id}/invoice-template/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> analyzeInvoiceTemplateSample(
            @PathVariable String id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        try {
            OcrSample sample = resolveOcrSample(id, file, sessionCookie);
            return ResponseEntity.ok(buildMappingAnalysisPayload(id, sample));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unable to analyze the sample invoice PDF."));
        }
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
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sample PDF file is required."));
        }

        // Upload the sample file to ERPNext and store the link on Supplier.
        UploadedFileInfo info = fileService.uploadFile(SUPPLIER, id, resolveSampleFileName(file), file, sessionCookie);

        // Auto-detect whether the built-in strict table template fits the sample.
        String chosenKey = "";
        int detectedItems = 0;
        String ocrText = "";
        try {
            byte[] pdfBytes = file.getBytes();
            ocrText = ocrService.extractTextFromPdf(pdfBytes);
            if (ocrText != null && !ocrText.isBlank()) {
                var table = templateCatalog.byKey(VendorInvoiceTemplateCatalog.KEY_TABLE_V1);
                if (table.isPresent()) {
                    int count = templateParser.parseItems(ocrText, table.get()).size();
                    if (count > 0) {
                        chosenKey = VendorInvoiceTemplateCatalog.KEY_TABLE_V1;
                        detectedItems = count;
                    }
                }
            }
        } catch (Exception ignored) {
            chosenKey = "";
            detectedItems = 0;
        }

        TemplateValidation validation = validateTemplate(id, templateJson, ocrText, chosenKey);

        Map<String, Object> update = new HashMap<>();
        if (templateJson != null && !templateJson.trim().isBlank()) {
            update.put("aas_invoice_template_json", templateJson.trim());
            update.put("aas_invoice_template_enabled", 1);
        }
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

    @PostMapping(value = "/{id}/invoice-template/profile/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> previewInvoiceTemplateProfile(
            @PathVariable String id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart("templateProfileId") String templateProfileId,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        try {
            OcrSample sample = resolveOcrSample(id, file, sessionCookie);
            Invoice2DataProfileCatalogService.Profile profile = invoice2DataProfileCatalogService.byId(templateProfileId)
                    .orElseThrow(() -> new IllegalArgumentException("Select a supported invoice template profile."));
            return ResponseEntity.ok(buildInvoice2DataPreviewPayload(id, profile, sample));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unable to preview invoice extraction template."));
        }
    }

    @PostMapping(value = "/{id}/invoice-template/profile/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> saveInvoiceTemplateProfile(
            @PathVariable String id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart("templateProfileId") String templateProfileId,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        try {
            OcrSample sample = resolveOcrSample(id, file, sessionCookie);
            Invoice2DataProfileCatalogService.Profile profile = invoice2DataProfileCatalogService.byId(templateProfileId)
                    .orElseThrow(() -> new IllegalArgumentException("Select a supported invoice template profile."));
            Map<String, Object> preview = buildInvoice2DataPreviewPayload(id, profile, sample);
            if (!Boolean.TRUE.equals(preview.get("profileReady"))) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Preview must succeed before saving invoice extraction setup.",
                        "preview", preview));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> previewMetrics = (Map<String, Object>) preview.getOrDefault("previewMetrics", Map.of());
            String profileJson = invoice2DataExtractionService.buildProfileJson(profile, previewMetrics, sample.fileName());
            Map<String, Object> update = new HashMap<>();
            update.put("aas_invoice_template_json", profileJson);
            update.put("aas_invoice_template_enabled", 1);
            update.put("aas_invoice_template_key", "");
            if (sample.fileUrl() != null && !sample.fileUrl().isBlank()) {
                update.put("aas_invoice_template_sample_pdf", sample.fileUrl());
            }
            Map<String, Object> supplier = erpNextClient.updateResource(SUPPLIER, id, update);
            return ResponseEntity.ok(Map.of(
                    "vendor", supplier,
                    "profilePreview", preview,
                    "templateJson", profileJson,
                    "file", Map.of(
                            "fileName", sample.fileName(),
                            "fileUrl", sample.fileUrl())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unable to save invoice extraction setup."));
        }
    }

    @PostMapping(value = "/{id}/invoice-template/mapping/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> previewTemplateMapping(
            @PathVariable String id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mappingJson,
            HttpServletRequest request) {
        return doPreviewTemplateMapping(id, file, mappingJson, request);
    }

    @PostMapping(value = "/{id}/invoice-template/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> previewTemplateMappingAlias(
            @PathVariable String id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mappingJson,
            HttpServletRequest request) {
        return doPreviewTemplateMapping(id, file, mappingJson, request);
    }

    @PostMapping(value = "/{id}/invoice-template/mapping/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> saveTemplateMapping(
            @PathVariable String id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "mapping", required = false) String mappingJson,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }

        VendorInvoiceTemplateMappingRequest mapping = parseMappingRequest(mappingJson);
        OcrSample ocrSample = resolveOcrSample(id, file, sessionCookie);
        Map<String, Object> preview = buildMappingPreviewPayload(id, mapping, ocrSample.ocrText());
        if (!Boolean.TRUE.equals(preview.get("mappingReady"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Preview must succeed before saving invoice extraction setup.",
                    "preview", preview));
        }

        String generatedTemplateJson = generateTemplateJson(mapping, ocrSample.ocrText(), ocrSample.fileName());
        Map<String, Object> update = new HashMap<>();
        update.put("aas_invoice_template_json", generatedTemplateJson);
        update.put("aas_invoice_template_enabled", 1);
        update.put("aas_invoice_template_key", "");
        if (ocrSample.fileUrl() != null && !ocrSample.fileUrl().isBlank()) {
            update.put("aas_invoice_template_sample_pdf", ocrSample.fileUrl());
        }
        Map<String, Object> supplier = erpNextClient.updateResource(SUPPLIER, id, update);

        return ResponseEntity.ok(Map.of(
                "vendor", supplier,
                "mappingPreview", preview,
                "templateJson", generatedTemplateJson,
                "file", Map.of(
                        "fileName", ocrSample.fileName(),
                        "fileUrl", ocrSample.fileUrl())));
    }

    private ResponseEntity<Map<String, Object>> doPreviewTemplateMapping(
            String id,
            MultipartFile file,
            String mappingJson,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }

        VendorInvoiceTemplateMappingRequest mapping = parseMappingRequest(mappingJson);
        try {
            OcrSample ocrSample = resolveOcrSample(id, file, sessionCookie);
            return ResponseEntity.ok(buildMappingPreviewPayload(id, mapping, ocrSample.ocrText()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unable to OCR the uploaded PDF."));
        }
    }

    @DeleteMapping("/{id}/invoice-template")
    public ResponseEntity<Map<String, Object>> clearTemplate(
            @PathVariable String id,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
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
        String parserSource = "unconfigured";
        boolean configured = false;
        boolean used = false;
        Optional<VendorInvoiceTemplate> template = parseVendorTemplate(effectiveTemplateJson);
        VendorInvoiceTemplate effectiveTemplate = template.orElse(null);
        if (template.isPresent() && !sampleText.isBlank()) {
            configured = true;
            parserSource = "vendor_json";
            parsedItems = templateParser.parseItems(sampleText, template.get());
            used = !parsedItems.isEmpty();
        } else if (!fallbackKey.isBlank() && !sampleText.isBlank()) {
            var builtin = templateCatalog.byKey(fallbackKey);
            if (builtin.isPresent()) {
                configured = true;
                parserSource = fallbackKey;
                effectiveTemplate = builtin.get();
                parsedItems = templateParser.parseItems(sampleText, builtin.get());
                used = !parsedItems.isEmpty();
            }
        }
        return buildValidation(sampleText, configured, used, parserSource, parsedItems, effectiveTemplate);
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
            if (hasText(item.uom())) {
                parsedColumns.add("uom");
            }
            if (item.rate() > 0) {
                parsedColumns.add("rate");
            }
            if (item.gstPercent() != null) {
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
        SummaryExtraction finalBill = extractFinalAmount(ocrText, template);
        String finalBillAmount = finalBill.amount();
        SummaryExtraction transportCharge = extractTransportCharge(ocrText, template);
        String transportChargeAmount = transportCharge.amount();
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
                        "uom", item.uom() == null ? "" : item.uom(),
                        "rate", item.rate(),
                        "gst", item.gstPercent() == null ? "" : item.gstPercent(),
                        "total", item.amount()))
                .toList();
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
                finalBill.matchedText(),
                transportChargeAmount,
                transportCharge.matchedText(),
                activationReady,
                countNonEmptyLines(ocrText),
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
            String transportChargeRegex = parserMap.get("transportChargeRegex") == null ? null : String.valueOf(parserMap.get("transportChargeRegex"));
            if (version <= 0 || itemLineRegex.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new VendorInvoiceTemplate(version, itemLineRegex, billDateRegex, finalAmountRegex, transportChargeRegex));
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

    private Map<String, Object> buildTemplateAnalysisPayload(String vendorId, OcrSample sample) {
        VendorInvoiceTemplateMappingRequest savedMapping = loadSavedMapping(vendorId);
        List<Map<String, Object>> detectedItemColumns =
                detectColumns(sample.ocrText(), invoiceTemplateModelService.itemFieldDefinitions());
        List<Map<String, Object>> detectedSummaryColumns =
                detectColumns(sample.ocrText(), invoiceTemplateModelService.summaryFieldDefinitions());

        VendorInvoiceTemplateMappingRequest suggestedMapping =
                suggestMapping(savedMapping, detectedItemColumns, detectedSummaryColumns);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vendorId", vendorId);
        response.put("sample", Map.of(
                "fileName", sample.fileName(),
                "fileUrl", sample.fileUrl() == null ? "" : sample.fileUrl()));
        response.put("detectedColumns", Map.of(
                "items", detectedItemColumns,
                "summary", detectedSummaryColumns));
        response.put("currentMapping", Map.of(
                "itemMappings", toMappingMaps(savedMapping.itemMappings()),
                "summaryMappings", toMappingMaps(savedMapping.summaryMappings())));
        response.put("suggestedMapping", Map.of(
                "itemMappings", toMappingMaps(suggestedMapping.itemMappings()),
                "summaryMappings", toMappingMaps(suggestedMapping.summaryMappings())));
        response.put("analysisMetrics", Map.of(
                "itemsDetected", detectItemCount(sample.ocrText()),
                "totalRows", countNonEmptyLines(sample.ocrText()),
                "headerPreview", findBestCandidateHeader(sample.ocrText(), invoiceTemplateModelService.itemFieldDefinitions())));
        response.put("ocrPreview", buildOcrPreview(sample.ocrText()));
        response.put("analysisReady", !detectedItemColumns.isEmpty());
        response.put("nextStep", "Map the required business fields using the detected invoice columns, preview the extraction, then save the generated vendor template.");
        return response;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private SummaryExtraction extractFinalAmount(String ocrText, VendorInvoiceTemplate template) {
        if (ocrText == null || ocrText.isBlank() || template == null) {
            return new SummaryExtraction("", "");
        }
        InvoiceSummaryExtractor.Extraction extraction =
                InvoiceSummaryExtractor.extractFinalAmount(ocrText, asText(template.finalAmountRegex()));
        return new SummaryExtraction(extraction.amount(), extraction.matchedText());
    }

    private SummaryExtraction extractTransportCharge(String ocrText, VendorInvoiceTemplate template) {
        if (ocrText == null || ocrText.isBlank() || template == null || !hasText(template.transportChargeRegex())) {
            return new SummaryExtraction("", "");
        }
        InvoiceSummaryExtractor.Extraction extraction =
                InvoiceSummaryExtractor.extractFinalAmount(ocrText, asText(template.transportChargeRegex()));
        return new SummaryExtraction(extraction.amount(), extraction.matchedText());
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

    private VendorInvoiceTemplateMappingRequest parseMappingRequest(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
        }
        try {
            VendorInvoiceTemplateMappingRequest request = objectMapper.readValue(mappingJson, VendorInvoiceTemplateMappingRequest.class);
            return request == null
                    ? new VendorInvoiceTemplateMappingRequest(List.of(), List.of())
                    : request;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid mapping payload.");
        }
    }

    private Map<String, Object> buildMappingAnalysisPayload(String vendorId, OcrSample sample) {
        Map<String, Object> supplier = unwrapResource(erpNextClient.getResource(SUPPLIER, vendorId));
        VendorInvoiceTemplateMappingRequest savedMapping = parseStoredMapping(asText(supplier.get("aas_invoice_template_json")));
        List<Map<String, Object>> detectedItemColumns = detectItemColumns(sample.ocrText());
        List<Map<String, Object>> detectedSummaryFields = detectSummaryFields(sample.ocrText());
        VendorInvoiceTemplateMappingRequest suggestedMapping =
                savedMapping.itemMappings().isEmpty() && savedMapping.summaryMappings().isEmpty()
                        ? suggestMappings(detectedItemColumns, detectedSummaryFields)
                        : savedMapping;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vendorId", vendorId);
        response.put("sample", Map.of(
                "fileName", sample.fileName(),
                "fileUrl", sample.fileUrl() == null ? "" : sample.fileUrl()));
        response.put("ocrLineCount", countNonEmptyLines(sample.ocrText()));
        response.put("ocrPreview", buildOcrPreview(sample.ocrText()));
        response.put("requiredFields", Map.of(
                "items", invoiceTemplateModelService.requiredItemKeys(),
                "summary", invoiceTemplateModelService.requiredSummaryKeys()));
        response.put("detectedColumns", Map.of(
                "items", detectedItemColumns,
                "summary", detectedSummaryFields));
        response.put("savedMapping", Map.of(
                "itemMappings", toMappingMaps(savedMapping.itemMappings()),
                "summaryMappings", toMappingMaps(savedMapping.summaryMappings())));
        response.put("suggestedMapping", Map.of(
                "itemMappings", toMappingMaps(suggestedMapping.itemMappings()),
                "summaryMappings", toMappingMaps(suggestedMapping.summaryMappings())));
        response.put("previewMetrics", Map.of(
                "itemsDetected", detectItemCount(sample.ocrText()),
                "totalRows", countNonEmptyLines(sample.ocrText()),
                "billAmount", detectSummaryAmount(sample.ocrText(), List.of("bill amount", "invoice total", "grand total", "net amount", "total")),
                "transportCharge", detectSummaryAmount(sample.ocrText(), List.of("transport", "freight", "delivery charge", "shipping", "cartage"))));
        response.put("nextStep", "Map required business fields to the detected invoice labels, preview the extraction result, then save the generated vendor template.");
        return response;
    }

    private VendorInvoiceTemplateMappingRequest parseStoredMapping(String templateJson) {
        if (!hasText(templateJson)) {
            return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(templateJson, new TypeReference<Map<String, Object>>() {});
            Object mappingObject = parsed.get("mapping");
            if (!(mappingObject instanceof Map<?, ?> mappingMap)) {
                return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
            }
            List<VendorInvoiceFieldMapping> itemMappings = readStoredMappings(mappingMap.get("itemMappings"));
            List<VendorInvoiceFieldMapping> summaryMappings = readStoredMappings(mappingMap.get("summaryMappings"));
            return new VendorInvoiceTemplateMappingRequest(itemMappings, summaryMappings);
        } catch (Exception ex) {
            return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
        }
    }

    private List<VendorInvoiceFieldMapping> readStoredMappings(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<VendorInvoiceFieldMapping> mappings = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String targetField = asText(map.get("targetField"));
            String sourceLabel = asText(map.get("sourceLabel"));
            if (targetField.isBlank() || sourceLabel.isBlank()) {
                continue;
            }
            mappings.add(new VendorInvoiceFieldMapping(targetField, sourceLabel));
        }
        return mappings;
    }

    private VendorInvoiceTemplateMappingRequest suggestMappings(
            List<Map<String, Object>> detectedItemColumns,
            List<Map<String, Object>> detectedSummaryFields) {
        List<VendorInvoiceFieldMapping> itemMappings = new ArrayList<>();
        for (Map<String, Object> candidate : detectedItemColumns) {
            String targetField = asText(candidate.get("targetField"));
            String sourceLabel = asText(candidate.get("value"));
            if (targetField.isBlank() || sourceLabel.isBlank()) {
                continue;
            }
            itemMappings.add(new VendorInvoiceFieldMapping(targetField, sourceLabel));
        }
        List<VendorInvoiceFieldMapping> summaryMappings = new ArrayList<>();
        for (Map<String, Object> candidate : detectedSummaryFields) {
            String targetField = asText(candidate.get("targetField"));
            String sourceLabel = asText(candidate.get("value"));
            if (targetField.isBlank() || sourceLabel.isBlank()) {
                continue;
            }
            summaryMappings.add(new VendorInvoiceFieldMapping(targetField, sourceLabel));
        }
        return new VendorInvoiceTemplateMappingRequest(itemMappings, summaryMappings);
    }

    private List<Map<String, Object>> detectItemColumns(String ocrText) {
        if (!hasText(ocrText)) {
            return List.of();
        }
        String headerLine = findBestDetectedHeaderLine(ocrText);
        if (!hasText(headerLine)) {
            return List.of();
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        Set<String> seenValues = new LinkedHashSet<>();
        for (FieldAliasCandidate candidate : itemFieldCandidates()) {
            String matchedLabel = findBestLabelInLine(headerLine, candidate.aliases());
            if (!hasText(matchedLabel) || !seenValues.add(normalizeSearchText(matchedLabel))) {
                continue;
            }
            columns.add(Map.of(
                    "value", matchedLabel,
                    "label", matchedLabel,
                    "targetField", candidate.targetField(),
                    "required", invoiceTemplateModelService.requiredItemKeys().contains(candidate.targetField())));
        }
        return columns;
    }

    private List<Map<String, Object>> detectSummaryFields(String ocrText) {
        if (!hasText(ocrText)) {
            return List.of();
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        Set<String> seenValues = new LinkedHashSet<>();
        for (FieldAliasCandidate candidate : summaryFieldCandidates()) {
            String matchedLabel = findBestSummaryLabel(ocrText, candidate.aliases());
            if (!hasText(matchedLabel) || !seenValues.add(normalizeSearchText(matchedLabel))) {
                continue;
            }
            fields.add(Map.of(
                    "value", matchedLabel,
                    "label", matchedLabel,
                    "targetField", candidate.targetField(),
                    "required", invoiceTemplateModelService.requiredSummaryKeys().contains(candidate.targetField())));
        }
        return fields;
    }

    private String findBestDetectedHeaderLine(String ocrText) {
        String bestLine = "";
        int bestScore = 0;
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            int score = scoreHeaderLine(line);
            if (score > bestScore) {
                bestScore = score;
                bestLine = line;
            }
        }
        return bestLine;
    }

    private int scoreHeaderLine(String line) {
        String normalized = normalizeSearchText(line);
        if (normalized.isBlank()) {
            return 0;
        }
        int score = 0;
        int matches = 0;
        for (FieldAliasCandidate candidate : itemFieldCandidates()) {
            if (findBestLabelInLine(line, candidate.aliases()).isBlank()) {
                continue;
            }
            matches++;
            score += 12;
        }
        if (containsWholeToken(normalized, "hsn") || containsWholeToken(normalized, "qty")
                || containsWholeToken(normalized, "quantity") || containsWholeToken(normalized, "amount")
                || containsWholeToken(normalized, "rate")) {
            score += 8;
        }
        if (matches >= 3) {
            score += 20;
        }
        return score;
    }

    private String findBestLabelInLine(String line, List<String> aliases) {
        if (!hasText(line) || aliases == null || aliases.isEmpty()) {
            return "";
        }
        String normalizedLine = normalizeSearchText(line);
        String bestAlias = "";
        for (String alias : aliases) {
            String normalizedAlias = normalizeSearchText(alias);
            if (normalizedAlias.isBlank()) {
                continue;
            }
            if (containsWholePhrase(normalizedLine, normalizedAlias) || normalizedLine.contains(normalizedAlias)) {
                if (normalizedAlias.length() > normalizeSearchText(bestAlias).length()) {
                    bestAlias = alias;
                }
            }
        }
        return bestAlias;
    }

    private String findBestSummaryLabel(String ocrText, List<String> aliases) {
        String bestLabel = "";
        int bestScore = 0;
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            for (String alias : aliases) {
                String normalizedAlias = normalizeSearchText(alias);
                if (normalizedAlias.isBlank()) {
                    continue;
                }
                int score = scoreLineMatch(line, normalizedAlias);
                if (score <= 0) {
                    continue;
                }
                if (!hasText(extractInlineAmount(line)) && parseAmount(line) <= 0) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestLabel = alias;
                }
            }
        }
        return bestLabel;
    }

    private String detectSummaryAmount(String ocrText, List<String> aliases) {
        String label = findBestSummaryLabel(ocrText, aliases);
        if (!hasText(label)) {
            return "";
        }
        String matchedLine = findMatchingOcrLine(ocrText, label);
        return extractInlineAmount(matchedLine);
    }

    private List<FieldAliasCandidate> itemFieldCandidates() {
        return List.of(
                new FieldAliasCandidate("item_name", List.of("description of goods", "particulars", "description", "item name", "item")),
                new FieldAliasCandidate("item_id", List.of("hsn/sac", "hsn code", "hsn", "item code", "item no")),
                new FieldAliasCandidate("gst", List.of("gst rate", "gst", "tax(%)", "tax %", "tax percent")),
                new FieldAliasCandidate("qty", List.of("quantity", "qty")),
                new FieldAliasCandidate("uom", List.of("uom", "unit", "per")),
                new FieldAliasCandidate("mrp", List.of("mrp")),
                new FieldAliasCandidate("rate", List.of("rate after tax", "rate(incl. of tax)", "rate incl. of tax", "unit rate", "rate")),
                new FieldAliasCandidate("total", List.of("total value after tax", "total value", "line total", "amount", "total")));
    }

    private List<FieldAliasCandidate> summaryFieldCandidates() {
        return List.of(
                new FieldAliasCandidate("final_bill_amount", List.of("bill amount", "invoice total", "grand total", "net amount", "total")),
                new FieldAliasCandidate("transport_charge", List.of("transport", "freight", "delivery charge", "shipping", "cartage")));
    }

    private VendorInvoiceTemplateMappingRequest loadSavedMapping(String vendorId) {
        Map<String, Object> supplier = unwrapResource(erpNextClient.getResource(SUPPLIER, vendorId));
        String templateJson = asText(supplier.get("aas_invoice_template_json"));
        if (!hasText(templateJson)) {
            return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(templateJson, new TypeReference<Map<String, Object>>() {});
            Object mappingObj = payload.get("mapping");
            if (!(mappingObj instanceof Map<?, ?> mappingMap)) {
                return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
            }
            Object itemMappings = mappingMap.get("itemMappings");
            Object summaryMappings = mappingMap.get("summaryMappings");
            List<VendorInvoiceFieldMapping> itemFields = parseStoredMappings(itemMappings);
            List<VendorInvoiceFieldMapping> summaryFields = parseStoredMappings(summaryMappings);
            return new VendorInvoiceTemplateMappingRequest(itemFields, summaryFields);
        } catch (Exception ignored) {
            return new VendorInvoiceTemplateMappingRequest(List.of(), List.of());
        }
    }

    @SuppressWarnings("unchecked")
    private List<VendorInvoiceFieldMapping> parseStoredMappings(Object rawMappings) {
        if (!(rawMappings instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<VendorInvoiceFieldMapping> mappings = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> map = (Map<String, Object>) rawMap;
            String targetField = asText(map.get("targetField"));
            String sourceLabel = asText(map.get("sourceLabel"));
            if (!targetField.isBlank() && !sourceLabel.isBlank()) {
                mappings.add(new VendorInvoiceFieldMapping(targetField, sourceLabel));
            }
        }
        return mappings;
    }

    private VendorInvoiceTemplateMappingRequest suggestMapping(
            VendorInvoiceTemplateMappingRequest savedMapping,
            List<Map<String, Object>> detectedItemColumns,
            List<Map<String, Object>> detectedSummaryColumns) {
        List<VendorInvoiceFieldMapping> itemMappings = suggestMappingsForFields(
                invoiceTemplateModelService.itemFieldDefinitions(),
                savedMapping.itemMappings(),
                detectedItemColumns);
        List<VendorInvoiceFieldMapping> summaryMappings = suggestMappingsForFields(
                invoiceTemplateModelService.summaryFieldDefinitions(),
                savedMapping.summaryMappings(),
                detectedSummaryColumns);
        return new VendorInvoiceTemplateMappingRequest(itemMappings, summaryMappings);
    }

    private List<VendorInvoiceFieldMapping> suggestMappingsForFields(
            List<InvoiceTemplateModelProperties.TemplateField> fieldDefinitions,
            List<VendorInvoiceFieldMapping> savedMappings,
            List<Map<String, Object>> detectedColumns) {
        Map<String, String> savedByTarget = new LinkedHashMap<>();
        for (VendorInvoiceFieldMapping mapping : savedMappings) {
            if (mapping == null || !hasText(mapping.targetField()) || !hasText(mapping.sourceLabel())) {
                continue;
            }
            savedByTarget.put(mapping.targetField(), mapping.sourceLabel());
        }
        Set<String> usedLabels = new LinkedHashSet<>();
        List<VendorInvoiceFieldMapping> suggestions = new ArrayList<>();
        for (InvoiceTemplateModelProperties.TemplateField field : fieldDefinitions) {
            String targetField = asText(field.getKey());
            String saved = savedByTarget.getOrDefault(targetField, "");
            if (hasText(saved)) {
                suggestions.add(new VendorInvoiceFieldMapping(targetField, saved));
                usedLabels.add(saved);
                continue;
            }
            String detected = findDetectedColumnForField(targetField, detectedColumns, usedLabels);
            if (!detected.isBlank()) {
                suggestions.add(new VendorInvoiceFieldMapping(targetField, detected));
                usedLabels.add(detected);
            }
        }
        return suggestions;
    }

    private String findDetectedColumnForField(
            String fieldKey,
            List<Map<String, Object>> detectedColumns,
            Set<String> usedLabels) {
        for (Map<String, Object> column : detectedColumns) {
            String label = asText(column.get("label"));
            if (label.isBlank() || usedLabels.contains(label)) {
                continue;
            }
            Object matched = column.get("matchedFieldKeys");
            if (!(matched instanceof List<?> matches)) {
                continue;
            }
            for (Object entry : matches) {
                if (fieldKey.equals(asText(entry))) {
                    return label;
                }
            }
        }
        return "";
    }

    private List<Map<String, Object>> detectColumns(
            String ocrText,
            List<InvoiceTemplateModelProperties.TemplateField> fieldDefinitions) {
        if (!hasText(ocrText) || fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return List.of();
        }
        List<String> candidateLines = findCandidateHeaderLines(ocrText, fieldDefinitions);
        Map<String, Set<String>> detectedToFields = new LinkedHashMap<>();
        for (InvoiceTemplateModelProperties.TemplateField field : fieldDefinitions) {
            for (String alias : columnAliases(field)) {
                String normalizedAlias = normalizeSearchText(alias);
                if (normalizedAlias.isBlank()) {
                    continue;
                }
                boolean matched = candidateLines.stream()
                        .map(this::normalizeSearchText)
                        .anyMatch(line -> containsWholePhrase(line, normalizedAlias) || containsWholeToken(line, normalizedAlias));
                if (!matched) {
                    continue;
                }
                detectedToFields.computeIfAbsent(alias, key -> new LinkedHashSet<>()).add(field.getKey());
                break;
            }
        }
        return detectedToFields.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "id", entry.getKey(),
                        "label", humanizeColumnLabel(entry.getKey()),
                        "matchedFieldKeys", new ArrayList<>(entry.getValue())))
                .sorted(Comparator.comparing(entry -> asText(entry.get("label"))))
                .toList();
    }

    private List<String> findCandidateHeaderLines(
            String ocrText,
            List<InvoiceTemplateModelProperties.TemplateField> fieldDefinitions) {
        List<String> lines = nonEmptyLines(ocrText);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> candidates = new ArrayList<>();
        int bestScore = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String combined = index + 1 < lines.size() ? line + " " + lines.get(index + 1) : line;
            int lineScore = scoreHeaderCandidate(line, fieldDefinitions);
            int combinedScore = scoreHeaderCandidate(combined, fieldDefinitions);
            if (lineScore > 0 && lineScore >= bestScore - 5) {
                candidates.add(line);
                bestScore = Math.max(bestScore, lineScore);
            }
            if (combinedScore > 0 && combinedScore >= bestScore - 5) {
                candidates.add(combined);
                bestScore = Math.max(bestScore, combinedScore);
            }
        }
        if (candidates.isEmpty()) {
            String fallback = findBestScoredHeader(lines, fieldDefinitions);
            return fallback.isBlank() ? List.of() : List.of(fallback);
        }
        return candidates.stream().distinct().limit(4).toList();
    }

    private String findBestCandidateHeader(
            String ocrText,
            List<InvoiceTemplateModelProperties.TemplateField> fieldDefinitions) {
        return findBestScoredHeader(nonEmptyLines(ocrText), fieldDefinitions);
    }

    private String findBestScoredHeader(
            List<String> lines,
            List<InvoiceTemplateModelProperties.TemplateField> fieldDefinitions) {
        String best = "";
        int bestScore = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String combined = index + 1 < lines.size() ? line + " " + lines.get(index + 1) : line;
            int lineScore = scoreHeaderCandidate(line, fieldDefinitions);
            if (lineScore > bestScore) {
                bestScore = lineScore;
                best = line;
            }
            int combinedScore = scoreHeaderCandidate(combined, fieldDefinitions);
            if (combinedScore > bestScore) {
                bestScore = combinedScore;
                best = combined;
            }
        }
        return best;
    }

    private int scoreHeaderCandidate(
            String line,
            List<InvoiceTemplateModelProperties.TemplateField> fieldDefinitions) {
        String normalized = normalizeSearchText(line);
        if (normalized.isBlank()) {
            return 0;
        }
        int score = 0;
        for (InvoiceTemplateModelProperties.TemplateField field : fieldDefinitions) {
            for (String alias : columnAliases(field)) {
                String normalizedAlias = normalizeSearchText(alias);
                if (normalizedAlias.isBlank()) {
                    continue;
                }
                if (containsWholePhrase(normalized, normalizedAlias) || containsWholeToken(normalized, normalizedAlias)) {
                    score += normalizedAlias.length() > 5 ? 10 : 6;
                    break;
                }
            }
        }
        return score;
    }

    private List<String> columnAliases(InvoiceTemplateModelProperties.TemplateField field) {
        List<String> aliases = new ArrayList<>();
        if (field == null) {
            return aliases;
        }
        aliases.add(asText(field.getLabel()));
        if (field.getSourceAliases() != null) {
            for (String alias : field.getSourceAliases()) {
                if (hasText(alias)) {
                    aliases.add(alias.trim());
                }
            }
        }
        return aliases.stream().filter(this::hasText).distinct().toList();
    }

    private String humanizeColumnLabel(String label) {
        if (!hasText(label)) {
            return "";
        }
        String compact = label.trim();
        String[] parts = compact.split("\\s+");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.length() <= 3 && part.equals(part.toUpperCase(Locale.ROOT))) {
                words.add(part);
                continue;
            }
            if (part.contains("/") || part.contains("%") || part.contains("(")) {
                words.add(part);
                continue;
            }
            words.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", words);
    }

    private List<String> nonEmptyLines(String ocrText) {
        if (!hasText(ocrText)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<String> detectParsedItemFields(List<ParsedItem> parsedItems) {
        Set<String> parsedFields = new LinkedHashSet<>();
        for (ParsedItem item : parsedItems) {
            if (item == null) {
                continue;
            }
            if (hasText(item.name())) {
                parsedFields.add("item_name");
            }
            if (hasText(item.hsn())) {
                parsedFields.add("item_id");
            }
            if (item.qty() > 0) {
                parsedFields.add("qty");
            }
            if (hasText(item.uom())) {
                parsedFields.add("uom");
            }
            if (item.rate() > 0) {
                parsedFields.add("rate");
            }
            if (item.gstPercent() != null) {
                parsedFields.add("gst");
            }
            if (item.amount() > 0) {
                parsedFields.add("total");
            }
        }
        return new ArrayList<>(parsedFields);
    }

    private List<String> detectParsedSummaryFields(String ocrText, VendorInvoiceTemplate template) {
        List<String> fields = new ArrayList<>();
        if (hasText(extractFinalAmount(ocrText, template).amount())) {
            fields.add("final_bill_amount");
        }
        if (hasText(extractTransportCharge(ocrText, template).amount())) {
            fields.add("transport_charge");
        }
        return fields;
    }

    private Map<String, Object> buildMappingPreviewPayload(
            String vendorId,
            VendorInvoiceTemplateMappingRequest mapping,
            String ocrText) {
        List<String> requiredItemFields = invoiceTemplateModelService.requiredItemKeys();
        List<String> requiredSummaryFields = invoiceTemplateModelService.requiredSummaryKeys();
        List<String> mappedItemFields = distinctTargetFields(mapping.itemMappings());
        List<String> mappedSummaryFields = distinctTargetFields(mapping.summaryMappings());
        List<String> unmappedItemFields = requiredItemFields.stream().filter(field -> !mappedItemFields.contains(field)).toList();
        List<String> unmappedSummaryFields = requiredSummaryFields.stream().filter(field -> !mappedSummaryFields.contains(field)).toList();
        List<Map<String, Object>> itemChecks = toMappingChecks(mapping.itemMappings(), ocrText);
        List<Map<String, Object>> summaryChecks = toMappingChecks(mapping.summaryMappings(), ocrText);
        int ocrLineCount = countNonEmptyLines(ocrText);
        int analyzedItemCount = detectItemCount(ocrText);
        int detectedItems = analyzedItemCount;
        List<String> parsedItemFields = List.of();
        List<String> parsedSummaryFields = List.of();
        List<String> missingItemFields = new ArrayList<>(unmappedItemFields);
        List<String> missingSummaryFields = new ArrayList<>(unmappedSummaryFields);
        List<Map<String, Object>> previewItems = List.of();
        List<String> validationWarnings = new ArrayList<>();
        String billAmount = extractSummaryAmount(summaryChecks, "final_bill_amount");
        String transportCharge = extractSummaryAmount(summaryChecks, "transport_charge");

        if (unmappedItemFields.isEmpty() && unmappedSummaryFields.isEmpty()) {
            try {
                String generatedTemplateJson = generateTemplateJson(mapping, ocrText, null);
                Optional<VendorInvoiceTemplate> template = parseVendorTemplate(generatedTemplateJson);
                if (template.isPresent()) {
                    List<ParsedItem> parsedItems = templateParser.parseItems(ocrText, template.get());
                    detectedItems = parsedItems.size();
                    parsedItemFields = detectParsedItemFields(parsedItems);
                    parsedSummaryFields = detectParsedSummaryFields(ocrText, template.get());
                    List<String> parsedItemFieldsSnapshot = parsedItemFields;
                    List<String> parsedSummaryFieldsSnapshot = parsedSummaryFields;
                    missingItemFields = requiredItemFields.stream()
                            .filter(field -> !parsedItemFieldsSnapshot.contains(field))
                            .toList();
                    missingSummaryFields = requiredSummaryFields.stream()
                            .filter(field -> !parsedSummaryFieldsSnapshot.contains(field))
                            .toList();
                    previewItems = parsedItems.stream()
                            .limit(8)
                            .map(item -> Map.<String, Object>of(
                                    "item_name", item.name() == null ? "" : item.name(),
                                    "item_id", item.hsn() == null ? "" : item.hsn(),
                                    "qty", item.qty(),
                                    "uom", item.uom() == null ? "" : item.uom(),
                                    "rate", item.rate(),
                                    "gst", item.gstPercent() == null ? "" : item.gstPercent(),
                                    "total", item.amount()))
                            .toList();
                    billAmount = extractFinalAmount(ocrText, template.get()).amount();
                    transportCharge = extractTransportCharge(ocrText, template.get()).amount();
                }
            } catch (Exception ignored) {
                // Keep the response useful even when the generated parser is not previewable yet.
            }
        }

        boolean rowCountLooksReasonable = analyzedItemCount <= 0
                || detectedItems >= analyzedItemCount
                || detectedItems >= Math.max(3, (int) Math.ceil(analyzedItemCount * 0.6));
        if (!rowCountLooksReasonable) {
            validationWarnings.add(String.format(
                    Locale.ROOT,
                    "Generated template extracted %d item rows, but the sample analysis detected %d. Review the mapping or use a matching invoice sample.",
                    detectedItems,
                    analyzedItemCount));
        }
        boolean mappingReady = !previewItems.isEmpty()
                && missingItemFields.isEmpty()
                && missingSummaryFields.isEmpty()
                && rowCountLooksReasonable;

        Map<String, Object> response = new HashMap<>();
        response.put("vendorId", vendorId);
        response.put("ocrLineCount", ocrLineCount);
        response.put("ocrPreview", buildOcrPreview(ocrText));
        response.put("requiredFields", Map.of(
                "items", requiredItemFields,
                "summary", requiredSummaryFields));
        response.put("mappedFields", Map.of(
                "items", mappedItemFields,
                "summary", mappedSummaryFields));
        response.put("parsedFields", Map.of(
                "items", parsedItemFields,
                "summary", parsedSummaryFields));
        response.put("missingFields", Map.of(
                "items", missingItemFields,
                "summary", missingSummaryFields));
        response.put("mapping", Map.of(
                "itemMappings", toMappingMaps(mapping.itemMappings()),
                "summaryMappings", toMappingMaps(mapping.summaryMappings())));
        response.put("mappingChecks", Map.of(
                "items", itemChecks,
                "summary", summaryChecks));
        response.put("previewMetrics", Map.of(
                "itemsDetected", detectedItems,
                "analyzedItemsDetected", analyzedItemCount,
                "itemFieldsMatched", parsedItemFields.isEmpty()
                        ? countMatchedChecks(itemChecks)
                        : requiredItemFields.size() - missingItemFields.size(),
                "totalRows", ocrLineCount,
                "billAmount", billAmount,
                "transportCharge", transportCharge));
        response.put("previewItems", previewItems);
        response.put("validationWarnings", validationWarnings);
        response.put("mappingReady", mappingReady);
        response.put("nextStep", "Review the extracted rows, row count, and summary values. Save only when the generated vendor template matches this sample invoice.");
        return response;
    }

    private Map<String, Object> buildInvoice2DataPreviewPayload(
            String vendorId,
            Invoice2DataProfileCatalogService.Profile profile,
            OcrSample sample) {
        Invoice2DataExtractionService.ExtractionResult extraction = invoice2DataExtractionService.extract(
                sample.pdfBytes(),
                new Invoice2DataExtractionService.Invoice2DataProfile(
                        profile.id(),
                        profile.label(),
                        profile.inputReader(),
                        profile.yaml()));

        Set<String> parsedItemFields = new LinkedHashSet<>();
        for (ParsedItem item : extraction.items()) {
            if (item == null) {
                continue;
            }
            if (hasText(item.name())) {
                parsedItemFields.add("item_name");
            }
            if (hasText(item.hsn())) {
                parsedItemFields.add("item_id");
            }
            if (item.qty() > 0) {
                parsedItemFields.add("qty");
            }
            if (hasText(item.uom())) {
                parsedItemFields.add("uom");
            }
            if (item.rate() > 0) {
                parsedItemFields.add("rate");
            }
            if (item.gstPercent() != null || profile.supportedItemFields().contains("gst")) {
                parsedItemFields.add("gst");
            }
            if (item.mrp() != null && item.mrp() > 0) {
                parsedItemFields.add("mrp");
            }
            if (item.amount() > 0) {
                parsedItemFields.add("total");
            }
        }

        List<String> requiredItemFields = invoiceTemplateModelService.requiredItemKeys();
        List<String> requiredSummaryFields = invoiceTemplateModelService.requiredSummaryKeys();
        List<String> missingItemFields = requiredItemFields.stream()
                .filter(field -> !parsedItemFields.contains(field))
                .toList();
        List<String> parsedSummaryFields = new ArrayList<>();
        if (hasText(extraction.finalAmount())) {
            parsedSummaryFields.add("final_bill_amount");
        }
        if (hasText(extraction.transportCharge())) {
            parsedSummaryFields.add("transport_charge");
        }
        List<String> missingSummaryFields = requiredSummaryFields.stream()
                .filter(field -> !parsedSummaryFields.contains(field))
                .toList();

        List<Map<String, Object>> previewItems = extraction.items().stream()
                .limit(8)
                .map(item -> Map.<String, Object>of(
                        "item_name", item.name(),
                        "item_id", item.hsn() == null ? "" : item.hsn(),
                        "qty", item.qty(),
                        "uom", item.uom() == null ? "" : item.uom(),
                        "rate", item.rate(),
                        "gst", item.gstPercent() == null ? "" : item.gstPercent(),
                        "total", item.amount()))
                .toList();

        Map<String, Object> previewMetrics = new LinkedHashMap<>();
        previewMetrics.put("itemsDetected", extraction.items().size());
        previewMetrics.put("itemFieldsMatched", requiredItemFields.size() - missingItemFields.size());
        previewMetrics.put("totalRows", countNonEmptyLines(sample.ocrText()));
        previewMetrics.put("billAmount", extraction.finalAmount());
        previewMetrics.put("transportCharge", extraction.transportCharge());
        previewMetrics.put("invoiceNumber", extraction.invoiceNumber());
        previewMetrics.put("invoiceDate", extraction.invoiceDate());

        Map<String, Object> sampleMap = new LinkedHashMap<>();
        sampleMap.put("fileName", sample.fileName());
        sampleMap.put("fileUrl", sample.fileUrl() == null ? "" : sample.fileUrl());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vendorId", vendorId);
        response.put("engineType", "invoice2data");
        response.put("templateProfile", Map.of(
                "id", profile.id(),
                "label", profile.label(),
                "vendorName", profile.vendorName(),
                "description", profile.description(),
                "supportedItemFields", profile.supportedItemFields(),
                "supportedSummaryFields", profile.supportedSummaryFields()));
        response.put("sample", sampleMap);
        response.put("ocrLineCount", countNonEmptyLines(sample.ocrText()));
        response.put("ocrPreview", buildOcrPreview(sample.ocrText()));
        response.put("previewMetrics", previewMetrics);
        response.put("requiredFields", Map.of(
                "items", requiredItemFields,
                "summary", requiredSummaryFields));
        response.put("parsedFields", Map.of(
                "items", new ArrayList<>(parsedItemFields),
                "summary", parsedSummaryFields));
        response.put("missingFields", Map.of(
                "items", missingItemFields,
                "summary", missingSummaryFields));
        response.put("previewItems", previewItems);
        response.put("profileReady", missingItemFields.isEmpty() && missingSummaryFields.isEmpty());
        response.put("nextStep", "Review the extracted rows and save this vendor parser profile when the output matches the invoice.");
        return response;
    }

    private OcrSample resolveOcrSample(String vendorId, MultipartFile file, String sessionCookie) {
        try {
            if (file != null && !file.isEmpty()) {
                UploadedFileInfo uploaded = fileService.uploadFile(SUPPLIER, vendorId, resolveSampleFileName(file), file, sessionCookie);
                byte[] pdfBytes = file.getBytes();
                String ocrText = ocrService.extractTextFromPdf(pdfBytes);
                return new OcrSample(
                        uploaded.fileName(),
                        uploaded.fileUrl(),
                        ocrText == null ? "" : ocrText,
                        pdfBytes);
            }
            Map<String, Object> supplier = unwrapResource(erpNextClient.getResource(SUPPLIER, vendorId));
            String fileUrl = asText(supplier.get("aas_invoice_template_sample_pdf"));
            if (fileUrl.isBlank()) {
                throw new IllegalArgumentException("Sample PDF is required to save invoice extraction setup.");
            }
            DownloadedFile downloaded = fileService.downloadFile(fileUrl);
            String ocrText = ocrService.extractTextFromPdf(downloaded.bytes());
            return new OcrSample(downloaded.fileName(), fileUrl, ocrText == null ? "" : ocrText, downloaded.bytes());
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read and OCR the sample invoice PDF.");
        }
    }

    private String generateTemplateJson(VendorInvoiceTemplateMappingRequest mapping, String ocrText, String sampleFileName) {
        String itemLineRegex = buildItemLineRegex(mapping.itemMappings(), ocrText);
        if (itemLineRegex.isBlank()) {
            throw new IllegalStateException("Unable to generate item extraction rules from the mapped invoice labels.");
        }
        String finalAmountRegex = buildSummaryRegex(findSourceLabel(mapping.summaryMappings(), "final_bill_amount"));
        if (finalAmountRegex.isBlank()) {
            throw new IllegalStateException("Final bill amount mapping is required before saving invoice extraction setup.");
        }
        String transportChargeRegex = buildSummaryRegex(findSourceLabel(mapping.summaryMappings(), "transport_charge"));
        Map<String, Object> parser = new HashMap<>();
        parser.put("version", 1);
        parser.put("itemLineRegex", itemLineRegex);
        parser.put("finalAmountRegex", finalAmountRegex);
        if (!transportChargeRegex.isBlank()) {
            parser.put("transportChargeRegex", transportChargeRegex);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("kind", "field_mapping_generated");
        payload.put("mapping", Map.of(
                "itemMappings", toMappingMaps(mapping.itemMappings()),
                "summaryMappings", toMappingMaps(mapping.summaryMappings())));
        payload.put("parser", parser);
        if (sampleFileName != null && !sampleFileName.isBlank()) {
            payload.put("sample", Map.of("fileName", sampleFileName));
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate vendor parser configuration.");
        }
    }

    private String resolveSampleFileName(MultipartFile file) {
        String original = file == null ? "" : asText(file.getOriginalFilename());
        if (original.isBlank()) {
            return "invoice_template.pdf";
        }
        String normalized = original
                .replace("\\", "_")
                .replace("/", "_")
                .replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalized.isBlank()) {
            return "invoice_template.pdf";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") ? normalized : normalized + ".pdf";
    }

    private String buildItemLineRegex(List<VendorInvoiceFieldMapping> itemMappings, String ocrText) {
        String headerLine = findBestHeaderLine(itemMappings, ocrText);
        List<VendorInvoiceFieldMapping> orderedMappings = orderItemMappings(itemMappings, headerLine);
        if (orderedMappings.isEmpty()) {
            return "";
        }
        StringBuilder regex = new StringBuilder("^(?:\\d+\\s+)?");
        boolean first = true;
        for (VendorInvoiceFieldMapping mapping : orderedMappings) {
            String tokenRegex = tokenRegexForField(asText(mapping.targetField()));
            if (tokenRegex.isBlank()) {
                continue;
            }
            if (!first) {
                regex.append("\\s+(?:\\S+\\s+){0,2}");
            }
            regex.append(tokenRegex);
            first = false;
        }
        if (first) {
            return "";
        }
        regex.append("$");
        return regex.toString();
    }

    private String findBestHeaderLine(List<VendorInvoiceFieldMapping> itemMappings, String ocrText) {
        if (!hasText(ocrText) || itemMappings == null || itemMappings.isEmpty()) {
            return "";
        }
        String bestLine = "";
        int bestScore = 0;
        List<String> lines = nonEmptyLines(ocrText);
        for (int index = 0; index < lines.size(); index++) {
            String line = asText(lines.get(index));
            if (line.isBlank()) {
                continue;
            }
            String combined = index + 1 < lines.size() ? line + " " + lines.get(index + 1) : line;
            int lineScore = scoreHeaderLineForMappings(line, itemMappings);
            if (lineScore > bestScore) {
                bestScore = lineScore;
                bestLine = line;
            }
            int combinedScore = scoreHeaderLineForMappings(combined, itemMappings);
            if (combinedScore > bestScore) {
                bestScore = combinedScore;
                bestLine = combined;
            }
        }
        return bestLine;
    }

    private int scoreHeaderLineForMappings(String line, List<VendorInvoiceFieldMapping> itemMappings) {
        if (!hasText(line) || itemMappings == null || itemMappings.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (VendorInvoiceFieldMapping mapping : itemMappings) {
            score += scoreLineMatch(line, normalizeSearchText(asText(mapping.sourceLabel())));
        }
        return score;
    }

    private List<VendorInvoiceFieldMapping> orderItemMappings(List<VendorInvoiceFieldMapping> itemMappings, String headerLine) {
        if (itemMappings == null || itemMappings.isEmpty()) {
            return List.of();
        }
        String normalizedHeader = normalizeSearchText(headerLine);
        return itemMappings.stream()
                .filter(mapping -> mapping != null && hasText(mapping.targetField()))
                .sorted((left, right) -> Integer.compare(
                        labelPosition(normalizedHeader, asText(left.sourceLabel())),
                        labelPosition(normalizedHeader, asText(right.sourceLabel()))))
                .toList();
    }

    private int labelPosition(String normalizedHeader, String sourceLabel) {
        if (!hasText(normalizedHeader) || !hasText(sourceLabel)) {
            return Integer.MAX_VALUE;
        }
        String normalizedLabel = normalizeSearchText(sourceLabel);
        if (normalizedLabel.isBlank()) {
            return Integer.MAX_VALUE;
        }
        int index = normalizedHeader.indexOf(normalizedLabel);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    private String tokenRegexForField(String targetField) {
        return switch (targetField) {
            case "item_name" -> "(?<name>.+?)";
            case "item_id" -> "(?<hsn>\\d{4,10})";
            case "gst" -> "(?<gst>\\d+(?:\\.\\d+)?\\s*%)";
            case "qty" -> "(?<qty>\\d+(?:\\.\\d+)?)";
            case "mrp" -> "(?<mrp>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)";
            case "rate" -> "(?<rate>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)";
            case "uom" -> "(?<uom>[A-Za-z]{1,12})";
            case "total" -> "(?<amount>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)";
            default -> "";
        };
    }

    private String findSourceLabel(List<VendorInvoiceFieldMapping> mappings, String targetField) {
        if (mappings == null || mappings.isEmpty()) {
            return "";
        }
        return mappings.stream()
                .filter(mapping -> mapping != null && targetField.equals(asText(mapping.targetField())))
                .map(mapping -> asText(mapping.sourceLabel()))
                .filter(label -> !label.isBlank())
                .findFirst()
                .orElse("");
    }

    private String buildSummaryRegex(String sourceLabel) {
        if (!hasText(sourceLabel)) {
            return "";
        }
        String escaped = String.join("\\s+",
                java.util.Arrays.stream(sourceLabel.trim().split("\\s+"))
                        .map(Pattern::quote)
                        .toList());
        return "(?im)^" + escaped + "\\s+[^\\d\\n\\r]*(?<amount>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)$";
    }

    private List<String> distinctTargetFields(List<VendorInvoiceFieldMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(mapping -> mapping != null && hasText(mapping.targetField()))
                .map(VendorInvoiceFieldMapping::targetField)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> toMappingMaps(List<VendorInvoiceFieldMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(mapping -> mapping != null)
                .map(mapping -> Map.<String, Object>of(
                        "targetField", asText(mapping.targetField()),
                        "sourceLabel", asText(mapping.sourceLabel())))
                .toList();
    }

    private List<Map<String, Object>> toMappingChecks(List<VendorInvoiceFieldMapping> mappings, String ocrText) {
        if (mappings == null || mappings.isEmpty()) {
            return List.of();
        }
        return mappings.stream()
                .filter(mapping -> mapping != null)
                .map(mapping -> {
                    String sourceLabel = asText(mapping.sourceLabel());
                    String matchedLine = findMatchingOcrLine(ocrText, sourceLabel);
                    return Map.<String, Object>of(
                            "targetField", asText(mapping.targetField()),
                            "sourceLabel", sourceLabel,
                            "matched", !matchedLine.isBlank(),
                            "matchedLine", matchedLine);
                })
                .toList();
    }

    private String findMatchingOcrLine(String ocrText, String sourceLabel) {
        if (!hasText(ocrText) || !hasText(sourceLabel)) {
            return "";
        }
        String needle = normalizeSearchText(sourceLabel);
        if (needle.isBlank()) {
            return "";
        }
        String bestLine = "";
        int bestScore = 0;
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            int score = scoreLineMatch(line, needle);
            if (score > bestScore) {
                bestScore = score;
                bestLine = line;
            }
        }
        return bestLine;
    }

    private int scoreLineMatch(String line, String needle) {
        String normalizedLine = normalizeSearchText(line);
        if (normalizedLine.isBlank() || needle.isBlank()) {
            return 0;
        }
        if (normalizedLine.equals(needle)) {
            return 100;
        }
        if (containsWholePhrase(normalizedLine, needle)) {
            return 90;
        }
        List<String> needleTokens = List.of(needle.split("\\s+"));
        long matchingTokens = needleTokens.stream()
                .filter(token -> containsWholeToken(normalizedLine, token))
                .count();
        if (matchingTokens == needleTokens.size() && !needleTokens.isEmpty()) {
            int score = 70;
            if (looksLikeHeaderLine(normalizedLine)) {
                score += 20;
            }
            return score;
        }
        if (matchingTokens > 0) {
            return (int) (matchingTokens * 10);
        }
        return 0;
    }

    private boolean containsWholePhrase(String line, String phrase) {
        return Pattern.compile("(^|\\s)" + Pattern.quote(phrase) + "(\\s|$)").matcher(line).find();
    }

    private boolean containsWholeToken(String line, String token) {
        return Pattern.compile("(^|\\s)" + Pattern.quote(token) + "(\\s|$)").matcher(line).find();
    }

    private boolean looksLikeHeaderLine(String line) {
        return containsWholeToken(line, "description")
                || containsWholeToken(line, "quantity")
                || containsWholeToken(line, "amount")
                || containsWholeToken(line, "rate")
                || containsWholeToken(line, "gst");
    }

    private String normalizeSearchText(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9/%]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double parseAmount(String raw) {
        return InvoiceSummaryExtractor.parseAmount(raw);
    }

    private int countMatchedChecks(List<Map<String, Object>> checks) {
        if (checks == null || checks.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> check : checks) {
            if (check == null) {
                continue;
            }
            if (Boolean.TRUE.equals(check.get("matched"))) {
                count++;
            }
        }
        return count;
    }

    private int detectItemCount(String ocrText) {
        if (!hasText(ocrText)) {
            return 0;
        }
        int serializedDistinctCount = countDistinctSerializedItemRows(ocrText);
        if (serializedDistinctCount > 0) {
            return serializedDistinctCount;
        }
        int parsedCount = templateCatalog.byKey(VendorInvoiceTemplateCatalog.KEY_TABLE_V1)
                .map(template -> templateParser.parseItems(ocrText, template).size())
                .orElse(0);
        if (parsedCount > 0) {
            return parsedCount;
        }
        return countSerializedItemRows(ocrText);
    }

    private int countSerializedItemRows(String ocrText) {
        int count = 0;
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            String lowered = line.toLowerCase(Locale.ROOT);
            if (!line.matches("^\\d+\\s+.+")) {
                continue;
            }
            if (lowered.startsWith("1 invoice")
                    || lowered.startsWith("1 buyer")
                    || lowered.startsWith("1 consignee")
                    || lowered.startsWith("1 gstin")) {
                continue;
            }
            count++;
        }
        return count;
    }

    private int countDistinctSerializedItemRows(String ocrText) {
        Set<Integer> serials = new LinkedHashSet<>();
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            String line = asText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            Integer serial = extractLikelyItemSerial(line);
            if (serial == null) {
                continue;
            }
            serials.add(serial);
        }
        return serials.size();
    }

    private Integer extractLikelyItemSerial(String line) {
        if (!hasText(line)) {
            return null;
        }
        String lowered = line.toLowerCase(Locale.ROOT);
        if (lowered.startsWith("1 invoice")
                || lowered.startsWith("1 buyer")
                || lowered.startsWith("1 consignee")
                || lowered.startsWith("1 gstin")) {
            return null;
        }
        var matcher = Pattern.compile("^(\\d{1,3})\\s+.+").matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractSummaryAmount(List<Map<String, Object>> summaryChecks, String targetField) {
        if (summaryChecks == null || summaryChecks.isEmpty()) {
            return "";
        }
        for (Map<String, Object> check : summaryChecks) {
            if (check == null) {
                continue;
            }
            if (!targetField.equals(asText(check.get("targetField")))) {
                continue;
            }
            String matchedLine = asText(check.get("matchedLine"));
            InvoiceSummaryExtractor.Extraction extraction =
                    InvoiceSummaryExtractor.extractFinalAmount(matchedLine, "");
            if (hasText(extraction.amount())) {
                return extraction.amount();
            }
            String inlineAmount = extractInlineAmount(matchedLine);
            if (hasText(inlineAmount)) {
                return inlineAmount;
            }
            double amount = parseAmount(matchedLine);
            if (amount > 0) {
                return String.format(Locale.ROOT, "%.2f", amount);
            }
        }
        return "";
    }

    private String extractInlineAmount(String text) {
        if (!hasText(text)) {
            return "";
        }
        var matcher = Pattern.compile("\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{2})").matcher(text);
        String candidate = "";
        while (matcher.find()) {
            candidate = asText(matcher.group());
        }
        return candidate;
    }

    private int countNonEmptyLines(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String rawLine : ocrText.replace('\f', '\n').split("\\r?\\n")) {
            if (hasText(rawLine)) {
                count++;
            }
        }
        return count;
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
            String finalBillMatch,
            String transportChargeAmount,
            String transportChargeMatch,
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
            payload.put("finalBillMatch", finalBillMatch);
            payload.put("transportChargeAmount", transportChargeAmount);
            payload.put("transportChargeMatch", transportChargeMatch);
            payload.put("activationReady", activationReady);
            payload.put("ocrLineCount", ocrLineCount);
            payload.put("ocrPreview", ocrPreview);
            payload.put("previewItems", previewItems);
            return payload;
        }
    }

    private record FieldAliasCandidate(String targetField, List<String> aliases) {
    }

    private record SummaryExtraction(String amount, String matchedText) {
    }

    private record OcrSample(String fileName, String fileUrl, String ocrText, byte[] pdfBytes) {
    }
}
