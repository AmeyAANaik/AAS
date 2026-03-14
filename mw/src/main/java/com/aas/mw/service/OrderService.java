package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.OrderItemLine;
import com.aas.mw.dto.OrderRequest;
import com.aas.mw.dto.UploadedFileInfo;
import feign.FeignException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final String DOCTYPE = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String BRANCH_IMAGE_ITEM_CODE = "AAS-SYSTEM-BRANCH-IMAGE";
    private final ErpNextClient erpNextClient;
    private final ErpNextFileService erpNextFileService;
    private final OrderFlowStateMachine orderFlowStateMachine;
    private final double defaultMarginPercent;

    public OrderService(
            ErpNextClient erpNextClient,
            ErpNextFileService erpNextFileService,
            OrderFlowStateMachine orderFlowStateMachine,
            @Value("${app.order.margin.default-percent:7}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.erpNextFileService = erpNextFileService;
        this.orderFlowStateMachine = orderFlowStateMachine;
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public Map<String, Object> createOrder(OrderRequest request) {
        Map<String, Object> fields = request.getFields();
        ensureSalesOrderPricingDefaults(fields, asText(fields.get("company")));
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
        // ERPNext often requires a selling price list + currency fields on Sales Order.
        ensureSalesOrderPricingDefaults(payload, asText(company));
        payload.put("transaction_date", resolveDate(transactionDate));
        payload.put("delivery_date", resolveDate(deliveryDate, transactionDate));
        payload.put("aas_status", "DRAFT");
        payload.put("aas_margin_percent", defaultMarginPercent);
        payload.put("items", List.of(applyItemMarginDefaults(buildBranchImageItem(warehouse))));
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

    private void ensureSalesOrderPricingDefaults(Map<String, Object> payload, String company) {
        if (payload == null) {
            return;
        }
        if (!payload.containsKey("selling_price_list")) {
            PriceListChoice choice = resolveSellingPriceList();
            if (!choice.name().isBlank()) {
                payload.put("selling_price_list", choice.name());
                if (!payload.containsKey("price_list_currency") && !choice.currency().isBlank()) {
                    payload.put("price_list_currency", choice.currency());
                }
            }
        }
        String currency = asText(payload.get("price_list_currency"));
        if (currency.isBlank()) {
            currency = resolveCompanyCurrency(company);
            if (!currency.isBlank()) {
                payload.put("price_list_currency", currency);
            }
        }
        if (!payload.containsKey("currency") && !currency.isBlank()) {
            payload.put("currency", currency);
        }
        if (!payload.containsKey("conversion_rate")) {
            payload.put("conversion_rate", 1);
        }
        if (!payload.containsKey("plc_conversion_rate")) {
            payload.put("plc_conversion_rate", 1);
        }
    }

    private PriceListChoice resolveSellingPriceList() {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"currency\"]");
            params.put("limit_page_length", "1");
            params.put("filters", "[[\"selling\",\"=\",\"1\"],[\"enabled\",\"=\",\"1\"]]");
            List<Map<String, Object>> lists = erpNextClient.listResources("Price List", params);
            if (lists.isEmpty()) {
                return new PriceListChoice("", "");
            }
            Map<String, Object> row = lists.get(0);
            return new PriceListChoice(asText(row.get("name")), asText(row.get("currency")));
        } catch (Exception ex) {
            return new PriceListChoice("", "");
        }
    }

    private String resolveCompanyCurrency(String company) {
        if (company == null || company.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> companyDoc = erpNextClient.getResource("Company", company);
            return asText(companyDoc.get("default_currency"));
        } catch (Exception ex) {
            return "";
        }
    }

    private record PriceListChoice(String name, String currency) {}

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

    public Map<String, Object> updateOrderItems(String orderId, List<OrderItemLine> items) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order id is required.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items are required.");
        }

        Map<String, Object> order = erpNextClient.getResource(DOCTYPE, orderId);
        Map<String, Object> orderData = unwrap(order);
        String status = asText(orderData.get("aas_status"));
        String normalized = orderFlowStateMachine.normalize(status);
        if (!"VENDOR_ASSIGNED".equals(normalized) && !"VENDOR_PDF_RECEIVED".equals(normalized)) {
            throw new IllegalStateException("Order items can only be edited when status is VENDOR_ASSIGNED or VENDOR_PDF_RECEIVED.");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existingSoItems =
                orderData.get("items") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
        List<Map<String, Object>> updatedSoItems = buildUpdatedChildItems(
                "Sales Order Item",
                existingSoItems,
                items);
        Map<String, Object> updatedOrder = erpNextClient.updateResource(DOCTYPE, orderId, Map.of("items", updatedSoItems));

        String purchaseOrderId = asText(orderData.get("aas_po")).trim();
        Map<String, Object> updatedPo = null;
        if (!purchaseOrderId.isBlank()) {
            Map<String, Object> po = erpNextClient.getResource(PURCHASE_ORDER, purchaseOrderId);
            Map<String, Object> poData = unwrap(po);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingPoItems =
                    poData.get("items") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
            List<Map<String, Object>> updatedPoItems = buildUpdatedChildItems(
                    "Purchase Order Item",
                    existingPoItems,
                    items);
            updatedPo = erpNextClient.updateResource(PURCHASE_ORDER, purchaseOrderId, Map.of("items", updatedPoItems));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("order", updatedOrder);
        response.put("purchaseOrderId", purchaseOrderId);
        if (updatedPo != null) {
            response.put("purchaseOrder", updatedPo);
        }
        response.put("items", extractSimpleItems(unwrap(updatedOrder)));
        return response;
    }

    private Map<String, Object> buildBranchImageItem(String warehouse) {
        ensureBranchImageItem();
        Map<String, Object> item = new HashMap<>();
        item.put("item_code", BRANCH_IMAGE_ITEM_CODE);
        item.put("qty", 1);
        item.put("rate", 0);
        item.put("price_list_rate", 0);
        item.put("amount", 0);
        if (warehouse != null && !warehouse.isBlank()) {
            item.put("warehouse", warehouse);
        }
        return item;
    }

    private void ensureBranchImageItem() {
        try {
            Map<String, Object> item = unwrap(erpNextClient.getResource("Item", BRANCH_IMAGE_ITEM_CODE));
            Object disabled = item.get("disabled");
            if (disabled instanceof Number n && n.intValue() != 0) {
                erpNextClient.updateResource("Item", BRANCH_IMAGE_ITEM_CODE, Map.of("disabled", 0));
            } else if (disabled instanceof Boolean b && b) {
                erpNextClient.updateResource("Item", BRANCH_IMAGE_ITEM_CODE, Map.of("disabled", 0));
            } else if (disabled != null && "1".equals(disabled.toString().trim())) {
                erpNextClient.updateResource("Item", BRANCH_IMAGE_ITEM_CODE, Map.of("disabled", 0));
            }
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", BRANCH_IMAGE_ITEM_CODE);
            payload.put("item_name", "System Branch Image");
            payload.put("item_group", "All Item Groups");
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 0);
            payload.put("disabled", 0);
            payload.put("description", "Internal system item used for branch image order creation.");
            erpNextClient.createResource("Item", payload);
        }
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
        Map<String, Object> orderData = unwrap(order);
        String status = readField(order, "aas_status");
        orderFlowStateMachine.ensureCanDeleteOrder(status);
        String purchaseOrderId = asText(orderData.get("aas_po")).trim();
        boolean deletedPurchaseOrder = false;

        if (!purchaseOrderId.isBlank()) {
            deletedPurchaseOrder = deleteLinkedDraftPurchaseOrder(orderId, purchaseOrderId);
        }

        clearInboundSalesOrderLinks(orderId);
        erpNextClient.deleteResource(DOCTYPE, orderId);
        return Map.of(
                "orderId", orderId,
                "purchaseOrderId", purchaseOrderId,
                "purchaseOrderDeleted", deletedPurchaseOrder);
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
                        + "\"currency\",\"price_list_currency\","
                        + "\"aas_vendor_bill_total\",\"aas_vendor_bill_ref\",\"aas_vendor_bill_date\",\"aas_margin_percent\","
                        + "\"aas_vendor_pdf\",\"aas_po\",\"aas_so_branch\",\"aas_si_branch\"]");
        // Sort by last modification so newly created orders show up reliably on the first page.
        params.put("order_by", "modified desc");
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> resource) {
        if (resource == null) {
            return Map.of();
        }
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return resource;
    }

    private List<Map<String, Object>> buildUpdatedChildItems(
            String childDoctype,
            List<Map<String, Object>> existing,
            List<OrderItemLine> desired) {
        // Match existing rows by item_code (first unused match) so updates are stable and don't create duplicates.
        boolean[] used = new boolean[existing == null ? 0 : existing.size()];
        List<Map<String, Object>> out = new ArrayList<>();
        for (OrderItemLine line : desired) {
            String itemCode = asText(line.getItem_code());
            if (itemCode.isBlank()) {
                continue;
            }
            int matchIdx = -1;
            for (int i = 0; i < used.length; i++) {
                if (used[i]) {
                    continue;
                }
                Map<String, Object> row = existing.get(i);
                if (itemCode.equalsIgnoreCase(asText(row.get("item_code")))) {
                    matchIdx = i;
                    used[i] = true;
                    break;
                }
            }
            Map<String, Object> row = new HashMap<>();
            row.put("doctype", childDoctype);
            row.put("item_code", itemCode);
            row.put("qty", line.getQty());
            row.put("rate", line.getRate());
            row.put("amount", line.getQty() * line.getRate());
            row.put("aas_margin_percent", resolveMarginPercent(line.getAas_margin_percent(), itemCode));
            if (matchIdx >= 0) {
                Map<String, Object> existingRow = existing.get(matchIdx);
                Object name = existingRow.get("name");
                if (name != null) {
                    row.put("name", name);
                }
                copyIfPresent(existingRow, row, "warehouse");
                copyIfPresent(existingRow, row, "uom");
                copyIfPresent(existingRow, row, "stock_uom");
                copyIfPresent(existingRow, row, "conversion_factor");
                copyIfPresent(existingRow, row, "schedule_date");
                copyIfPresent(existingRow, row, "expense_account");
                copyIfPresent(existingRow, row, "cost_center");
                copyIfPresent(existingRow, row, "aas_vendor_rate");
            }
            out.add(row);
        }
        return out;
    }

    private void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from == null || to == null || key == null) {
            return;
        }
        if (from.containsKey(key) && from.get(key) != null) {
            to.put(key, from.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSimpleItems(Map<String, Object> doc) {
        if (doc == null) {
            return List.of();
        }
        Object itemsObj = doc.get("items");
        if (!(itemsObj instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object obj : list) {
            if (obj instanceof Map<?, ?> map) {
                Map<String, Object> row = (Map<String, Object>) map;
                Map<String, Object> simple = new HashMap<>();
                simple.put("item_code", row.get("item_code"));
                simple.put("item_name", row.getOrDefault("item_name", row.get("item_code")));
                simple.put("qty", row.get("qty"));
                simple.put("rate", row.get("rate"));
                simple.put("amount", row.get("amount"));
                simple.put("aas_margin_percent", row.get("aas_margin_percent"));
                out.add(simple);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void applySalesOrderDefaults(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        double margin = asDouble(fields.get("aas_margin_percent"));
        if (!fields.containsKey("aas_margin_percent") || margin <= 0) {
            fields.put("aas_margin_percent", defaultMarginPercent);
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
        for (Object rowObj : items) {
            if (!(rowObj instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) row;
            String itemCode = asText(item.get("item_code"));
            item.put("aas_margin_percent", resolveMarginPercent(item.get("aas_margin_percent"), itemCode));
            if (!warehouse.isBlank() && asText(item.get("warehouse")).isBlank()) {
                item.put("warehouse", warehouse);
            }
        }
    }

    private Map<String, Object> applyItemMarginDefaults(Map<String, Object> item) {
        if (item == null) {
            return Map.of();
        }
        Map<String, Object> copy = new HashMap<>(item);
        String itemCode = asText(copy.get("item_code"));
        copy.put("aas_margin_percent", resolveMarginPercent(copy.get("aas_margin_percent"), itemCode));
        return copy;
    }

    private double resolveMarginPercent(Object value, String itemCode) {
        double margin = asDouble(value);
        if (value != null && !value.toString().trim().isEmpty() && margin > 0) {
            return margin;
        }
        double itemMargin = resolveItemMarginPercent(itemCode);
        if (itemMargin > 0) {
            return itemMargin;
        }
        return defaultMarginPercent;
    }

    private boolean deleteLinkedDraftPurchaseOrder(String orderId, String purchaseOrderId) {
        Map<String, Object> purchaseOrder = unwrap(erpNextClient.getResource(PURCHASE_ORDER, purchaseOrderId));
        int purchaseOrderDocstatus = (int) Math.round(asDouble(purchaseOrder.get("docstatus")));
        if (purchaseOrderDocstatus != 0) {
            throw new IllegalStateException(
                    "Order cannot be deleted because linked Purchase Order "
                            + purchaseOrderId
                            + " is submitted/cancelled. Cancel it in ERPNext first.");
        }

        List<Map<String, Object>> linkedPurchaseInvoices = listLinkedPurchaseInvoices(orderId);
        if (!linkedPurchaseInvoices.isEmpty()) {
            String invoiceIds = linkedPurchaseInvoices.stream()
                    .map(row -> asText(row.get("name")))
                    .filter(name -> !name.isBlank())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            throw new IllegalStateException(
                    "Order cannot be deleted because linked Purchase Invoice exists"
                            + (invoiceIds.isBlank() ? "." : ": " + invoiceIds + "."));
        }

        // ERPNext blocks deleting the Purchase Order while the Sales Order still links to it.
        erpNextClient.updateResource(DOCTYPE, orderId, Map.of("aas_po", ""));
        erpNextClient.deleteResource(PURCHASE_ORDER, purchaseOrderId);
        return true;
    }

    private List<Map<String, Object>> listLinkedPurchaseInvoices(String orderId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"docstatus\"]");
        params.put("filters", "[[\"Purchase Invoice\",\"aas_source_sales_order\",\"=\",\"" + escape(orderId) + "\"]]");
        return erpNextClient.listResources("Purchase Invoice", params);
    }

    private void clearInboundSalesOrderLinks(String orderId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"aas_so_branch\",\"aas_si_branch\"]");
        params.put(
                "or_filters",
                "[[\"Sales Order\",\"aas_so_branch\",\"=\",\"" + escape(orderId) + "\"],"
                        + "[\"Sales Order\",\"aas_si_branch\",\"=\",\"" + escape(orderId) + "\"]]");
        List<Map<String, Object>> linkedOrders = erpNextClient.listResources(DOCTYPE, params);
        for (Map<String, Object> linkedOrder : linkedOrders) {
            String linkedOrderId = asText(linkedOrder.get("name"));
            if (linkedOrderId.isBlank() || linkedOrderId.equals(orderId)) {
                continue;
            }
            Map<String, Object> payload = new HashMap<>();
            if (orderId.equals(asText(linkedOrder.get("aas_so_branch")))) {
                payload.put("aas_so_branch", "");
            }
            if (orderId.equals(asText(linkedOrder.get("aas_si_branch")))) {
                payload.put("aas_si_branch", "");
            }
            if (!payload.isEmpty()) {
                erpNextClient.updateResource(DOCTYPE, linkedOrderId, payload);
            }
        }
    }

    private double resolveItemMarginPercent(String itemCode) {
        String code = asText(itemCode);
        if (code.isBlank()) {
            return 0.0;
        }
        try {
            Map<String, Object> item = unwrap(erpNextClient.getResource("Item", code));
            double margin = asDouble(item.get("aas_margin_percent"));
            return margin > 0 ? margin : 0.0;
        } catch (Exception ex) {
            return 0.0;
        }
    }
}
