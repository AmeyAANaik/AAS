package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.meta.VendorFieldMapper;
import com.aas.mw.meta.VendorFieldRegistry;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class MasterDataService {

    private final ErpNextClient erpNextClient;
    private final VendorFieldRegistry vendorFieldRegistry;

    public MasterDataService(ErpNextClient erpNextClient, VendorFieldRegistry vendorFieldRegistry) {
        this.erpNextClient = erpNextClient;
        this.vendorFieldRegistry = vendorFieldRegistry;
    }

    public List<Map<String, Object>> listItems() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_name\",\"item_code\",\"item_group\",\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\",\"aas_packaging_unit\"]");
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

        String trimmed = search == null ? "" : search.trim();
        if (!trimmed.isEmpty()) {
            params.put("or_filters", buildItemSearchFilters(trimmed));
        }

        List<Map<String, Object>> items = erpNextClient.listResources("Item", params);

        Map<String, Object> countParams = new HashMap<>();
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
                        + "\"aas_branch_location\",\"aas_whatsapp_group_name\"]");
        return erpNextClient.listResources("Customer", params);
    }

    public Map<String, Object> createShop(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("customer_type", "Company");
        payload.putIfAbsent("customer_group", "All Customer Groups");
        payload.putIfAbsent("territory", "All Territories");
        return erpNextClient.createResource("Customer", payload);
    }

    public Map<String, Object> createVendor(FieldsRequest request) {
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(filterVendorBaseFields(request.getFields()));
        payload.putAll(mapper.toErpPayload(request.getFields()));
        payload.putIfAbsent("supplier_type", "Company");
        payload.putIfAbsent("supplier_group", "All Supplier Groups");
        return erpNextClient.createResource("Supplier", payload);
    }

    public Map<String, Object> updateVendor(String id, FieldsRequest request) {
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(filterVendorBaseFields(request.getFields()));
        payload.putAll(mapper.toErpPayload(request.getFields()));
        return erpNextClient.updateResource("Supplier", id, payload);
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
}
