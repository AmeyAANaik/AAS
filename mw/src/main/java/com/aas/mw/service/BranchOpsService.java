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
import java.util.LinkedHashSet;
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
    private static final double CATEGORY_SETTLEMENT_TOLERANCE = 0.5;

    private static final int ERP_PAGE_SIZE = 500;
    private static final String CUSTOMER = "Customer";
    private static final String SALES_ORDER = "Sales Order";
    private static final String SALES_INVOICE = "Sales Invoice";
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String JOURNAL_ENTRY = "Journal Entry";
    private static final String PLACEHOLDER_ITEM = "AAS-SYSTEM-BRANCH-IMAGE";
    private static final String INVOICE_VERSION_OLD = "OLD";
    private static final Set<String> OPEN_STATUSES = Set.of("DRAFT", "VENDOR_ASSIGNED", "VENDOR_PDF_RECEIVED", "VENDOR_BILL_CAPTURED", "SELL_ORDER_CREATED");
    private static final DateTimeFormatter ERP_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]", Locale.ROOT);

    private static final String PAYMENT_ENTRY_REFERENCE = "Payment Entry Reference";

    /**
     * Receivables aging buckets, keyed by payload field name. Bands are absolute days past
     * {@code due_date} and identical for every branch; per-branch credit days enter only through
     * the due date itself.
     */
    private static final LinkedHashMap<String, String> AGING_BUCKETS = new LinkedHashMap<>();
    static {
        AGING_BUCKETS.put("notDue", "Not due");
        AGING_BUCKETS.put("d1_7", "1-7 days");
        AGING_BUCKETS.put("d8_15", "8-15 days");
        AGING_BUCKETS.put("d16_30", "16-30 days");
        AGING_BUCKETS.put("d30Plus", "30+ days");
    }

    // ---- Receivables risk tiering (tune here only) ----
    private static final int[] RISK_AGE_TIER_DAYS = {30, 15, 7};              // 3 / 2 / 1 pts
    private static final double[] RISK_AMOUNT_TIER_RUPEES = {100_000, 50_000, 10_000};
    private static final double[] RISK_ONTIME_TIER_PCT = {60, 75, 90};
    private static final double RISK_DEFAULTER_MIN_PCT = 55.0;
    private static final double RISK_WATCH_MIN_PCT = 25.0;
    private static final int RISK_HARD_DEFAULTER_DAYS = 45;
    private static final double RISK_MIN_OVERDUE_TO_FLAG = 500.0;
    private static final int ONTIME_GRACE_DAYS = 0;
    private static final double ONTIME_MIN_COVERAGE_PCT = 40.0;

    private static final String RISK_TIER_GOOD = "GOOD";
    private static final String RISK_TIER_WATCH = "WATCH";
    private static final String RISK_TIER_DEFAULTER = "DEFAULTER";

    private final ErpNextClient erpNextClient;
    private final AdjustmentNoteErpService adjustmentNoteErpService;

    public BranchOpsService(ErpNextClient erpNextClient, AdjustmentNoteErpService adjustmentNoteErpService) {
        this.erpNextClient = erpNextClient;
        this.adjustmentNoteErpService = adjustmentNoteErpService;
    }

    public Map<String, Object> getSummary() {
        List<Map<String, Object>> branches = fetchBranches();
        List<Map<String, Object>> orders = fetchOrderRows(null);
        List<Map<String, Object>> invoices = fetchSalesInvoices(null);
        List<Map<String, Object>> payments = mergePayments(fetchCustomerPayments(null, invoices), fetchCustomerCategoryPayments(null));
        List<Map<String, Object>> adjustmentNotes = fetchApprovedCustomerAdjustmentNotes(null, null);

        List<Map<String, Object>> rows = branches.stream()
                .map(branch -> buildBranchSummary(branch, orders, invoices, payments, adjustmentNotes))
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
        List<Map<String, Object>> payments = mergePayments(fetchCustomerPayments(branchId, invoices), fetchCustomerCategoryPayments(branchId));
        List<Map<String, Object>> adjustmentNotes = fetchApprovedCustomerAdjustmentNotes(branchId, null);
        List<Map<String, Object>> ledgerEntries = buildLedgerEntries(invoices, payments, adjustmentNotes);

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
        kpis.put("openReceivableAmount", openReceivableAmount(ledgerEntries));
        kpis.put("invoicedAmount", round(invoices.stream().mapToDouble(this::invoiceLedgerAmount).sum()));
        kpis.put("paymentCollectionRate", calculatePaymentCollectionRate(invoices, payments));

        Map<String, Object> billing = new LinkedHashMap<>();
        billing.put("invoicesRaised", invoices.size());
        billing.put("openInvoices", invoices.stream().filter(invoice -> resolveInvoiceDueAmount(invoice) > 0).count());
        billing.put("paymentsReceived", payments.size());
        billing.put("approvedAdjustmentNotes", adjustmentNotes.size());
        billing.put("ledgerBalance", getLedgerBalance(ledgerEntries));

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
        List<Map<String, Object>> payments = mergePayments(fetchCustomerPayments(branchId, invoices), fetchCustomerCategoryPayments(branchId));

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
        List<Map<String, Object>> payments = mergePayments(fetchCustomerPayments(branchId, invoices), fetchCustomerCategoryPayments(branchId));
        List<Map<String, Object>> adjustmentNotes = fetchApprovedCustomerAdjustmentNotes(branchId, null);
        List<Map<String, Object>> fullEntries = buildLedgerEntries(invoices, payments, adjustmentNotes);
        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        LedgerWindow window = sliceLedger(fullEntries, range);
        // Category summary uses all data up to the closing date so each category's
        // balance reflects its actual outstanding (not just net-change within the window).
        LedgerRange upToClose = LedgerRange.parse(null, range.toInclusive());
        List<Map<String, Object>> allCategoryPayments = fetchCustomerCategoryPayments(branchId);
        List<Map<String, Object>> allAdjustmentNotes = fetchApprovedCustomerAdjustmentNotes(branchId, null);
        List<Map<String, Object>> invoicesUpToClose = filterByPostingDate(invoices, upToClose);
        List<Map<String, Object>> categoryPaymentsUpToClose = filterPaymentsByPostingDate(allCategoryPayments, upToClose);
        List<Map<String, Object>> categoryAdjustmentNotesUpToClose = filterAdjustmentNotesByPostingDate(allAdjustmentNotes, upToClose);
        List<Map<String, Object>> categorySummary = buildCategoryBalanceSummary(invoicesUpToClose, categoryPaymentsUpToClose, categoryAdjustmentNotesUpToClose);
        double openingBalance = normalizeCategoryBalance(window.openingBalance);
        double closingBalance = normalizedBranchClosingBalance(openingBalance, window.closingBalance, categorySummary);
        return Map.of(
                "branchId", branchId,
                "branchName", preferredBranchName(branch),
                "openingBalance", openingBalance,
                "closingBalance", closingBalance,
                "balance", closingBalance,
                "categorySummary", categorySummary,
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
        List<Map<String, Object>> categoryPayments = mergePayments(
                fetchCustomerPaymentsByCategory(branchId, normalizedCategory),
                fetchCustomerPaymentsLinkedToCategoryInvoices(branchId, invoices, normalizedCategory));
        List<Map<String, Object>> paymentEntries = buildCategoryLedgerEntriesFromPayments(
                categoryPayments,
                false);
        List<Map<String, Object>> adjustmentEntries = buildCategoryLedgerEntriesFromAdjustments(
                fetchApprovedCustomerAdjustmentNotes(branchId, normalizedCategory));
        List<Map<String, Object>> fullEntries = mergeAndFinalizeLedger(invoiceEntries, paymentEntries, adjustmentEntries, true);
        LedgerRange range = LedgerRange.parse(fromDate, toDate);
        LedgerWindow window = sliceLedger(fullEntries, range);
        double openingBalance = normalizeCategoryBalance(window.openingBalance);
        double closingBalance = normalizeCategoryBalance(window.closingBalance);

        return Map.of(
                "branchId", branchId,
                "branchName", preferredBranchName(branch),
                "categoryId", normalizedCategory,
                "categoryLabel", normalizedCategory,
                "openingBalance", openingBalance,
                "closingBalance", closingBalance,
                "balance", closingBalance,
                "entries", window.entries);
    }

    // ------------------------------------------------------------------
    // Receivables aging / defaulter reporting
    // ------------------------------------------------------------------

    /**
     * All-branch receivables aging. Only submitted invoices are aged into buckets; draft
     * (unbilled) value is reported alongside as its own dimension and never feeds the risk tier.
     *
     * @param asOf   aging reference date, defaults to today, clamped to today when in the future
     * @param from   optional inclusive {@code posting_date} lower bound on which invoices are included
     * @param to     optional inclusive {@code posting_date} upper bound
     */
    public Map<String, Object> getAgingSummary(String asOf, String from, String to) {
        LocalDate asOfDate = resolveAsOfDate(asOf);
        LedgerRange range = LedgerRange.parse(from, to);

        List<Map<String, Object>> branches = fetchBranches();
        List<Map<String, Object>> invoices = filterByPostingDate(fetchSalesInvoices(null), range);
        Map<String, List<String>> allocations = fetchPaymentInvoiceAllocations(null);
        List<Map<String, Object>> payments = mergePayments(
                fetchAgingPayments(null, invoices, allocations),
                fetchCustomerCategoryPayments(null));
        List<Map<String, Object>> adjustmentNotes = fetchApprovedCustomerAdjustmentNotes(null, null);
        Map<String, LocalDate> settlementDates = resolveInvoiceSettlementDates(payments, allocations);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> branch : branches) {
            rows.add(buildBranchAgingRow(branch, invoices, payments, adjustmentNotes, settlementDates, asOfDate));
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("branches", rows.size());
        for (String bucket : AGING_BUCKETS.keySet()) {
            totals.put(bucket, round(rows.stream().mapToDouble(row -> asDouble(row.get(bucket))).sum()));
        }
        for (String key : List.of("submittedOutstanding", "overdueAmount", "draftUnbilledAmount", "ledgerBalance", "unappliedCredits")) {
            totals.put(key, round(rows.stream().mapToDouble(row -> asDouble(row.get(key))).sum()));
        }
        totals.put("defaulterBranches", countTier(rows, RISK_TIER_DEFAULTER));
        totals.put("watchBranches", countTier(rows, RISK_TIER_WATCH));
        totals.put("goodBranches", countTier(rows, RISK_TIER_GOOD));

        int settled = rows.stream().mapToInt(row -> asInt(row.get("onTimePaymentDenominator"))).sum();
        int sampled = rows.stream().mapToInt(row -> asInt(row.get("onTimePaymentSample"))).sum();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("asOfDate", asOfDate.toString());
        payload.put("asOfDateIsHistorical", asOfDate.isBefore(LocalDate.now()));
        payload.put("buckets", agingBucketDescriptors());
        payload.put("coverage", buildCoverage(settled, sampled));
        payload.put("thresholds", agingThresholds());
        payload.put("totals", totals);
        payload.put("collectionsRanking", rankBranches(rows, "overdueAmount", "oldestOverdueDays"));
        payload.put("backlogRanking", rankBranches(rows, "draftUnbilledAmount", "oldestDraftDays"));
        payload.put("branches", rows.stream()
                .sorted(agingRowComparator("overdueAmount", "oldestOverdueDays"))
                .toList());
        return payload;
    }

    /** Per-branch aging with invoice-level drill-down and the ledger reconciliation bridge. */
    public Map<String, Object> getBranchAging(String branchId, String asOf, String from, String to) {
        Map<String, Object> branch = unwrap(erpNextClient.getResource(CUSTOMER, branchId));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        LocalDate asOfDate = resolveAsOfDate(asOf);
        LedgerRange range = LedgerRange.parse(from, to);

        List<Map<String, Object>> invoices = filterByPostingDate(fetchSalesInvoices(branchId), range);
        Map<String, List<String>> allocations = fetchPaymentInvoiceAllocations(branchId);
        List<Map<String, Object>> payments = mergePayments(
                fetchAgingPayments(branchId, invoices, allocations),
                fetchCustomerCategoryPayments(branchId));
        List<Map<String, Object>> adjustmentNotes = fetchApprovedCustomerAdjustmentNotes(branchId, null);
        Map<String, LocalDate> settlementDates = resolveInvoiceSettlementDates(payments, allocations);

        Map<String, Object> summary = buildBranchAgingRow(branch, invoices, payments, adjustmentNotes, settlementDates, asOfDate);
        int creditDays = asInt(branch.get("aas_credit_days"));

        List<Map<String, Object>> invoiceRows = invoices.stream()
                .filter(invoice -> asInt(invoice.get("docstatus")) != 2)
                .map(invoice -> buildAgingInvoiceRow(invoice, creditDays, settlementDates, asOfDate))
                .sorted(agingInvoiceComparator())
                .toList();

        Map<String, Object> reconciliation = new LinkedHashMap<>();
        reconciliation.put("submittedOutstanding", summary.get("submittedOutstanding"));
        reconciliation.put("draftUnbilled", summary.get("draftUnbilledAmount"));
        reconciliation.put("unappliedCredits", summary.get("unappliedCredits"));
        reconciliation.put("ledgerBalance", summary.get("ledgerBalance"));
        double residual = asDouble(summary.get("submittedOutstanding"))
                + asDouble(summary.get("draftUnbilledAmount"))
                - asDouble(summary.get("unappliedCredits"))
                - asDouble(summary.get("ledgerBalance"));
        reconciliation.put("balanced", Math.abs(round(residual)) <= CATEGORY_SETTLEMENT_TOLERANCE);

        Map<String, Object> branchInfo = new LinkedHashMap<>();
        branchInfo.put("branchId", asText(branch.get("name")));
        branchInfo.put("branchName", preferredBranchName(branch));
        branchInfo.put("location", asText(branch.get("aas_branch_location")));
        branchInfo.put("creditDays", creditDays);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("asOfDate", asOfDate.toString());
        payload.put("asOfDateIsHistorical", asOfDate.isBefore(LocalDate.now()));
        payload.put("buckets", agingBucketDescriptors());
        payload.put("branch", branchInfo);
        payload.put("summary", summary);
        payload.put("reconciliation", reconciliation);
        payload.put("coverage", buildCoverage(
                asInt(summary.get("onTimePaymentDenominator")),
                asInt(summary.get("onTimePaymentSample"))));
        payload.put("invoices", invoiceRows);
        return payload;
    }

    /** Flat export rows for the all-branch aging report. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAgingSummaryRows(String asOf, String from, String to) {
        Map<String, Object> payload = getAgingSummary(asOf, from, to);
        List<Map<String, Object>> branches = (List<Map<String, Object>>) payload.get("branches");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> branch : branches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Branch", branch.get("branchName"));
            row.put("Location", branch.get("location"));
            row.put("Credit Days", branch.get("creditDays"));
            for (Map.Entry<String, String> bucket : AGING_BUCKETS.entrySet()) {
                row.put(bucket.getValue(), branch.get(bucket.getKey()));
            }
            row.put("Submitted Outstanding", branch.get("submittedOutstanding"));
            row.put("Overdue", branch.get("overdueAmount"));
            row.put("Oldest Overdue Days", branch.get("oldestOverdueDays"));
            row.put("Open Invoices", branch.get("openInvoiceCount"));
            row.put("Draft / Unbilled", branch.get("draftUnbilledAmount"));
            row.put("Draft Invoices", branch.get("draftInvoiceCount"));
            row.put("Oldest Draft Days", branch.get("oldestDraftDays"));
            row.put("Unapplied Credits", branch.get("unappliedCredits"));
            row.put("Ledger Balance", branch.get("ledgerBalance"));
            row.put("On-time Payment %", branch.get("onTimePaymentPct"));
            row.put("On-time Reliable", branch.get("onTimeReliable"));
            row.put("Risk Tier", branch.get("riskTier"));
            row.put("Risk Reasons", String.join(" | ", (List<String>) branch.get("riskReasons")));
            rows.add(row);
        }
        return rows;
    }

    /** Flat export rows for one branch's invoice-level aging. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getBranchAgingRows(String branchId, String asOf, String from, String to) {
        Map<String, Object> payload = getBranchAging(branchId, asOf, from, to);
        List<Map<String, Object>> invoices = (List<Map<String, Object>>) payload.get("invoices");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Invoice", invoice.get("invoiceId"));
            row.put("Posting Date", invoice.get("postingDate"));
            row.put("Due Date", invoice.get("dueDate"));
            row.put("Stage", invoice.get("stage"));
            row.put("Status", invoice.get("status"));
            row.put("Invoice Amount", invoice.get("invoiceAmount"));
            row.put("Outstanding", invoice.get("outstandingAmount"));
            row.put("Paid", invoice.get("paidAmount"));
            row.put("Days Past Due", invoice.get("daysPastDue"));
            row.put("Bucket", invoice.get("bucketLabel"));
            row.put("Settled On", invoice.get("settlementDate"));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> buildBranchAgingRow(
            Map<String, Object> branch,
            List<Map<String, Object>> invoices,
            List<Map<String, Object>> payments,
            List<Map<String, Object>> adjustmentNotes,
            Map<String, LocalDate> settlementDates,
            LocalDate asOfDate) {
        String branchId = asText(branch.get("name"));
        int creditDays = asInt(branch.get("aas_credit_days"));
        List<Map<String, Object>> branchInvoices = invoices.stream()
                .filter(invoice -> branchId.equals(asText(invoice.get("customer"))))
                .toList();
        List<Map<String, Object>> branchPayments = payments.stream()
                .filter(payment -> branchId.equals(asText(payment.get("party"))))
                .toList();
        List<Map<String, Object>> branchNotes = adjustmentNotes.stream()
                .filter(note -> branchId.equals(asText(note.get(AdjustmentNoteErpService.FIELD_PARTY))))
                .toList();

        Map<String, Double> buckets = new LinkedHashMap<>();
        AGING_BUCKETS.keySet().forEach(key -> buckets.put(key, 0.0));

        double draftAmount = 0.0;
        int draftCount = 0;
        int oldestDraftDays = 0;
        int openInvoiceCount = 0;
        int overdueInvoiceCount = 0;
        int oldestOverdueDays = 0;
        int dueDateMissingCount = 0;
        String oldestOverdueInvoiceId = "";
        String oldestOverdueDueDate = "";
        int settledCount = 0;
        int settledWithDateCount = 0;
        int paidOnTimeCount = 0;

        for (Map<String, Object> invoice : branchInvoices) {
            int docstatus = asInt(invoice.get("docstatus"));
            if (docstatus == 2) {
                continue;
            }
            if (docstatus == 0) {
                draftAmount += resolveInvoiceDueAmount(invoice);
                draftCount++;
                LocalDate posting = parseIsoDate(asText(invoice.get("posting_date")));
                if (posting != null) {
                    oldestDraftDays = Math.max(oldestDraftDays, (int) (asOfDate.toEpochDay() - posting.toEpochDay()));
                }
                continue;
            }

            LocalDate dueDate = resolveInvoiceDueDate(invoice, creditDays);
            if (dueDate == null) {
                dueDateMissingCount++;
            }
            double open = resolveInvoiceDueAmount(invoice);
            if (open <= CATEGORY_SETTLEMENT_TOLERANCE) {
                // Fully settled: outside the buckets, but still part of the on-time denominator.
                settledCount++;
                LocalDate settledOn = settlementDates.get(asText(invoice.get("name")));
                if (settledOn != null) {
                    settledWithDateCount++;
                    if (dueDate == null || !settledOn.isAfter(dueDate.plusDays(ONTIME_GRACE_DAYS))) {
                        paidOnTimeCount++;
                    }
                }
                continue;
            }

            Integer daysPastDue = agingDaysPastDue(dueDate, asOfDate);
            String bucket = agingBucketKey(daysPastDue);
            buckets.merge(bucket, open, Double::sum);
            openInvoiceCount++;
            if (daysPastDue != null && daysPastDue >= 1) {
                overdueInvoiceCount++;
                if (daysPastDue > oldestOverdueDays) {
                    oldestOverdueDays = daysPastDue;
                    oldestOverdueInvoiceId = asText(invoice.get("name"));
                    oldestOverdueDueDate = dueDate == null ? "" : dueDate.toString();
                }
            }
        }

        double submittedOutstanding = round(buckets.values().stream().mapToDouble(Double::doubleValue).sum());
        double overdueAmount = round(submittedOutstanding - buckets.get("notDue"));
        draftAmount = round(draftAmount);

        double ledgerBalance = getLedgerBalance(buildLedgerEntries(branchInvoices, branchPayments, branchNotes));
        double unappliedCredits = round(submittedOutstanding + draftAmount - ledgerBalance);

        Double onTimePct = settledWithDateCount == 0
                ? null
                : round((paidOnTimeCount * 100.0) / settledWithDateCount);
        double coveragePct = settledCount == 0 ? 0.0 : round((settledWithDateCount * 100.0) / settledCount);
        boolean onTimeReliable = settledWithDateCount > 0 && coveragePct >= ONTIME_MIN_COVERAGE_PCT;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("branchId", branchId);
        row.put("branchName", preferredBranchName(branch));
        row.put("location", asText(branch.get("aas_branch_location")));
        row.put("creditDays", creditDays);

        row.put("submittedOutstanding", submittedOutstanding);
        AGING_BUCKETS.keySet().forEach(key -> row.put(key, round(buckets.get(key))));
        row.put("overdueAmount", overdueAmount);
        row.put("openInvoiceCount", openInvoiceCount);
        row.put("overdueInvoiceCount", overdueInvoiceCount);
        row.put("oldestOverdueDays", oldestOverdueDays);
        row.put("oldestOverdueInvoiceId", oldestOverdueInvoiceId);
        row.put("oldestOverdueDueDate", oldestOverdueDueDate);

        row.put("draftUnbilledAmount", draftAmount);
        row.put("draftInvoiceCount", draftCount);
        row.put("oldestDraftDays", oldestDraftDays);

        row.put("ledgerBalance", ledgerBalance);
        row.put("unappliedCredits", unappliedCredits);

        row.put("onTimePaymentPct", onTimePct);
        row.put("onTimePaymentSample", settledWithDateCount);
        row.put("onTimePaymentDenominator", settledCount);
        row.put("onTimeCoveragePct", coveragePct);
        row.put("onTimeReliable", onTimeReliable);
        row.put("dueDateMissingCount", dueDateMissingCount);

        row.putAll(classifyReceivableRisk(oldestOverdueDays, overdueAmount, onTimePct, onTimeReliable));
        return row;
    }

    private Map<String, Object> buildAgingInvoiceRow(
            Map<String, Object> invoice,
            int creditDays,
            Map<String, LocalDate> settlementDates,
            LocalDate asOfDate) {
        boolean draft = asInt(invoice.get("docstatus")) == 0;
        LocalDate dueDate = resolveInvoiceDueDate(invoice, creditDays);
        double invoiceAmount = invoiceLedgerAmount(invoice);
        double outstanding = resolveInvoiceDueAmount(invoice);
        Integer daysPastDue = draft ? null : agingDaysPastDue(dueDate, asOfDate);
        LocalDate settledOn = settlementDates.get(asText(invoice.get("name")));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoiceId", asText(invoice.get("name")));
        row.put("stage", draft ? "DRAFT" : "SUBMITTED");
        row.put("docstatus", asInt(invoice.get("docstatus")));
        row.put("postingDate", asText(invoice.get("posting_date")));
        row.put("dueDate", dueDate == null ? "" : dueDate.toString());
        row.put("dueDateSource", hasText(invoice.get("due_date")) ? "ERP" : "DERIVED");
        row.put("status", asText(invoice.get("status")));
        row.put("invoiceAmount", round(invoiceAmount));
        row.put("outstandingAmount", round(outstanding));
        row.put("paidAmount", round(Math.max(0.0, invoiceAmount - outstanding)));
        row.put("daysPastDue", daysPastDue);
        String bucket = draft ? "draft" : agingBucketKey(daysPastDue);
        row.put("bucket", bucket);
        row.put("bucketLabel", "draft".equals(bucket) ? "Draft / unbilled" : AGING_BUCKETS.get(bucket));
        row.put("settlementDate", settledOn == null ? "" : settledOn.toString());
        row.put("paidOnTime", settledOn == null || dueDate == null
                ? null
                : !settledOn.isAfter(dueDate.plusDays(ONTIME_GRACE_DAYS)));
        return row;
    }

    /**
     * Scores collections risk from overdue exposure only. The unbilled draft backlog is an
     * internal billing-process signal and deliberately never contributes here.
     */
    private Map<String, Object> classifyReceivableRisk(
            int oldestOverdueDays,
            double overdueAmount,
            Double onTimePct,
            boolean onTimeReliable) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> reasons = new ArrayList<>();

        if (overdueAmount <= RISK_MIN_OVERDUE_TO_FLAG && oldestOverdueDays == 0) {
            result.put("riskScore", 0);
            result.put("riskScoreMax", 6);
            result.put("riskPct", 0.0);
            result.put("riskTier", RISK_TIER_GOOD);
            result.put("riskBasis", onTimeReliable ? "FULL" : "AGING_ONLY");
            result.put("riskReasons", List.of("No overdue balance"));
            return result;
        }

        int agePts = tierPoints(oldestOverdueDays, RISK_AGE_TIER_DAYS);
        int amountPts = tierPoints(overdueAmount, RISK_AMOUNT_TIER_RUPEES);
        if (agePts > 0) {
            reasons.add("Oldest overdue " + oldestOverdueDays + " days");
        }
        if (amountPts > 0) {
            reasons.add(String.format(Locale.ROOT, "Overdue ₹%,.0f", overdueAmount));
        }

        int score = agePts + amountPts;
        int scoreMax = 6;
        String basis = "AGING_ONLY";
        if (onTimeReliable && onTimePct != null) {
            int onTimePts = descendingTierPoints(onTimePct, RISK_ONTIME_TIER_PCT);
            if (onTimePts > 0) {
                reasons.add(String.format(Locale.ROOT, "On-time payments %.0f%%", onTimePct));
            }
            score += onTimePts;
            scoreMax = 9;
            basis = "FULL";
        }

        double riskPct = round((score * 100.0) / scoreMax);
        String tier;
        if (oldestOverdueDays >= RISK_HARD_DEFAULTER_DAYS || riskPct >= RISK_DEFAULTER_MIN_PCT) {
            tier = RISK_TIER_DEFAULTER;
        } else if (riskPct >= RISK_WATCH_MIN_PCT) {
            tier = RISK_TIER_WATCH;
        } else {
            tier = RISK_TIER_GOOD;
        }
        if (reasons.isEmpty()) {
            reasons.add("Within tolerance");
        }

        result.put("riskScore", score);
        result.put("riskScoreMax", scoreMax);
        result.put("riskPct", riskPct);
        result.put("riskTier", tier);
        result.put("riskBasis", basis);
        result.put("riskReasons", List.copyOf(reasons));
        return result;
    }

    /** 3 points at or above the first threshold, then 2, then 1, else 0. */
    private int tierPoints(double value, double[] thresholds) {
        for (int i = 0; i < thresholds.length; i++) {
            if (value >= thresholds[i]) {
                return thresholds.length - i;
            }
        }
        return 0;
    }

    private int tierPoints(int value, int[] thresholds) {
        for (int i = 0; i < thresholds.length; i++) {
            if (value >= thresholds[i]) {
                return thresholds.length - i;
            }
        }
        return 0;
    }

    /** Inverted tiering: worse (lower) on-time percentages score more risk points. */
    private int descendingTierPoints(double value, double[] thresholds) {
        for (int i = 0; i < thresholds.length; i++) {
            if (value <= thresholds[i]) {
                return thresholds.length - i;
            }
        }
        return 0;
    }

    /**
     * Payment Entry to Sales Invoice allocations, read in a single child-table query.
     * The {@code parent} parameter is mandatory: ERPNext rejects child doctype queries without it.
     * A failure here degrades on-time reporting rather than failing the whole report.
     */
    private Map<String, List<String>> fetchPaymentInvoiceAllocations(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("parent", PAYMENT_ENTRY);
        params.put("fields", "[\"parent\",\"reference_name\",\"allocated_amount\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("parenttype", "=", PAYMENT_ENTRY));
        filters.add(List.of("reference_doctype", "=", SALES_INVOICE));
        filters.add(List.of("docstatus", "=", "1"));
        params.put("filters", toJson(filters));

        List<Map<String, Object>> rows;
        try {
            rows = listResourcesPaged(PAYMENT_ENTRY_REFERENCE, params);
        } catch (RuntimeException ex) {
            return Map.of();
        }
        Map<String, List<String>> byPayment = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String payment = asText(row.get("parent"));
            String invoice = asText(row.get("reference_name"));
            if (hasText(payment) && hasText(invoice)) {
                byPayment.computeIfAbsent(payment, key -> new ArrayList<>()).add(invoice);
            }
        }
        return byPayment;
    }

    /**
     * Customer payments linked to this branch's invoices, resolved against the in-memory
     * allocation map. Reproduces {@link #filterPaymentsLinkedToInvoices} semantics without its
     * per-payment {@code getResource} call, so the aging report stays at a fixed number of
     * ERPNext round trips regardless of payment volume.
     */
    private List<Map<String, Object>> fetchAgingPayments(
            String branchId,
            List<Map<String, Object>> invoices,
            Map<String, List<String>> allocations) {
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }
        Map<String, String> invoiceCustomers = new LinkedHashMap<>();
        for (Map<String, Object> invoice : invoices) {
            invoiceCustomers.put(asText(invoice.get("name")), asText(invoice.get("customer")));
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

        return listResourcesPaged(PAYMENT_ENTRY, params).stream()
                .filter(payment -> {
                    String party = asText(payment.get("party"));
                    if (!hasText(party)) {
                        return false;
                    }
                    return allocations.getOrDefault(asText(payment.get("name")), List.of()).stream()
                            .anyMatch(invoiceId -> party.equals(invoiceCustomers.get(invoiceId)));
                })
                .toList();
    }

    /** Latest submitted payment date per invoice, used to judge on-time settlement. */
    private Map<String, LocalDate> resolveInvoiceSettlementDates(
            List<Map<String, Object>> payments,
            Map<String, List<String>> allocations) {
        Map<String, LocalDate> settlementDates = new LinkedHashMap<>();
        for (Map<String, Object> payment : payments) {
            LocalDate postingDate = parseIsoDate(asText(payment.get("posting_date")));
            if (postingDate == null) {
                continue;
            }
            for (String invoiceId : allocations.getOrDefault(asText(payment.get("name")), List.of())) {
                LocalDate current = settlementDates.get(invoiceId);
                if (current == null || postingDate.isAfter(current)) {
                    settlementDates.put(invoiceId, postingDate);
                }
            }
        }
        return settlementDates;
    }

    /**
     * ERPNext stores {@code due_date} at invoice creation from the branch's credit days.
     * The fallback recomputes it for older or imported invoices; {@code creditDays == 0} is
     * valid and means the invoice is due on its posting date.
     */
    private LocalDate resolveInvoiceDueDate(Map<String, Object> invoice, int creditDays) {
        LocalDate dueDate = parseIsoDate(asText(invoice.get("due_date")));
        if (dueDate != null) {
            return dueDate;
        }
        LocalDate postingDate = parseIsoDate(asText(invoice.get("posting_date")));
        return postingDate == null ? null : postingDate.plusDays(Math.max(creditDays, 0));
    }

    private Integer agingDaysPastDue(LocalDate dueDate, LocalDate asOfDate) {
        return dueDate == null ? null : (int) (asOfDate.toEpochDay() - dueDate.toEpochDay());
    }

    private String agingBucketKey(Integer daysPastDue) {
        if (daysPastDue == null || daysPastDue <= 0) {
            return "notDue";
        }
        if (daysPastDue <= 7) {
            return "d1_7";
        }
        if (daysPastDue <= 15) {
            return "d8_15";
        }
        if (daysPastDue <= 30) {
            return "d16_30";
        }
        return "d30Plus";
    }

    private LocalDate resolveAsOfDate(String raw) {
        LocalDate today = LocalDate.now();
        LocalDate parsed = parseIsoDate(raw == null ? "" : raw.trim());
        if (parsed == null) {
            return today;
        }
        return parsed.isAfter(today) ? today : parsed;
    }

    private LocalDate parseIsoDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException ex) {
            return null;
        }
    }

    private List<Map<String, Object>> agingBucketDescriptors() {
        return AGING_BUCKETS.entrySet().stream()
                .map(entry -> Map.<String, Object>of("key", entry.getKey(), "label", entry.getValue()))
                .toList();
    }

    private Map<String, Object> buildCoverage(int settledInvoices, int referencedInvoices) {
        double coveragePct = settledInvoices == 0 ? 0.0 : round((referencedInvoices * 100.0) / settledInvoices);
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("settledSubmittedInvoices", settledInvoices);
        coverage.put("referencedSettledInvoices", referencedInvoices);
        coverage.put("onTimeCoveragePct", coveragePct);
        coverage.put("onTimeReliable", referencedInvoices > 0 && coveragePct >= ONTIME_MIN_COVERAGE_PCT);
        coverage.put("note", "On-time % uses only settled invoices carrying a Payment Entry reference row.");
        return coverage;
    }

    private Map<String, Object> agingThresholds() {
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("defaulterMinPct", RISK_DEFAULTER_MIN_PCT);
        thresholds.put("watchMinPct", RISK_WATCH_MIN_PCT);
        thresholds.put("hardDefaulterDays", RISK_HARD_DEFAULTER_DAYS);
        thresholds.put("ageTierDays", List.of(RISK_AGE_TIER_DAYS[0], RISK_AGE_TIER_DAYS[1], RISK_AGE_TIER_DAYS[2]));
        thresholds.put("amountTierRupees", List.of(RISK_AMOUNT_TIER_RUPEES[0], RISK_AMOUNT_TIER_RUPEES[1], RISK_AMOUNT_TIER_RUPEES[2]));
        thresholds.put("onTimeTierPct", List.of(RISK_ONTIME_TIER_PCT[0], RISK_ONTIME_TIER_PCT[1], RISK_ONTIME_TIER_PCT[2]));
        thresholds.put("minOverdueToFlag", RISK_MIN_OVERDUE_TO_FLAG);
        thresholds.put("onTimeGraceDays", ONTIME_GRACE_DAYS);
        thresholds.put("onTimeMinCoveragePct", ONTIME_MIN_COVERAGE_PCT);
        return thresholds;
    }

    private long countTier(List<Map<String, Object>> rows, String tier) {
        return rows.stream().filter(row -> tier.equals(asText(row.get("riskTier")))).count();
    }

    private List<String> rankBranches(List<Map<String, Object>> rows, String primaryKey, String secondaryKey) {
        return rows.stream()
                .sorted(agingRowComparator(primaryKey, secondaryKey))
                .map(row -> asText(row.get("branchId")))
                .toList();
    }

    private Comparator<Map<String, Object>> agingRowComparator(String primaryKey, String secondaryKey) {
        return Comparator
                .comparingDouble((Map<String, Object> row) -> asDouble(row.get(primaryKey))).reversed()
                .thenComparing(Comparator.comparingDouble((Map<String, Object> row) -> asDouble(row.get(secondaryKey))).reversed())
                .thenComparing(row -> asText(row.get("branchName")));
    }

    /** Overdue invoices first (longest past due), then not-yet-due, then drafts. */
    private Comparator<Map<String, Object>> agingInvoiceComparator() {
        return Comparator
                .comparingInt((Map<String, Object> row) -> "DRAFT".equals(asText(row.get("stage"))) ? 1 : 0)
                .thenComparing(Comparator.comparingInt((Map<String, Object> row) -> {
                    Object days = row.get("daysPastDue");
                    return days == null ? Integer.MIN_VALUE : asInt(days);
                }).reversed())
                .thenComparing(row -> asText(row.get("postingDate")));
    }

    private List<Map<String, Object>> filterByPostingDate(List<Map<String, Object>> invoices, LedgerRange range) {
        if (invoices == null || invoices.isEmpty() || range == null || !range.hasBounds()) {
            return invoices == null ? List.of() : invoices;
        }
        return invoices.stream()
                .filter(invoice -> withinDateRange(asText(invoice.get("posting_date")), range.fromInclusive, range.toInclusive))
                .toList();
    }

    private List<Map<String, Object>> filterPaymentsByPostingDate(List<Map<String, Object>> payments, LedgerRange range) {
        if (payments == null || payments.isEmpty() || range == null || !range.hasBounds()) {
            return payments == null ? List.of() : payments;
        }
        return payments.stream()
                .filter(payment -> withinDateRange(asText(payment.get("posting_date")), range.fromInclusive, range.toInclusive))
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

    private double normalizedBranchClosingBalance(
            double openingBalance,
            double rawClosingBalance,
            List<Map<String, Object>> categorySummary) {
        if (categorySummary == null || categorySummary.isEmpty()) {
            return normalizeCategoryBalance(rawClosingBalance);
        }
        // Category balances now represent per-category closing balances (all history up to
        // the closing date). Their sum equals the overall branch closing balance.
        double total = 0.0;
        for (Map<String, Object> row : categorySummary) {
            total += normalizeCategoryBalance(asDouble(row.get("balance")));
        }
        return normalizeCategoryBalance(total);
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
        List<Map<String, Object>> payments = filterPaymentsByPostingDate(fetchCustomerCategoryPayments(null), range);
        List<Map<String, Object>> adjustmentNotes = filterAdjustmentNotesByPostingDate(fetchApprovedCustomerAdjustmentNotes(null, null), range);
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
            double dueBase = invoiceLedgerAmount(invoice);
            if (dueBase <= 0) {
                continue;
            }
            Map<String, Double> shares = allocateInvoiceShares(invoice, dueBase, resolver);
            if (shares.isEmpty()) {
                shares = Map.of(resolveInvoiceCategory(invoice), dueBase);
            }
            Map<String, Double> branchTotals = totals.computeIfAbsent(customerId, key -> new LinkedHashMap<>());
            for (Map.Entry<String, Double> entry : shares.entrySet()) {
                branchTotals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        for (Map<String, Object> payment : payments) {
            if (!isSubmitted(payment)) {
                continue;
            }
            String branchId = asText(payment.get("party"));
            String category = asText(payment.get("aas_category"));
            double amount = round(resolvePaymentAmount(payment));
            if (!hasText(branchId) || !hasText(category) || amount <= 0) {
                continue;
            }
            Map<String, Double> branchTotals = totals.computeIfAbsent(branchId, key -> new LinkedHashMap<>());
            branchTotals.merge(category, -amount, Double::sum);
        }
        for (Map<String, Object> note : adjustmentNotes) {
            if (asInt(note.get("docstatus")) != 1) {
                continue;
            }
            String branchId = asText(note.get(AdjustmentNoteErpService.FIELD_PARTY));
            String category = asText(note.get(AdjustmentNoteErpService.FIELD_CATEGORY));
            double netChange = round(adjustmentNetChange(note));
            if (!hasText(branchId) || !hasText(category) || netChange == 0.0) {
                continue;
            }
            Map<String, Double> branchTotals = totals.computeIfAbsent(branchId, key -> new LinkedHashMap<>());
            branchTotals.merge(category, netChange, Double::sum);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> branchEntry : totals.entrySet()) {
            String branchId = branchEntry.getKey();
            String branchName = branchNames.getOrDefault(branchId, branchId);
            for (Map.Entry<String, Double> categoryEntry : branchEntry.getValue().entrySet()) {
                double amount = normalizeCategoryBalance(categoryEntry.getValue());
                if (amount <= 0) {
                    continue;
                }
                rows.add(Map.of(
                        "branchId", branchId,
                        "branchName", branchName,
                        "category", categoryEntry.getKey(),
                        "amount", amount));
            }
        }
        return rows.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> asText(row.get("branchName")))
                        .thenComparing(row -> asText(row.get("category"))))
                .toList();
    }

    private List<Map<String, Object>> buildCategoryBalanceSummary(List<Map<String, Object>> invoices, List<Map<String, Object>> payments, List<Map<String, Object>> adjustmentNotes) {
        if (invoices == null || invoices.isEmpty()) {
            invoices = List.of();
        }
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Double> activityTotals = new LinkedHashMap<>();
        Set<String> categoriesWithHistory = new LinkedHashSet<>();
        for (Map<String, Object> invoiceRow : invoices) {
            String invoiceId = asText(invoiceRow.get("name"));
            if (!hasText(invoiceId) || asInt(invoiceRow.get("docstatus")) == 2) {
                continue;
            }
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(SALES_INVOICE, invoiceId));
            if (invoice.isEmpty()) {
                continue;
            }
            double dueBase = invoiceLedgerAmount(invoice);
            if (dueBase <= 0) {
                continue;
            }
            List<Map<String, Object>> items = childItems(invoice.get("items"));
            if (items.isEmpty()) {
                String invoiceCategory = resolveInvoiceCategory(invoice);
                categoriesWithHistory.add(invoiceCategory);
                totals.merge(invoiceCategory, dueBase, Double::sum);
                activityTotals.merge(invoiceCategory, dueBase, Double::sum);
                continue;
            }
            List<LineWeight> weights = new ArrayList<>();
            double totalWeight = 0.0;
            for (Map<String, Object> item : items) {
                double amount = asDouble(firstNonNull(item.get("base_amount"), firstNonNull(item.get("amount"), item.get("net_amount"))));
                if (amount <= 0) {
                    continue;
                }
                String group = resolveLedgerItemCategory(invoice, item, resolver);
                if (!hasText(group)) {
                    group = "Uncategorized";
                }
                categoriesWithHistory.add(group);
                weights.add(new LineWeight(group, amount));
                totalWeight += amount;
            }
            if (weights.isEmpty() || totalWeight <= 0) {
                String invoiceCategory = resolveInvoiceCategory(invoice);
                categoriesWithHistory.add(invoiceCategory);
                totals.merge(invoiceCategory, dueBase, Double::sum);
                activityTotals.merge(invoiceCategory, dueBase, Double::sum);
                continue;
            }
            for (LineWeight weight : weights) {
                double share = dueBase * (weight.amount() / totalWeight);
                totals.merge(weight.group(), share, Double::sum);
                activityTotals.merge(weight.group(), share, Double::sum);
            }
        }
        if (payments != null) {
            for (Map<String, Object> payment : payments) {
                if (!isSubmitted(payment)) {
                    continue;
                }
                String category = asText(payment.get("aas_category"));
                double amount = round(resolvePaymentAmount(payment));
                if (!hasText(category) || amount <= 0) {
                    continue;
                }
                categoriesWithHistory.add(category);
                totals.merge(category, -amount, Double::sum);
            }
        }
        if (adjustmentNotes != null) {
            for (Map<String, Object> note : adjustmentNotes) {
                if (asInt(note.get("docstatus")) != 1) {
                    continue;
                }
                String category = asText(note.get(AdjustmentNoteErpService.FIELD_CATEGORY));
                double netChange = round(adjustmentNetChange(note));
                if (!hasText(category) || netChange == 0.0) {
                    continue;
                }
                categoriesWithHistory.add(category);
                totals.merge(category, netChange, Double::sum);
            }
        }
        Set<String> categories = new LinkedHashSet<>();
        categories.addAll(categoriesWithHistory);
        categories.addAll(totals.keySet());
        categories.addAll(activityTotals.keySet());
        return categories.stream()
                .filter(this::hasText)
                .map(category -> {
                    double activity = round(activityTotals.getOrDefault(category, 0.0));
                    double balance = normalizeCategoryBalance(totals.getOrDefault(category, 0.0));
                    return Map.<String, Object>of(
                            "category", category,
                            "amount", activity,
                            "balance", balance);
                })
                .filter(row -> Math.abs(asDouble(row.get("amount"))) > 0.009
                        || Math.abs(asDouble(row.get("balance"))) > 0.009
                        || categoriesWithHistory.contains(asText(row.get("category"))))
                .sorted((left, right) -> Double.compare(Math.abs(asDouble(right.get("balance"))), Math.abs(asDouble(left.get("balance")))))
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
            double dueBase = invoiceLedgerAmount(invoice);
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

    private List<Map<String, Object>> fetchCustomerPaymentsLinkedToCategoryInvoices(
            String branchId,
            List<Map<String, Object>> invoices,
            String categoryId) {
        Set<String> categoryInvoiceIds = resolveCategoryInvoiceIds(invoices, categoryId);
        if (categoryInvoiceIds.isEmpty()) {
            return List.of();
        }
        return fetchCustomerPayments(branchId, invoices).stream()
                .map(payment -> paymentAllocationForInvoices(payment, categoryInvoiceIds))
                .filter(payment -> round(resolvePaymentAmount(payment)) > 0)
                .toList();
    }

    private Set<String> resolveCategoryInvoiceIds(List<Map<String, Object>> invoices, String categoryId) {
        if (invoices == null || invoices.isEmpty() || !hasText(categoryId)) {
            return Set.of();
        }
        ItemGroupResolver resolver = new ItemGroupResolver(erpNextClient);
        Set<String> invoiceIds = new LinkedHashSet<>();
        for (Map<String, Object> invoiceRow : invoices) {
            String invoiceId = asText(invoiceRow.get("name"));
            if (!hasText(invoiceId) || asInt(invoiceRow.get("docstatus")) == 2) {
                continue;
            }
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(SALES_INVOICE, invoiceId));
            if (invoice.isEmpty()) {
                continue;
            }
            double dueBase = invoiceLedgerAmount(invoice);
            if (dueBase <= 0) {
                continue;
            }
            Map<String, Double> shares = allocateInvoiceShares(invoice, dueBase, resolver);
            if (shares.getOrDefault(categoryId, 0.0) > 0) {
                invoiceIds.add(invoiceId);
            }
        }
        return invoiceIds;
    }

    private Map<String, Object> paymentAllocationForInvoices(Map<String, Object> payment, Set<String> invoiceIds) {
        if (payment == null || invoiceIds == null || invoiceIds.isEmpty()) {
            return Map.of();
        }
        String paymentName = asText(payment.get("name"));
        if (!hasText(paymentName)) {
            return Map.of();
        }
        Map<String, Object> paymentDoc = unwrap(erpNextClient.getResource(PAYMENT_ENTRY, paymentName));
        List<Map<String, Object>> references = childItems(paymentDoc.get("references"));
        double allocated = 0.0;
        boolean matched = false;
        for (Map<String, Object> reference : references) {
            if (!SALES_INVOICE.equals(asText(reference.get("reference_doctype")))
                    || !invoiceIds.contains(asText(reference.get("reference_name")))) {
                continue;
            }
            matched = true;
            allocated += asDouble(firstNonNull(reference.get("allocated_amount"), reference.get("base_allocated_amount")));
        }
        if (!matched) {
            return Map.of();
        }
        double amount = allocated > 0 ? allocated : resolvePaymentAmount(payment);
        if (amount <= 0) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(payment);
        copy.put("paid_amount", round(amount));
        copy.put("received_amount", 0.0);
        return copy;
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

    private List<Map<String, Object>> buildCategoryLedgerEntriesFromAdjustments(List<Map<String, Object>> notes) {
        if (notes == null || notes.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> note : notes) {
            if (asInt(note.get("docstatus")) != 1) {
                continue;
            }
            double amount = round(asDouble(note.get(AdjustmentNoteErpService.FIELD_AMOUNT)));
            double netChange = round(adjustmentNetChange(note));
            if (amount <= 0 || netChange == 0.0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(note.get("posting_date")));
            row.put("voucherType", adjustmentVoucherType(note));
            row.put("voucherNo", asText(note.get("name")));
            row.put("reference", adjustmentReference(note));
            row.put("debit", netChange > 0 ? amount : 0.0);
            row.put("credit", netChange < 0 ? amount : 0.0);
            row.put("netChange", netChange);
            entries.add(row);
        }
        return entries;
    }

    private List<Map<String, Object>> mergeAndFinalizeLedger(
            List<Map<String, Object>> invoiceEntries,
            List<Map<String, Object>> paymentEntries,
            List<Map<String, Object>> adjustmentEntries,
            boolean includeRunningBalance) {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (invoiceEntries != null) {
            entries.addAll(invoiceEntries);
        }
        if (paymentEntries != null) {
            entries.addAll(paymentEntries);
        }
        if (adjustmentEntries != null) {
            entries.addAll(adjustmentEntries);
        }
        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> ledgerVoucherSortOrder(asText(row.get("voucherType"))))
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

    private int ledgerVoucherSortOrder(String voucherType) {
        String normalized = voucherType == null ? "" : voucherType.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("invoice")) {
            return 0;
        }
        if (normalized.contains("credit note") || normalized.contains("debit note")) {
            return 1;
        }
        if (normalized.contains("payment")) {
            return 2;
        }
        return 3;
    }

    private Map<String, Double> allocateInvoiceShares(
            Map<String, Object> invoice,
            double dueBase,
            ItemGroupResolver resolver) {
        List<Map<String, Object>> items = childItems(invoice.get("items"));
        if (items.isEmpty() || dueBase <= 0) {
            return dueBase > 0 ? Map.of(resolveInvoiceCategory(invoice), dueBase) : Map.of();
        }
        Map<String, Double> weights = new HashMap<>();
        double totalWeight = 0.0;
        for (Map<String, Object> item : items) {
            double amount = asDouble(firstNonNull(item.get("base_amount"), firstNonNull(item.get("amount"), item.get("net_amount"))));
            if (amount <= 0) {
                continue;
            }
            String group = resolveLedgerItemCategory(invoice, item, resolver);
            if (!hasText(group)) {
                group = "Uncategorized";
            }
            weights.merge(group, amount, Double::sum);
            totalWeight += amount;
        }
        if (weights.isEmpty() || totalWeight <= 0) {
            return Map.of(resolveInvoiceCategory(invoice), dueBase);
        }
        Map<String, Double> shares = new HashMap<>();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            shares.put(entry.getKey(), dueBase * (entry.getValue() / totalWeight));
        }
        return shares;
    }

    private String resolveLedgerItemCategory(
            Map<String, Object> invoice,
            Map<String, Object> item,
            ItemGroupResolver resolver) {
        String group = asText(firstNonNull(item.get("item_group"), item.get("item_group_name")));
        group = normalizeItemGroup(group);
        if (!hasText(group)) {
            group = resolver.resolve(asText(item.get("item_code")));
        }
        group = normalizeItemGroup(group);
        if (!hasText(group)) {
            group = resolveInvoiceCategory(invoice);
        }
        return group;
    }

    private String resolveInvoiceCategory(Map<String, Object> invoice) {
        String category = normalizeItemGroup(asText(invoice == null ? null : invoice.get("aas_category")));
        return hasText(category) ? category : "Uncategorized";
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
        List<Map<String, Object>> adjustmentNotes = fetchApprovedCustomerAdjustmentNotes(null, null);

        for (Map<String, Object> invoice : invoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            String branchId = asText(invoice.get("customer"));
            double debit = invoiceLedgerAmount(invoice);
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
        for (Map<String, Object> note : adjustmentNotes) {
            if (asInt(note.get("docstatus")) != 1) {
                continue;
            }
            String branchId = asText(note.get(AdjustmentNoteErpService.FIELD_PARTY));
            double amount = round(asDouble(note.get(AdjustmentNoteErpService.FIELD_AMOUNT)));
            double netChange = round(adjustmentNetChange(note));
            if (!hasText(branchId) || amount <= 0 || netChange == 0.0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", asText(note.get("posting_date")));
            row.put("branchId", branchId);
            row.put("branchName", branchNames.getOrDefault(branchId, branchId));
            row.put("voucherType", adjustmentVoucherType(note));
            row.put("voucherNo", asText(note.get("name")));
            row.put("reference", adjustmentReference(note));
            row.put("debit", netChange > 0 ? amount : 0.0);
            row.put("credit", netChange < 0 ? amount : 0.0);
            row.put("netChange", netChange);
            entries.add(row);
        }

        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> asText(row.get("branchName")))
                .thenComparing(row -> ledgerVoucherSortOrder(asText(row.get("voucherType"))))
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
            List<Map<String, Object>> payments,
            List<Map<String, Object>> adjustmentNotes) {
        String branchId = asText(branch.get("name"));
        List<Map<String, Object>> branchOrders = orders.stream().filter(order -> branchId.equals(asText(order.get("customer")))).toList();
        List<Map<String, Object>> branchInvoices = invoices.stream().filter(invoice -> branchId.equals(asText(invoice.get("customer")))).toList();
        List<Map<String, Object>> branchPayments = payments.stream().filter(payment -> branchId.equals(asText(payment.get("party")))).toList();
        List<Map<String, Object>> branchAdjustmentNotes = adjustmentNotes.stream()
                .filter(note -> branchId.equals(asText(note.get(AdjustmentNoteErpService.FIELD_PARTY))))
                .toList();
        List<Map<String, Object>> ledger = buildLedgerEntries(branchInvoices, branchPayments, branchAdjustmentNotes);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("branchId", branchId);
        row.put("branchName", preferredBranchName(branch));
        row.put("pendingOrders", branchOrders.stream().filter(order -> OPEN_STATUSES.contains(asText(order.get("aas_status")))).count());
        row.put("awaitingVendorAssignment", branchOrders.stream().filter(order -> "DRAFT".equals(asText(order.get("aas_status")))).count());
        row.put("awaitingVendorResponse", branchOrders.stream().filter(order -> "VENDOR_ASSIGNED".equals(asText(order.get("aas_status"))) || "VENDOR_PDF_RECEIVED".equals(asText(order.get("aas_status")))).count());
        row.put("inProgress", branchOrders.stream().filter(order -> "VENDOR_BILL_CAPTURED".equals(asText(order.get("aas_status"))) || "SELL_ORDER_CREATED".equals(asText(order.get("aas_status")))).count());
        row.put("openReceivableAmount", openReceivableAmount(ledger));
        row.put("lastActivity", resolveLastActivity(
                branchOrders.stream().map(order -> asText(order.get("modified"))).toList(),
                branchInvoices.stream().map(invoice -> asText(invoice.get("modified"))).toList(),
                branchPayments.stream().map(payment -> asText(payment.get("modified"))).toList(),
                branchAdjustmentNotes.stream().map(note -> asText(note.get("modified"))).toList()));
        row.put("location", asText(branch.get("aas_branch_location")));
        row.put("ledgerBalance", getLedgerBalance(ledger));
        row.put("paymentCollectionRate", calculatePaymentCollectionRate(branchInvoices, branchPayments));
        return row;
    }

    private List<Map<String, Object>> buildLedgerEntries(List<Map<String, Object>> invoices, List<Map<String, Object>> payments, List<Map<String, Object>> adjustmentNotes) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            if (asInt(invoice.get("docstatus")) == 2) {
                continue;
            }
            double debit = invoiceLedgerAmount(invoice);
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
        if (adjustmentNotes != null) {
            for (Map<String, Object> note : adjustmentNotes) {
                if (asInt(note.get("docstatus")) != 1) {
                    continue;
                }
                double amount = round(asDouble(note.get(AdjustmentNoteErpService.FIELD_AMOUNT)));
                double netChange = round(adjustmentNetChange(note));
                if (amount <= 0 || netChange == 0.0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("date", asText(note.get("posting_date")));
                row.put("voucherType", adjustmentVoucherType(note));
                row.put("voucherNo", asText(note.get("name")));
                row.put("reference", adjustmentReference(note));
                row.put("debit", netChange > 0 ? amount : 0.0);
                row.put("credit", netChange < 0 ? amount : 0.0);
                row.put("netChange", netChange);
                entries.add(row);
            }
        }
        entries.sort(Comparator
                .comparing((Map<String, Object> row) -> asText(row.get("date")))
                .thenComparing(row -> ledgerVoucherSortOrder(asText(row.get("voucherType"))))
                .thenComparing(row -> asText(row.get("voucherType")))
                .thenComparing(row -> asText(row.get("voucherNo"))));
        double running = 0.0;
        for (Map<String, Object> entry : entries) {
            running = round(running + asDouble(entry.get("netChange")));
            entry.put("runningBalance", running);
        }
        return entries;
    }

    private List<Map<String, Object>> fetchApprovedCustomerAdjustmentNotes(String branchId, String categoryId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields",
                "[\"name\",\"posting_date\",\"docstatus\",\"modified\",\""
                        + AdjustmentNoteErpService.FIELD_PARTY + "\",\""
                        + AdjustmentNoteErpService.FIELD_PARTY_TYPE + "\",\""
                        + AdjustmentNoteErpService.FIELD_CATEGORY + "\",\""
                        + AdjustmentNoteErpService.FIELD_DIRECTION + "\",\""
                        + AdjustmentNoteErpService.FIELD_AMOUNT + "\",\""
                        + AdjustmentNoteErpService.FIELD_REASON + "\",\""
                        + AdjustmentNoteErpService.FIELD_REFERENCE_INVOICE + "\"]");
        params.put("order_by", "posting_date asc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("docstatus", "=", "1"));
        filters.add(List.of(AdjustmentNoteErpService.FIELD_REVIEW_STATUS, "=", AdjustmentNoteErpService.STATUS_APPROVED));
        filters.add(List.of(AdjustmentNoteErpService.FIELD_PARTY_TYPE, "=", AdjustmentNoteErpService.PARTY_CUSTOMER));
        if (hasText(branchId)) {
            filters.add(List.of(AdjustmentNoteErpService.FIELD_PARTY, "=", branchId));
        }
        if (hasText(categoryId)) {
            filters.add(List.of(AdjustmentNoteErpService.FIELD_CATEGORY, "=", categoryId));
        }
        params.put("filters", toJson(filters));
        return listResourcesPaged(JOURNAL_ENTRY, params);
    }

    private List<Map<String, Object>> filterAdjustmentNotesByPostingDate(List<Map<String, Object>> notes, LedgerRange range) {
        if (notes == null || notes.isEmpty() || range == null || !range.hasBounds()) {
            return notes == null ? List.of() : notes;
        }
        return notes.stream()
                .filter(note -> withinDateRange(asText(note.get("posting_date")), range.fromInclusive, range.toInclusive))
                .toList();
    }

    private double adjustmentNetChange(Map<String, Object> note) {
        return adjustmentNoteErpService.signedImpact(
                AdjustmentNoteErpService.PARTY_CUSTOMER,
                asText(note.get(AdjustmentNoteErpService.FIELD_DIRECTION)),
                adjustmentNoteErpService.asDecimal(note.get(AdjustmentNoteErpService.FIELD_AMOUNT))).doubleValue();
    }

    private String adjustmentVoucherType(Map<String, Object> note) {
        String direction = adjustmentNoteErpService.normalizeDirection(note.get(AdjustmentNoteErpService.FIELD_DIRECTION));
        return AdjustmentNoteErpService.DIRECTION_TAKE.equals(direction) ? "Debit Note" : "Credit Note";
    }

    private String adjustmentReference(Map<String, Object> note) {
        String invoiceId = asText(note.get(AdjustmentNoteErpService.FIELD_REFERENCE_INVOICE));
        if (hasText(invoiceId)) {
            return invoiceId;
        }
        return asText(note.get(AdjustmentNoteErpService.FIELD_REASON));
    }

    private double resolveInvoiceDueAmount(Map<String, Object> invoice) {
        if (invoice == null) {
            return 0.0;
        }
        int docstatus = asInt(invoice.get("docstatus"));
        if (docstatus == 0) {
            return invoiceLedgerAmount(invoice);
        }
        if (docstatus == 1) {
            double outstanding = asDouble(invoice.get("outstanding_amount"));
            return outstanding > 0 ? outstanding : 0.0;
        }
        return 0.0;
    }

    private double invoiceLedgerAmount(Map<String, Object> invoice) {
        if (invoice == null) {
            return 0.0;
        }
        double roundedTotal = asDouble(invoice.get("rounded_total"));
        if (roundedTotal > 0) {
            return round(roundedTotal);
        }
        double grandTotal = asDouble(invoice.get("grand_total"));
        double roundingAdjustment = asDouble(firstNonNull(
                invoice.get("aas_rounding_adjustment"),
                invoice.get("rounding_adjustment")));
        if (Math.abs(roundingAdjustment) > 0.0001) {
            return round(grandTotal + roundingAdjustment);
        }
        double outstandingAmount = asDouble(invoice.get("outstanding_amount"));
        if (asInt(invoice.get("docstatus")) == 0
                && outstandingAmount > 0
                && Math.abs(outstandingAmount - grandTotal) < 1.0) {
            return round(outstandingAmount);
        }
        return round(grandTotal);
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
        params.put(
                "fields",
                "[\"name\",\"customer\",\"posting_date\",\"due_date\",\"grand_total\",\"rounded_total\",\"rounding_adjustment\",\"aas_rounding_adjustment\",\"outstanding_amount\",\"status\",\"modified\",\"creation\",\"docstatus\","
                        + "\"aas_invoice_version_status\",\"aas_replaced_by\",\"aas_source_sales_order\"]");
        params.put("order_by", "posting_date asc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("docstatus", "!=", "2"));
        if (hasText(branchId)) {
            filters.add(List.of("customer", "=", branchId));
        }
        params.put("filters", toJson(filters));
        List<Map<String, Object>> rows = listResourcesPaged(SALES_INVOICE, params).stream()
                .filter(invoice -> !"Cancelled".equalsIgnoreCase(asText(invoice.get("status"))))
                .filter(invoice -> !INVOICE_VERSION_OLD.equalsIgnoreCase(asText(invoice.get("aas_invoice_version_status"))))
                .filter(invoice -> asText(invoice.get("aas_replaced_by")).isBlank())
                .toList();
        return dedupeLatestBySourceOrder(rows);
    }

    private List<Map<String, Object>> dedupeLatestBySourceOrder(List<Map<String, Object>> invoices) {
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

    private List<Map<String, Object>> fetchCustomerCategoryPayments(String branchId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\",\"payment_type\",\"reference_no\",\"modified\",\"creation\",\"docstatus\",\"aas_category\"]");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", "Customer"));
        filters.add(List.of("docstatus", "=", "1"));
        if (hasText(branchId)) {
            filters.add(List.of("party", "=", branchId));
        }
        params.put("filters", toJson(filters));
        params.put("order_by", "posting_date asc");
        return listResourcesPaged(PAYMENT_ENTRY, params).stream()
                .filter(payment -> hasText(payment.get("aas_category")))
                .toList();
    }

    private List<Map<String, Object>> mergePayments(List<Map<String, Object>> primary, List<Map<String, Object>> secondary) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        if (primary != null) {
            for (Map<String, Object> payment : primary) {
                String name = asText(payment == null ? null : payment.get("name"));
                if (hasText(name)) {
                    byName.put(name, payment);
                }
            }
        }
        if (secondary != null) {
            for (Map<String, Object> payment : secondary) {
                String name = asText(payment == null ? null : payment.get("name"));
                if (hasText(name)) {
                    byName.putIfAbsent(name, payment);
                }
            }
        }
        return List.copyOf(byName.values());
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
        double invoiced = invoices.stream().mapToDouble(this::invoiceLedgerAmount).sum();
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

    private double openReceivableAmount(List<Map<String, Object>> ledgerEntries) {
        return round(Math.max(0.0, getLedgerBalance(ledgerEntries)));
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

    private double normalizeCategoryBalance(double value) {
        double rounded = round(value);
        return Math.abs(rounded) <= CATEGORY_SETTLEMENT_TOLERANCE ? 0.0 : rounded;
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
