package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.meta.VendorFieldMapper;
import com.aas.mw.meta.VendorFieldRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Locale;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;

@Service
public class MasterDataService {

    private final ErpNextClient erpNextClient;
    private final VendorFieldRegistry vendorFieldRegistry;
    private final ObjectMapper objectMapper;
    private final OcrService ocrService;
    private final VendorInvoiceTemplateParser templateParser;
    private final InvoiceTemplateModelService invoiceTemplateModelService;
    private final String erpBaseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MasterDataService(
            ErpNextClient erpNextClient,
            VendorFieldRegistry vendorFieldRegistry,
            ObjectMapper objectMapper,
            OcrService ocrService,
            VendorInvoiceTemplateParser templateParser,
            InvoiceTemplateModelService invoiceTemplateModelService,
            @Value("${erpnext.base-url}") String erpBaseUrl) {
        this.erpNextClient = erpNextClient;
        this.vendorFieldRegistry = vendorFieldRegistry;
        this.objectMapper = objectMapper;
        this.ocrService = ocrService;
        this.templateParser = templateParser;
        this.invoiceTemplateModelService = invoiceTemplateModelService;
        this.erpBaseUrl = erpBaseUrl;
    }

    public List<Map<String, Object>> listItems() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_name\",\"item_code\",\"item_group\",\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\",\"aas_packaging_unit\"]");
        params.put("filters", "[[\"Item\",\"disabled\",\"=\",0]]");
        params.put("limit_page_length", 1000);
        return erpNextClient.listResources("Item", params);
    }

    public Map<String, Object> listItemsPaged(int page, int size, String search, String sort, String dir) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String safeDir = "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
        String orderBy = resolveItemOrderBy(sort, safeDir);

        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_name\",\"item_code\",\"item_group\",\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\",\"aas_packaging_unit\"]");
        params.put("limit_start", (safePage - 1) * safeSize);
        params.put("limit_page_length", safeSize);
        params.put("order_by", orderBy);
        params.put("filters", "[[\"Item\",\"disabled\",\"=\",0]]");

        String trimmed = search == null ? "" : search.trim();
        if (!trimmed.isEmpty()) {
            params.put("or_filters", buildItemSearchFilters(trimmed));
        }

        List<Map<String, Object>> items = erpNextClient.listResources("Item", params);

        Map<String, Object> countParams = new HashMap<>();
        countParams.put("filters", params.get("filters"));
        if (!trimmed.isEmpty()) {
            countParams.put("or_filters", params.get("or_filters"));
        }
        long total = erpNextClient.getCount("Item", countParams);

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", total);
        response.put("page", safePage);
        response.put("size", safeSize);
        return response;
    }

    public List<Map<String, Object>> listVendors() {
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> params = new HashMap<>();
        params.put("fields", mapper.erpListFieldsJson());
        return erpNextClient.listResources("Supplier", params).stream()
                .map(mapper::withApiAliases)
                .toList();
    }

    public List<Map<String, Object>> listCategories() {
        return erpNextClient.listResources("Item Group", null);
    }

    public List<Map<String, Object>> listShops() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"customer_name\",\"customer_type\",\"customer_group\",\"territory\","
                        + "\"aas_branch_location\",\"aas_whatsapp_group_name\",\"aas_credit_days\"]");
        return erpNextClient.listResources("Customer", params);
    }

    public List<Map<String, Object>> listCompanies() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"abbr\",\"default_currency\"]");
        params.put("filters", "[[\"Company\",\"is_group\",\"=\",0]]");
        return erpNextClient.listResources("Company", params);
    }

    public Map<String, Object> createShop(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("customer_type", "Company");
        payload.putIfAbsent("customer_group", "All Customer Groups");
        payload.putIfAbsent("territory", "All Territories");
        return erpNextClient.createResource("Customer", payload);
    }

    public Map<String, Object> createVendor(FieldsRequest request) {
        validateVendorTemplateRequirement(null, request.getFields());
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(filterVendorBaseFields(request.getFields()));
        payload.putAll(mapper.toErpPayload(request.getFields()));
        applyTemplateFlags(payload);
        payload.putIfAbsent("supplier_type", "Company");
        payload.putIfAbsent("supplier_group", "All Supplier Groups");
        return erpNextClient.createResource("Supplier", payload);
    }

    public Map<String, Object> updateVendor(String id, FieldsRequest request) {
        validateVendorTemplateRequirement(id, request.getFields());
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(filterVendorBaseFields(request.getFields()));
        payload.putAll(mapper.toErpPayload(request.getFields()));
        applyTemplateFlags(payload);
        return erpNextClient.updateResource("Supplier", id, payload);
    }

    public Map<String, Object> deleteVendor(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vendor id is required.");
        }
        Map<String, Object> vendor = unwrapResource(erpNextClient.getResource("Supplier", id));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }
        if (!asFlag(vendor.get("disabled"))) {
            throw new IllegalStateException("Only inactive vendors can be deleted.");
        }
        if (hasLinkedVendorUsage("Sales Order", "aas_vendor", id)
                || hasLinkedVendorUsage("Purchase Order", "supplier", id)
                || hasLinkedVendorUsage("Purchase Invoice", "supplier", id)) {
            throw new IllegalStateException("Vendor cannot be deleted because it is linked to orders or bills.");
        }
        erpNextClient.deleteResource("Supplier", id);
        return Map.of("vendorId", id, "deleted", true);
    }

    private Map<String, Object> filterVendorBaseFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        // Allow only known built-in Supplier fields to pass through directly.
        Set<String> allowed = Set.of(
                "supplier_name",
                "supplier_type",
                "supplier_group",
                "disabled");
        Map<String, Object> out = new HashMap<>();
        for (String key : allowed) {
            if (fields.containsKey(key)) {
                out.put(key, fields.get(key));
            }
        }
        return out;
    }

    public Map<String, Object> createCategory(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("parent_item_group", "All Item Groups");
        return erpNextClient.createResource("Item Group", payload);
    }

    public Map<String, Object> updateCategory(String id, FieldsRequest request) {
        return erpNextClient.updateResource("Item Group", id, new HashMap<>(request.getFields()));
    }

    public Map<String, Object> createItem(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("item_group", "All Item Groups");
        payload.putIfAbsent("stock_uom", "Nos");
        payload.putIfAbsent("is_stock_item", 1);
        return erpNextClient.createResource("Item", payload);
    }

    public Map<String, Object> updateShop(String id, FieldsRequest request) {
        return erpNextClient.updateResource("Customer", id, new HashMap<>(request.getFields()));
    }

    public Map<String, Object> updateItem(String id, FieldsRequest request) {
        return erpNextClient.updateResource("Item", id, new HashMap<>(request.getFields()));
    }

    private String resolveItemOrderBy(String sort, String dir) {
        if (sort == null || sort.isBlank()) {
            return "item_name " + dir;
        }
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        String field = switch (normalized) {
            case "code" -> "item_code";
            case "name" -> "item_name";
            case "category" -> "item_group";
            case "uom" -> "stock_uom";
            case "packaging" -> "aas_packaging_unit";
            case "margin" -> "aas_margin_percent";
            default -> "item_name";
        };
        return field + " " + dir;
    }

    private String buildItemSearchFilters(String term) {
        String escaped = escapeJson(term);
        String pattern = "%" + escaped + "%";
        return "[[\"Item\",\"item_code\",\"like\",\"" + pattern + "\"],"
                + "[\"Item\",\"item_name\",\"like\",\"" + pattern + "\"]]";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private boolean hasLinkedVendorUsage(String doctype, String field, String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("filters", "[[\"" + escapeJson(doctype) + "\",\"" + escapeJson(field) + "\",\"=\",\"" + escapeJson(vendorId) + "\"]]");
        return erpNextClient.getCount(doctype, params) > 0;
    }

    private void validateVendorTemplateRequirement(String vendorId, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        boolean disabled = asFlag(fields.get("disabled"));
        if (disabled) {
            return;
        }
        String templateJson = asText(fields.get("invoice_template_json"));
        if (templateJson.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Active vendors require invoice template JSON to be present.");
        }
        if (!hasTemplateParser(templateJson)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vendor invoice template JSON must include a parser with itemLineRegex.");
        }
        Map<String, Object> supplier = vendorId == null || vendorId.isBlank()
                ? Map.of()
                : unwrapResource(erpNextClient.getResource("Supplier", vendorId));
        if (!hasTemplateSample(supplier)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Active vendors require a validated sample invoice PDF before activation.");
        }
        if (!sampleMatchesTemplate(supplier, templateJson)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Active vendors require a sample invoice that parses item_name, item_id, qty, rate, gst, and total.");
        }
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private void applyTemplateFlags(Map<String, Object> payload) {
        String templateJson = asText(payload.get("aas_invoice_template_json"));
        payload.put("aas_invoice_template_enabled", templateJson.isBlank() ? 0 : 1);
    }

    private boolean hasTemplateParser(String templateJson) {
        try {
            Map<String, Object> map = objectMapper.readValue(templateJson, new TypeReference<Map<String, Object>>() {});
            Object parserObj = map.get("parser");
            Map<String, Object> parser = map;
            if (parserObj instanceof Map<?, ?> parserMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) parserMap;
                parser = casted;
            }
            String itemLineRegex = asText(parser.get("itemLineRegex"));
            return !itemLineRegex.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasTemplateSample(Map<String, Object> supplier) {
        String samplePdf = asText(supplier.get("aas_invoice_template_sample_pdf"));
        return !samplePdf.isBlank();
    }

    private boolean sampleMatchesTemplate(Map<String, Object> supplier, String templateJson) {
        if (supplier == null || supplier.isEmpty()) {
            return false;
        }
        String samplePdf = asText(supplier.get("aas_invoice_template_sample_pdf"));
        if (samplePdf.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(templateJson, new TypeReference<Map<String, Object>>() {});
            Object parserObj = map.get("parser");
            Map<String, Object> parser = map;
            if (parserObj instanceof Map<?, ?> parserMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) parserMap;
                parser = casted;
            }
            int version = parser.get("version") instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(asText(parser.get("version")));
            String itemLineRegex = asText(parser.get("itemLineRegex"));
            String billDateRegex = asText(parser.get("billDateRegex"));
            String finalAmountRegex = asText(parser.get("finalAmountRegex"));
            if (version <= 0 || itemLineRegex.isBlank()) {
                return false;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveFileUrl(samplePdf))).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            String ocrText = ocrService.extractTextFromPdf(response.body());
            List<ParsedItem> items = templateParser.parseItems(
                    ocrText,
                    new VendorInvoiceTemplate(
                            version,
                            itemLineRegex,
                            billDateRegex.isBlank() ? null : billDateRegex,
                            finalAmountRegex.isBlank() ? null : finalAmountRegex));
            if (items.isEmpty()) {
                return false;
            }
            Set<String> parsedColumns = new LinkedHashSet<>();
            for (ParsedItem item : items) {
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
            if (!parsedColumns.containsAll(invoiceTemplateModelService.requiredItemKeys())) {
                return false;
            }
            Set<String> parsedSummaryFields = new LinkedHashSet<>();
            String finalBillAmount = extractFinalAmount(
                    ocrText,
                    new VendorInvoiceTemplate(
                            version,
                            itemLineRegex,
                            billDateRegex.isBlank() ? null : billDateRegex,
                            finalAmountRegex.isBlank() ? null : finalAmountRegex));
            if (!finalBillAmount.isBlank()) {
                parsedSummaryFields.add("final_bill_amount");
            }
            return parsedSummaryFields.containsAll(invoiceTemplateModelService.requiredSummaryKeys());
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveFileUrl(String filePath) {
        String base = erpBaseUrl.endsWith("/") ? erpBaseUrl.substring(0, erpBaseUrl.length() - 1) : erpBaseUrl;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath;
        }
        String path = filePath.startsWith("/") ? filePath : "/" + filePath;
        return base + path;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String extractFinalAmount(String ocrText, VendorInvoiceTemplate template) {
        if (ocrText == null || ocrText.isBlank() || template == null || !hasText(template.finalAmountRegex())) {
            return "";
        }
        try {
            var matcher = java.util.regex.Pattern.compile(template.finalAmountRegex(), java.util.regex.Pattern.MULTILINE).matcher(ocrText);
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

    private boolean asFlag(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }
}
