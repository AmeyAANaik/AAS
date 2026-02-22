package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.OrderRequest;
import com.aas.mw.dto.UploadedFileInfo;
import feign.FeignException;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final String DOCTYPE = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String PLACEHOLDER_ITEM_CODE = "AAS-BRANCH-IMAGE";
    private static final double DEFAULT_MARGIN_PERCENT = 10.0;

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService erpNextFileService;
    private final OrderFlowStateMachine orderFlowStateMachine;

    public OrderService(
            ErpNextClient erpNextClient,
            ErpNextFileService erpNextFileService,
            OrderFlowStateMachine orderFlowStateMachine) {
        this.erpNextClient = erpNextClient;
        this.erpNextFileService = erpNextFileService;
        this.orderFlowStateMachine = orderFlowStateMachine;
    }

    public Map<String, Object> createOrder(OrderRequest request) {
        Map<String, Object> fields = request.getFields();
        applySalesOrderDefaults(fields);
        return erpNextClient.createResource(DOCTYPE, fields);
    }

    public Map<String, Object> createOrderWithImage(
            String customer,
            String company,
            String transactionDate,
            String deliveryDate,
            org.springframework.web.multipart.MultipartFile file,
            String sessionCookie) {
        Map<String, Object> payload = new HashMap<>();
        String warehouse = resolveDefaultWarehouse(asText(company));
        if (!warehouse.isBlank()) {
            payload.put("set_warehouse", warehouse);
        }
        payload.put("customer", customer);
        payload.put("company", company);
        payload.put("transaction_date", resolveDate(transactionDate));
        payload.put("delivery_date", resolveDate(deliveryDate, transactionDate));
        payload.put("aas_status", "DRAFT");
        payload.put("aas_margin_percent", DEFAULT_MARGIN_PERCENT);
        payload.put("items", List.of(buildPlaceholderItem(warehouse)));
        Map<String, Object> order = erpNextClient.createResource(DOCTYPE, payload);
        String orderId = extractDocName(order);
        if (orderId != null && !orderId.isBlank()) {
            UploadedFileInfo info = erpNextFileService.uploadOrderImage(orderId, file, sessionCookie);
            if (info.fileUrl() != null) {
                erpNextClient.updateResource(DOCTYPE, orderId, Map.of("aas_branch_image", info.fileUrl()));
            }
        }
        return order;
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

    public Map<String, Object> getOrder(String id) {
        return erpNextClient.getResource(DOCTYPE, id);
    }

    public Map<String, Object> updateOrder(String id, OrderRequest request) {
        Map<String, Object> fields = request.getFields();
        applySalesOrderDefaults(fields);
        return erpNextClient.updateResource(DOCTYPE, id, fields);
    }

    public Map<String, Object> updateOrderFields(String id, Map<String, Object> fields) {
        if (fields.containsKey("aas_status")) {
            String targetStatus = String.valueOf(fields.get("aas_status"));
            Map<String, Object> current = erpNextClient.getResource(DOCTYPE, id);
            String currentStatus = readField(current, "aas_status");
            orderFlowStateMachine.ensureTransitionAllowed(currentStatus, targetStatus);
            fields.put("aas_status", orderFlowStateMachine.normalize(targetStatus));
        }
        return erpNextClient.updateResource(DOCTYPE, id, fields);
    }

    public Map<String, Object> attachOrderImage(String orderId, org.springframework.web.multipart.MultipartFile file, String sessionCookie) {
        UploadedFileInfo info = erpNextFileService.uploadOrderImage(orderId, file, sessionCookie);
        Map<String, Object> update = new HashMap<>();
        if (info.fileUrl() != null) {
            update.put("aas_branch_image", info.fileUrl());
        }
        if (!update.isEmpty()) {
            erpNextClient.updateResource(DOCTYPE, orderId, update);
        }
        return Map.of(
                "orderId", orderId,
                "fileName", info.fileName(),
                "fileUrl", info.fileUrl(),
                "fileId", info.fileId());
    }

    public Map<String, Object> deleteOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order id is required.");
        }
        Map<String, Object> order = erpNextClient.getResource(DOCTYPE, orderId);
        String status = readField(order, "aas_status");
        orderFlowStateMachine.ensureCanDeleteOrder(status);
        String purchaseOrderId = readField(order, "aas_po").trim();

        if (!purchaseOrderId.isBlank()) {
            try {
                erpNextClient.deleteResource(PURCHASE_ORDER, purchaseOrderId);
            } catch (FeignException.NotFound notFound) {
                // Ignore missing PO.
            }
        }
        erpNextClient.deleteResource(DOCTYPE, orderId);
        return Map.of(
                "orderId", orderId,
                "purchaseOrderId", purchaseOrderId);
    }

    private Map<String, Object> buildPlaceholderItem(String warehouse) {
        ensurePlaceholderItem();
        Map<String, Object> item = new HashMap<>();
        item.put("item_code", PLACEHOLDER_ITEM_CODE);
        item.put("qty", 1);
        item.put("rate", 0);
        item.put("price_list_rate", 0);
        item.put("amount", 0);
        if (warehouse != null && !warehouse.isBlank()) {
            item.put("warehouse", warehouse);
        }
        return item;
    }

    private void ensurePlaceholderItem() {
        try {
            erpNextClient.getResource("Item", PLACEHOLDER_ITEM_CODE);
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", PLACEHOLDER_ITEM_CODE);
            payload.put("item_name", "Branch Image Placeholder");
            payload.put("item_group", "All Item Groups");
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 0);
            payload.put("description", "Placeholder item for branch image orders.");
            erpNextClient.createResource("Item", payload);
        }
    }

    private String resolveDate(String value) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return java.time.LocalDate.now().toString();
    }

    private String resolveDate(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return java.time.LocalDate.now().toString();
    }

    public List<Map<String, Object>> listOrders(Map<String, String> filters) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields",
                "[\"name\",\"customer\",\"company\",\"transaction_date\",\"delivery_date\",\"aas_vendor\",\"aas_status\",\"status\",\"grand_total\","
                        + "\"aas_vendor_bill_total\",\"aas_vendor_bill_ref\",\"aas_vendor_bill_date\",\"aas_margin_percent\","
                        + "\"aas_vendor_pdf\",\"aas_po\",\"aas_so_branch\",\"aas_si_branch\"]");
        params.put("order_by", "transaction_date desc");
        if (!filters.isEmpty()) {
            List<List<String>> filterList = new ArrayList<>();
            filters.forEach((key, value) -> {
                if ("from".equals(key)) {
                    filterList.add(List.of("transaction_date", ">=", value));
                } else if ("to".equals(key)) {
                    filterList.add(List.of("transaction_date", "<=", value));
                } else {
                    filterList.add(List.of(key, "=", value));
                }
            });
            params.put("filters", toJson(filterList));
        }
        List<Map<String, Object>> orders = erpNextClient.listResources(DOCTYPE, params);
        addOrderCostMetrics(orders);
        return orders;
    }

    private void addOrderCostMetrics(List<Map<String, Object>> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (Map<String, Object> order : orders) {
            String name = order == null ? null : String.valueOf(order.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> full = erpNextClient.getResource(DOCTYPE, name);
            OrderCost cost = computeOrderCost(full);
            order.put("aas_cost_total", cost.costTotal());
            order.put("aas_margin_total", cost.marginTotal());
            order.put("aas_margin_percent", cost.marginPercent());
        }
    }

    private OrderCost computeOrderCost(Map<String, Object> order) {
        if (order == null) {
            return new OrderCost(0.0, 0.0, 0.0);
        }
        double costTotal = 0.0;
        double sellTotal = 0.0;
        Object items = order.get("items");
        if (items instanceof List<?> list) {
            for (Object itemObj : list) {
                if (itemObj instanceof Map<?, ?> item) {
                    double qty = asDouble(item.get("qty"));
                    double rate = asDouble(item.get("rate"));
                    double amount = asDouble(item.get("amount"));
                    double vendorRate = asDouble(item.get("aas_vendor_rate"));
                    if (amount == 0 && qty > 0) {
                        amount = rate * qty;
                    }
                    sellTotal += amount;
                    if (vendorRate > 0 && qty > 0) {
                        costTotal += vendorRate * qty;
                    }
                }
            }
        }
        double marginTotal = sellTotal - costTotal;
        double marginPercent = costTotal > 0 ? (marginTotal / costTotal) * 100.0 : 0.0;
        return new OrderCost(round(costTotal), round(marginTotal), round(marginPercent));
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void applySalesOrderDefaults(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Object itemsObj = fields.get("items");
        if (!(itemsObj instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        String warehouse = asText(fields.get("set_warehouse"));
        if (warehouse.isBlank()) {
            warehouse = resolveDefaultWarehouse(asText(fields.get("company")));
            if (!warehouse.isBlank()) {
                fields.put("set_warehouse", warehouse);
            }
        }
        if (warehouse.isBlank()) {
            return;
        }
        for (Object rowObj : items) {
            if (rowObj instanceof Map<?, ?> row) {
                Map<String, Object> item = (Map<String, Object>) row;
                String rowWarehouse = asText(item.get("warehouse"));
                if (rowWarehouse.isBlank()) {
                    item.put("warehouse", warehouse);
                }
            }
        }
    }

    private String resolveDefaultWarehouse(String company) {
        if (company.isBlank()) {
            return "";
        }
        String abbr = "";
        try {
            Map<String, Object> companyDoc = erpNextClient.getResource("Company", company);
            abbr = asText(companyDoc.get("abbr"));
        } catch (Exception ex) {
            abbr = "";
        }
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("company", "=", company));
        filters.add(List.of("is_group", "=", "0"));
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"warehouse_name\",\"company\",\"is_group\"]");
        params.put("filters", toJson(filters));
        List<Map<String, Object>> warehouses = erpNextClient.listResources("Warehouse", params);
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

    @SuppressWarnings("unchecked")
    private String readField(Map<String, Object> resource, String fieldName) {
        if (resource == null) {
            return "";
        }
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return String.valueOf(((Map<String, Object>) dataMap).getOrDefault(fieldName, ""));
        }
        return String.valueOf(resource.getOrDefault(fieldName, ""));
    }

    private record OrderCost(double costTotal, double marginTotal, double marginPercent) {
    }

    private String toJson(List<List<String>> filters) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < filters.size(); i++) {
            List<String> entry = filters.get(i);
            builder.append("[");
            for (int j = 0; j < entry.size(); j++) {
                builder.append("\"").append(escape(entry.get(j))).append("\"");
                if (j < entry.size() - 1) {
                    builder.append(",");
                }
            }
            builder.append("]");
            if (i < filters.size() - 1) {
                builder.append(",");
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
