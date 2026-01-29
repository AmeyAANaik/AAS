package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.stereotype.Service;

@Service
public class MasterDataService {

    private final ErpNextClient erpNextClient;

    public MasterDataService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public List<Map<String, Object>> listItems() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_name\",\"item_code\",\"item_group\",\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\"]");
        return erpNextClient.listResources("Item", params);
    }

    public List<Map<String, Object>> listVendors() {
        return erpNextClient.listResources("Supplier", null);
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
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("supplier_type", "Company");
        payload.putIfAbsent("supplier_group", "All Supplier Groups");
        return erpNextClient.createResource("Supplier", payload);
    }

    public Map<String, Object> createCategory(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("parent_item_group", "All Item Groups");
        return erpNextClient.createResource("Item Group", payload);
    }

    public Map<String, Object> createItem(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("item_group", "All Item Groups");
        payload.putIfAbsent("stock_uom", "Nos");
        payload.putIfAbsent("is_stock_item", 1);
        return erpNextClient.createResource("Item", payload);
    }
}
