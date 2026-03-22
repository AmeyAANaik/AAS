package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VendorPdfService {

    private static final String SALES_ORDER = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String ITEM = "Item";
    private static final String WAREHOUSE = "Warehouse";
    private static final String COMPANY = "Company";
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService fileService;
    private final OcrService ocrService;
    private final VendorInvoiceTemplateResolver templateResolver;
    private final VendorInvoiceTemplateCatalog templateCatalog;
    private final VendorInvoiceTemplateParser templateParser;
    private final Invoice2DataExtractionService invoice2DataExtractionService;
    private final InvoiceTemplateModelService invoiceTemplateModelService;
    private final OrderFlowStateMachine orderFlowStateMachine;
    private final CatalogRoutingService catalogRoutingService;
    private final OrderPricingService orderPricingService;
    private final ObjectMapper objectMapper;
    private final double defaultMarginPercent;

    public VendorPdfService(
            ErpNextClient erpNextClient,
            ErpNextFileService fileService,
            OcrService ocrService,
            VendorInvoiceTemplateResolver templateResolver,
            VendorInvoiceTemplateCatalog templateCatalog,
            VendorInvoiceTemplateParser templateParser,
            Invoice2DataExtractionService invoice2DataExtractionService,
            InvoiceTemplateModelService invoiceTemplateModelService,
            OrderFlowStateMachine orderFlowStateMachine,
            CatalogRoutingService catalogRoutingService,
            OrderPricingService orderPricingService,
            ObjectMapper objectMapper,
            @Value("${app.order.margin.default-percent:7}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.fileService = fileService;
        this.ocrService = ocrService;
        this.templateResolver = templateResolver;
        this.templateCatalog = templateCatalog;
        this.templateParser = templateParser;
        this.invoice2DataExtractionService = invoice2DataExtractionService;
        this.invoiceTemplateModelService = invoiceTemplateModelService;
        this.orderFlowStateMachine = orderFlowStateMachine;
        this.catalogRoutingService = catalogRoutingService;
        this.orderPricingService = orderPricingService;
        this.objectMapper = objectMapper;
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public Map<String, Object> processVendorPdf(String orderId, MultipartFile pdfFile, String sessionCookie) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order id is required.");
        }
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("Vendor PDF is required.");
        }
        byte[] pdfBytes = toBytes(pdfFile);
        validatePdf(pdfBytes);

        Map<String, Object> originalOrder = erpNextClient.getResource(SALES_ORDER, orderId);
        Map<String, Object> orderData = unwrapResource(originalOrder);
        String customer = asText(orderData.get("customer"));
        String company = asText(orderData.get("company"));
        String vendor = asText(orderData.get("aas_vendor"));
        String category = asText(orderData.get("aas_category"));
        String currentStatus = asText(orderData.get("aas_status"));
        orderFlowStateMachine.ensureCanUploadVendorPdf(currentStatus);
        if (vendor.isBlank()) {
            throw new IllegalStateException("Vendor must be assigned before uploading vendor PDF.");
        }
        if (category.isBlank()) {
            throw new IllegalStateException("Order category is required before uploading vendor PDF.");
        }
        if (customer.isBlank() || company.isBlank()) {
            throw new IllegalStateException("Order must include customer and company before processing vendor PDF.");
        }
        CatalogRoutingService.VendorCategoryResolution vendorResolution =
                catalogRoutingService.resolveVendorForCategory(vendor, category);

        String ocrText = ocrService.extractTextFromPdf(pdfBytes);
        if (ocrText == null || ocrText.isBlank()) {
            throw new IllegalStateException("Unable to extract any text from vendor PDF. The PDF may be scanned, encrypted, or too low quality for OCR.");
        }
        boolean templateConfigured = false;
        boolean templateUsed = false;
        String templateKey = "";
        List<ParsedItem> parsedItems = List.of();
        VendorInvoiceTemplate vendorTemplate = null;
        Invoice2DataExtractionService.ExtractionResult invoice2DataResult = null;
        var resolvedJson = templateResolver.loadTemplateJson(vendor);
        if (resolvedJson.isPresent()) {
            templateConfigured = true;
            Invoice2DataExtractionService.Invoice2DataProfile invoice2DataProfile =
                    invoice2DataExtractionService.parseStoredProfile(resolvedJson.get());
            if (invoice2DataProfile != null) {
                templateKey = invoice2DataProfile.id();
                invoice2DataResult = invoice2DataExtractionService.extract(pdfBytes, invoice2DataProfile);
                parsedItems = invoice2DataResult.items();
                templateUsed = parsedItems != null && !parsedItems.isEmpty();
            } else {
                templateKey = "vendor_json";
                vendorTemplate = parseVendorTemplate(resolvedJson.get()).orElse(null);
                if (vendorTemplate == null) {
                    throw new IllegalStateException("Vendor template JSON is invalid. Save a valid parser configuration before uploading vendor PDFs.");
                }
            }
        } else {
            var resolvedKey = templateResolver.loadTemplateKey(vendor);
            if (resolvedKey.isPresent()) {
                templateConfigured = true;
                templateKey = resolvedKey.get();
                final String resolvedTemplateKey = templateKey;
                vendorTemplate = templateCatalog.byKey(resolvedTemplateKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "Vendor template key \"" + resolvedTemplateKey + "\" is not supported."));
            }
        }
        if (!templateConfigured || vendorTemplate == null) {
            if (invoice2DataResult == null) {
                throw new IllegalStateException(
                        "Vendor invoice template is required before uploading vendor PDF. Configure and validate the vendor template first.");
            }
        }
        if (invoice2DataResult == null) {
            parsedItems = templateParser.parseItems(ocrText, vendorTemplate);
            templateUsed = parsedItems != null && !parsedItems.isEmpty();
        }
        if (!templateUsed) {
            int lineCount = countNonEmptyLines(ocrText);
            throw new IllegalStateException(
                    "Configured vendor template did not extract any invoice items. "
                            + "Extracted "
                            + ocrText.length()
                            + " characters across "
                            + lineCount
                            + " non-empty lines. Review the vendor template mapping and sample invoice.");
        }
        validateParsedTemplateOutput(parsedItems, ocrText, vendorTemplate, invoice2DataResult);

        List<Map<String, Object>> baseItems = resolveItems(parsedItems, vendorResolution);
        List<Map<String, Object>> sourceOrderItems = withVendorRate(baseItems);
        List<Map<String, Object>> sellItems = withSellMargin(baseItems);
        double marginPercent = calculateDerivedMarginPercent(sourceOrderItems);

        Map<String, Object> purchaseOrder = createPurchaseOrder(orderId, vendor, company, baseItems, orderData);
        String purchaseOrderId = extractDocName(purchaseOrder);
        double vendorBillTotal = sumAmount(baseItems);
        if (invoice2DataResult != null && hasText(invoice2DataResult.finalAmount())) {
            vendorBillTotal = parseNumber(invoice2DataResult.finalAmount());
        } else if (vendorTemplate != null && vendorTemplate.finalAmountRegex() != null && !vendorTemplate.finalAmountRegex().isBlank()) {
            double extractedTotal = extractAmountByRegex(ocrText, vendorTemplate.finalAmountRegex());
            if (extractedTotal > 0) {
                vendorBillTotal = extractedTotal;
            }
        }
        String vendorBillDate = "";
        if (invoice2DataResult != null && hasText(invoice2DataResult.invoiceDate())) {
            vendorBillDate = normalizeDateToIso(invoice2DataResult.invoiceDate());
        } else if (vendorTemplate != null && vendorTemplate.billDateRegex() != null && !vendorTemplate.billDateRegex().isBlank()) {
            vendorBillDate = extractBillDateByRegex(ocrText, vendorTemplate.billDateRegex());
        }
        String vendorBillRef = purchaseOrderId == null ? "" : purchaseOrderId;
        double transportCharge = 0.0;
        if (invoice2DataResult != null && hasText(invoice2DataResult.transportCharge())) {
            transportCharge = parseNumber(invoice2DataResult.transportCharge());
        } else if (vendorTemplate != null && vendorTemplate.transportChargeRegex() != null
                && !vendorTemplate.transportChargeRegex().isBlank()) {
            transportCharge = extractAmountByRegex(ocrText, vendorTemplate.transportChargeRegex());
        }

        UploadedFileInfo pdfInfo;
        try {
            pdfInfo = fileService.uploadOrderPdf(orderId, pdfFile, sessionCookie);
        } catch (Exception ex) {
            String fallbackName = pdfFile.getOriginalFilename() == null ? "vendor_order.pdf" : pdfFile.getOriginalFilename();
            pdfInfo = new UploadedFileInfo(fallbackName, null, null);
        }

        Map<String, Object> linkUpdate = new HashMap<>();
        linkUpdate.put("items", sourceOrderItems);
        linkUpdate.put("aas_margin_percent", marginPercent);
        linkUpdate.put("aas_po", purchaseOrderId);
        linkUpdate.put("aas_status", "VENDOR_PDF_RECEIVED");
        linkUpdate.put("aas_vendor_bill_total", vendorBillTotal);
        linkUpdate.put("aas_vendor_bill_ref", vendorBillRef);
        linkUpdate.put("aas_transport_charge", transportCharge);
        if (vendorBillDate != null && !vendorBillDate.isBlank()) {
            linkUpdate.put("aas_vendor_bill_date", vendorBillDate);
        }
        if (pdfInfo.fileUrl() != null) {
            linkUpdate.put("aas_vendor_pdf", pdfInfo.fileUrl());
        }
        erpNextClient.updateResource(SALES_ORDER, orderId, linkUpdate);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("purchaseOrder", purchaseOrder);
        response.put("sellPreview", Map.of(
                "vendorTotal", sumAmount(baseItems),
                "marginPercent", marginPercent,
                "sellTotal", sumAmount(sellItems)));
        response.put("marginPercent", marginPercent);
        response.put("vendorBillTotal", vendorBillTotal);
        response.put("vendorBillRef", vendorBillRef);
        response.put("vendorBillDate", vendorBillDate);
        response.put("transportCharge", transportCharge);
        response.put("items", parsedItems);
        response.put("orderItems", baseItems);
        response.put("template", Map.of(
                "configured", templateConfigured,
                "used", templateUsed,
                "key", templateKey));
        response.put("file", Map.of(
                "fileName", pdfInfo.fileName(),
                "fileUrl", pdfInfo.fileUrl(),
                "fileId", pdfInfo.fileId()));
        return response;
    }

    private void validateParsedTemplateOutput(
            List<ParsedItem> parsedItems,
            String ocrText,
            VendorInvoiceTemplate template,
            Invoice2DataExtractionService.ExtractionResult invoice2DataResult) {
        java.util.Set<String> parsedColumns = new java.util.LinkedHashSet<>();
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
        List<String> missingColumns = invoiceTemplateModelService.requiredItemKeys().stream()
                .filter(key -> !parsedColumns.contains(key))
                .toList();
        if (!missingColumns.isEmpty()) {
            throw new IllegalStateException(
                    "Configured vendor template did not extract required item fields: " + String.join(", ", missingColumns) + ".");
        }
        List<String> missingSummaryFields = new ArrayList<>();
        if (invoiceTemplateModelService.requiredSummaryKeys().contains("final_bill_amount")) {
            double extractedTotal = invoice2DataResult != null
                    ? parseNumber(invoice2DataResult.finalAmount())
                    : extractAmountByRegex(ocrText, template == null ? null : template.finalAmountRegex());
            if (extractedTotal <= 0) {
                missingSummaryFields.add("final_bill_amount");
            }
        }
        if (!missingSummaryFields.isEmpty()) {
            throw new IllegalStateException(
                    "Configured vendor template did not extract required summary fields: " + String.join(", ", missingSummaryFields) + ".");
        }
    }

    private int countNonEmptyLines(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String rawLine : text.replace('\f', '\n').split("\\r?\\n")) {
            if (!asText(rawLine).isBlank()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private java.util.Optional<VendorInvoiceTemplate> parseVendorTemplate(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Optional.empty();
        }
        // 1) Preferred: JSON config stored as { "version": 1, "itemLineRegex": "...", "billDateRegex": "..." }
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
            if (versionObj != null && regexObj != null) {
                int version = versionObj instanceof Number n ? n.intValue() : Integer.parseInt(versionObj.toString().trim());
                String itemLineRegex = regexObj.toString();
                String billDateRegex = parserMap.get("billDateRegex") == null ? null : String.valueOf(parserMap.get("billDateRegex"));
                if (version > 0 && itemLineRegex != null && !itemLineRegex.isBlank()) {
                    String finalAmountRegex = parserMap.get("finalAmountRegex") == null ? null : String.valueOf(parserMap.get("finalAmountRegex"));
                    String transportChargeRegex = parserMap.get("transportChargeRegex") == null ? null : String.valueOf(parserMap.get("transportChargeRegex"));
                    return java.util.Optional.of(new VendorInvoiceTemplate(version, itemLineRegex, billDateRegex, finalAmountRegex, transportChargeRegex));
                }
            }

            // 2) Backward-compatible: if user pasted an "invoice schema JSON" with an items[] array,
            // use a generic single-line item regex (serial + description + hsn + qty + unit + rate + amount).
            if (map.containsKey("items")) {
                Object itemsObj = map.get("items");
                if (itemsObj instanceof List<?> list && !list.isEmpty()) {
                    // Generic invoice line:
                    //   1 SFK SAMRAT ATTA 50KG 11010000 500 KG 35.50 17750.00
                    // Named groups required by VendorInvoiceTemplateParser: name/qty/rate/amount/(optional hsn)
                    String generic = "^(?:\\d+\\s+)?(?<name>.+?)\\s+(?<hsn>\\d{4,10})\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s*(?:[A-Za-z]{1,6})?\\s+(?<rate>\\d+(?:\\.\\d+)?)\\s+(?<amount>\\d+(?:\\.\\d+)?)$";
                    return java.util.Optional.of(new VendorInvoiceTemplate(1, generic, null, null, null));
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return java.util.Optional.empty();
    }

    private String extractBillDateByRegex(String ocrText, String billDateRegex) {
        if (ocrText == null || ocrText.isBlank() || billDateRegex == null || billDateRegex.isBlank()) {
            return "";
        }
        try {
            var pattern = java.util.regex.Pattern.compile(billDateRegex, java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.MULTILINE);
            var matcher = pattern.matcher(ocrText);
            if (!matcher.find()) {
                return "";
            }
            String raw;
            try {
                raw = matcher.group("date");
            } catch (IllegalArgumentException ex) {
                raw = matcher.group(1);
            }
            String cleaned = raw == null ? "" : raw.trim();
            return normalizeDateToIso(cleaned);
        } catch (Exception ex) {
            return "";
        }
    }

    private double extractAmountByRegex(String ocrText, String amountRegex) {
        if (ocrText == null || ocrText.isBlank()) {
            return 0.0;
        }
        InvoiceSummaryExtractor.Extraction extraction =
                InvoiceSummaryExtractor.extractFinalAmount(ocrText, amountRegex);
        return InvoiceSummaryExtractor.parseAmount(extraction.amount());
    }

    private double parseNumber(String raw) {
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

    private String normalizeDateToIso(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        // Strip common OCR artifacts like leading punctuation/letters before the date.
        text = text.replaceAll("^[^0-9]+", "").trim();
        // Prefer ISO-like yyyy-mm-dd
        java.util.regex.Matcher ymd = java.util.regex.Pattern
                .compile("\\b(\\d{4})[\\-/.](\\d{1,2})[\\-/.](\\d{1,2})\\b")
                .matcher(text);
        if (ymd.find()) {
            int y = safeInt(ymd.group(1));
            int m = safeInt(ymd.group(2));
            int d = safeInt(ymd.group(3));
            return toIsoDate(y, m, d);
        }
        java.util.regex.Matcher dmy = java.util.regex.Pattern
                .compile("\\b(\\d{1,2})[\\-/.](\\d{1,2})[\\-/.](\\d{2,4})\\b")
                .matcher(text);
        if (dmy.find()) {
            int d = safeInt(dmy.group(1));
            int m = safeInt(dmy.group(2));
            int y = safeInt(dmy.group(3));
            if (y < 100) {
                y += 2000;
            }
            return toIsoDate(y, m, d);
        }
        // Month name format: 27-Feb-26 / 27-Feb-2026
        java.util.regex.Matcher dMonY = java.util.regex.Pattern
                .compile("\\b(\\d{1,2})[\\-/.]([A-Za-z]{3,})[\\-/.](\\d{2,4})\\b")
                .matcher(text);
        if (dMonY.find()) {
            int d = safeInt(dMonY.group(1));
            int m = monthToNumber(dMonY.group(2));
            int y = safeInt(dMonY.group(3));
            if (y < 100) {
                y += 2000;
            }
            return toIsoDate(y, m, d);
        }
        return "";
    }

    private int safeInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private String toIsoDate(int y, int m, int d) {
        try {
            return java.time.LocalDate.of(y, m, d).toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private int monthToNumber(String raw) {
        if (raw == null) {
            return 0;
        }
        String key = raw.trim().toLowerCase();
        if (key.length() >= 3) {
            key = key.substring(0, 3);
        }
        return switch (key) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> 0;
        };
    }

    private void validatePdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Vendor PDF is required.");
        }
        try (PDDocument ignored = Loader.loadPDF(pdfBytes)) {
            // Valid PDF.
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid PDF file. Please upload a real PDF export.");
        }
    }

    private List<Map<String, Object>> resolveItems(
            List<ParsedItem> parsedItems,
            CatalogRoutingService.VendorCategoryResolution vendorResolution) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ParsedItem item : parsedItems) {
            String itemName = item.name();
            String vendorHsnCode = asText(catalogRoutingService.normalizeCodeSegment(item.hsn()));
            String uom = normalizeUom(item.uom());
            if (uom.isBlank()) {
                uom = "Nos";
            }
            ensureUomExists(uom);
            if (vendorHsnCode.isBlank()) {
                throw new IllegalStateException(
                        "Vendor HSN code is required for parsed item \"" + itemName
                                + "\". Update the vendor template/parser so item_id or hsn is captured.");
            }
            String itemCode = catalogRoutingService.buildParsedItemCode(
                    vendorResolution.vendorCode(),
                    vendorResolution.categoryCode(),
                    vendorHsnCode,
                    itemName);
            if (!resourceExists(itemCode)) {
                itemCode = createItem(itemName, vendorResolution, vendorHsnCode, uom);
            }
            ensureItemEnabled(itemCode);
            Map<String, Object> row = new HashMap<>();
            row.put("item_code", itemCode);
            row.put("item_name", itemName);
            row.put("qty", item.qty());
            row.put("uom", uom);
            row.put("stock_uom", uom);
            row.put("rate", item.rate());
            row.put("aas_margin_percent", resolveItemMarginPercent(itemCode));
            if (item.gstPercent() != null && item.gstPercent() >= 0) {
                row.put("aas_gst_percent", round(item.gstPercent()));
            }
            if (item.mrp() != null && item.mrp() > 0) {
                row.put("aas_mrp", round(item.mrp()));
            }
            double amount = item.amount();
            if (amount <= 0 && item.qty() > 0) {
                amount = item.rate() * item.qty();
            }
            row.put("amount", amount);
            rows.add(row);
        }
        return rows;
    }

    private void ensureItemEnabled(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            Object disabled = item == null ? null : item.get("disabled");
            if (disabled instanceof Number n && n.intValue() != 0) {
                erpNextClient.updateResource(ITEM, itemCode, Map.of("disabled", 0));
            } else if (disabled instanceof Boolean b && b) {
                erpNextClient.updateResource(ITEM, itemCode, Map.of("disabled", 0));
            } else if (disabled != null && "1".equals(disabled.toString().trim())) {
                erpNextClient.updateResource(ITEM, itemCode, Map.of("disabled", 0));
            }
        } catch (Exception ignored) {
            // Best-effort. If enabling fails, PO creation might still fail and the user will see ERP error.
        }
    }

    private String normalizeNameForLookup(String itemName) {
        if (itemName == null) {
            return "";
        }
        String cleaned = itemName.trim();
        // Remove likely HSN codes / long numeric tokens.
        cleaned = cleaned.replaceAll("\\b\\d{4,10}\\b", " ");
        // Remove trailing qty-like tokens that OCR sometimes appends into names.
        cleaned = cleaned.replaceAll("\\b\\d{1,3}(?:\\.\\d+)?\\b\\s*$", " ");
        cleaned = NON_ALNUM_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String findSingleItemCodeByLike(String cleanedName) {
        if (cleanedName == null || cleanedName.isBlank()) {
            return null;
        }
        if (cleanedName.length() < 4) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"item_name\"]");
        params.put("limit_page_length", "5");
        params.put("filters", "[[\"item_name\",\"like\",\"%" + escape(cleanedName) + "%\"]]");
        List<Map<String, Object>> data = erpNextClient.listResources(ITEM, params);
        if (data.size() != 1) {
            return null;
        }
        Object name = data.get(0).get("name");
        return name == null ? null : name.toString();
    }

    private List<Map<String, Object>> withVendorRate(List<Map<String, Object>> baseItems) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : baseItems) {
            Map<String, Object> copy = new HashMap<>(row);
            double vendorRate = asDouble(row.get("rate"));
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
            OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
                    vendorRate,
                    marginPercent,
                    asNullableDouble(row.get("aas_mrp")),
                    asText(row.get("item_name")).isBlank() ? asText(row.get("item_code")) : asText(row.get("item_name")));
            copy.put("aas_vendor_rate", vendorRate);
            copy.put("aas_margin_percent", pricing.effectiveMarginPercent());
            enriched.add(copy);
        }
        return enriched;
    }

    private List<Map<String, Object>> withSellMargin(List<Map<String, Object>> baseItems) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : baseItems) {
            Map<String, Object> copy = new HashMap<>(row);
            double vendorRate = asDouble(row.get("rate"));
            double qty = asDouble(row.get("qty"));
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
            OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
                    vendorRate,
                    marginPercent,
                    asNullableDouble(row.get("aas_mrp")),
                    asText(row.get("item_name")).isBlank() ? asText(row.get("item_code")) : asText(row.get("item_name")));
            copy.put("rate", pricing.sellRate());
            copy.put("amount", round(pricing.sellRate() * qty));
            copy.put("aas_vendor_rate", vendorRate);
            copy.put("aas_margin_percent", pricing.effectiveMarginPercent());
            enriched.add(copy);
        }
        return enriched;
    }

    private double calculateDerivedMarginPercent(List<Map<String, Object>> items) {
        double vendorTotal = 0.0;
        double sellTotal = 0.0;
        for (Map<String, Object> row : items) {
            double qty = asDouble(row.get("qty"));
            if (qty <= 0) {
                qty = 1.0;
            }
            double vendorRate = asDouble(row.get("aas_vendor_rate"));
            if (vendorRate <= 0) {
                vendorRate = asDouble(row.get("rate"));
            }
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
            vendorTotal += vendorRate * qty;
            sellTotal += vendorRate * (1 + marginPercent / 100.0) * qty;
        }
        vendorTotal = round(vendorTotal);
        sellTotal = round(sellTotal);
        if (vendorTotal <= 0) {
            return defaultMarginPercent;
        }
        return round(((sellTotal - vendorTotal) / vendorTotal) * 100.0);
    }

    private double resolveItemMarginPercent(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return defaultMarginPercent;
        }
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            double margin = asDouble(item.get("aas_margin_percent"));
            if (margin > 0) {
                return margin;
            }
        } catch (Exception ignored) {
            // Fall back to default margin if item master lookup fails.
        }
        return defaultMarginPercent;
    }

    private String findItemCodeByName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"item_name\"]");
        params.put("limit_page_length", "1");
        params.put("filters", "[[\"item_name\",\"=\",\"" + escape(itemName) + "\"]]");
        List<Map<String, Object>> data = erpNextClient.listResources(ITEM, params);
        if (data.isEmpty()) {
            return null;
        }
        Object name = data.get(0).get("name");
        return name == null ? null : name.toString();
    }

    private String createItem(
            String itemName,
            CatalogRoutingService.VendorCategoryResolution vendorResolution,
            String vendorHsnCode,
            String uom) {
        Map<String, Object> payload = new HashMap<>();
        String code = catalogRoutingService.buildParsedItemCode(
                vendorResolution.vendorCode(),
                vendorResolution.categoryCode(),
                vendorHsnCode,
                itemName);
        payload.put("item_code", code);
        payload.put("item_name", itemName);
        payload.put("item_group", vendorResolution.categoryId());
        payload.put("stock_uom", normalizeUom(asText(uom).isBlank() ? "Nos" : uom));
        payload.put("is_stock_item", 1);
        payload.put("aas_vendor", vendorResolution.vendorId());
        payload.put("aas_vendor_hsn_code", vendorHsnCode);
        Map<String, Object> created = erpNextClient.createResource(ITEM, payload);
        Object name = created.get("name");
        return name == null ? code : name.toString();
    }

    private String normalizeUom(String raw) {
        String normalized = asText(raw).trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        return switch (normalized) {
            case "KG", "KGS", "KILOGRAM", "KILOGRAMS" -> "Kg";
            case "GM", "GMS", "GRAM", "GRAMS" -> "Gram";
            case "LTR", "LITRE", "LITRES", "LITER", "LITERS" -> "Litre";
            case "PCS", "PC", "PIECE", "PIECES", "NOS", "NO", "NUMBER", "NUMBERS", "UNIT", "UNITS" -> "Nos";
            case "TIN", "TINS" -> "Tin";
            case "PACK", "PACKS", "PKT", "PKTS", "PACKET", "PACKETS" -> "Pack";
            default -> {
                String titleCase = normalized.substring(0, 1) + normalized.substring(1).toLowerCase(Locale.ROOT);
                yield titleCase;
            }
        };
    }

    private void ensureUomExists(String uomName) {
        if (uomName == null || uomName.isBlank() || resourceExists("UOM", uomName)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("uom_name", uomName);
        payload.put("must_be_whole_number", "Nos".equalsIgnoreCase(uomName) ? 1 : 0);
        erpNextClient.createResource("UOM", payload);
    }

    private boolean resourceExists(String itemCode) {
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            return !item.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean resourceExists(String doctype, String name) {
        if (doctype == null || doctype.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> resource = unwrapResource(erpNextClient.getResource(doctype, name));
            return !resource.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> createPurchaseOrder(
            String sourceOrderId,
            String vendor,
            String company,
            List<Map<String, Object>> items,
            Map<String, Object> originalOrder) {
        String warehouse = resolveWarehouse(originalOrder);
        if (warehouse.isBlank()) {
            warehouse = resolveDefaultWarehouse(company);
        }
        List<Map<String, Object>> poItems = withWarehouse(items, warehouse);
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", vendor);
        payload.put("company", company);
        payload.put("schedule_date", resolveScheduleDate(originalOrder));
        payload.put("items", poItems);
        payload.put("aas_source_sales_order", sourceOrderId);
        return erpNextClient.createResource(PURCHASE_ORDER, payload);
    }

    @SuppressWarnings("unchecked")
    private String resolveWarehouse(Map<String, Object> originalOrder) {
        String orderWarehouse = asText(originalOrder == null ? null : originalOrder.get("set_warehouse"));
        if (!orderWarehouse.isBlank()) {
            return orderWarehouse;
        }
        Object itemsObj = originalOrder == null ? null : originalOrder.get("items");
        if (itemsObj instanceof List<?> list) {
            for (Object rowObj : list) {
                if (rowObj instanceof Map<?, ?> row) {
                    String rowWarehouse = asText(((Map<String, Object>) row).get("warehouse"));
                    if (!rowWarehouse.isBlank()) {
                        return rowWarehouse;
                    }
                }
            }
        }
        return "";
    }

    private List<Map<String, Object>> withWarehouse(List<Map<String, Object>> items, String warehouse) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> copy = new HashMap<>(item);
            if (!warehouse.isBlank() && asText(copy.get("warehouse")).isBlank()) {
                copy.put("warehouse", warehouse);
            }
            rows.add(copy);
        }
        return rows;
    }

    private String resolveDefaultWarehouse(String company) {
        if (company == null || company.isBlank()) {
            return "";
        }
        String abbr = "";
        try {
            Map<String, Object> companyDoc = erpNextClient.getResource(COMPANY, company);
            abbr = asText(companyDoc.get("abbr"));
        } catch (Exception ex) {
            abbr = "";
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"warehouse_name\",\"company\",\"is_group\"]");
        params.put("limit_page_length", "50");
        params.put("filters", "[[\"company\",\"=\",\"" + escape(company) + "\"],[\"is_group\",\"=\",\"0\"]]");
        List<Map<String, Object>> warehouses = erpNextClient.listResources(WAREHOUSE, params);
        if (warehouses.isEmpty()) {
            return "";
        }
        if (!abbr.isBlank()) {
            String preferred = "Stores - " + abbr;
            for (Map<String, Object> wh : warehouses) {
                String name = asText(wh.get("name"));
                if (preferred.equals(name)) {
                    return name;
                }
            }
        }
        return asText(warehouses.get(0).get("name"));
    }

    private String resolveDate(Object value) {
        String text = asText(value);
        if (!text.isBlank()) {
            return text;
        }
        return LocalDate.now().toString();
    }

    private String resolveScheduleDate(Map<String, Object> originalOrder) {
        LocalDate today = LocalDate.now();
        LocalDate minScheduleDate = today.plusDays(1);
        LocalDate transactionDate = parseDate(originalOrder == null ? null : originalOrder.get("transaction_date"), today);
        LocalDate deliveryDate = parseDate(originalOrder == null ? null : originalOrder.get("delivery_date"), transactionDate);
        LocalDate scheduleDate = deliveryDate;
        if (scheduleDate.isBefore(transactionDate)) {
            scheduleDate = transactionDate;
        }
        if (scheduleDate.isBefore(minScheduleDate)) {
            scheduleDate = minScheduleDate;
        }
        return scheduleDate.toString();
    }

    private LocalDate parseDate(Object value, LocalDate fallback) {
        String text = asText(value);
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double resolveMarginPercent(Object value) {
        double margin = asDouble(value);
        if (value == null || value.toString().trim().isEmpty() || margin == 0.0) {
            margin = defaultMarginPercent;
        }
        if (margin < 0) {
            throw new IllegalArgumentException("Margin percent must be non-negative.");
        }
        return margin;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private Double asNullableDouble(Object value) {
        double parsed = asDouble(value);
        return parsed > 0 ? parsed : null;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double sumAmount(List<Map<String, Object>> items) {
        double total = 0.0;
        for (Map<String, Object> row : items) {
            total += asDouble(row.get("amount"));
        }
        return round(total);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read vendor PDF.", ex);
        }
    }

    private String normalizeItemCode(String name) {
        String cleaned = name.trim().replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (cleaned.isEmpty()) {
            return "ITEM-" + System.currentTimeMillis();
        }
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        return cleaned.toUpperCase();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractDocName(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object direct = response.get("name");
        if (direct != null) {
            return direct.toString();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            Object name = map.get("name");
            return name == null ? null : name.toString();
        }
        return null;
    }
}
