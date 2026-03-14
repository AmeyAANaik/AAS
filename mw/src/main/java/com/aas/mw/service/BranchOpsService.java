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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BranchOpsService {

    private static final String CUSTOMER = "Customer";
    private static final String SALES_ORDER = "Sales Order";
    private static final String SALES_INVOICE = "Sales Invoice";
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String PLACEHOLDER_ITEM = "AAS-SYSTEM-BRANCH-IMAGE";
    private static final Set<String> OPEN_STATUSES = Set.of("DRAFT", "VENDOR_ASSIGNED", "VENDOR_PDF_RECEIVED", "VENDOR_BILL_CAPTURED", "SELL_ORDER_CREATED");
    private static final DateTimeFormatter ERP_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]", Locale.ROOT);

    private final ErpNextClient erpNextClient;

    public BranchOpsService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> getSummary() {
        List<Map<String, Object>> branches = fetchBranches();
        List<Map<String, Object>> orders = fetchOrderRows(null);
        List<Map<String, Object>> invoices = fetchSalesInvoices(null);
        List<Map<String, Object>> payments = fetchCustomerPayments(null);

        List<Map<String, Object>> rows = branches.stream()
                .map(branch -> buildBranchSummary(branch, orders, invoices, payments))
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> row) -> asDouble(row.get("pendingOrders"))).reversed()
                        .thenComparing(row -> asText(row.get("branchName"))))
                .toList();

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalBranches", rows.size());
        totals.put("branchesWithPendingOrders", rows.stream().filter(row -> asDouble(row.get("pendingOrders")) > 0).count());
        totals.put("totalPendingOrders", round(rows.stream().mapToDouble(row -> asDouble(row.get("pendingOrders"))).sum()));
        totals.put("awaitingVendorAssignment", round(rows.stream().mapToDouble(row -> asDouble(row.get("awaitingVendorAssignment"))).sum()));
        totals.put("awaitingVendorResponse", round(rows.stream().mapToDouble(row -> asDouble(row.get("awaitingVendorResponse"))).sum()));
        totals.put("openReceivableAmount", round(rows.stream().mapToDouble(row -> asDouble(row.get("openReceivableAmount"))).sum()));

        return Map.of("totals", totals, "branches", rows);
    }

    public Map<String, Object> getBranchDetail(String branchId) {
        Map<String, Object> branch = unwrap(erpNextClient.getResource(CUSTOMER, branchId));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        List<Map<String, Object>> orderRows = getBranchOrders(branchId, null, null, null, null);
        List<Map<String, Object>> invoices = fetchSalesInvoices(branchId);
        List<Map<String, Object>> payments = fetchCustomerPayments(branchId);

        Map<String, Object> branchInfo = new LinkedHashMap<>();
        branchInfo.put("branchId", asText(branch.get("name")));
        branchInfo.put("branchName", preferredBranchName(branch));
        branchInfo.put("location", asText(branch.get("aas_branch_location")));
        branchInfo.put("creditDays", asInt(branch.get("aas_credit_days")));
        branchInfo.put("lastActivity", resolveLastActivity(
                orderRows.stream().map(row -> asText(row.get("lastUpdated"))).toList(),
                invoices.stream().map(invoice -> asText(invoice.get("modified"))).toList(),
                payments.stream().map(payment -> asText(payment.get("modified"))).toList()));

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("pendingOrders", orderRows.stream().filter(row -> OPEN_STATUSES.contains(asText(row.get("status")))).count());
        kpis.put("awaitingVendorAssignment", orderRows.stream().filter(row -> "DRAFT".equals(asText(row.get("status")))).count());
        kpis.put("awaitingVendorResponse", orderRows.stream().filter(row -> "VENDOR_ASSIGNED".equals(asText(row.get("status"))) || "VENDOR_PDF_RECEIVED".equals(asText(row.get("status")))).count());
        kpis.put("openReceivableAmount", round(invoices.stream().mapToDouble(invoice -> asDouble(invoice.get("outstanding_amount"))).sum()));
        kpis.put("invoicedAmount", round(invoices.stream().mapToDouble(invoice -> asDouble(invoice.get("grand_total"))).sum()));
        kpis.put("paymentCollectionRate", calculatePaymentCollectionRate(invoices, payments));

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("invoicesRaised", invoices.size());
        billing.put("openInvoices", invoices.stream().filter(invoice -> asDouble(invoice.get("outstanding_amount")) > 0).count());
        billing.put("paymentsReceived", payments.size());
        billing.put("ledgerBalance", getLedgerBalance(buildLedgerEntries(invoices, payments)));

        Map<String, Object> exceptions = new LinkedHashMap<>();
        exceptions.put("unassignedOrders", orderRows.stream().filter(row -> "DRAFT".equals(asText(row.get("status")))).count());
        exceptions.put("awaitingVendorPdf", orderRows.stream().filter(row -> "VENDOR_ASSIGNED".equals(asText(row.get("status")))).count());
        exceptions.put("awaitingBillCapture", orderRows.stream().filter(row -> "VENDOR_PDF_RECEIVED".equals(asText(row.get("status")))).count());
        exceptions.put("overdueInvoices", invoices.stream()
                .filter(invoice -> asDouble(invoice.get("outstanding_amount")) > 0)
                .filter(invoice -> "Overdue".equalsIgnoreCase(asText(invoice.get("status"))))
                .count());

        return Map.of("branch", branchInfo, "kpis", kpis, "billing", billing, "exceptions", exceptions);
    }

    public List<Map<String, Object>> getBranchOrders(
            String branchId,
            String status,
            String vendor,
            String fromDate,
            String toDate) {
        List<Map<String, Object>> orderRows = fetchOrderRows(branchId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : orderRows) {
            String orderStatus = asText(row.get("aas_status"));
            if (hasText(status) && !status.equals(orderStatus)) {
                continue;
            }
            String orderVendor = asText(row.get("aas_vendor"));
            if (hasText(vendor) && !vendor.equals(orderVendor)) {
                continue;
            }
            String orderDate = asText(row.get("transaction_date"));
            if (!withinDateRange(orderDate, fromDate, toDate)) {
                continue;
            }
            Map<String, Object> order = unwrap(erpNextClient.getResource(SALES_ORDER, asText(row.get("name"))));
            List<Map<String, Object>> items = childItems(order.get("items"));
            long itemCount = items.stream().filter(item -> !PLACEHOLDER_ITEM.equals(asText(item.get("item_code")))).count();
            double itemTotal = round(items.stream()
                    .filter(this::isAnalyticsItem)
                    .mapToDouble(item -> asDouble(item.get("amount")))
                    .sum());
            double vendorBillTotal = asDouble(order.get("aas_vendor_bill_total"));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("orderId", asText(order.get("name")));
            view.put("branch", asText(order.get("customer")));
            view.put("vendor", orderVendor);
            view.put("orderDate", orderDate);
            view.put("deliveryDate", asText(order.get("delivery_date")));
            view.put("status", orderStatus);
            view.put("pdfUploaded", hasText(order.get("aas_vendor_pdf")));
            view.put("vendorBillTotal", round(vendorBillTotal));
            view.put("sellOrderTotal", round(asDouble(order.get("aas_sell_order_total"))));
            view.put("invoiceId", asText(order.get("aas_si_branch")));
            view.put("parsedItems", itemCount);
            view.put("poNumber", asText(order.get("aas_po")));
            view.put("lastUpdated", asText(order.get("modified")));
            view.put("itemsTotal", itemTotal);
            view.put("hasMismatch", vendorBillTotal > 0 && Math.abs(round(vendorBillTotal - itemTotal)) > 0.5);
            rows.add(view);
        }
        return rows.stream()
                .sorted(Comparator.comparing((Map<String, Object> row) -> asText(row.get("orderDate"))).reversed())
                .toList();
    }

    public Map<String, Object> getBranchAnalytics(String branchId) {
        Map<String, Object> branch = unwrap(erpNextClient.getResource(CUSTOMER, branchId));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        List<Map<String, Object>> orderRows = getBranchOrders(branchId, null, null, null, null);
        List<Map<String, Object>> analyticsRows = orderRows.stream()
                .filter(row -> !asFlag(row.get("hasMismatch")))
                .toList();
        List<Map<String, Object>> fullOrders = analyticsRows.stream()
                .map(row -> unwrap(erpNextClient.getResource(SALES_ORDER, asText(row.get("orderId")))))
                .toList();
        List<Map<String, Object>> invoices = fetchSalesInvoices(branchId);
        List<Map<String, Object>> payments = fetchCustomerPayments(branchId);

        Map<String, Object> turnaround = new LinkedHashMap<>();
        turnaround.put("avgOrderToInvoiceHours", averageHoursBetweenOrderAndInvoice(fullOrders));
        turnaround.put("paymentCollectionRate", calculatePaymentCollectionRate(invoices, payments));
        turnaround.put("avgInvoiceToPaymentHours", averageHoursBetweenInvoiceAndPayment(invoices, payments));

        return Map.of(
                "branchId", branchId,
                "ordersByStatus", aggregateCount(orderRows, "status", "status"),
                "billedAmountByVendor", aggregateSum(analyticsRows, "vendor", "vendor", "vendorBillTotal"),
                "topItemsByQty", aggregateItems(fullOrders, true),
                "topItemsByValue", aggregateItems(fullOrders, false),
                "turnaround", turnaround);
    }

    public Map<String, Object> getBranchLedger(String branchId) {
        Map<String, Object> branch = unwrap(erpNextClient.getResource(CUSTOMER, branchId));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        List<Map<String, Object>> entries = buildLedgerEntries(fetchSalesInvoices(branchId), fetchCustomerPayments(branchId));
        return Map.of(
                "branchId", branchId,
                "branchName", preferredBranchName(branch),
                "balance", getLedgerBalance(entries),
                "entries", entries);
    }

    private Map<String, Object> buildBranchSummary(
            Map<String, Object> branch,
            List<Map<String, Object>> orders,
            List<Map<String, Object>> invoices,
            List<Map<String, Object>> payments) {
        String branchId = asText(branch.get("name"));
        List<Map<String, Object>> branchOrders = orders.stream().filter(order -> branchId.equals(asText(order.get("customer")))).toList();
        List<Map<String, Object>> branchInvoices = invoices.stream().filter(invoice -> branchId.equals(asText(invoice.get("customer")))).toList();
        List<Map<String, Object>> branchPayments = payments.stream().filter(payment -> branchId.equals(asText(payment.get("party")))).toList();
        List<Map<String, Object>> ledger = buildLedgerEntries(branchInvoices, branchPayments);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("branchId", branchId);
        row.put("branchName", preferredBranchName(branch));
        row.put("pendingOrders", branchOrders.stream().filter(order -> OPEN_STATUSES.contains(asText(order.get("aas_status")))).count());
        row.put("awaitingVendorAssignment", branchOrders.stream().filter(order -> "DRAFT".equals(asText(order.get("aas_status")))).count());
        row.put("awaitingVendorResponse", branchOrders.stream().filter(order -> "VENDOR_ASSIGNED".equals(asText(order.get("aas_status"))) || "VENDOR_PDF_RECEIVED".equals(asText(order.get("aas_status")))).count());
        row.put("inProgress", branchOrders.stream().filter(order -> "VENDOR_BILL_CAPTURED".equals(asText(order.get("aas_status"))) || "SELL_ORDER_CREATED".equals(asText(order.get("aas_status")))).count());
        row.put("openReceivableAmount", round(branchInvoices.stream().mapToDouble(invoice -> asDouble(invoice.get("outstanding_amount"))).sum()));
        row.put("lastActivity", resolveLastActivity(
                branchOrders.stream().map(order -> asText(order.get("modified"))).toList(),
                branchInvoices.stream().map(invoice -> asText(invoice.get("modified"))).toList(),
                branchPayments.stream().map(payment -> asText(payment.get("modified"))).toList()));
        row.put("location", asText(branch.get("aas_branch_location")));
        row.put("ledgerBalance", getLedgerBalance(ledger));
        row.put("paymentCollectionRate", calculatePaymentCollectionRate(branchInvoices, branchPayments));
        return row;
    }

    private List<Map<String, Object>> buildLedgerEntries(List<Map<String, Object>> invoices, List<Map<String, Object>> payments) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            double debit = round(asDouble(invoice.get("grand_total")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(invoice.get("posting_date")));
            row.put("voucherType", "Sales Invoice");
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("customer")));
            row.put("debit", debit);
            row.put("credit", 0.0);
            row.put("netChange", debit);
            entries.add(row);
        }
        for (Map<String, Object> payment : payments) {
            if (asInt(payment.get("docstatus")) == 2) {
                continue;
            }
            double credit = round(resolvePaymentAmount(payment));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(payment.get("posting_date")));
            row.put("voucherType", "Payment Entry");
            row.put("voucherNo", asText(payment.get("name")));
            row.put("reference", asText(payment.get("reference_no")));
            row.put("debit", 0.0);
            row.put("credit", credit);
            row.put("netChange", round(-credit));
            entries.add(row);
        }
        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> asText(row.get("voucherType")))
                .thenComparing(row -> asText(row.get("voucherNo"))));
        double running = 0.0;
        for (Map<String, Object> entry : entries) {
            running = round(running + asDouble(entry.get("netChange")));
            entry.put("runningBalance", running);
        }
        return entries;
    }

    private List<Map<String, Object>> fetchBranches() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer_name\",\"aas_branch_location\",\"aas_credit_days\",\"modified\"]");
        return erpNextClient.listResources(CUSTOMER, params);
    }

    private List<Map<String, Object>> fetchOrderRows(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"transaction_date\",\"delivery_date\",\"aas_vendor\",\"aas_status\",\"aas_vendor_pdf\",\"aas_vendor_bill_total\",\"aas_sell_order_total\",\"aas_po\",\"aas_si_branch\",\"modified\"]");
        params.put("order_by", "modified desc");
        if (hasText(branchId)) {
            params.put("filters", "[[\"Sales Order\",\"customer\",\"=\",\"" + escapeJson(branchId) + "\"]]");
        }
        return erpNextClient.listResources(SALES_ORDER, params);
    }

    private List<Map<String, Object>> fetchSalesInvoices(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"posting_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"modified\",\"creation\",\"docstatus\"]");
        params.put("order_by", "posting_date asc");
        if (hasText(branchId)) {
            params.put("filters", "[[\"Sales Invoice\",\"customer\",\"=\",\"" + escapeJson(branchId) + "\"]]");
        }
        return erpNextClient.listResources(SALES_INVOICE, params);
    }

    private List<Map<String, Object>> fetchCustomerPayments(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\",\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Customer"));
        if (hasText(branchId)) {
            filters.add(List.of("party", "=", branchId));
        }
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        return erpNextClient.listResources(PAYMENT_ENTRY, params);
    }

    private List<Map<String, Object>> aggregateCount(List<Map<String, Object>> rows, String sourceKey, String targetKey) {
        Map<String, Long> counts = rows.stream().collect(Collectors.groupingBy(row -> asText(row.get(sourceKey)), Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> Map.<String, Object>of(targetKey, entry.getKey(), "count", entry.getValue()))
                .sorted(Comparator.comparing(row -> -asDouble(row.get("count"))))
                .toList();
    }

    private List<Map<String, Object>> aggregateSum(List<Map<String, Object>> rows, String sourceKey, String targetKey, String valueKey) {
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
                String code = asText(item.get("item_code"));
                if (!isAnalyticsItem(item)) {
                    continue;
                }
                String label = hasText(item.get("item_name")) ? asText(item.get("item_name")) : code;
                double value = byQty ? asDouble(item.get("qty")) : asDouble(item.get("amount"));
                totals.merge(label, value, Double::sum);
            }
        }
        String metric = byQty ? "qty" : "value";
        return totals.entrySet().stream()
                .map(entry -> Map.<String, Object>of("item", entry.getKey(), metric, round(entry.getValue())))
                .sorted(Comparator.comparing(row -> -asDouble(row.get(metric))))
                .limit(10)
                .toList();
    }

    private boolean isAnalyticsItem(Map<String, Object> item) {
        String code = asText(item.get("item_code"));
        String name = asText(item.get("item_name"));
        String normalized = (code + " " + name).toUpperCase(Locale.ROOT);
        double qty = asDouble(item.get("qty"));
        double amount = asDouble(item.get("amount"));
        double rate = asDouble(item.get("rate"));

        if (!hasText(code) || PLACEHOLDER_ITEM.equals(code) || qty <= 0 || amount <= 0 || rate <= 0) {
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

    private double averageHoursBetweenOrderAndInvoice(List<Map<String, Object>> fullOrders) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> order : fullOrders) {
            String created = asText(order.get("creation"));
            String invoiceId = asText(order.get("aas_si_branch"));
            if (!hasText(invoiceId)) {
                continue;
            }
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(SALES_INVOICE, invoiceId));
            double hours = diffHours(created, asText(invoice.get("creation")));
            if (hours > 0) {
                values.add(hours);
            }
        }
        return average(values);
    }

    private double averageHoursBetweenInvoiceAndPayment(List<Map<String, Object>> invoices, List<Map<String, Object>> payments) {
        if (invoices.isEmpty() || payments.isEmpty()) {
            return 0.0;
        }
        LocalDateTime firstPayment = payments.stream()
                .map(payment -> parseDateTime(asText(payment.get("creation"))))
                .filter(dt -> dt != null)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        if (firstPayment == null) {
            return 0.0;
        }
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            LocalDateTime invoiceTime = parseDateTime(asText(invoice.get("creation")));
            if (invoiceTime == null || firstPayment.isBefore(invoiceTime)) {
                continue;
            }
            values.add(round(Duration.between(invoiceTime, firstPayment).toMinutes() / 60.0));
        }
        return average(values);
    }

    private double calculatePaymentCollectionRate(List<Map<String, Object>> invoices, List<Map<String, Object>> payments) {
        double invoiced = invoices.stream().mapToDouble(invoice -> asDouble(invoice.get("grand_total"))).sum();
        if (invoiced <= 0) {
            return 0.0;
        }
        double collected = payments.stream().mapToDouble(this::resolvePaymentAmount).sum();
        return round((collected * 100.0) / invoiced);
    }

    private double resolvePaymentAmount(Map<String, Object> payment) {
        double received = asDouble(payment.get("received_amount"));
        double paid = asDouble(payment.get("paid_amount"));
        return received > 0 ? received : paid;
    }

    private double getLedgerBalance(List<Map<String, Object>> entries) {
        return entries.isEmpty() ? 0.0 : asDouble(entries.get(entries.size() - 1).get("runningBalance"));
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
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

    private String preferredBranchName(Map<String, Object> branch) {
        String name = asText(branch.get("customer_name"));
        return name.isBlank() ? asText(branch.get("name")) : name;
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
