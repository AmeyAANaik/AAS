package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentDueService {

    private final ErpNextClient erpNextClient;
    private final AdjustmentNoteErpService adjustmentNoteErpService;
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String FIELD_REVIEW_STATUS = "aas_payment_review_status";
    private static final String FIELD_CATEGORY = "aas_category";
    private static final String ALL_ITEM_GROUPS = "All Item Groups";
    private static final String INVOICE_VERSION_OLD = "OLD";
    private static final String TRANSPORT_ITEM_CODE = "AAS-TRANSPORT-CHARGE";

    public PaymentDueService(ErpNextClient erpNextClient, AdjustmentNoteErpService adjustmentNoteErpService) {
        this.erpNextClient = erpNextClient;
        this.adjustmentNoteErpService = adjustmentNoteErpService;
    }

    public Map<String, Object> dueByCategory(String partyType, String partyId, String categoryId) {
        String normalizedPartyType = normalizePartyType(partyType);
        String normalizedPartyId = partyId == null ? "" : partyId.trim();
        String normalizedCategoryId = categoryId == null ? "" : categoryId.trim();
        if (normalizedPartyId.isBlank()) {
            throw new IllegalArgumentException("partyId is required.");
        }
        if (normalizedCategoryId.isBlank()) {
            throw new IllegalArgumentException("categoryId is required.");
        }

        BigDecimal selectedDue = "Supplier".equalsIgnoreCase(normalizedPartyType)
                ? dueByCategoryForSupplier(normalizedPartyId, normalizedCategoryId)
                : dueByCategoryForCustomer(normalizedPartyId, normalizedCategoryId);

        BigDecimal underReviewAmount = underReviewAmount(normalizedPartyType, normalizedPartyId, normalizedCategoryId, null);
        BigDecimal pendingAdjustmentAmount = adjustmentPendingAmount(normalizedPartyType, normalizedPartyId, normalizedCategoryId, null);
        BigDecimal availableDueAmount = selectedDue.subtract(underReviewAmount).max(BigDecimal.ZERO);
        Map<String, Object> response = new HashMap<>();
        response.put("partyType", normalizedPartyType);
        response.put("partyId", normalizedPartyId);
        response.put("categoryId", normalizedCategoryId);
        response.put("dueAmount", selectedDue);
        response.put("underReviewAmount", underReviewAmount);
        response.put("pendingAdminApprovalAmount", underReviewAmount);
        response.put("pendingAdjustmentAmount", pendingAdjustmentAmount);
        response.put("availableDueAmount", availableDueAmount);
        return response;
    }

    private BigDecimal dueByCategoryForCustomer(String customerId, String categoryId) {
        InvoiceContext ctx = resolveInvoiceContext("Customer");
        List<Map<String, Object>> openInvoices = fetchOpenInvoices(ctx.invoiceDoctype(), ctx.partyField(), customerId);
        Map<String, BigDecimal> dueByCategory = new HashMap<>();
        ItemGroupResolver itemGroupResolver = new ItemGroupResolver(erpNextClient);

        for (Map<String, Object> row : openInvoices) {
            String invoiceId = asText(row.get("name"));
            if (invoiceId.isBlank()) {
                continue;
            }
            Map<String, Object> invoice = unwrapDoc(erpNextClient.getResource(ctx.invoiceDoctype(), invoiceId));
            int docstatus = asInt(firstNonNull(invoice.get("docstatus"), row.get("docstatus")));
            if (docstatus == 2) {
                continue;
            }
            if (isOldOrReplacedInvoice(invoice, row)) {
                continue;
            }
            BigDecimal dueBase = effectiveDueBase(docstatus, invoice, row);
            if (dueBase.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            List<Map<String, Object>> items = childItems(invoice.get("items"));
            distributeOutstandingByItemGroup(dueBase, items, itemGroupResolver, dueByCategory, invoice);
        }
        BigDecimal selectedDue = dueByCategory.getOrDefault(categoryId, BigDecimal.ZERO);
        BigDecimal submittedPayments = submittedCategoryPayments("Customer", customerId, categoryId);
        BigDecimal approvedAdjustments = approvedAdjustmentImpact("Customer", customerId, categoryId);
        return selectedDue.subtract(submittedPayments).add(approvedAdjustments).max(BigDecimal.ZERO);
    }

    private BigDecimal dueByCategoryForSupplier(String supplierId, String categoryId) {
        List<Map<String, Object>> purchaseInvoices = fetchOpenPurchaseInvoices(supplierId);
        Map<String, BigDecimal> dueByCategory = new HashMap<>();
        ItemGroupResolver itemGroupResolver = new ItemGroupResolver(erpNextClient);
        Map<String, CategoryWeights> orderCache = new HashMap<>();

        for (Map<String, Object> row : purchaseInvoices) {
            String invoiceId = asText(row.get("name"));
            if (invoiceId.isBlank()) {
                continue;
            }
            int docstatus = asInt(row.get("docstatus"));
            if (docstatus == 2) {
                continue;
            }
            if (isOldOrReplacedInvoice(row, row)) {
                continue;
            }
            BigDecimal dueBase = effectiveDueBase(docstatus, row, row);
            if (dueBase.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> invoice = unwrapDoc(erpNextClient.getResource("Purchase Invoice", invoiceId));
            if (isOldOrReplacedInvoice(invoice, row)) {
                continue;
            }
            String sourceOrderId = asText(invoice.get("aas_source_sales_order"));
            if (sourceOrderId.isBlank()) {
                List<Map<String, Object>> items = childItems(invoice.get("items"));
                distributeOutstandingByItemGroup(dueBase, items, itemGroupResolver, dueByCategory, invoice);
                continue;
            }
            CategoryWeights weights = orderCache.computeIfAbsent(
                    sourceOrderId,
                    key -> computeSalesOrderCategoryWeights(key, itemGroupResolver));
            if (weights.total().compareTo(BigDecimal.ZERO) <= 0 || weights.weights().isEmpty()) {
                throw new IllegalStateException("Unable to compute category weights for Sales Order " + sourceOrderId + ".");
            }
            for (Map.Entry<String, BigDecimal> entry : weights.weights().entrySet()) {
                BigDecimal share = dueBase.multiply(entry.getValue())
                        .divide(weights.total(), 6, java.math.RoundingMode.HALF_UP);
                dueByCategory.merge(entry.getKey(), share, BigDecimal::add);
            }
        }
        BigDecimal submittedPayments = submittedCategoryPayments("Supplier", supplierId, categoryId);
        BigDecimal approvedAdjustments = approvedAdjustmentImpact("Supplier", supplierId, categoryId);
        return dueByCategory.getOrDefault(categoryId, BigDecimal.ZERO)
                .subtract(submittedPayments)
                .add(approvedAdjustments)
                .max(BigDecimal.ZERO);
    }

    private List<Map<String, Object>> fetchOpenPurchaseInvoices(String supplierId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"grand_total\",\"outstanding_amount\",\"docstatus\",\"aas_invoice_version_status\",\"aas_replaced_by\",\"aas_category\"]");
        params.put("limit_page_length", 500);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("supplier", "=", supplierId));
        filters.add(List.of("docstatus", "in", "[0,1]"));
        params.put("filters", toJson(filters));
        return erpNextClient.listResources("Purchase Invoice", params);
    }

    private CategoryWeights computeSalesOrderCategoryWeights(String salesOrderId, ItemGroupResolver itemGroupResolver) {
        Map<String, Object> order = unwrapDoc(erpNextClient.getResource("Sales Order", salesOrderId));
        List<Map<String, Object>> items = childItems(order.get("items"));
        if (items.isEmpty()) {
            throw new IllegalStateException("Sales Order " + salesOrderId + " has no items; cannot compute category weights.");
        }
        Map<String, BigDecimal> weights = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal qty = asDecimal(item.get("qty"));
            BigDecimal rate = asDecimal(firstNonNull(item.get("aas_vendor_rate"), item.get("rate")));
            if (qty.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal amount = qty.multiply(rate);
            String group = asText(firstNonNull(item.get("item_group"), item.get("item_group_name")));
            if (group.isBlank()) {
                String code = asText(item.get("item_code"));
                group = itemGroupResolver.resolve(code);
            }
            if (group.isBlank()) {
                String code = asText(item.get("item_code"));
                throw new IllegalStateException("Unable to resolve category (item group) for Sales Order item " + (code.isBlank() ? "<unknown>" : code) + ".");
            }
            weights.merge(group, amount, BigDecimal::add);
            total = total.add(amount);
        }
        if (weights.isEmpty() || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Sales Order " + salesOrderId + " has no category information; cannot compute category weights.");
        }
        return new CategoryWeights(weights, total);
    }

    private List<Map<String, Object>> fetchOpenInvoices(String doctype, String partyField, String partyId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"grand_total\",\"outstanding_amount\",\"docstatus\",\"aas_invoice_version_status\",\"aas_replaced_by\",\"is_opening\",\"po_no\",\"remarks\"]");
        params.put("limit_page_length", 500);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of(partyField, "=", partyId));
        filters.add(List.of("docstatus", "in", "[0,1]"));
        params.put("filters", toJson(filters));
        return erpNextClient.listResources(doctype, params);
    }

    private boolean isOldOrReplacedInvoice(Map<String, Object> invoice, Map<String, Object> row) {
        String versionStatus = asText(firstNonNull(invoice.get("aas_invoice_version_status"), row.get("aas_invoice_version_status")));
        if (INVOICE_VERSION_OLD.equalsIgnoreCase(versionStatus)) {
            return true;
        }
        return !asText(firstNonNull(invoice.get("aas_replaced_by"), row.get("aas_replaced_by"))).isBlank();
    }

    private BigDecimal effectiveDueBase(int docstatus, Map<String, Object> invoice, Map<String, Object> row) {
        if (docstatus == 0) {
            return asDecimal(firstNonNull(invoice.get("grand_total"), row.get("grand_total")));
        }
        BigDecimal outstanding = asDecimal(firstNonNull(invoice.get("outstanding_amount"), row.get("outstanding_amount")));
        if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
            return outstanding;
        }
        return BigDecimal.ZERO;
    }

    private void distributeOutstandingByItemGroup(
            BigDecimal outstanding,
            List<Map<String, Object>> items,
            ItemGroupResolver itemGroupResolver,
            Map<String, BigDecimal> dueByCategory,
            Map<String, Object> invoice) {
        String invoiceCategory = normalizeItemGroup(asText(invoice.get(FIELD_CATEGORY)));
        if (items.isEmpty()) {
            if (!invoiceCategory.isBlank()) {
                dueByCategory.merge(invoiceCategory, outstanding, BigDecimal::add);
                return;
            }
            throw new IllegalStateException("Invoice has no items; cannot allocate by category.");
        }
        BigDecimal total = BigDecimal.ZERO;
        List<Line> lines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            BigDecimal amount = asDecimal(firstNonNull(
                    item.get("base_amount"),
                    firstNonNull(item.get("amount"),
                            firstNonNull(item.get("net_amount"), item.get("base_net_amount")))));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String group = normalizeItemGroup(asText(firstNonNull(item.get("item_group"), item.get("item_group_name"))));
            if (group.isBlank()) {
                String code = asText(item.get("item_code"));
                group = normalizeItemGroup(itemGroupResolver.resolve(code));
            }
            if (group.isBlank() && TRANSPORT_ITEM_CODE.equals(asText(item.get("item_code")))) {
                group = invoiceCategory;
            }
            if (group.isBlank()) {
                group = invoiceCategory;
            }
            if (group.isBlank()) {
                String code = asText(item.get("item_code"));
                throw new IllegalStateException("Unable to resolve category (item group) for item " + (code.isBlank() ? "<unknown>" : code) + ".");
            }
            lines.add(new Line(group, amount));
            total = total.add(amount);
        }
        if (lines.isEmpty() || total.compareTo(BigDecimal.ZERO) <= 0) {
            if (!invoiceCategory.isBlank()) {
                dueByCategory.merge(invoiceCategory, outstanding, BigDecimal::add);
                return;
            }
            throw new IllegalStateException("Invoice has no allocatable item amounts; cannot allocate by category.");
        }
        for (Line line : lines) {
            BigDecimal share = outstanding.multiply(line.amount()).divide(total, 6, java.math.RoundingMode.HALF_UP);
            dueByCategory.merge(line.group(), share, BigDecimal::add);
        }
    }

    private BigDecimal submittedCategoryPayments(String partyType, String partyId, String categoryId) {
        String normalizedPartyId = partyId == null ? "" : partyId.trim();
        String normalizedCategoryId = categoryId == null ? "" : categoryId.trim();
        if (normalizedPartyId.isBlank() || normalizedCategoryId.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"paid_amount\",\"received_amount\"]");
            params.put("limit_page_length", 500);
            List<List<String>> filters = new ArrayList<>();
            filters.add(List.of("docstatus", "=", "1"));
            filters.add(List.of("party_type", "=", partyType));
            filters.add(List.of("party", "=", normalizedPartyId));
            filters.add(List.of(FIELD_CATEGORY, "=", normalizedCategoryId));
            params.put("filters", toJson(filters));
            List<Map<String, Object>> rows = erpNextClient.listResources(PAYMENT_ENTRY, params);
            if (rows == null || rows.isEmpty()) {
                return BigDecimal.ZERO;
            }
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                BigDecimal amount = paymentAmount(row);
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    total = total.add(amount);
                }
            }
            return total;
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal paymentAmount(Map<String, Object> payment) {
        BigDecimal paid = asDecimal(payment.get("paid_amount"));
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return paid;
        }
        return asDecimal(payment.get("received_amount"));
    }

    private String normalizeItemGroup(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank() || ALL_ITEM_GROUPS.equalsIgnoreCase(text)) {
            return "";
        }
        return text;
    }

    private InvoiceContext resolveInvoiceContext(String partyType) {
        if ("Supplier".equalsIgnoreCase(partyType)) {
            return new InvoiceContext("Purchase Invoice", "supplier");
        }
        return new InvoiceContext("Sales Invoice", "customer");
    }

    private String normalizePartyType(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.equalsIgnoreCase("Supplier") || normalized.equalsIgnoreCase("Vendor")) {
            return "Supplier";
        }
        return "Customer";
    }

    private BigDecimal underReviewAmount(String partyType, String partyId, String categoryId, String excludePaymentId) {
        String normalizedPartyId = partyId == null ? "" : partyId.trim();
        String normalizedCategoryId = categoryId == null ? "" : categoryId.trim();
        if (normalizedPartyId.isBlank() || normalizedCategoryId.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"paid_amount\",\"received_amount\"]");
            params.put("limit_page_length", 500);
            List<List<String>> filters = new ArrayList<>();
            filters.add(List.of("docstatus", "=", "0"));
            filters.add(List.of(FIELD_REVIEW_STATUS, "=", "UNDER_REVIEW"));
            filters.add(List.of("party_type", "=", partyType));
            filters.add(List.of("party", "=", normalizedPartyId));
            filters.add(List.of(FIELD_CATEGORY, "=", normalizedCategoryId));
            if (excludePaymentId != null && !excludePaymentId.isBlank()) {
                filters.add(List.of("name", "!=", excludePaymentId.trim()));
            }
            params.put("filters", toJson(filters));
            List<Map<String, Object>> rows = erpNextClient.listResources(PAYMENT_ENTRY, params);
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                BigDecimal paid = asDecimal(row.get("paid_amount"));
                if (paid.compareTo(BigDecimal.ZERO) <= 0) {
                    paid = asDecimal(row.get("received_amount"));
                }
                if (paid.compareTo(BigDecimal.ZERO) > 0) {
                    total = total.add(paid);
                }
            }
            return total;
        } catch (Exception ex) {
            String message = String.valueOf(ex.getMessage() == null ? "" : ex.getMessage()).toLowerCase();
            if (message.contains("field not permitted in query")) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal approvedAdjustmentImpact(String partyType, String partyId, String categoryId) {
        try {
            return adjustmentNoteErpService.approvedDueImpact(partyType, partyId, categoryId);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal adjustmentPendingAmount(String partyType, String partyId, String categoryId, String excludeNoteId) {
        try {
            BigDecimal impact = adjustmentNoteErpService.pendingAmount(partyType, partyId, categoryId, excludeNoteId);
            return impact.abs();
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
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
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childItems(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapDoc(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Object message = response.get("message");
        if (message instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private record InvoiceContext(String invoiceDoctype, String partyField) {}

    private record Line(String group, BigDecimal amount) {}

    private record CategoryWeights(Map<String, BigDecimal> weights, BigDecimal total) {}

    private static class ItemGroupResolver {
        private final ErpNextClient erpNextClient;
        private final Map<String, String> cache = new HashMap<>();

        ItemGroupResolver(ErpNextClient erpNextClient) {
            this.erpNextClient = erpNextClient;
        }

        String resolve(String itemCode) {
            String key = itemCode == null ? "" : itemCode.trim();
            if (key.isBlank()) {
                return "";
            }
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
            String group = "";
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = (Map<String, Object>) erpNextClient.getResource("Item", key).getOrDefault("data", Map.of());
                group = doc == null ? "" : String.valueOf(doc.getOrDefault("item_group", "")).trim();
            } catch (Exception ignored) {
                group = "";
            }
            cache.put(key, group);
            return group;
        }
    }
}
