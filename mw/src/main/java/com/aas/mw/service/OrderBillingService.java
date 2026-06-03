package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
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
    private static final String TRANSPORT_ITEM_CODE = "AAS-TRANSPORT-CHARGE";
    private static final String INVOICE_VERSION_CURRENT = "CURRENT";

    private final ErpNextClient erpNextClient;
    private final OrderFlowStateMachine orderFlowStateMachine;
    private final OrderPricingService orderPricingService;
    private final double defaultMarginPercent;
    private final String gstTemplate;

    public OrderBillingService(
            ErpNextClient erpNextClient,
            OrderFlowStateMachine orderFlowStateMachine,
            OrderPricingService orderPricingService,
            @Value("${app.billing.gst-template:}") String gstTemplate,
            @Value("${app.order.margin.default-percent:7}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.orderFlowStateMachine = orderFlowStateMachine;
        this.orderPricingService = orderPricingService;
        this.gstTemplate = gstTemplate == null ? "" : gstTemplate.trim();
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
        double transportCharge = readOptionalNonNegative(fields, "transport_charge");
        boolean allowMismatch = asFlag(fields.get("allow_mismatch"));
        double itemsTotal = sumOrderItemsTotal(orderData, vendorBillTotal, transportCharge);
        double expectedBillTotal = round(itemsTotal + transportCharge);
        double diff = round(vendorBillTotal - expectedBillTotal);
        double roundingAdjustment = Math.abs(diff) < 1.0 ? diff : 0.0;
        // Hard validation: bill total must match scanned items total including GST plus optional transport (within rounding tolerance).
        if (itemsTotal > 0 && Math.abs(diff) >= 1.0) {
            if (transportCharge <= 0 && allowMismatch) {
                // Explicit mismatch override: no transport/additional spend is being applied, but the user chose to proceed.
            } else {
            throw new IllegalArgumentException(
                    "Vendor bill total must match scanned items total including GST plus transport charge. "
                            + "Items total=" + itemsTotal
                            + ", Transport=" + round(transportCharge)
                            + ", Bill total=" + round(vendorBillTotal)
                            + ", Diff=" + diff + ".");
            }
        }
        String billRef = asText(fields.get("vendor_bill_ref"));
        if (billRef.isBlank()) {
            billRef = asText(orderData.get("aas_po"));
        }
        String billDate = asText(fields.get("vendor_bill_date"));
        if (billDate.isBlank()) {
            billDate = LocalDate.now().toString();
        }
        double marginPercent = deriveOrderMarginPercent(orderData);

        ensureBillItem();
        Map<String, Object> purchaseInvoice = createPurchaseInvoice(
                orderId, vendor, company, vendorBillTotal, billRef, billDate, roundingAdjustment);

        Map<String, Object> update = new HashMap<>();
        update.put("aas_vendor_bill_total", vendorBillTotal);
        update.put("aas_vendor_bill_ref", billRef);
        update.put("aas_vendor_bill_date", billDate);
        update.put("aas_transport_charge", transportCharge);
        update.put("aas_rounding_adjustment", roundingAdjustment);
        update.put("aas_margin_percent", marginPercent);
        update.put("aas_pi_vendor", extractDocName(purchaseInvoice));
        update.put("aas_status", "VENDOR_BILL_CAPTURED");
        erpNextClient.updateResource(SALES_ORDER, orderId, update);

        return Map.of(
                "orderId", orderId,
                "vendorBillTotal", vendorBillTotal,
                "transportCharge", transportCharge,
                "roundingAdjustment", roundingAdjustment,
                "marginPercent", marginPercent,
                "purchaseInvoice", purchaseInvoice);
    }

    public Map<String, Object> recordGeneratedVendorBill(String orderId, Map<String, Object> fields) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        String vendor = asText(orderData.get("aas_vendor"));
        String company = asText(orderData.get("company"));
        if (vendor.isBlank()) {
            throw new IllegalStateException("Vendor must be assigned before creating generated vendor bill.");
        }
        if (company.isBlank()) {
            throw new IllegalStateException("Order company is required before creating generated vendor bill.");
        }

        double vendorBillTotal = readRequiredPositive(fields, "vendor_bill_total");
        double transportCharge = readOptionalNonNegative(fields, "transport_charge");
        double roundingAdjustment = round(asDouble(fields.get("rounding_adjustment")));
        String billRef = asText(fields.get("vendor_bill_ref"));
        if (billRef.isBlank()) {
            billRef = asText(orderData.get("aas_po"));
        }
        if (billRef.isBlank()) {
            billRef = orderId;
        }
        String billDate = asText(fields.get("vendor_bill_date"));
        if (billDate.isBlank()) {
            billDate = LocalDate.now().toString();
        }
        double marginPercent = deriveOrderMarginPercent(orderData);

        ensureBillItem();
        Map<String, Object> purchaseInvoice = createPurchaseInvoice(
                orderId, vendor, company, vendorBillTotal, billRef, billDate, roundingAdjustment);

        Map<String, Object> update = new HashMap<>();
        update.put("aas_vendor_bill_total", vendorBillTotal);
        update.put("aas_vendor_bill_ref", billRef);
        update.put("aas_vendor_bill_date", billDate);
        update.put("aas_transport_charge", transportCharge);
        update.put("aas_rounding_adjustment", roundingAdjustment);
        update.put("aas_margin_percent", marginPercent);
        update.put("aas_pi_vendor", extractDocName(purchaseInvoice));
        update.put("aas_status", "VENDOR_BILL_CAPTURED");
        erpNextClient.updateResource(SALES_ORDER, orderId, update);

        return Map.of(
                "orderId", orderId,
                "vendorBillTotal", vendorBillTotal,
                "transportCharge", transportCharge,
                "roundingAdjustment", roundingAdjustment,
                "marginPercent", marginPercent,
                "purchaseInvoice", purchaseInvoice);
    }

    public Map<String, Object> getSellPreview(String orderId) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        double vendorBillTotal = asDouble(orderData.get("aas_vendor_bill_total"));
        if (vendorBillTotal <= 0) {
            throw new IllegalStateException("Vendor bill must be captured before calculating sell preview.");
        }
        List<Map<String, Object>> sellItems = buildSellItems(orderData, vendorBillTotal, deriveOrderMarginPercent(orderData));
        double sellAmount = sumAmount(sellItems);
        double marginPercent = deriveSummaryMarginPercent(vendorBillTotal, sellAmount);
        return Map.of(
                "orderId", orderId,
                "vendorBillTotal", vendorBillTotal,
                "marginPercent", marginPercent,
                "sellAmount", sellAmount,
                "marginAmount", round(sellAmount - vendorBillTotal));
    }

    public Map<String, Object> createSellOrder(String orderId) {
        return createSellOrder(orderId, Map.of());
    }

    public Map<String, Object> createSellOrder(String orderId, Map<String, Object> fields) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        orderFlowStateMachine.ensureCanCreateSellOrder(asText(orderData.get("aas_status")));
        return createSellOrderInternal(orderId, fields, orderData);
    }

    public Map<String, Object> createOrReplaceSellOrder(String orderId, Map<String, Object> fields) {
        Map<String, Object> orderData = unwrap(erpNextClient.getResource(SALES_ORDER, orderId));
        String normalizedStatus = orderFlowStateMachine.normalize(asText(orderData.get("aas_status")));
        String existingSalesInvoiceId = asText(orderData.get("aas_si_branch")).trim();
        if (!"VENDOR_BILL_CAPTURED".equals(normalizedStatus) && !"SELL_ORDER_CREATED".equals(normalizedStatus)) {
            throw new IllegalStateException(
                    "Sell order can only be created or replaced when status is VENDOR_BILL_CAPTURED or SELL_ORDER_CREATED.");
        }
        if ("SELL_ORDER_CREATED".equals(normalizedStatus)) {
            ensureDraftInvoiceCanBeReplaced(SALES_INVOICE, existingSalesInvoiceId, "branch invoice");
        }
        Map<String, Object> result = createSellOrderInternal(orderId, fields, orderData);
        if ("SELL_ORDER_CREATED".equals(normalizedStatus)) {
            Map<String, Object> salesInvoice = result.get("salesInvoice") instanceof Map<?, ?> map ? castMap(map) : null;
            String replacementSalesInvoiceId = extractDocName(salesInvoice);
            archiveReplacedInvoice(SALES_INVOICE, existingSalesInvoiceId, replacementSalesInvoiceId);
        }
        return result;
    }

    private Map<String, Object> createSellOrderInternal(String orderId, Map<String, Object> fields, Map<String, Object> orderData) {
        String customer = asText(orderData.get("customer"));
        String company = asText(orderData.get("company"));
        if (customer.isBlank() || company.isBlank()) {
            throw new IllegalStateException("Order must include customer and company before creating sell order.");
        }
        double vendorBillTotal = asDouble(orderData.get("aas_vendor_bill_total"));
        if (vendorBillTotal <= 0) {
            throw new IllegalStateException("Vendor bill total is required before creating sell order.");
        }
        List<Map<String, Object>> sellItems = buildSellItems(orderData, vendorBillTotal, deriveOrderMarginPercent(orderData));
        List<Map<String, Object>> invoiceItems = new ArrayList<>(sellItems);
        String invoiceCategory = resolveOrderCategory(orderData, sellItems);
        boolean applyTransportToInvoice = asFlag(fields == null ? null : fields.get("apply_transport_to_invoice"));
        double transportCharge = asDouble(orderData.get("aas_transport_charge"));
        if (applyTransportToInvoice && transportCharge > 0) {
            invoiceItems.add(buildTransportInvoiceItem(transportCharge, invoiceCategory));
        }
        double sellTotal = sumAmount(invoiceItems);
        double marginPercent = deriveSummaryMarginPercent(vendorBillTotal, sellTotal);
        Map<String, Object> salesInvoice = createSalesInvoice(orderId, customer, company, invoiceItems, orderData, marginPercent);

        double sellTotalWithTax = sellTotal;
        String salesInvoiceId = extractDocName(salesInvoice);
        if (!salesInvoiceId.isBlank()) {
            Map<String, Object> invoiceDoc = unwrap(erpNextClient.getResource(SALES_INVOICE, salesInvoiceId));
            double roundedTotal = asDouble(invoiceDoc.get("rounded_total"));
            double grandTotal = asDouble(invoiceDoc.get("grand_total"));
            if (roundedTotal > 0) {
                sellTotalWithTax = roundedTotal;
            } else if (grandTotal > 0) {
                sellTotalWithTax = grandTotal;
            }
        }

        Map<String, Object> update = new HashMap<>();
        update.put("aas_so_branch", "");
        update.put("aas_si_branch", extractDocName(salesInvoice));
        update.put("aas_sell_order_total", sellTotalWithTax);
        update.put("aas_status", "SELL_ORDER_CREATED");
        update.put("items", sellItems);
        update.put("aas_margin_percent", marginPercent);
        erpNextClient.updateResource(SALES_ORDER, orderId, update);

        return Map.of(
                "orderId", orderId,
                "salesInvoice", salesInvoice,
                "sellTotal", sellTotal,
                "marginPercent", marginPercent,
                "transportAppliedToInvoice", applyTransportToInvoice && transportCharge > 0,
                "transportCharge", round(transportCharge));
    }

    private void ensureDraftInvoiceCanBeReplaced(String doctype, String invoiceId, String label) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return;
        }
        Map<String, Object> invoice = unwrap(erpNextClient.getResource(doctype, invoiceId));
        int docstatus = (int) Math.round(asDouble(invoice.get("docstatus")));
        if (docstatus != 0) {
            throw new IllegalStateException(
                    "Cannot replace sell order because the current " + label + " (" + invoiceId + ") is not draft.");
        }
    }

    private void archiveReplacedInvoice(String doctype, String oldInvoiceId, String newInvoiceId) {
        if (oldInvoiceId == null || oldInvoiceId.isBlank() || newInvoiceId == null || newInvoiceId.isBlank() || oldInvoiceId.equals(newInvoiceId)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("aas_invoice_version_status", "OLD");
        payload.put("aas_replaced_by", newInvoiceId);
        erpNextClient.updateResource(doctype, oldInvoiceId, payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private Map<String, Object> createPurchaseInvoice(
            String sourceOrderId,
            String vendor,
            String company,
            double vendorBillTotal,
            String billRef,
            String billDate,
            double roundingAdjustment) {
        String companyCurrency = resolveCompanyCurrency(company);
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", vendor);
        payload.put("company", company);
        payload.put("posting_date", billDate);
        payload.put("bill_no", billRef);
        payload.put("currency", companyCurrency);
        payload.put("conversion_rate", 1.0);
        payload.put("price_list_currency", companyCurrency);
        payload.put("plc_conversion_rate", 1.0);
        payload.put("items", List.of(Map.of(
                "item_code", BILL_ITEM_CODE,
                "qty", 1,
                "rate", vendorBillTotal,
                "amount", vendorBillTotal)));
        payload.put("aas_source_sales_order", sourceOrderId);
        payload.put("aas_invoice_version_status", INVOICE_VERSION_CURRENT);
        if (Math.abs(roundingAdjustment) > 0.0001) {
            payload.put("rounding_adjustment", roundingAdjustment);
            payload.put("disable_rounded_total", 0);
            payload.put("rounded_total", round(vendorBillTotal));
        }
        return erpNextClient.createResource(PURCHASE_INVOICE, payload);
    }

    private Map<String, Object> createSalesInvoice(
            String sourceOrderId,
            String customer,
            String company,
            List<Map<String, Object>> items,
            Map<String, Object> sourceOrder,
            double marginPercent) {
        String companyCurrency = resolveCompanyCurrency(company);
        String warehouse = resolveDefaultWarehouse(company);
        if (warehouse != null && !warehouse.isBlank()) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                item.putIfAbsent("warehouse", warehouse);
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer", customer);
        payload.put("company", company);
        payload.put("posting_date", resolveDate(sourceOrder.get("transaction_date")));
        payload.put("currency", companyCurrency);
        payload.put("conversion_rate", 1.0);
        payload.put("price_list_currency", companyCurrency);
        payload.put("plc_conversion_rate", 1.0);
        payload.put("items", items);
        payload.put("aas_margin_percent", marginPercent);
        payload.put("aas_source_sales_order", sourceOrderId);
        payload.put("aas_invoice_version_status", INVOICE_VERSION_CURRENT);
        String category = resolveOrderCategory(sourceOrder, items);
        if (!category.isBlank()) {
            payload.put("aas_category", category);
        }
        int creditDays = resolveBranchCreditDays(customer);
        if (creditDays > 0) {
            String orderDate = resolveDate(sourceOrder.get("transaction_date"));
            String dueDate = LocalDate.parse(orderDate).plusDays(creditDays).toString();
            payload.put("due_date", dueDate);
        }
        double roundingAdjustment = asDouble(sourceOrder.get("aas_rounding_adjustment"));
        if (Math.abs(roundingAdjustment) > 0.0001) {
            payload.put("aas_rounding_adjustment", roundingAdjustment);
            payload.put("rounding_adjustment", roundingAdjustment);
            payload.put("disable_rounded_total", 0);
        }
        if (hasGst(items)) {
            applyGstTemplates(payload, company);
        } else if (!gstTemplate.isBlank()) {
            payload.put("taxes_and_charges", gstTemplate);
        }
        if (warehouse != null && !warehouse.isBlank()) {
            payload.put("set_warehouse", warehouse);
        }
        return erpNextClient.createResource(SALES_INVOICE, payload);
    }

    private String resolveOrderCategory(Map<String, Object> sourceOrder, List<Map<String, Object>> items) {
        String category = asText(sourceOrder == null ? null : sourceOrder.get("aas_category"));
        if (!category.isBlank()) {
            return category;
        }
        // Fallback: derive from vendor configuration (Supplier.aas_category).
        String vendor = asText(sourceOrder == null ? null : sourceOrder.get("aas_vendor"));
        if (!vendor.isBlank()) {
            try {
                Map<String, Object> supplier = unwrap(erpNextClient.getResource("Supplier", vendor));
                String vendorCategory = asText(supplier.get("aas_category"));
                if (!vendorCategory.isBlank()) {
                    return vendorCategory;
                }
            } catch (Exception ignored) {
                // Ignore and fall back further.
            }
        }
        // Last resort: first non-empty item_group on invoice items (if present).
        if (items != null) {
            for (Map<String, Object> item : items) {
                if (item == null) {
                    continue;
                }
                String itemGroup = asText(item.get("item_group"));
                if (!itemGroup.isBlank()) {
                    return itemGroup;
                }
            }
        }
        return "";
    }

    private String resolveDefaultWarehouse(String company) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"company\",\"is_group\",\"disabled\"]");
            params.put("limit_page_length", 100);
            List<Map<String, Object>> warehouses = erpNextClient.listResources("Warehouse", params);
            if (warehouses == null || warehouses.isEmpty()) {
                return null;
            }
            for (Map<String, Object> warehouse : warehouses) {
                if (warehouse == null) {
                    continue;
                }
                String name = asText(warehouse.get("name"));
                String warehouseCompany = asText(warehouse.get("company"));
                boolean isGroup = asDouble(warehouse.get("is_group")) >= 1;
                boolean disabled = asDouble(warehouse.get("disabled")) >= 1;
                if (name.isBlank() || isGroup || disabled) {
                    continue;
                }
                if (company != null && !company.isBlank() && !company.equals(warehouseCompany)) {
                    continue;
                }
                return name;
            }
            for (Map<String, Object> warehouse : warehouses) {
                if (warehouse == null) {
                    continue;
                }
                String name = asText(warehouse.get("name"));
                boolean isGroup = asDouble(warehouse.get("is_group")) >= 1;
                boolean disabled = asDouble(warehouse.get("disabled")) >= 1;
                if (!name.isBlank() && !isGroup && !disabled) {
                    return name;
                }
            }
            return asText(warehouses.get(0).get("name"));
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Map<String, Object>> buildSellItems(
            Map<String, Object> sourceOrder,
            double vendorBillTotal,
            double fallbackMarginPercent) {
        Object rawItems = sourceOrder.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            double marginPercent = resolveMarginPercent(null, fallbackMarginPercent);
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
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"), fallbackMarginPercent);
            OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
                    vendorRate,
                    marginPercent,
                    asNullableDouble(row.get("aas_mrp")),
                    asText(row.get("item_name")).isBlank() ? asText(row.get("item_code")) : asText(row.get("item_name")));
            Map<String, Object> item = new HashMap<>();
            item.put("item_code", asText(row.get("item_code")));
            item.put("qty", qty);
            item.put("rate", pricing.sellRate());
            item.put("amount", round(pricing.sellRate() * qty));
            item.put("aas_vendor_rate", vendorRate);
            item.put("aas_margin_percent", pricing.effectiveMarginPercent());
            double gstPercent = asDouble(row.get("aas_gst_percent"));
            if (gstPercent > 0) {
                item.put("aas_gst_percent", gstPercent);
            }
            String description = stripAasLineMeta(asText(row.get("description")));
            if (!description.isBlank()) {
                item.put("description", description);
            }
            if (asNullableDouble(row.get("aas_mrp")) != null) {
                item.put("aas_mrp", asNullableDouble(row.get("aas_mrp")));
            }
            out.add(item);
        }
        if (out.isEmpty()) {
            return buildSellItems(Map.of(), vendorBillTotal, fallbackMarginPercent);
        }
        return out;
    }

    private double deriveOrderMarginPercent(Map<String, Object> orderData) {
        Object rawItems = orderData == null ? null : orderData.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) {
            return resolveMarginPercent(null, orderData == null ? null : orderData.get("aas_margin_percent"));
        }
        double vendorTotal = 0.0;
        double sellTotal = 0.0;
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> row)) {
                continue;
            }
            double qty = asDouble(row.get("qty"));
            if (qty <= 0) {
                qty = 1.0;
            }
            double vendorRate = asDouble(row.get("aas_vendor_rate"));
            if (vendorRate <= 0) {
                vendorRate = asDouble(row.get("rate"));
            }
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"), orderData.get("aas_margin_percent"));
            vendorTotal += vendorRate * qty;
            sellTotal += vendorRate * (1 + marginPercent / 100.0) * qty;
        }
        vendorTotal = round(vendorTotal);
        sellTotal = round(sellTotal);
        if (vendorTotal <= 0) {
            return resolveMarginPercent(null, orderData.get("aas_margin_percent"));
        }
        return deriveSummaryMarginPercent(vendorTotal, sellTotal);
    }

    private double deriveSummaryMarginPercent(double vendorBillTotal, double sellTotal) {
        if (vendorBillTotal <= 0) {
            return defaultMarginPercent;
        }
        return round(((sellTotal - vendorBillTotal) / vendorBillTotal) * 100.0);
    }

    private void ensureBillItem() {
        try {
            Map<String, Object> existing = unwrap(erpNextClient.getResource("Item", BILL_ITEM_CODE));
            Object disabled = existing.get("disabled");
            boolean isDisabled = false;
            if (disabled instanceof Boolean b) {
                isDisabled = b;
            } else if (disabled instanceof Number n) {
                isDisabled = n.intValue() != 0;
            } else if (disabled != null) {
                isDisabled = "1".equals(disabled.toString().trim());
            }
            if (isDisabled) {
                erpNextClient.updateResource("Item", BILL_ITEM_CODE, Map.of("disabled", 0));
            }
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", BILL_ITEM_CODE);
            payload.put("item_name", "Vendor Bill Total");
            payload.put("item_group", "All Item Groups");
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 1);
            payload.put("disabled", 0);
            payload.put("description", "Synthetic item for vendor bill total mapping.");
            erpNextClient.createResource("Item", payload);
        }
    }

    private void ensureTransportItem() {
        try {
            Map<String, Object> existing = unwrap(erpNextClient.getResource("Item", TRANSPORT_ITEM_CODE));
            Object disabled = existing.get("disabled");
            boolean isDisabled = false;
            if (disabled instanceof Boolean b) {
                isDisabled = b;
            } else if (disabled instanceof Number n) {
                isDisabled = n.intValue() != 0;
            } else if (disabled != null) {
                isDisabled = "1".equals(disabled.toString().trim());
            }
            if (isDisabled) {
                erpNextClient.updateResource("Item", TRANSPORT_ITEM_CODE, Map.of("disabled", 0));
            }
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", TRANSPORT_ITEM_CODE);
            payload.put("item_name", "Transport Charges");
            payload.put("item_group", "All Item Groups");
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 0);
            payload.put("disabled", 0);
            payload.put("description", "Synthetic item for customer-side transport recovery.");
            erpNextClient.createResource("Item", payload);
        }
    }

    private Map<String, Object> buildTransportInvoiceItem(double transportCharge, String category) {
        ensureTransportItem();
        double roundedCharge = round(transportCharge);
        Map<String, Object> item = new HashMap<>();
        item.put("item_code", TRANSPORT_ITEM_CODE);
        if (category != null && !category.isBlank()) {
            item.put("item_group", category);
        }
        item.put("qty", 1);
        item.put("rate", roundedCharge);
        item.put("amount", roundedCharge);
        item.put("aas_vendor_rate", roundedCharge);
        item.put("aas_margin_percent", 0.0);
        item.put("aas_gst_percent", 0.0);
        return item;
    }

    private int resolveBranchCreditDays(String customer) {
        if (customer == null || customer.isBlank()) {
            return 0;
        }
        try {
            Map<String, Object> customerDoc = unwrap(erpNextClient.getResource("Customer", customer));
            int days = (int) asDouble(customerDoc.get("aas_credit_days"));
            if (days <= 0) {
                days = (int) asDouble(customerDoc.get("credit_days"));
            }
            return Math.max(days, 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    private String resolveDate(Object value) {
        String text = asText(value);
        return text.isBlank() ? LocalDate.now().toString() : text;
    }

    private String resolveCompanyCurrency(String company) {
        if (company == null || company.isBlank()) {
            return "USD";
        }
        Map<String, Object> companyDoc = unwrap(erpNextClient.getResource("Company", company));
        String currency = asText(companyDoc.get("default_currency"));
        if (currency.isBlank()) {
            currency = asText(companyDoc.get("currency"));
        }
        return currency.isBlank() ? "USD" : currency;
    }

    private double readRequiredPositive(Map<String, Object> fields, String key) {
        double value = asDouble(fields == null ? null : fields.get(key));
        if (value <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero.");
        }
        return value;
    }

    private double readOptionalNonNegative(Map<String, Object> fields, String key) {
        double value = asDouble(fields == null ? null : fields.get(key));
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be non-negative.");
        }
        return value;
    }

    private double resolveMarginPercent(Object requestValue, Object existingValue) {
        Object source = requestValue == null ? existingValue : requestValue;
        double margin = asDouble(source);
        if (source == null || source.toString().trim().isEmpty() || margin == 0.0) {
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

    private boolean hasGst(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (Map<String, Object> item : items) {
            if (asDouble(item.get("aas_gst_percent")) > 0) {
                return true;
            }
        }
        return false;
    }

    private void applyGstTemplates(Map<String, Object> payload, String company) {
        if (payload == null || company == null || company.isBlank()) {
            return;
        }
        Object rawItems = payload.get("items");
        if (!(rawItems instanceof List<?> rawList) || rawList.isEmpty()) {
            return;
        }

        String gstAccountHead = ensureGstAccountHead(company);
        if (gstAccountHead.isBlank()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        boolean applied = false;
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            double gstPercent = asDouble(item.get("aas_gst_percent"));
            if (gstPercent <= 0) {
                continue;
            }
            item.put("item_tax_template", ensureItemTaxTemplate(company, gstAccountHead, gstPercent));
            applied = true;
        }

        if (!applied) {
            return;
        }

        payload.remove("taxes_and_charges");
        payload.remove("taxes");
    }

    private String ensureGstAccountHead(String company) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"account_name\",\"company\",\"account_type\",\"parent_account\",\"is_group\",\"root_type\",\"report_type\"]");
        params.put("filters", "[[\"company\",\"=\",\"" + escape(company) + "\"]]");
        params.put("limit_page_length", 200);
        List<Map<String, Object>> accounts = erpNextClient.listResources("Account", params);
        if (accounts != null) {
            for (Map<String, Object> account : accounts) {
                if (account == null) {
                    continue;
                }
                if (company.equals(asText(account.get("company")))
                        && "Tax".equalsIgnoreCase(asText(account.get("account_type")))
                        && "GST".equalsIgnoreCase(asText(account.get("account_name")))) {
                    return asText(account.get("name"));
                }
            }
            for (Map<String, Object> account : accounts) {
                if (account == null) {
                    continue;
                }
                if (company.equals(asText(account.get("company")))
                        && "Duties and Taxes".equalsIgnoreCase(asText(account.get("account_name")))) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("account_name", "GST");
                    payload.put("company", company);
                    payload.put("parent_account", asText(account.get("name")));
                    payload.put("account_type", "Tax");
                    payload.put("is_group", 0);
                    if (!asText(account.get("root_type")).isBlank()) {
                        payload.put("root_type", asText(account.get("root_type")));
                    }
                    if (!asText(account.get("report_type")).isBlank()) {
                        payload.put("report_type", asText(account.get("report_type")));
                    }
                    Map<String, Object> created = unwrap(erpNextClient.createResource("Account", payload));
                    return asText(created.get("name"));
                }
            }
        }
        return "";
    }

    private String ensureItemTaxTemplate(String company, String gstAccountHead, double gstPercent) {
        String title = buildGstTemplateTitle(gstPercent);
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"title\",\"company\"]");
        params.put(
                "filters",
                "[[\"company\",\"=\",\"" + escape(company) + "\"],[\"title\",\"=\",\"" + escape(title) + "\"]]");
        params.put("limit_page_length", 20);
        List<Map<String, Object>> templates = erpNextClient.listResources("Item Tax Template", params);
        if (templates != null) {
            for (Map<String, Object> template : templates) {
                if (template == null) {
                    continue;
                }
                if (company.equals(asText(template.get("company"))) && title.equals(asText(template.get("title")))) {
                    return asText(template.get("name"));
                }
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("company", company);
        payload.put("taxes", List.of(Map.of(
                "tax_type", gstAccountHead,
                "tax_rate", round(gstPercent))));
        Map<String, Object> created = unwrap(erpNextClient.createResource("Item Tax Template", payload));
        return asText(created.get("name"));
    }

    private String buildGstTemplateTitle(double gstPercent) {
        BigDecimal value = BigDecimal.valueOf(round(gstPercent)).stripTrailingZeros();
        return "AAS GST " + value.toPlainString() + "%";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String stripAasLineMeta(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        return description.lines()
                .map(String::trim)
                .filter(line -> !line.startsWith("[AAS_MANUAL_ENTRY]"))
                .filter(line -> !line.startsWith("[AAS_PARSE_NOTE]"))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private double sumOrderItemsTotal(Map<String, Object> orderData, double referenceBillTotal, double transportCharge) {
        if (orderData == null) {
            return 0.0;
        }
        Object itemsObj = orderData.get("items");
        if (!(itemsObj instanceof List<?> list) || list.isEmpty()) {
            return 0.0;
        }
        boolean gstIncludedInLineAmounts = inferGstIncludedInLineAmounts(list, referenceBillTotal, transportCharge);
        double total = 0.0;
        for (Object rowObj : list) {
            if (rowObj instanceof Map<?, ?> row) {
                double amount = asDouble(((Map<?, ?>) row).get("amount"));
                if (amount > 0) {
                    double gstPercent = asDouble(((Map<?, ?>) row).get("aas_gst_percent"));
                    if (!gstIncludedInLineAmounts && gstPercent > 0) {
                        amount += amount * (gstPercent / 100.0);
                    }
                    total += amount;
                }
            }
        }
        return round(total);
    }

    private boolean inferGstIncludedInLineAmounts(List<?> items, double referenceBillTotal, double transportCharge) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        boolean sawInclusiveRate = false;
        boolean sawExclusiveRate = false;
        double subtotal = 0.0;
        double rawGstOnTopTotal = 0.0;
        for (Object rowObj : items) {
            if (!(rowObj instanceof Map<?, ?> row)) {
                continue;
            }
            double amount = asDouble(row.get("amount"));
            if (amount > 0) {
                subtotal += amount;
                double gstPercent = asDouble(row.get("aas_gst_percent"));
                if (gstPercent > 0) {
                    rawGstOnTopTotal += amount * (gstPercent / 100.0);
                }
            }
            double rate = asDouble(row.get("rate"));
            double rateBeforeTax = asDouble(row.get("aas_rate_before_tax"));
            double rateAfterTax = asDouble(row.get("aas_rate_after_tax"));
            if (rate > 0 && rateBeforeTax > 0 && rateAfterTax > 0 && rateAfterTax > rateBeforeTax) {
                if (Math.abs(rate - rateAfterTax) < 0.01) {
                    sawInclusiveRate = true;
                } else if (Math.abs(rate - rateBeforeTax) < 0.01) {
                    sawExclusiveRate = true;
                }
            }
        }
        if (sawInclusiveRate && !sawExclusiveRate) {
            return true;
        }
        if (sawExclusiveRate && !sawInclusiveRate) {
            return false;
        }
        if (referenceBillTotal <= 0 || rawGstOnTopTotal <= 0) {
            return false;
        }
        double subtotalWithTransport = round(subtotal + transportCharge);
        double totalWithExtraGst = round(subtotalWithTransport + rawGstOnTopTotal);
        double diffToInclusive = Math.abs(referenceBillTotal - subtotalWithTransport);
        double diffToExclusive = Math.abs(referenceBillTotal - totalWithExtraGst);
        return diffToInclusive <= 1.0 && diffToInclusive + 0.01 < diffToExclusive;
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

    private Double asNullableDouble(Object value) {
        double parsed = asDouble(value);
        return parsed > 0 ? parsed : null;
    }

    private boolean asFlag(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
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
