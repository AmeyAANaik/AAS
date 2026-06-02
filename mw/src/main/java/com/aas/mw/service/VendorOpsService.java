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
    private static final String ALL_ITEM_GROUPS = "All Item Groups";

    private static final int ERP_PAGE_SIZE = 500;
    private static final String SALES_ORDER = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String PURCHASE_INVOICE = "Purchase Invoice";
    private static final String SUPPLIER = "Supplier";
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String PLACEHOLDER_ITEM = "AAS-SYSTEM-BRANCH-IMAGE";
    private static final String INVOICE_VERSION_OLD = "OLD";
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
        List<Map<String, Object>> payments = fetchSupplierPayments(null, purchaseInvoices);

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
        List<Map<String, Object>> payments = fetchSupplierPayments(vendorId, purchaseInvoices);
        List<Map<String, Object>> ledgerEntries = buildLedgerEntries(purchaseInvoices, payments);

        double parseSuccessRate = calculateParseSuccessRate(orderRows);
        double billCaptureRate = calculateBillCaptureRate(orderRows);
        double outstandingBalance = round(Math.max(0.0, getLedgerBalance(ledgerEntries)));
        long mismatchCount = orderRows.stream().filter(row -> asFlag(row.get("hasMismatch"))).count();
        long parseFailureCount = orderRows.stream()
                .filter(row -> asFlag(row.get("pdfUploaded")) && asDouble(row.get("parsedItems")) <= 0)
                .count();
        long unpaidInvoiceCount = purchaseInvoices.stream()
                .filter(invoice -> resolveInvoiceDueAmount(invoice) > 0)
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
        return getVendorLedger(vendorId, null, null);
    }

    public Map<String, Object> getVendorLedger(String vendorId, String fromDate, String toDate) {
        Map<String, Object> vendor = unwrap(erpNextClient.getResource(SUPPLIER, vendorId));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }
        List<Map<String, Object>> purchaseInvoices = fetchPurchaseInvoices(vendorId);
        List<Map<String, Object>> payments = fetchSupplierPayments(vendorId, purchaseInvoices);
        List<Map<String, Object>> fullEntries = buildLedgerEntries(purchaseInvoices, payments);
        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        LedgerWindow window = sliceLedger(fullEntries, range);
        return Map.of(
                "vendorId", vendorId,
                "vendorName", preferredVendorName(vendor),
                "openingBalance", window.openingBalance,
                "closingBalance", window.closingBalance,
                "balance", window.closingBalance,
                "categorySummary", buildCategorySummaryFromPurchaseInvoices(filterByPostingDate(purchaseInvoices, range)),
                "entries", window.entries);
    }

    public Map<String, Object> getVendorLedgerByCategory(String vendorId, String categoryId) {
        return getVendorLedgerByCategory(vendorId, categoryId, null, null);
    }

    public Map<String, Object> getVendorLedgerByCategory(String vendorId, String categoryId, String fromDate, String toDate) {
        String normalizedCategory = categoryId == null ? "" : categoryId.trim();
        if (normalizedCategory.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required.");
        }
        Map<String, Object> vendor = unwrap(erpNextClient.getResource(SUPPLIER, vendorId));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }

        List<Map<String, Object>> purchaseInvoices = fetchPurchaseInvoices(vendorId);
        List<Map<String, Object>> invoiceEntries = buildCategoryLedgerEntriesFromPurchaseInvoices(purchaseInvoices, normalizedCategory);
        List<Map<String, Object>> paymentEntries = buildCategoryLedgerEntriesFromPayments(
                fetchSupplierPaymentsByCategory(vendorId, normalizedCategory));
        List<Map<String, Object>> fullEntries = mergeAndFinalizeLedger(invoiceEntries, paymentEntries, true);
        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        LedgerWindow window = sliceLedger(fullEntries, range);

        return Map.of(
                "vendorId", vendorId,
                "vendorName", preferredVendorName(vendor),
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

    public List<Map<String, Object>> getAllVendorCategorySummaries() {
        return getAllVendorCategorySummaries(null, null);
    }

    public List<Map<String, Object>> getAllVendorCategorySummaries(String fromDate, String toDate) {
        List<Map<String, Object>> vendors = fetchVendors();
        Map<String, String> vendorNames = vendors.stream()
                .collect(Collectors.toMap(
                        vendor -> asText(vendor.get("name")),
                        this::preferredVendorName,
                        (left, right) -> left,
                        LinkedHashMap::new));

        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        List<Map<String, Object>> purchaseInvoices = filterByPostingDate(fetchPurchaseInvoices(null), range);
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Map<String, CategoryWeights> orderCache = new HashMap<>();
        Map<String, Map<String, Double>> totals = new LinkedHashMap<>();

        for (Map<String, Object> invoice : purchaseInvoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            String supplierId = asText(invoice.get("supplier"));
            if (!hasText(supplierId)) {
                continue;
            }
            double dueBase = asDouble(invoice.get("outstanding_amount"));
            if (dueBase <= 0) {
                dueBase = asDouble(invoice.get("grand_total"));
            }
            if (dueBase <= 0) {
                continue;
            }
            String sourceOrderId = asText(invoice.get("aas_source_sales_order"));
            Map<String, Double> shares = new HashMap<>();
            if (!hasText(sourceOrderId)) {
                shares.put("Uncategorized", dueBase);
            } else {
                CategoryWeights weights = orderCache.computeIfAbsent(
                        sourceOrderId,
                        key -> computeSalesOrderCategoryWeights(key, resolver));
                if (weights.total() <= 0 || weights.weights().isEmpty()) {
                    shares.put("Uncategorized", dueBase);
                } else {
                    for (Map.Entry<String, Double> entry : weights.weights().entrySet()) {
                        shares.put(entry.getKey(), dueBase * (entry.getValue() / weights.total()));
                    }
                }
            }
            Map<String, Double> vendorTotals = totals.computeIfAbsent(supplierId, key -> new LinkedHashMap<>());
            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                vendorTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> vendorEntry : totals.entrySet()) {
            String id = vendorEntry.getKey();
            String name = vendorNames.getOrDefault(id, id);
            for (Map.Entry<String, Double> categoryEntry : vendorEntry.getValue().entrySet()) {
                rows.add(Map.of(
                        "vendorId", id,
                        "vendorName", name,
                        "category", categoryEntry.getKey(),
                        "amount", round(categoryEntry.getValue())));
            }
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> asText(row.get("vendorName")))
                        .thenComparing(row -> asText(row.get("category"))))
                .toList();
    }

    private List<Map<String, Object>> buildCategorySummaryFromPurchaseInvoices(List<Map<String, Object>> purchaseInvoices) {
        if (purchaseInvoices == null || purchaseInvoices.isEmpty()) {
            return List.of();
        }
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Map<String, CategoryWeights> orderCache = new HashMap<>();
        Map<String, Double> totals = new LinkedHashMap<>();

        for (Map<String, Object> invoice : purchaseInvoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            double base = asDouble(invoice.get("outstanding_amount"));
            if (base <= 0) {
                base = asDouble(invoice.get("grand_total"));
            }
            if (base <= 0) {
                continue;
            }
            String directCategory = asText(invoice.get("aas_category"));
            if (hasText(directCategory)) {
                totals.merge(directCategory, base, Double::sum);
                continue;
            }
            String sourceOrderId = asText(invoice.get("aas_source_sales_order"));
            if (!hasText(sourceOrderId)) {
                totals.merge("Uncategorized", base, Double::sum);
                continue;
            }
            CategoryWeights weights = orderCache.computeIfAbsent(
                    sourceOrderId,
                    key -> computeSalesOrderCategoryWeights(key, resolver));
            if (weights.total() <= 0 || weights.weights().isEmpty()) {
                totals.merge("Uncategorized", base, Double::sum);
                continue;
            }
            for (Map.Entry<String, Double> entry : weights.weights().entrySet()) {
                double share = base * (entry.getValue() / weights.total());
                totals.merge(entry.getKey(), share, Double::sum);
            }
        }

        return totals.entrySet().stream()
                .filter(entry -> hasText(entry.getKey()) && entry.getValue() != null && entry.getValue() > 0)
                .map(entry -> Map.<String, Object>of("category", entry.getKey(), "amount", round(entry.getValue())))
                .sorted((left, right) -> Double.compare(asDouble(right.get("amount")), asDouble(left.get("amount"))))
                .toList();
    }

    private CategoryWeights computeSalesOrderCategoryWeights(String salesOrderId, ItemGroupResolver resolver) {
        Map<String, Object> order = unwrap(erpNextClient.getResource(SALES_ORDER, salesOrderId));
        List<Map<String, Object>> items = childItems(order.get("items"));
        if (items.isEmpty()) {
            return new CategoryWeights(Map.of(), 0.0);
        }
        Map<String, Double> weights = new HashMap<>();
        double total = 0.0;
        for (Map<String, Object> item : items) {
            double qty = asDouble(item.get("qty"));
            double rate = asDouble(item.get("aas_vendor_rate"));
            if (rate <= 0) {
                rate = asDouble(item.get("rate"));
            }
            if (qty <= 0 || rate <= 0) {
                continue;
            }
            double amount = qty * rate;
            String group = asText(item.get("item_group"));
            group = normalizeItemGroup(group);
            if (!hasText(group)) {
                group = resolver.resolve(asText(item.get("item_code")));
            }
            group = normalizeItemGroup(group);
            if (!hasText(group)) {
                group = "Uncategorized";
            }
            weights.merge(group, amount, Double::sum);
            total += amount;
        }
        return new CategoryWeights(weights, total);
    }

    private record CategoryWeights(Map<String, Double> weights, double total) {}

    private List<Map<String, Object>> buildCategoryLedgerEntriesFromPurchaseInvoices(List<Map<String, Object>> purchaseInvoices, String categoryId) {
        if (purchaseInvoices == null || purchaseInvoices.isEmpty() || categoryId == null || categoryId.isBlank()) {
            return List.of();
        }
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Map<String, CategoryWeights> orderCache = new HashMap<>();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> invoice : purchaseInvoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            double dueBase = asDouble(invoice.get("outstanding_amount"));
            if (dueBase <= 0) {
                dueBase = asDouble(invoice.get("grand_total"));
            }
            if (dueBase <= 0) {
                continue;
            }
            String directCategory = asText(invoice.get("aas_category"));
            if (hasText(directCategory)) {
                if (!directCategory.equals(categoryId)) {
                    continue;
                }
                double credit = round(dueBase);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", asText(invoice.get("posting_date")));
                row.put("voucherType", invoiceVoucherType(invoice, "Purchase Invoice"));
                row.put("voucherNo", asText(invoice.get("name")));
                row.put("reference", asText(invoice.get("bill_no")));
                row.put("debit", 0.0);
                row.put("credit", credit);
                row.put("netChange", credit);
                entries.add(row);
                continue;
            }
            String sourceOrderId = asText(invoice.get("aas_source_sales_order"));
            double share = 0.0;
            if (!hasText(sourceOrderId)) {
                share = "Uncategorized".equals(categoryId) ? dueBase : 0.0;
            } else {
                CategoryWeights weights = orderCache.computeIfAbsent(
                        sourceOrderId,
                        key -> computeSalesOrderCategoryWeights(key, resolver));
                if (weights.total() > 0 && weights.weights().containsKey(categoryId)) {
                    share = dueBase * (weights.weights().get(categoryId) / weights.total());
                }
            }
            if (share <= 0) {
                continue;
            }
            double credit = round(share);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(invoice.get("posting_date")));
            row.put("voucherType", invoiceVoucherType(invoice, "Purchase Invoice"));
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("bill_no")));
            row.put("debit", 0.0);
            row.put("credit", credit);
            row.put("netChange", credit);
            entries.add(row);
        }
        return entries;
    }

    private List<Map<String, Object>> buildCategoryLedgerEntriesFromPayments(List<Map<String, Object>> payments) {
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
            row.put("debit", amount);
            row.put("credit", 0.0);
            row.put("netChange", round(-amount));
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
        double runningBalance = 0.0;
        for (Map<String, Object> entry : entries) {
            runningBalance = round(runningBalance + asDouble(entry.get("netChange")));
            entry.put("runningBalance", runningBalance);
        }
        return entries;
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

    public List<Map<String, Object>> getAllVendorLedgerEntries() {
        Map<String, String> vendorNames = fetchVendors().stream()
                .collect(Collectors.toMap(
                        vendor -> asText(vendor.get("name")),
                        this::preferredVendorName,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Map<String, Object>> entries = new ArrayList<>();
        List<Map<String, Object>> purchaseInvoices = fetchPurchaseInvoices(null);

        for (Map<String, Object> invoice : purchaseInvoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            String vendorId = asText(invoice.get("supplier"));
            double credit = round(asDouble(invoice.get("grand_total")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(invoice.get("posting_date")));
            row.put("vendorId", vendorId);
            row.put("vendorName", vendorNames.getOrDefault(vendorId, vendorId));
            row.put("voucherType", "Purchase Invoice");
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("bill_no")));
            row.put("debit", 0.0);
            row.put("credit", credit);
            row.put("netChange", credit);
            entries.add(row);
        }

        for (Map<String, Object> payment : fetchSupplierPayments(null, purchaseInvoices)) {
            if (asInt(payment.get("docstatus")) == 2) {
                continue;
            }
            String vendorId = asText(payment.get("party"));
            double debit = round(resolvePaymentAmount(payment));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(payment.get("posting_date")));
            row.put("vendorId", vendorId);
            row.put("vendorName", vendorNames.getOrDefault(vendorId, vendorId));
            row.put("voucherType", "Payment Entry");
            row.put("voucherNo", asText(payment.get("name")));
            row.put("reference", asText(payment.get("reference_no")));
            row.put("debit", debit);
            row.put("credit", 0.0);
            row.put("netChange", round(-debit));
            entries.add(row);
        }

        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> asText(row.get("vendorName")))
                .thenComparing(row -> asText(row.get("voucherType")))
                .thenComparing(row -> asText(row.get("voucherNo"))));
        double running = 0.0;
        for (Map<String, Object> entry : entries) {
            running = round(running + asDouble(entry.get("netChange")));
            entry.put("runningBalance", running);
        }
        return entries;
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
        double ledgerBalance = getLedgerBalance(ledgerEntries);
        double preCaptureEstimatedAmount = vendorOrders.stream()
                .filter(order -> "VENDOR_PDF_RECEIVED".equals(asText(order.get("aas_status")))
                        || ("VENDOR_BILL_CAPTURED".equals(asText(order.get("aas_status")))
                        && !hasText(order.get("aas_pi_vendor"))))
                .mapToDouble(order -> asDouble(order.get("aas_vendor_bill_total")))
                .sum();
        double pendingBillAmount = Math.max(0.0, ledgerBalance) + preCaptureEstimatedAmount;

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
        row.put("ledgerBalance", ledgerBalance);
        row.put("parseSuccessRate", calculateParseSuccessRateFromSummary(vendorOrders));
        return row;
    }

    private double resolveInvoiceDueAmount(Map<String, Object> invoice) {
        if (invoice == null) {
            return 0.0;
        }
        int docstatus = asInt(invoice.get("docstatus"));
        if (docstatus == 0) {
            return asDouble(invoice.get("grand_total"));
        }
        if (docstatus == 1) {
            double outstanding = asDouble(invoice.get("outstanding_amount"));
            return outstanding > 0 ? outstanding : 0.0;
        }
        return 0.0;
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
            row.put("voucherType", invoiceVoucherType(invoice, "Purchase Invoice"));
            row.put("voucherNo", asText(invoice.get("name")));
            row.put("reference", asText(invoice.get("bill_no")));
            row.put("debit", 0.0);
            row.put("credit", credit);
            row.put("netChange", credit);
            entries.add(row);
        }

        for (Map<String, Object> payment : payments) {
            if (!isSubmitted(payment)) {
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
        return listResourcesPaged(SUPPLIER, params);
    }

    private List<Map<String, Object>> fetchOrderRows(String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"customer\",\"transaction_date\",\"delivery_date\",\"aas_vendor\",\"aas_status\","
                        + "\"aas_vendor_pdf\",\"aas_vendor_bill_total\",\"aas_vendor_bill_ref\",\"aas_vendor_bill_date\","
                        + "\"aas_po\",\"aas_pi_vendor\",\"aas_is_deleted\",\"modified\",\"creation\"]");
        params.put("order_by", "modified desc");
        if (hasText(vendorId)) {
            params.put("filters", "[[\"Sales Order\",\"aas_vendor\",\"=\",\"" + escapeJson(vendorId) + "\"]]");
        }
        return listResourcesPaged(SALES_ORDER, params).stream()
                .filter(row -> !asFlag(row.get("aas_is_deleted")) && !"DELETED".equalsIgnoreCase(asText(row.get("aas_status"))))
                .toList();
    }

    private List<Map<String, Object>> fetchPurchaseInvoices(String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"supplier\",\"posting_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"bill_no\","
                        + "\"aas_source_sales_order\",\"aas_replaced_by\",\"modified\",\"creation\",\"docstatus\","
                        + "\"aas_invoice_version_status\",\"aas_category\"]");
        params.put("order_by", "posting_date asc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("docstatus", "!=", "2"));
        if (hasText(vendorId)) {
            filters.add(List.of("supplier", "=", vendorId));
        }
        params.put("filters", toJson(filters));
        List<Map<String, Object>> rows = listResourcesPaged(PURCHASE_INVOICE, params).stream()
                .filter(invoice -> !INVOICE_VERSION_OLD.equalsIgnoreCase(asText(invoice.get("aas_invoice_version_status"))))
                .filter(invoice -> asText(invoice.get("aas_replaced_by")).isBlank())
                .toList();
        return dedupeLatestInvoices(rows);
    }

    private List<Map<String, Object>> dedupeLatestInvoices(List<Map<String, Object>> invoices) {
        if (invoices == null || invoices.size() < 2) {
            return invoices == null ? List.of() : invoices;
        }
        Map<String, Map<String, Object>> bestByKey = new HashMap<>();
        List<Map<String, Object>> passthrough = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            if (invoice == null) {
                continue;
            }
            String key = asText(invoice.get("aas_source_sales_order")).trim();
            if (key.isBlank()) {
                key = asText(invoice.get("bill_no")).trim();
            }
            if (key.isBlank()) {
                passthrough.add(invoice);
                continue;
            }
            Map<String, Object> best = bestByKey.get(key);
            if (best == null || compareInvoiceRecency(invoice, best) > 0) {
                bestByKey.put(key, invoice);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(passthrough.size() + bestByKey.size());
        result.addAll(passthrough);
        result.addAll(bestByKey.values());
        result.sort((a, b) -> {
            int posting = asText(a.get("posting_date")).compareTo(asText(b.get("posting_date")));
            if (posting != 0) {
                return posting;
            }
            return compareInvoiceRecency(a, b);
        });
        return result;
    }

    private int compareInvoiceRecency(Map<String, Object> left, Map<String, Object> right) {
        String leftModified = asText(left == null ? null : left.get("modified")).trim();
        String rightModified = asText(right == null ? null : right.get("modified")).trim();
        if (!leftModified.isBlank() || !rightModified.isBlank()) {
            return leftModified.compareTo(rightModified);
        }
        String leftCreated = asText(left == null ? null : left.get("creation")).trim();
        String rightCreated = asText(right == null ? null : right.get("creation")).trim();
        if (!leftCreated.isBlank() || !rightCreated.isBlank()) {
            return leftCreated.compareTo(rightCreated);
        }
        return asText(left == null ? null : left.get("name")).compareTo(asText(right == null ? null : right.get("name")));
    }

    private List<Map<String, Object>> fetchSupplierPayments(String vendorId, List<Map<String, Object>> purchaseInvoices) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\","
                        + "\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Supplier"));
        filters.add(List.of("docstatus", "=", "1"));
        if (hasText(vendorId)) {
            filters.add(List.of("party", "=", vendorId));
        }
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        return listResourcesPaged(PAYMENT_ENTRY, params);
    }

    private List<Map<String, Object>> fetchSupplierPaymentsByCategory(String vendorId, String categoryId) {
        if (!hasText(vendorId) || !hasText(categoryId)) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\","
                        + "\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\",\"aas_category\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Supplier"));
        filters.add(List.of("docstatus", "=", "1"));
        filters.add(List.of("party", "=", vendorId));
        filters.add(List.of("aas_category", "=", categoryId));
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        return listResourcesPaged(PAYMENT_ENTRY, params);
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

    private boolean isSubmitted(Map<String, Object> doc) {
        return asInt(doc.get("docstatus")) == 1;
    }

    private String invoiceVoucherType(Map<String, Object> doc, String baseType) {
        return isSubmitted(doc) ? baseType : "Draft " + baseType;
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

    private double getLedgerBalance(List<Map<String, Object>> entries) {
        return entries == null || entries.isEmpty()
                ? 0.0
                : asDouble(entries.get(entries.size() - 1).get("runningBalance"));
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
