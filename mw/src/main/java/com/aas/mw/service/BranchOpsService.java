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
    private static final String ALL_ITEM_GROUPS = "All Item Groups";

    private static final int ERP_PAGE_SIZE = 500;
    private static final String CUSTOMER = "Customer";
    private static final String SALES_ORDER = "Sales Order";
    private static final String SALES_INVOICE = "Sales Invoice";
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String PLACEHOLDER_ITEM = "AAS-SYSTEM-BRANCH-IMAGE";
    private static final String INVOICE_VERSION_OLD = "OLD";
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
        List<Map<String, Object>> payments = fetchCustomerPayments(null, invoices);

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
        List<Map<String, Object>> payments = fetchCustomerPayments(branchId, invoices);

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
        List<Map<String, Object>> payments = fetchCustomerPayments(branchId, invoices);

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
        return getBranchLedger(branchId, null, null);
    }

    public Map<String, Object> getBranchLedger(String branchId, String fromDate, String toDate) {
        Map<String, Object> branch = unwrap(erpNextClient.getResource(CUSTOMER, branchId));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        List<Map<String, Object>> invoices = fetchSalesInvoices(branchId);
        List<Map<String, Object>> payments = fetchCustomerPayments(branchId, invoices);
        List<Map<String, Object>> fullEntries = buildLedgerEntries(invoices, payments);
        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        LedgerWindow window = sliceLedger(fullEntries, range);
        return Map.of(
                "branchId", branchId,
                "branchName", preferredBranchName(branch),
                "openingBalance", window.openingBalance,
                "closingBalance", window.closingBalance,
                "balance", window.closingBalance,
                "categorySummary", buildCategorySummaryFromInvoices(filterByPostingDate(invoices, range)),
                "entries", window.entries);
    }

    public Map<String, Object> getBranchLedgerByCategory(String branchId, String categoryId) {
        return getBranchLedgerByCategory(branchId, categoryId, null, null);
    }

    public Map<String, Object> getBranchLedgerByCategory(String branchId, String categoryId, String fromDate, String toDate) {
        String normalizedCategory = categoryId == null ? "" : categoryId.trim();
        if (normalizedCategory.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required.");
        }
        Map<String, Object> branch = unwrap(erpNextClient.getResource(CUSTOMER, branchId));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }

        List<Map<String, Object>> invoices = fetchSalesInvoices(branchId);
        List<Map<String, Object>> invoiceEntries = buildCategoryLedgerEntriesFromInvoices(invoices, normalizedCategory);
        List<Map<String, Object>> paymentEntries = buildCategoryLedgerEntriesFromPayments(
                fetchCustomerPaymentsByCategory(branchId, normalizedCategory),
                false);
        List<Map<String, Object>> fullEntries = mergeAndFinalizeLedger(invoiceEntries, paymentEntries, true);
        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        LedgerWindow window = sliceLedger(fullEntries, range);

        return Map.of(
                "branchId", branchId,
                "branchName", preferredBranchName(branch),
                "categoryId", normalizedCategory,
                "categoryLabel", normalizedCategory,
                "openingBalance", window.openingBalance,
                "closingBalance", window.closingBalance,
                "balance", window.closingBalance,
                "entries", window.entries);
    }

    private List<Map<String, Object>> filterByPostingDate(List<Map<String, Object>> invoices, LedgerRange range) {
        if (invoices == null || invoices.isEmpty() || range == null || !range.hasBounds()) {
            return invoices == null ? List.of() : invoices;
        }
        return invoices.stream()
                .filter(invoice -> withinDateRange(asText(invoice.get("posting_date")), range.fromInclusive, range.toInclusive))
                .toList();
    }

    private LedgerWindow sliceLedger(List<Map<String, Object>> fullEntries, LedgerRange range) {
        if (fullEntries == null || fullEntries.isEmpty() || range == null || !range.hasBounds()) {
            double closing = fullEntries == null || fullEntries.isEmpty()
                    ? 0.0
                    : asDouble(fullEntries.get(fullEntries.size() - 1).get("runningBalance"));
            return new LedgerWindow(0.0, closing, fullEntries == null ? List.of() : fullEntries);
        }
        double opening = 0.0;
        List<Map<String, Object>> windowEntries = new ArrayList<>();
        for (Map<String, Object> entry : fullEntries) {
            String date = asText(entry.get("date"));
            if (!hasText(date)) {
                continue;
            }
            if (date.compareTo(range.fromInclusive) < 0) {
                opening = round(opening + asDouble(entry.get("netChange")));
                continue;
            }
            if (date.compareTo(range.toInclusive) > 0) {
                continue;
            }
            windowEntries.add(entry);
        }
        double running = opening;
        List<Map<String, Object>> rebased = new ArrayList<>();
        for (Map<String, Object> entry : windowEntries) {
            running = round(running + asDouble(entry.get("netChange")));
            Map<String, Object> copy = new LinkedHashMap<>(entry);
            copy.put("runningBalance", running);
            rebased.add(copy);
        }
        return new LedgerWindow(opening, running, rebased);
    }

    private record LedgerWindow(double openingBalance, double closingBalance, List<Map<String, Object>> entries) {}

    private record LedgerRange(String fromInclusive, String toInclusive) {
        static LedgerRange parse(String fromDate, String toDate) {
            String from = normalizeIsoDate(fromDate);
            String to = normalizeIsoDate(toDate);
            if (from.isBlank() && to.isBlank()) {
                return new LedgerRange("", "");
            }
            if (from.isBlank() && !to.isBlank()) {
                return new LedgerRange("0000-01-01", to);
            }
            if (!from.isBlank() && to.isBlank()) {
                return new LedgerRange(from, "9999-12-31");
            }
            if (from.compareTo(to) > 0) {
                return new LedgerRange(to, from);
            }
            return new LedgerRange(from, to);
        }

        boolean hasBounds() {
            return !fromInclusive.isBlank() || !toInclusive.isBlank();
        }

        private static String normalizeIsoDate(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (value.isBlank()) {
                return "";
            }
            try {
                return LocalDate.parse(value).toString();
            } catch (DateTimeParseException ignored) {
                return "";
            }
        }
    }

    public List<Map<String, Object>> getAllBranchCategorySummaries() {
        return getAllBranchCategorySummaries(null, null);
    }

    public List<Map<String, Object>> getAllBranchCategorySummaries(String fromDate, String toDate) {
        List<Map<String, Object>> branches = fetchBranches();
        Map<String, String> branchNames = branches.stream()
                .collect(Collectors.toMap(
                        branch -> asText(branch.get("name")),
                        this::preferredBranchName,
                        (left, right) -> left,
                        LinkedHashMap::new));

        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        List<Map<String, Object>> invoices = filterByPostingDate(fetchSalesInvoices(null), range);
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Map<String, Map<String, Double>> totals = new LinkedHashMap<>();

        for (Map<String, Object> invoiceRow : invoices) {
            String invoiceId = asText(invoiceRow.get("name"));
            String customerId = asText(invoiceRow.get("customer"));
            if (!hasText(invoiceId) || !hasText(customerId) || asInt(invoiceRow.get("docstatus")) == 2) {
                continue;
            }
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(SALES_INVOICE, invoiceId));
            if (invoice.isEmpty()) {
                continue;
            }
            double dueBase = asDouble(invoice.get("outstanding_amount"));
            if (dueBase <= 0) {
                dueBase = asDouble(invoice.get("grand_total"));
            }
            if (dueBase <= 0) {
                continue;
            }
            Map<String, Double> shares = allocateInvoiceShares(invoice, dueBase, resolver);
            if (shares.isEmpty()) {
                shares = Map.of("Uncategorized", dueBase);
            }
            Map<String, Double> branchTotals = totals.computeIfAbsent(customerId, key -> new LinkedHashMap<>());
            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                branchTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> branchEntry : totals.entrySet()) {
            String branchId = branchEntry.getKey();
            String branchName = branchNames.getOrDefault(branchId, branchId);
            for (Map.Entry<String, Double> categoryEntry : branchEntry.getValue().entrySet()) {
                rows.add(Map.of(
                        "branchId", branchId,
                        "branchName", branchName,
                        "category", categoryEntry.getKey(),
                        "amount", round(categoryEntry.getValue())));
            }
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> asText(row.get("branchName")))
                        .thenComparing(row -> asText(row.get("category"))))
                .toList();
    }

    private List<Map<String, Object>> buildCategorySummaryFromInvoices(List<Map<String, Object>> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Map<String, Object> invoiceRow : invoices) {
            String invoiceId = asText(invoiceRow.get("name"));
            if (!hasText(invoiceId) || asInt(invoiceRow.get("docstatus")) == 2) {
                continue;
            }
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(SALES_INVOICE, invoiceId));
            if (invoice.isEmpty()) {
                continue;
            }
            double dueBase = asDouble(invoice.get("outstanding_amount"));
            if (dueBase <= 0) {
                dueBase = asDouble(invoice.get("grand_total"));
            }
            if (dueBase <= 0) {
                continue;
            }
            List<Map<String, Object>> items = childItems(invoice.get("items"));
            if (items.isEmpty()) {
                totals.merge("Uncategorized", dueBase, Double::sum);
                continue;
            }
            List<LineWeight> weights = new ArrayList<>();
            double totalWeight = 0.0;
            for (Map<String, Object> item : items) {
                double amount = asDouble(firstNonNull(item.get("base_amount"), firstNonNull(item.get("amount"), item.get("net_amount"))));
                if (amount <= 0) {
                    continue;
                }
                String group = asText(firstNonNull(item.get("item_group"), item.get("item_group_name")));
                group = normalizeItemGroup(group);
                if (!hasText(group)) {
                    group = resolver.resolve(asText(item.get("item_code")));
                }
                group = normalizeItemGroup(group);
                if (!hasText(group)) {
                    group = "Uncategorized";
                }
                weights.add(new LineWeight(group, amount));
                totalWeight += amount;
            }
            if (weights.isEmpty() || totalWeight <= 0) {
                totals.merge("Uncategorized", dueBase, Double::sum);
                continue;
            }
            for (LineWeight weight : weights) {
                double share = dueBase * (weight.amount() / totalWeight);
                totals.merge(weight.group(), share, Double::sum);
            }
        }
        return totals.entrySet().stream()
                .filter(entry -> hasText(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> Map.<String, Object>of("category", entry.getKey(), "amount", round(entry.getValue())))
                .sorted((left, right) -> Double.compare(asDouble(right.get("amount")), asDouble(left.get("amount"))))
                .toList();
    }

    private record LineWeight(String group, double amount) {}

    private List<Map<String, Object>> buildCategoryLedgerEntriesFromInvoices(List<Map<String, Object>> invoices, String categoryId) {
        if (invoices == null || invoices.isEmpty() || categoryId == null || categoryId.isBlank()) {
            return List.of();
        }
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> invoiceRow : invoices) {
            String invoiceId = asText(invoiceRow.get("name"));
            if (!hasText(invoiceId) || asInt(invoiceRow.get("docstatus")) == 2) {
                continue;
            }
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(SALES_INVOICE, invoiceId));
            if (invoice.isEmpty()) {
                continue;
            }
            double dueBase = asDouble(invoice.get("outstanding_amount"));
            if (dueBase <= 0) {
                dueBase = asDouble(invoice.get("grand_total"));
            }
            if (dueBase <= 0) {
                continue;
            }
            Map<String, Double> shares = allocateInvoiceShares(invoice, dueBase, resolver);
            double share = shares.getOrDefault(categoryId, 0.0);
            if (share <= 0) {
                continue;
            }
            double debit = round(share);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(invoice.get("posting_date")));
            row.put("voucherType", invoiceVoucherType(invoice, "Sales Invoice"));
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("customer")));
            row.put("debit", debit);
            row.put("credit", 0.0);
            row.put("netChange", debit);
            entries.add(row);
        }
        return entries;
    }

    private List<Map<String, Object>> buildCategoryLedgerEntriesFromPayments(List<Map<String, Object>> payments, boolean debit) {
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> payment : payments) {
            if (!isSubmitted(payment)) {
                continue;
            }
            double amount = round(resolvePaymentAmount(payment));
            if (amount <= 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(payment.get("posting_date")));
            row.put("voucherType", "Payment Entry");
            row.put("voucherNo", asText(payment.get("name")));
            row.put("reference", asText(payment.get("reference_no")));
            row.put("debit", debit ? amount : 0.0);
            row.put("credit", debit ? 0.0 : amount);
            row.put("netChange", debit ? amount : round(-amount));
            entries.add(row);
        }
        return entries;
    }

    private List<Map<String, Object>> mergeAndFinalizeLedger(
            List<Map<String, Object>> invoiceEntries,
            List<Map<String, Object>> paymentEntries,
            boolean includeRunningBalance) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (invoiceEntries != null) {
            entries.addAll(invoiceEntries);
        }
        if (paymentEntries != null) {
            entries.addAll(paymentEntries);
        }
        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> asText(row.get("voucherType")))
                .thenComparing(row -> asText(row.get("voucherNo"))));
        if (!includeRunningBalance) {
            return entries;
        }
        double running = 0.0;
        for (Map<String, Object> entry : entries) {
            running = round(running + asDouble(entry.get("netChange")));
            entry.put("runningBalance", running);
        }
        return entries;
    }

    private Map<String, Double> allocateInvoiceShares(
            Map<String, Object> invoice,
            double dueBase,
            ItemGroupResolver resolver) {
        List<Map<String, Object>> items = childItems(invoice.get("items"));
        if (items.isEmpty() || dueBase <= 0) {
            return Map.of();
        }
        Map<String, Double> weights = new HashMap<>();
        double totalWeight = 0.0;
        for (Map<String, Object> item : items) {
            double amount = asDouble(firstNonNull(item.get("base_amount"), firstNonNull(item.get("amount"), item.get("net_amount"))));
            if (amount <= 0) {
                continue;
            }
            String group = asText(firstNonNull(item.get("item_group"), item.get("item_group_name")));
            group = normalizeItemGroup(group);
            if (!hasText(group)) {
                group = resolver.resolve(asText(item.get("item_code")));
            }
            group = normalizeItemGroup(group);
            if (!hasText(group)) {
                group = "Uncategorized";
            }
            weights.merge(group, amount, Double::sum);
            totalWeight += amount;
        }
        if (weights.isEmpty() || totalWeight <= 0) {
            return Map.of();
        }
        Map<String, Double> shares = new HashMap<>();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            shares.put(entry.getKey(), dueBase * (entry.getValue() / totalWeight));
        }
        return shares;
    }

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
            group = normalizeItemGroup(group);
            cache.put(key, group);
            return group;
        }
    }

    private static String normalizeItemGroup(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return "";
        }
        return ALL_ITEM_GROUPS.equalsIgnoreCase(text) ? "" : text;
    }

    public List<Map<String, Object>> getAllBranchLedgerEntries() {
        Map<String, String> branchNames = fetchBranches().stream()
                .collect(Collectors.toMap(
                        branch -> asText(branch.get("name")),
                        this::preferredBranchName,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> entries = new ArrayList<>();
        List<Map<String, Object>> invoices = fetchSalesInvoices(null);

        for (Map<String, Object> invoice : invoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            String branchId = asText(invoice.get("customer"));
            double debit = round(asDouble(invoice.get("grand_total")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(invoice.get("posting_date")));
            row.put("branchId", branchId);
            row.put("branchName", branchNames.getOrDefault(branchId, branchId));
            row.put("voucherType", "Sales Invoice");
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", branchId);
            row.put("debit", debit);
            row.put("credit", 0.0);
            row.put("netChange", debit);
            entries.add(row);
        }

        for (Map<String, Object> payment : fetchCustomerPayments(null, invoices)) {
            if (asInt(payment.get("docstatus")) == 2) {
                continue;
            }
            String branchId = asText(payment.get("party"));
            double credit = round(resolvePaymentAmount(payment));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(payment.get("posting_date")));
            row.put("branchId", branchId);
            row.put("branchName", branchNames.getOrDefault(branchId, branchId));
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
                .thenComparing(row -> asText(row.get("branchName")))
                .thenComparing(row -> asText(row.get("voucherType")))
                .thenComparing(row -> asText(row.get("voucherNo"))));
        double running = 0.0;
        for (Map<String, Object> entry : entries) {
            running = round(running + asDouble(entry.get("netChange")));
            entry.put("runningBalance", running);
        }
        return entries;
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
            row.put("voucherType", invoiceVoucherType(invoice, "Sales Invoice"));
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("customer")));
            row.put("debit", debit);
            row.put("credit", 0.0);
            row.put("netChange", debit);
            entries.add(row);
        }
        for (Map<String, Object> payment : payments) {
            if (!isSubmitted(payment)) {
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
        return listResourcesPaged(CUSTOMER, params).stream()
                .filter(branch -> !asText(branch.get("customer_name")).trim().startsWith("[DELETED]"))
                .toList();
    }

    private List<Map<String, Object>> fetchOrderRows(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"transaction_date\",\"delivery_date\",\"aas_vendor\",\"aas_status\",\"aas_vendor_pdf\",\"aas_vendor_bill_total\",\"aas_sell_order_total\",\"aas_po\",\"aas_si_branch\",\"aas_is_deleted\",\"modified\"]");
        params.put("order_by", "modified desc");
        if (hasText(branchId)) {
            params.put("filters", "[[\"Sales Order\",\"customer\",\"=\",\"" + escapeJson(branchId) + "\"]]");
        }
        return listResourcesPaged(SALES_ORDER, params).stream()
                .filter(row -> !asFlag(row.get("aas_is_deleted")) && !"DELETED".equalsIgnoreCase(asText(row.get("aas_status"))))
                .toList();
    }

    private List<Map<String, Object>> fetchSalesInvoices(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"posting_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"modified\",\"creation\",\"docstatus\",\"aas_invoice_version_status\"]");
        params.put("order_by", "posting_date asc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("docstatus", "!=", "2"));
        if (hasText(branchId)) {
            filters.add(List.of("customer", "=", branchId));
        }
        params.put("filters", toJson(filters));
        return listResourcesPaged(SALES_INVOICE, params).stream()
                .filter(invoice -> !INVOICE_VERSION_OLD.equalsIgnoreCase(asText(invoice.get("aas_invoice_version_status"))))
                .toList();
    }

    private List<Map<String, Object>> fetchCustomerPayments(String branchId, List<Map<String, Object>> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\",\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Customer"));
        filters.add(List.of("docstatus", "=", "1"));
        if (hasText(branchId)) {
            filters.add(List.of("party", "=", branchId));
        }
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        List<Map<String, Object>> payments = listResourcesPaged(PAYMENT_ENTRY, params);
        return filterPaymentsLinkedToInvoices(payments, invoices);
    }

    private List<Map<String, Object>> fetchCustomerPaymentsByCategory(String branchId, String categoryId) {
        if (!hasText(branchId) || !hasText(categoryId)) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\",\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\",\"aas_category\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Customer"));
        filters.add(List.of("docstatus", "=", "1"));
        filters.add(List.of("party", "=", branchId));
        filters.add(List.of("aas_category", "=", categoryId));
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        return listResourcesPaged(PAYMENT_ENTRY, params);
    }

    private List<Map<String, Object>> filterPaymentsLinkedToInvoices(
            List<Map<String, Object>> payments,
            List<Map<String, Object>> invoices) {
        if (payments.isEmpty() || invoices.isEmpty()) {
            return List.of();
        }
        Map<String, String> invoiceCustomers = invoices.stream()
                .collect(Collectors.toMap(
                        invoice -> asText(invoice.get("name")),
                        invoice -> asText(invoice.get("customer")),
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> validPayments = new ArrayList<>();
        for (Map<String, Object> payment : payments) {
            String paymentName = asText(payment.get("name"));
            String party = asText(payment.get("party"));
            if (!hasText(paymentName) || !hasText(party)) {
                continue;
            }
            Map<String, Object> paymentDoc = unwrap(erpNextClient.getResource(PAYMENT_ENTRY, paymentName));
            List<Map<String, Object>> references = childItems(paymentDoc.get("references"));
            boolean linkedToBranchInvoice = references.stream().anyMatch(reference -> {
                if (!SALES_INVOICE.equals(asText(reference.get("reference_doctype")))) {
                    return false;
                }
                String invoiceId = asText(reference.get("reference_name"));
                String invoiceCustomer = invoiceCustomers.get(invoiceId);
                return hasText(invoiceCustomer) && invoiceCustomer.equals(party);
            });
            if (linkedToBranchInvoice) {
                validPayments.add(payment);
            }
        }
        return validPayments;
    }

    private List<Map<String, Object>> listResourcesPaged(String doctype, Map<String, Object> params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        int start = 0;
        while (true) {
            Map<String, Object> pageParams = new HashMap<>(params);
            pageParams.put("limit_start", start);
            pageParams.put("limit_page_length", ERP_PAGE_SIZE);
            List<Map<String, Object>> page = erpNextClient.listResources(doctype, pageParams);
            if (page.isEmpty()) {
                break;
            }
            rows.addAll(page);
            if (page.size() < ERP_PAGE_SIZE) {
                break;
            }
            start += ERP_PAGE_SIZE;
        }
        return rows;
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

    private boolean isSubmitted(Map<String, Object> doc) {
        return asInt(doc.get("docstatus")) == 1;
    }

    private String invoiceVoucherType(Map<String, Object> doc, String baseType) {
        return isSubmitted(doc) ? baseType : "Draft " + baseType;
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

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
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
