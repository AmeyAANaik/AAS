package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class VendorOpsService {

    private static final String SALES_ORDER = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String PURCHASE_INVOICE = "Purchase Invoice";
    private static final String SUPPLIER = "Supplier";
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String PLACEHOLDER_ITEM = "AAS-BRANCH-IMAGE";
    private static final Set<String> PENDING_STATUSES = Set.of("VENDOR_ASSIGNED", "VENDOR_PDF_RECEIVED", "VENDOR_BILL_CAPTURED");
    private static final DateTimeFormatter ERP_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]", Locale.ROOT);

    private final ErpNextClient erpNextClient;

    public VendorOpsService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> getSummary() {
        List<Map<String, Object>> vendors = fetchVendors();
        List<Map<String, Object>> orders = fetchOrderRows(null);
        List<Map<String, Object>> purchaseInvoices = fetchPurchaseInvoices(null);
        List<Map<String, Object>> payments = fetchSupplierPayments(null);

        List<Map<String, Object>> vendorRows = vendors.stream()
                .map(vendor -> buildVendorSummary(vendor, orders, purchaseInvoices, payments))
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> row) -> asDouble(row.get("pendingOrders"))).reversed()
                        .thenComparing(row -> asText(row.get("vendorName"))))
                .toList();

        double totalPendingBillAmount = vendorRows.stream()
                .mapToDouble(row -> asDouble(row.get("pendingBillAmount")))
                .sum();
        long vendorsWithPendingOrders = vendorRows.stream()
                .filter(row -> asDouble(row.get("pendingOrders")) > 0)
                .count();
        double totalPendingOrders = vendorRows.stream()
                .mapToDouble(row -> asDouble(row.get("pendingOrders")))
                .sum();
        double awaitingPdf = vendorRows.stream()
                .mapToDouble(row -> asDouble(row.get("awaitingPdf")))
                .sum();
        double awaitingBillCapture = vendorRows.stream()
                .mapToDouble(row -> asDouble(row.get("awaitingBillCapture")))
                .sum();

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalVendors", vendorRows.size());
        totals.put("vendorsWithPendingOrders", vendorsWithPendingOrders);
        totals.put("totalPendingOrders", round(totalPendingOrders));
        totals.put("awaitingPdf", round(awaitingPdf));
        totals.put("awaitingBillCapture", round(awaitingBillCapture));
        totals.put("totalPendingBillAmount", round(totalPendingBillAmount));

        return Map.of(
                "totals", totals,
                "vendors", vendorRows);
    }

    public Map<String, Object> getVendorDetail(String vendorId) {
        Map<String, Object> vendor = unwrap(erpNextClient.getResource(SUPPLIER, vendorId));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }

        List<Map<String, Object>> orderRows = getVendorOrders(vendorId, null, null, null, null);
        List<Map<String, Object>> purchaseInvoices = fetchPurchaseInvoices(vendorId);
        List<Map<String, Object>> payments = fetchSupplierPayments(vendorId);
        List<Map<String, Object>> ledgerEntries = buildLedgerEntries(purchaseInvoices, payments);

        double parseSuccessRate = calculateParseSuccessRate(orderRows);
        double billCaptureRate = calculateBillCaptureRate(orderRows);
        double outstandingBalance = round(purchaseInvoices.stream()
                .mapToDouble(invoice -> asDouble(invoice.get("outstanding_amount")))
                .sum());
        long mismatchCount = orderRows.stream().filter(row -> asFlag(row.get("hasMismatch"))).count();
        long parseFailureCount = orderRows.stream()
                .filter(row -> asFlag(row.get("pdfUploaded")) && asDouble(row.get("parsedItems")) <= 0)
                .count();
        long unpaidInvoiceCount = purchaseInvoices.stream()
                .filter(invoice -> asDouble(invoice.get("outstanding_amount")) > 0)
                .count();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("pendingOrders", orderRows.stream().filter(row -> PENDING_STATUSES.contains(asText(row.get("status")))).count());
        kpis.put("awaitingPdf", orderRows.stream().filter(row -> "VENDOR_ASSIGNED".equals(asText(row.get("status")))).count());
        kpis.put("awaitingBillCapture", orderRows.stream().filter(row -> "VENDOR_PDF_RECEIVED".equals(asText(row.get("status")))).count());
        kpis.put("totalVendorBillAmount", round(orderRows.stream().mapToDouble(row -> asDouble(row.get("vendorBillTotal"))).sum()));
        kpis.put("outstandingBalance", outstandingBalance);
        kpis.put("parseSuccessRate", parseSuccessRate);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("status", resolveTemplateStatus(vendor));
        template.put("hasTemplateJson", hasText(vendor.get("aas_invoice_template_json")));
        template.put("hasSamplePdf", hasText(vendor.get("aas_invoice_template_sample_pdf")));
        template.put("active", !asFlag(vendor.get("disabled")));

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("billsCaptured", orderRows.stream().filter(row -> asDouble(row.get("vendorBillTotal")) > 0).count());
        billing.put("unpaidPurchaseInvoices", unpaidInvoiceCount);
        billing.put("outstandingAmount", outstandingBalance);
        billing.put("ledgerBalance", ledgerEntries.isEmpty() ? 0.0 : asDouble(ledgerEntries.get(ledgerEntries.size() - 1).get("runningBalance")));

        Map<String, Object> exceptions = new LinkedHashMap<>();
        exceptions.put("mismatchCount", mismatchCount);
        exceptions.put("parseFailureCount", parseFailureCount);
        exceptions.put("missingTemplate", !"Ready".equals(resolveTemplateStatus(vendor)));
        exceptions.put("awaitingPdfTooLong", orderRows.stream()
                .filter(row -> "VENDOR_ASSIGNED".equals(asText(row.get("status"))))
                .filter(row -> ageInDays(asText(row.get("orderDate"))) >= 2)
                .count());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("vendorId", asText(vendor.get("name")));
        info.put("vendorName", preferredVendorName(vendor));
        info.put("templateStatus", resolveTemplateStatus(vendor));
        info.put("lastActivity", resolveLastActivity(
                orderRows.stream().map(row -> asText(row.get("lastUpdated"))).toList(),
                purchaseInvoices.stream().map(invoice -> asText(invoice.get("modified"))).toList(),
                payments.stream().map(payment -> asText(payment.get("modified"))).toList()));

        return Map.of(
                "vendor", info,
                "kpis", kpis,
                "template", template,
                "billing", billing,
                "exceptions", exceptions);
    }

    public List<Map<String, Object>> getVendorOrders(
            String vendorId,
            String status,
            String branch,
            String fromDate,
            String toDate) {
        List<Map<String, Object>> orderRows = fetchOrderRows(vendorId);
        Map<String, Map<String, Object>> purchaseOrders = new HashMap<>();
        Map<String, Map<String, Object>> purchaseInvoices = new HashMap<>();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> orderRow : orderRows) {
            Map<String, Object> order = unwrap(erpNextClient.getResource(SALES_ORDER, asText(orderRow.get("name"))));
            String orderStatus = asText(order.get("aas_status"));
            if (hasText(status) && !status.equals(orderStatus)) {
                continue;
            }
            String orderBranch = asText(order.get("customer"));
            if (hasText(branch) && !branch.equals(orderBranch)) {
                continue;
            }
            String orderDate = asText(order.get("transaction_date"));
            if (!withinDateRange(orderDate, fromDate, toDate)) {
                continue;
            }

            String poId = asText(order.get("aas_po"));
            String piId = asText(order.get("aas_pi_vendor"));
            Map<String, Object> po = loadDocIfPresent(PURCHASE_ORDER, poId, purchaseOrders);
            Map<String, Object> pi = loadDocIfPresent(PURCHASE_INVOICE, piId, purchaseInvoices);

            List<Map<String, Object>> items = childItems(order.get("items"));
            long parsedItems = items.stream()
                    .filter(item -> !PLACEHOLDER_ITEM.equals(asText(item.get("item_code"))))
                    .count();
            double itemTotal = round(items.stream()
                    .mapToDouble(item -> asDouble(item.get("amount")))
                    .sum());
            double vendorBillTotal = asDouble(order.get("aas_vendor_bill_total"));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", asText(order.get("name")));
            row.put("branch", orderBranch);
            row.put("orderDate", orderDate);
            row.put("deliveryDate", asText(order.get("delivery_date")));
            row.put("status", orderStatus);
            row.put("pdfUploaded", hasText(order.get("aas_vendor_pdf")));
            row.put("vendorPdf", asText(order.get("aas_vendor_pdf")));
            row.put("parsedItems", parsedItems);
            row.put("vendorBillTotal", round(vendorBillTotal));
            row.put("billRef", asText(order.get("aas_vendor_bill_ref")));
            row.put("billDate", asText(order.get("aas_vendor_bill_date")));
            row.put("poNumber", poId);
            row.put("purchaseInvoice", piId);
            row.put("lastUpdated", asText(order.get("modified")));
            row.put("hasMismatch", vendorBillTotal > 0 && Math.abs(round(vendorBillTotal - itemTotal)) > 0.5);
            row.put("itemsTotal", itemTotal);
            row.put("sourceOrderMargin", asDouble(order.get("aas_margin_percent")));
            row.put("assignmentToPdfHours", diffHours(asText(order.get("creation")), asText(po.get("creation"))));
            row.put("pdfToBillHours", diffHours(asText(po.get("creation")), asText(pi.get("creation"))));
            rows.add(row);
        }

        return rows.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> asText(row.get("orderDate")), Comparator.nullsLast(String::compareTo))
                        .reversed())
                .toList();
    }

    public Map<String, Object> getVendorAnalytics(String vendorId) {
        Map<String, Object> vendor = unwrap(erpNextClient.getResource(SUPPLIER, vendorId));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }

        List<Map<String, Object>> orderRows = getVendorOrders(vendorId, null, null, null, null);
        List<Map<String, Object>> analyticsRows = orderRows.stream()
                .filter(row -> !asFlag(row.get("hasMismatch")))
                .toList();
        List<Map<String, Object>> fullOrders = analyticsRows.stream()
                .map(row -> unwrap(erpNextClient.getResource(SALES_ORDER, asText(row.get("orderId")))))
                .toList();

        List<Map<String, Object>> ordersByStatus = aggregateCount(orderRows, "status", "status");
        List<Map<String, Object>> billedAmountByBranch = aggregateSum(analyticsRows, "branch", "branch", "vendorBillTotal");
        List<Map<String, Object>> topItemsByQty = aggregateItems(fullOrders, true);
        List<Map<String, Object>> topItemsByValue = aggregateItems(fullOrders, false);

        Map<String, Object> turnaround = new LinkedHashMap<>();
        turnaround.put("avgAssignmentToPdfHours", averageNumeric(orderRows, "assignmentToPdfHours"));
        turnaround.put("avgPdfToBillHours", averageNumeric(orderRows, "pdfToBillHours"));
        turnaround.put("parseSuccessRate", calculateParseSuccessRate(orderRows));
        turnaround.put("billCaptureRate", calculateBillCaptureRate(orderRows));

        return Map.of(
                "vendorId", vendorId,
                "ordersByStatus", ordersByStatus,
                "billedAmountByBranch", billedAmountByBranch,
                "topItemsByQty", topItemsByQty,
                "topItemsByValue", topItemsByValue,
                "turnaround", turnaround);
    }

    public Map<String, Object> getVendorLedger(String vendorId) {
        Map<String, Object> vendor = unwrap(erpNextClient.getResource(SUPPLIER, vendorId));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }
        List<Map<String, Object>> entries = buildLedgerEntries(fetchPurchaseInvoices(vendorId), fetchSupplierPayments(vendorId));
        double balance = entries.isEmpty() ? 0.0 : asDouble(entries.get(entries.size() - 1).get("runningBalance"));
        return Map.of(
                "vendorId", vendorId,
                "vendorName", preferredVendorName(vendor),
                "balance", balance,
                "entries", entries);
    }

    private Map<String, Object> buildVendorSummary(
            Map<String, Object> vendor,
            List<Map<String, Object>> orders,
            List<Map<String, Object>> purchaseInvoices,
            List<Map<String, Object>> payments) {
        String vendorId = asText(vendor.get("name"));
        List<Map<String, Object>> vendorOrders = orders.stream()
                .filter(order -> vendorId.equals(asText(order.get("aas_vendor"))))
                .toList();
        List<Map<String, Object>> vendorInvoices = purchaseInvoices.stream()
                .filter(invoice -> vendorId.equals(asText(invoice.get("supplier"))))
                .toList();
        List<Map<String, Object>> vendorPayments = payments.stream()
                .filter(payment -> vendorId.equals(asText(payment.get("party"))))
                .toList();

        List<Map<String, Object>> ledgerEntries = buildLedgerEntries(vendorInvoices, vendorPayments);
        double pendingBillAmount = vendorOrders.stream()
                .filter(order -> "VENDOR_PDF_RECEIVED".equals(asText(order.get("aas_status")))
                        || "VENDOR_BILL_CAPTURED".equals(asText(order.get("aas_status"))))
                .mapToDouble(order -> asDouble(order.get("aas_vendor_bill_total")))
                .sum();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("vendorId", vendorId);
        row.put("vendorName", preferredVendorName(vendor));
        row.put("pendingOrders", vendorOrders.stream().filter(order -> PENDING_STATUSES.contains(asText(order.get("aas_status")))).count());
        row.put("awaitingPdf", vendorOrders.stream().filter(order -> "VENDOR_ASSIGNED".equals(asText(order.get("aas_status")))).count());
        row.put("awaitingBillCapture", vendorOrders.stream().filter(order -> "VENDOR_PDF_RECEIVED".equals(asText(order.get("aas_status")))).count());
        row.put("inProgress", vendorOrders.stream().filter(order -> "VENDOR_BILL_CAPTURED".equals(asText(order.get("aas_status")))).count());
        row.put("pendingBillAmount", round(pendingBillAmount));
        row.put("lastActivity", resolveLastActivity(
                vendorOrders.stream().map(order -> asText(order.get("modified"))).toList(),
                vendorInvoices.stream().map(invoice -> asText(invoice.get("modified"))).toList(),
                vendorPayments.stream().map(payment -> asText(payment.get("modified"))).toList()));
        row.put("templateStatus", resolveTemplateStatus(vendor));
        row.put("ledgerBalance", ledgerEntries.isEmpty() ? 0.0 : asDouble(ledgerEntries.get(ledgerEntries.size() - 1).get("runningBalance")));
        row.put("parseSuccessRate", calculateParseSuccessRateFromSummary(vendorOrders));
        return row;
    }

    private List<Map<String, Object>> buildLedgerEntries(
            List<Map<String, Object>> purchaseInvoices,
            List<Map<String, Object>> payments) {
        List<Map<String, Object>> entries = new ArrayList<>();

        for (Map<String, Object> invoice : purchaseInvoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            double credit = round(asDouble(invoice.get("grand_total")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(invoice.get("posting_date")));
            row.put("voucherType", "Purchase Invoice");
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("bill_no")));
            row.put("debit", 0.0);
            row.put("credit", credit);
            row.put("netChange", credit);
            entries.add(row);
        }

        for (Map<String, Object> payment : payments) {
            if (asInt(payment.get("docstatus")) == 2) {
                continue;
            }
            double amount = round(resolvePaymentAmount(payment));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(payment.get("posting_date")));
            row.put("voucherType", "Payment Entry");
            row.put("voucherNo", asText(payment.get("name")));
            row.put("reference", asText(payment.get("reference_no")));
            row.put("debit", amount);
            row.put("credit", 0.0);
            row.put("netChange", round(-amount));
            entries.add(row);
        }

        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> asText(row.get("voucherType")))
                .thenComparing(row -> asText(row.get("voucherNo"))));

        double runningBalance = 0.0;
        for (Map<String, Object> entry : entries) {
            runningBalance = round(runningBalance + asDouble(entry.get("netChange")));
            entry.put("runningBalance", runningBalance);
        }
        return entries;
    }

    private List<Map<String, Object>> fetchVendors() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"supplier_name\",\"disabled\",\"modified\",\"aas_invoice_template_json\",\"aas_invoice_template_sample_pdf\"]");
        return erpNextClient.listResources(SUPPLIER, params);
    }

    private List<Map<String, Object>> fetchOrderRows(String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"customer\",\"transaction_date\",\"delivery_date\",\"aas_vendor\",\"aas_status\","
                        + "\"aas_vendor_pdf\",\"aas_vendor_bill_total\",\"aas_vendor_bill_ref\",\"aas_vendor_bill_date\","
                        + "\"aas_po\",\"aas_pi_vendor\",\"modified\",\"creation\"]");
        params.put("order_by", "modified desc");
        if (hasText(vendorId)) {
            params.put("filters", "[[\"Sales Order\",\"aas_vendor\",\"=\",\"" + escapeJson(vendorId) + "\"]]");
        }
        return erpNextClient.listResources(SALES_ORDER, params);
    }

    private List<Map<String, Object>> fetchPurchaseInvoices(String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"supplier\",\"posting_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"bill_no\","
                        + "\"aas_source_sales_order\",\"modified\",\"creation\",\"docstatus\"]");
        params.put("order_by", "posting_date asc");
        if (hasText(vendorId)) {
            params.put("filters", "[[\"Purchase Invoice\",\"supplier\",\"=\",\"" + escapeJson(vendorId) + "\"]]");
        }
        return erpNextClient.listResources(PURCHASE_INVOICE, params);
    }

    private List<Map<String, Object>> fetchSupplierPayments(String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\","
                        + "\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Supplier"));
        if (hasText(vendorId)) {
            filters.add(List.of("party", "=", vendorId));
        }
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        return erpNextClient.listResources(PAYMENT_ENTRY, params);
    }

    private Map<String, Object> loadDocIfPresent(
            String doctype,
            String docId,
            Map<String, Map<String, Object>> cache) {
        if (!hasText(docId)) {
            return Map.of();
        }
        return cache.computeIfAbsent(docId, key -> unwrap(erpNextClient.getResource(doctype, key)));
    }

    private List<Map<String, Object>> aggregateCount(List<Map<String, Object>> rows, String sourceKey, String targetKey) {
        Map<String, Long> counts = rows.stream()
                .collect(Collectors.groupingBy(row -> asText(row.get(sourceKey)), Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> Map.<String, Object>of(targetKey, entry.getKey(), "count", entry.getValue()))
                .sorted(Comparator.comparing(row -> -asDouble(row.get("count"))))
                .toList();
    }

    private List<Map<String, Object>> aggregateSum(
            List<Map<String, Object>> rows,
            String sourceKey,
            String targetKey,
            String valueKey) {
        Map<String, Double> totals = new HashMap<>();
        for (Map<String, Object> row : rows) {
            totals.merge(asText(row.get(sourceKey)), asDouble(row.get(valueKey)), Double::sum);
        }
        return totals.entrySet().stream()
                .map(entry -> Map.<String, Object>of(targetKey, entry.getKey(), "total", round(entry.getValue())))
                .sorted(Comparator.comparing(row -> -asDouble(row.get("total"))))
                .toList();
    }

    private List<Map<String, Object>> aggregateItems(List<Map<String, Object>> fullOrders, boolean byQty) {
        Map<String, Double> totals = new HashMap<>();
        for (Map<String, Object> order : fullOrders) {
            for (Map<String, Object> item : childItems(order.get("items"))) {
                String itemCode = asText(item.get("item_code"));
                if (!isAnalyticsItem(item)) {
                    continue;
                }
                String label = hasText(item.get("item_name")) ? asText(item.get("item_name")) : itemCode;
                double value = byQty ? asDouble(item.get("qty")) : asDouble(item.get("amount"));
                totals.merge(label, value, Double::sum);
            }
        }
        String metricKey = byQty ? "qty" : "value";
        return totals.entrySet().stream()
                .map(entry -> Map.<String, Object>of("item", entry.getKey(), metricKey, round(entry.getValue())))
                .sorted(Comparator.comparing(row -> -asDouble(row.get(metricKey))))
                .limit(10)
                .toList();
    }

    private boolean isAnalyticsItem(Map<String, Object> item) {
        String itemCode = asText(item.get("item_code"));
        String itemName = asText(item.get("item_name"));
        String normalized = (itemCode + " " + itemName).toUpperCase(Locale.ROOT);
        double qty = asDouble(item.get("qty"));
        double amount = asDouble(item.get("amount"));
        double rate = asDouble(item.get("rate"));

        if (!hasText(itemCode) || PLACEHOLDER_ITEM.equals(itemCode) || qty <= 0 || amount <= 0 || rate <= 0) {
            return false;
        }
        if (normalized.startsWith("FSSAI")
                || normalized.contains("FSSAI NO")
                || normalized.startsWith("HSN ")
                || normalized.startsWith("HSN-")
                || normalized.contains("(LINE ")
                || normalized.contains(" LINE ")) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childItems(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                items.add((Map<String, Object>) map);
            }
        }
        return items;
    }

    private boolean withinDateRange(String value, String fromDate, String toDate) {
        if (!hasText(value)) {
            return !hasText(fromDate) && !hasText(toDate);
        }
        if (hasText(fromDate) && value.compareTo(fromDate) < 0) {
            return false;
        }
        if (hasText(toDate) && value.compareTo(toDate) > 0) {
            return false;
        }
        return true;
    }

    private String resolveTemplateStatus(Map<String, Object> vendor) {
        boolean hasTemplate = hasText(vendor.get("aas_invoice_template_json"));
        boolean hasSample = hasText(vendor.get("aas_invoice_template_sample_pdf"));
        boolean active = !asFlag(vendor.get("disabled"));
        if (hasTemplate && hasSample && active) {
            return "Ready";
        }
        if (hasTemplate && hasSample) {
            return "Inactive";
        }
        if (hasTemplate) {
            return "Missing sample";
        }
        return "Missing";
    }

    private String preferredVendorName(Map<String, Object> vendor) {
        String name = asText(vendor.get("supplier_name"));
        return name.isBlank() ? asText(vendor.get("name")) : name;
    }

    private String resolveLastActivity(List<String>... sources) {
        String latest = "";
        for (List<String> source : sources) {
            for (String value : source) {
                if (hasText(value) && value.compareTo(latest) > 0) {
                    latest = value;
                }
            }
        }
        return latest;
    }

    private double calculateParseSuccessRate(List<Map<String, Object>> orderRows) {
        long uploaded = orderRows.stream().filter(row -> asFlag(row.get("pdfUploaded"))).count();
        if (uploaded == 0) {
            return 0.0;
        }
        long success = orderRows.stream()
                .filter(row -> asFlag(row.get("pdfUploaded")))
                .filter(row -> asDouble(row.get("parsedItems")) > 0)
                .count();
        return round((success * 100.0) / uploaded);
    }

    private double calculateBillCaptureRate(List<Map<String, Object>> orderRows) {
        long eligible = orderRows.stream()
                .filter(row -> asFlag(row.get("pdfUploaded")))
                .count();
        if (eligible == 0) {
            return 0.0;
        }
        long captured = orderRows.stream()
                .filter(row -> asDouble(row.get("vendorBillTotal")) > 0)
                .count();
        return round((captured * 100.0) / eligible);
    }

    private double calculateParseSuccessRateFromSummary(List<Map<String, Object>> orders) {
        long uploaded = orders.stream().filter(order -> hasText(order.get("aas_vendor_pdf"))).count();
        if (uploaded == 0) {
            return 0.0;
        }
        long success = orders.stream()
                .filter(order -> hasText(order.get("aas_vendor_pdf")))
                .filter(order -> !"VENDOR_ASSIGNED".equals(asText(order.get("aas_status"))))
                .count();
        return round((success * 100.0) / uploaded);
    }

    private double averageNumeric(List<Map<String, Object>> rows, String key) {
        List<Double> values = rows.stream()
                .map(row -> asDouble(row.get(key)))
                .filter(value -> value > 0)
                .toList();
        if (values.isEmpty()) {
            return 0.0;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
    }

    private long ageInDays(String date) {
        if (!hasText(date)) {
            return 0;
        }
        try {
            return Duration.between(LocalDate.parse(date).atStartOfDay(), LocalDate.now().atStartOfDay()).toDays();
        } catch (DateTimeParseException ex) {
            return 0;
        }
    }

    private double diffHours(String from, String to) {
        LocalDateTime start = parseDateTime(from);
        LocalDateTime end = parseDateTime(to);
        if (start == null || end == null || end.isBefore(start)) {
            return 0.0;
        }
        return round(Duration.between(start, end).toMinutes() / 60.0);
    }

    private LocalDateTime parseDateTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, ERP_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private double resolvePaymentAmount(Map<String, Object> payment) {
        double paidAmount = asDouble(payment.get("paid_amount"));
        double receivedAmount = asDouble(payment.get("received_amount"));
        return paidAmount > 0 ? paidAmount : receivedAmount;
    }

    private String toJson(List<List<String>> filters) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < filters.size(); i++) {
            List<String> entry = filters.get(i);
            builder.append("[");
            for (int j = 0; j < entry.size(); j++) {
                builder.append("\"").append(escapeJson(entry.get(j))).append("\"");
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

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().trim().isEmpty();
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
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return "1".equals(text) || "true".equals(text) || "yes".equals(text);
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

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 0;
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
}
