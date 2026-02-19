package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OrderBillingService {

    private static final String SALES_ORDER = "Sales Order";
    private static final String SALES_INVOICE = "Sales Invoice";
    private static final String PURCHASE_INVOICE = "Purchase Invoice";
    private static final String BILL_ITEM_CODE = "AAS-VENDOR-BILL";

    private final ErpNextClient erpNextClient;
    private final OrderFlowStateMachine orderFlowStateMachine;
    private final double defaultMarginPercent;

    public OrderBillingService(
            ErpNextClient erpNextClient,
            OrderFlowStateMachine orderFlowStateMachine,
            @Value("${app.order.margin.default-percent:10}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.orderFlowStateMachine = orderFlowStateMachine;
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public Map<String, Object> captureVendorBill(String orderId, Map<String, Object> fields) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        orderFlowStateMachine.ensureCanCaptureVendorBill(asText(orderData.get("aas_status")));
        String vendor = asText(orderData.get("aas_vendor"));
        String company = asText(orderData.get("company"));
        if (vendor.isBlank()) {
            throw new IllegalStateException("Vendor must be assigned before capturing vendor bill.");
        }
        if (company.isBlank()) {
            throw new IllegalStateException("Order company is required before capturing vendor bill.");
        }

        double vendorBillTotal = readRequiredPositive(fields, "vendor_bill_total");
        String billRef = asText(fields.get("vendor_bill_ref"));
        String billDate = asText(fields.get("vendor_bill_date"));
        if (billDate.isBlank()) {
            billDate = LocalDate.now().toString();
        }
        double marginPercent = resolveMarginPercent(fields.get("margin_percent"), orderData.get("aas_margin_percent"));

        ensureBillItem();
        Map<String, Object> purchaseInvoice = createPurchaseInvoice(
                orderId, vendor, company, vendorBillTotal, billRef, billDate);

        Map<String, Object> update = new HashMap<>();
        update.put("aas_vendor_bill_total", vendorBillTotal);
        update.put("aas_vendor_bill_ref", billRef);
        update.put("aas_vendor_bill_date", billDate);
        update.put("aas_margin_percent", marginPercent);
        update.put("aas_pi_vendor", extractDocName(purchaseInvoice));
        update.put("aas_status", "VENDOR_BILL_CAPTURED");
        erpNextClient.updateResource(SALES_ORDER, orderId, update);

        return Map.of(
                "orderId", orderId,
                "vendorBillTotal", vendorBillTotal,
                "marginPercent", marginPercent,
                "purchaseInvoice", purchaseInvoice);
    }

    public Map<String, Object> getSellPreview(String orderId) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        double vendorBillTotal = asDouble(orderData.get("aas_vendor_bill_total"));
        if (vendorBillTotal <= 0) {
            throw new IllegalStateException("Vendor bill must be captured before calculating sell preview.");
        }
        double marginPercent = resolveMarginPercent(null, orderData.get("aas_margin_percent"));
        double sellAmount = round(vendorBillTotal * (1 + marginPercent / 100.0));
        return Map.of(
                "orderId", orderId,
                "vendorBillTotal", vendorBillTotal,
                "marginPercent", marginPercent,
                "sellAmount", sellAmount,
                "marginAmount", round(sellAmount - vendorBillTotal));
    }

    public Map<String, Object> createSellOrder(String orderId) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        orderFlowStateMachine.ensureCanCreateSellOrder(asText(orderData.get("aas_status")));
        String customer = asText(orderData.get("customer"));
        String company = asText(orderData.get("company"));
        if (customer.isBlank() || company.isBlank()) {
            throw new IllegalStateException("Order must include customer and company before creating sell order.");
        }
        double vendorBillTotal = asDouble(orderData.get("aas_vendor_bill_total"));
        if (vendorBillTotal <= 0) {
            throw new IllegalStateException("Vendor bill total is required before creating sell order.");
        }
        double marginPercent = resolveMarginPercent(null, orderData.get("aas_margin_percent"));
        List<Map<String, Object>> sellItems = buildSellItems(orderData, vendorBillTotal, marginPercent);
        double sellTotal = sumAmount(sellItems);
        Map<String, Object> salesOrder = createSalesOrder(orderId, customer, company, sellItems, orderData, marginPercent);
        Map<String, Object> salesInvoice = createSalesInvoice(orderId, customer, company, sellItems, orderData, marginPercent);

        Map<String, Object> update = new HashMap<>();
        update.put("aas_so_branch", extractDocName(salesOrder));
        update.put("aas_si_branch", extractDocName(salesInvoice));
        update.put("aas_sell_order_total", sellTotal);
        update.put("aas_status", "SELL_ORDER_CREATED");
        erpNextClient.updateResource(SALES_ORDER, orderId, update);

        return Map.of(
                "orderId", orderId,
                "salesOrder", salesOrder,
                "salesInvoice", salesInvoice,
                "sellTotal", sellTotal,
                "marginPercent", marginPercent);
    }

    private Map<String, Object> createPurchaseInvoice(
            String sourceOrderId,
            String vendor,
            String company,
            double vendorBillTotal,
            String billRef,
            String billDate) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", vendor);
        payload.put("company", company);
        payload.put("posting_date", billDate);
        payload.put("bill_no", billRef);
        payload.put("items", List.of(Map.of(
                "item_code", BILL_ITEM_CODE,
                "qty", 1,
                "rate", vendorBillTotal,
                "amount", vendorBillTotal)));
        payload.put("aas_source_sales_order", sourceOrderId);
        return erpNextClient.createResource(PURCHASE_INVOICE, payload);
    }

    private Map<String, Object> createSalesOrder(
            String sourceOrderId,
            String customer,
            String company,
            List<Map<String, Object>> items,
            Map<String, Object> sourceOrder,
            double marginPercent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer", customer);
        payload.put("company", company);
        payload.put("transaction_date", resolveDate(sourceOrder.get("transaction_date")));
        payload.put("delivery_date", resolveDate(sourceOrder.get("delivery_date")));
        payload.put("items", items);
        payload.put("aas_margin_percent", marginPercent);
        payload.put("aas_source_sales_order", sourceOrderId);
        return erpNextClient.createResource(SALES_ORDER, payload);
    }

    private Map<String, Object> createSalesInvoice(
            String sourceOrderId,
            String customer,
            String company,
            List<Map<String, Object>> items,
            Map<String, Object> sourceOrder,
            double marginPercent) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer", customer);
        payload.put("company", company);
        payload.put("posting_date", resolveDate(sourceOrder.get("transaction_date")));
        payload.put("items", items);
        payload.put("aas_margin_percent", marginPercent);
        payload.put("aas_source_sales_order", sourceOrderId);
        return erpNextClient.createResource(SALES_INVOICE, payload);
    }

    private List<Map<String, Object>> buildSellItems(
            Map<String, Object> sourceOrder,
            double vendorBillTotal,
            double marginPercent) {
        Object rawItems = sourceOrder.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            double sellAmount = round(vendorBillTotal * (1 + marginPercent / 100.0));
            ensureBillItem();
            return List.of(new HashMap<>(Map.of(
                    "item_code", BILL_ITEM_CODE,
                    "qty", 1,
                    "rate", sellAmount,
                    "amount", sellAmount,
                    "aas_vendor_rate", vendorBillTotal,
                    "aas_margin_percent", marginPercent)));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> row)) {
                continue;
            }
            double qty = asDouble(row.get("qty"));
            if (qty <= 0) {
                qty = 1;
            }
            double vendorRate = asDouble(row.get("aas_vendor_rate"));
            if (vendorRate <= 0) {
                vendorRate = asDouble(row.get("rate"));
            }
            double sellRate = round(vendorRate * (1 + marginPercent / 100.0));
            Map<String, Object> item = new HashMap<>();
            item.put("item_code", asText(row.get("item_code")));
            item.put("qty", qty);
            item.put("rate", sellRate);
            item.put("amount", round(sellRate * qty));
            item.put("aas_vendor_rate", vendorRate);
            item.put("aas_margin_percent", marginPercent);
            out.add(item);
        }
        if (out.isEmpty()) {
            return buildSellItems(Map.of(), vendorBillTotal, marginPercent);
        }
        return out;
    }

    private void ensureBillItem() {
        try {
            erpNextClient.getResource("Item", BILL_ITEM_CODE);
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", BILL_ITEM_CODE);
            payload.put("item_name", "Vendor Bill Total");
            payload.put("item_group", "All Item Groups");
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 1);
            payload.put("description", "Synthetic item for vendor bill total mapping.");
            erpNextClient.createResource("Item", payload);
        }
    }

    private String resolveDate(Object value) {
        String text = asText(value);
        return text.isBlank() ? LocalDate.now().toString() : text;
    }

    private double readRequiredPositive(Map<String, Object> fields, String key) {
        double value = asDouble(fields == null ? null : fields.get(key));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero.");
        }
        return value;
    }

    private double resolveMarginPercent(Object requestValue, Object existingValue) {
        Object source = requestValue == null ? existingValue : requestValue;
        double margin = asDouble(source);
        if (source == null) {
            margin = defaultMarginPercent;
        }
        if (margin < 0) {
            throw new IllegalArgumentException("margin_percent must be non-negative.");
        }
        return margin;
    }

    private double sumAmount(List<Map<String, Object>> items) {
        double total = 0.0;
        for (Map<String, Object> item : items) {
            total += asDouble(item.get("amount"));
        }
        return round(total);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
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
