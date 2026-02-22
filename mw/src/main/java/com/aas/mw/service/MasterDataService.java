package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.meta.VendorFieldMapper;
import com.aas.mw.meta.VendorFieldRegistry;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
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
        return erpNextClient.listResources("Item", params);
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
        return erpNextClient.listResources("Customer", null);
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
}
