package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.ErpSetupProperties;
import feign.FeignException;
import java.math.BigDecimal;
import com.aas.mw.dto.InvoiceRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final String DOCTYPE = "Sales Invoice";
    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String PAYMENT_LEDGER_ENTRY = "Payment Ledger Entry";
    private static final String INVOICE_VERSION_OLD = "OLD";
    private static final String OPENING_REFERENCE_PREFIX = "AAS-OPENING-";
    private static final Pattern PAYMENT_ENTRY_ID_PATTERN = Pattern.compile("(ACC-PAY-\\d{4}-\\d+)");
    private static final Pattern FILTER_TOKEN_PATTERN = Pattern.compile("\\[\\[[^\\]]*?\"(%s)\"[^\\]]*?\\]\\]");

    private final ErpNextClient erpNextClient;
    private final PaymentDueService paymentDueService;
    private final BranchOpsService branchOpsService;
    private final String gstTemplate;
    private final String erpSetupUsername;
    private final String erpSetupPassword;

    public InvoiceService(
            ErpNextClient erpNextClient,
            PaymentDueService paymentDueService,
            BranchOpsService branchOpsService,
            @Value("${app.billing.gst-template:}") String gstTemplate,
            ErpSetupProperties erpSetupProperties) {
        this.erpNextClient = erpNextClient;
        this.paymentDueService = paymentDueService;
        this.branchOpsService = branchOpsService;
        this.gstTemplate = gstTemplate == null ? "" : gstTemplate.trim();
        this.erpSetupUsername = erpSetupProperties.getFullName();
        this.erpSetupPassword = erpSetupProperties.getPassword();
    }

    public Map<String, Object> createInvoice(InvoiceRequest request) {
        validateFields(request);
        Map<String, Object> payload = new HashMap<>(request.getFields());
        boolean applyGst = readBoolean(payload.remove("apply_gst"), true);
        if (payload.containsKey("applyGst")) {
            applyGst = readBoolean(payload.remove("applyGst"), applyGst);
        }
        normalizeRounding(payload);
        if (applyGst) {
            applyItemTaxTemplates(payload);
        }
        if (applyGst) {
            if (!gstTemplate.isBlank()
                    && !payload.containsKey("taxes_and_charges")
                    && !payload.containsKey("taxes")) {
                payload.put("taxes_and_charges", gstTemplate);
            }
        } else {
            payload.remove("taxes_and_charges");
            payload.remove("taxes");
            payload.remove("tax_category");
        }

        if (payload.containsKey("posting_date")) {
            payload.putIfAbsent("set_posting_time", 1);
        }
        String customer = asText(payload.get("customer"));
        int creditDays = resolveCreditDays(customer);
        if (creditDays > 0 && !payload.containsKey("due_date")) {
            LocalDate base = resolveBaseDate(payload.get("posting_date"));
            payload.put("due_date", base.plusDays(creditDays).toString());
        }

        String company = asText(payload.get("company"));
        if (!company.isBlank() && !hasValue(payload.get("currency"))) {
            String currency = resolveCompanyCurrency(company);
            if (!currency.isBlank()) {
                payload.put("currency", currency);
            }
        }

        String categoryId = asText(payload.get("aas_category"));
        double previousDue = 0.0;
        boolean hasCategory = !customer.isBlank() && !categoryId.isBlank();
        if (hasCategory) {
            try {
                Map<String, Object> due = paymentDueService.dueByCategory("Customer", customer, categoryId);
                Object amount = due.get("dueAmount");
                if (amount instanceof BigDecimal decimal) {
                    previousDue = decimal.doubleValue();
                } else {
                    previousDue = asDouble(amount);
                }
                payload.put("aas_previous_due", round(previousDue));
            } catch (Exception ignore) {
                // Never block invoice creation if due snapshot fails.
                payload.put("aas_previous_due", 0);
            }
        }

        Map<String, Object> created = erpNextClient.createResource(DOCTYPE, payload);
        Map<String, Object> createdDoc = unwrap(created);
        String invoiceId = asText(createdDoc.get("name"));
        if (!invoiceId.isBlank() && hasCategory) {
            Map<String, Object> invoiceDoc = createdDoc;
            if (!hasValue(invoiceDoc.get("grand_total"))) {
                invoiceDoc = unwrap(erpNextClient.getResource(DOCTYPE, invoiceId));
            }
            double invoiceTotal = asDouble(invoiceDoc.get("grand_total"));
            double roundingAdjustment = asDouble(firstNonNull(invoiceDoc.get("aas_rounding_adjustment"), invoiceDoc.get("rounding_adjustment")));
            double grandTotal = invoiceTotal + roundingAdjustment;
            double currentPending = previousDue + grandTotal;
            try {
                erpNextClient.updateResource(DOCTYPE, invoiceId, Map.of(
                        "aas_previous_due", round(previousDue),
                        "aas_current_pending", round(currentPending)));
            } catch (Exception ignore) {
                // Best-effort snapshot fields for print format; don't block creation.
            }
        }
        return created;
    }

    private void normalizeRounding(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        double roundingAdjustment = asDouble(payload.remove("rounding_adjustment"));
        if (Math.abs(roundingAdjustment) <= 0.0001) {
            roundingAdjustment = asDouble(payload.get("aas_rounding_adjustment"));
        }
        if (Math.abs(roundingAdjustment) <= 0.0001) {
            payload.remove("aas_rounding_adjustment");
            return;
        }
        payload.put("aas_rounding_adjustment", roundingAdjustment);
        payload.put("rounding_adjustment", roundingAdjustment);
        payload.putIfAbsent("disable_rounded_total", 0);
    }

    private void applyItemTaxTemplates(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        String company = asText(payload.get("company"));
        if (company.isBlank()) {
            return;
        }
        Object rawItems = payload.get("items");
        if (!(rawItems instanceof List<?> rawList) || rawList.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        String gstAccountHead = ensureGstAccountHead(company);
        if (gstAccountHead.isBlank()) {
            return;
        }
        boolean applied = false;
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            double gstPercent = readGstPercent(item);
            if (gstPercent <= 0) {
                continue;
            }
            item.put("aas_gst_percent", gstPercent);
            item.put("item_tax_template", ensureItemTaxTemplate(company, gstAccountHead, gstPercent));
            applied = true;
        }
        if (applied) {
            payload.remove("taxes_and_charges");
            payload.remove("taxes");
        }
    }

    private double readGstPercent(Map<String, Object> item) {
        double gstPercent = asDouble(item.get("aas_gst_percent"));
        if (gstPercent <= 0) {
            gstPercent = asDouble(item.get("gst_percent"));
        }
        if (gstPercent <= 0) {
            gstPercent = asDouble(item.get("gst"));
        }
        return gstPercent;
    }

    private String ensureGstAccountHead(String company) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"account_name\",\"company\",\"account_type\",\"parent_account\",\"root_type\",\"report_type\"]");
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
                if ("Duties and Taxes".equalsIgnoreCase(asText(account.get("account_name")))
                        && company.equals(asText(account.get("company")))) {
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
                    return asText(unwrap(erpNextClient.createResource("Account", payload)).get("name"));
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
        return asText(unwrap(erpNextClient.createResource("Item Tax Template", payload)).get("name"));
    }

    private String buildGstTemplateTitle(double gstPercent) {
        return "AAS GST " + BigDecimal.valueOf(round(gstPercent)).stripTrailingZeros().toPlainString() + "%";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> stripFilter(Map<String, Object> params, String field) {
        if (params == null || params.isEmpty() || field == null || field.isBlank()) {
            return params;
        }
        Object filters = params.get("filters");
        if (!(filters instanceof String filterList) || filterList.isBlank()) {
            return params;
        }
        String escaped = Pattern.quote(field);
        String tokenPattern = FILTER_TOKEN_PATTERN.pattern().formatted(escaped);
        String updated = filterList.replaceAll(",?" + tokenPattern, "");
        updated = updated.replace("[,", "[").replace(",]", "]");
        Map<String, Object> copy = new HashMap<>(params);
        copy.put("filters", updated);
        return copy;
    }

    public List<Map<String, Object>> listInvoices(String customer, String fromDate, String toDate) {
        return listInvoices("Customer", customer, fromDate, toDate);
    }

    public List<Map<String, Object>> listInvoices(String partyType, String partyId, String fromDate, String toDate) {
        InvoiceContext ctx = resolveInvoiceContext(normalizePartyType(partyType));
        if (ctx.isSupplier()) {
            return listPurchaseInvoices(partyId, fromDate, toDate);
        }
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"customer\",\"company\",\"posting_date\",\"grand_total\",\"net_total\",\"outstanding_amount\",\"status\",\"docstatus\","
                        + "\"modified\",\"creation\",\"aas_source_sales_order\",\"aas_replaced_by\",\"aas_invoice_version_status\","
                        + "\"is_opening\",\"po_no\",\"remarks\",\"aas_category\"]");
        params.put("order_by", "posting_date desc");
        // Without an explicit page length ERPNext defaults to 20 rows, which silently caps the
        // invoice list (and breaks version dedupe). 0 = no limit, so we fetch all matching invoices.
        params.put("limit_page_length", 0);
        List<List<String>> filters = new ArrayList<>();
        if (partyId != null && !partyId.isBlank()) {
            filters.add(List.of("customer", "=", partyId));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            filters.add(List.of("posting_date", ">=", fromDate));
        }
        if (toDate != null && !toDate.isBlank()) {
            filters.add(List.of("posting_date", "<=", toDate));
        }
        // Exclude opening balance invoices from standard invoice listings/metrics.
        filters.add(List.of("is_opening", "!=", "Yes"));
        if (!filters.isEmpty()) {
            params.put("filters", toJson(filters));
        }
        List<Map<String, Object>> baseRows;
        try {
            baseRows = listInvoicesResource(params);
        } catch (Exception ex) {
            // Some ERPNext instances may not allow filtering by is_opening; fall back to filtering client-side.
            Map<String, Object> fallback = stripFilter(params, "is_opening");
            baseRows = fallback == null ? List.of() : listInvoicesResource(fallback);
        }
        List<Map<String, Object>> rows = baseRows.stream()
                .filter(invoice -> asInt(invoice.get("docstatus")) != 2)
                .filter(invoice -> !"Cancelled".equalsIgnoreCase(asText(invoice.get("status"))))
                .filter(invoice -> !INVOICE_VERSION_OLD.equalsIgnoreCase(asText(invoice.get("aas_invoice_version_status"))))
                .filter(invoice -> asText(invoice.get("aas_replaced_by")).isBlank())
                .filter(invoice -> !isOpeningInvoice(invoice))
                .toList();
        return dedupeLatestBySourceOrder(rows);
    }

    private List<Map<String, Object>> listPurchaseInvoices(String supplier, String fromDate, String toDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"supplier\",\"company\",\"posting_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"docstatus\",\"is_opening\",\"bill_no\",\"remarks\",\"aas_category\"]");
        params.put("order_by", "posting_date desc");
        // 0 = no limit; otherwise ERPNext defaults to 20 rows and silently caps the list.
        params.put("limit_page_length", 0);
        List<List<String>> filters = new ArrayList<>();
        if (supplier != null && !supplier.isBlank()) {
            filters.add(List.of("supplier", "=", supplier));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            filters.add(List.of("posting_date", ">=", fromDate));
        }
        if (toDate != null && !toDate.isBlank()) {
            filters.add(List.of("posting_date", "<=", toDate));
        }
        // Exclude cancelled.
        filters.add(List.of("docstatus", "!=", "2"));
        // Exclude opening balance invoices from standard invoice listings/metrics.
        filters.add(List.of("is_opening", "!=", "Yes"));
        params.put("filters", toJson(filters));

        String privilegedSession = operationalReadSession();
        List<Map<String, Object>> rows;
        try {
            rows = privilegedSession == null
                    ? erpNextClient.listResources("Purchase Invoice", params)
                    : erpNextClient.listResourcesWithSession("Purchase Invoice", params, privilegedSession);
        } catch (Exception ex) {
            // Some ERPNext instances may not allow filtering by is_opening; fall back to filtering client-side.
            Map<String, Object> fallback = stripFilter(params, "is_opening");
            if (fallback == null) {
                rows = List.of();
            } else {
                rows = privilegedSession == null
                        ? erpNextClient.listResources("Purchase Invoice", fallback)
                        : erpNextClient.listResourcesWithSession("Purchase Invoice", fallback, privilegedSession);
            }
        }

        return rows.stream()
                .filter(invoice -> asInt(invoice.get("docstatus")) != 2)
                .filter(invoice -> !"Cancelled".equalsIgnoreCase(asText(invoice.get("status"))))
                .filter(invoice -> !isOpeningInvoice(invoice))
                .map(this::mapSupplierToCustomerField)
                .toList();
    }

    private boolean isOpeningInvoice(Map<String, Object> invoice) {
        if (invoice == null || invoice.isEmpty()) {
            return false;
        }
        String opening = asText(invoice.get("is_opening"));
        if (opening.equalsIgnoreCase("Yes") || opening.equals("1") || opening.equalsIgnoreCase("true")) {
            return true;
        }
        String remarks = asText(invoice.get("remarks"));
        if (!remarks.isBlank() && remarks.startsWith(OPENING_REFERENCE_PREFIX)) {
            return true;
        }
        String poNo = asText(invoice.get("po_no"));
        if (!poNo.isBlank() && poNo.startsWith(OPENING_REFERENCE_PREFIX)) {
            return true;
        }
        String billNo = asText(invoice.get("bill_no"));
        return !billNo.isBlank() && billNo.startsWith(OPENING_REFERENCE_PREFIX);
    }

    private Map<String, Object> mapSupplierToCustomerField(Map<String, Object> invoice) {
        if (invoice == null || invoice.isEmpty()) {
            return invoice;
        }
        if (invoice.containsKey("customer")) {
            return invoice;
        }
        Map<String, Object> mapped = new HashMap<>(invoice);
        Object supplier = mapped.get("supplier");
        mapped.put("customer", supplier);
        return mapped;
    }

    public byte[] downloadPdf(String invoiceId, String partyType) {
        String normalized = normalizePartyType(partyType);
        if ("Supplier".equalsIgnoreCase(normalized)) {
            byte[] pdf = erpNextClient.downloadPdf("Purchase Invoice", invoiceId);
            if (pdf == null || pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
                String snippet = pdf == null ? "" : new String(pdf, 0, Math.min(pdf.length, 240));
                throw new IllegalStateException("ERPNext did not return a valid PDF for " + invoiceId + ". " + snippet);
            }
            return pdf;
        }
        return downloadPdf(invoiceId);
    }

    private InvoiceContext resolveInvoiceContext(String partyType) {
        boolean supplier = "Supplier".equalsIgnoreCase(partyType);
        return new InvoiceContext(supplier);
    }

    private String normalizePartyType(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.equalsIgnoreCase("Supplier") || normalized.equalsIgnoreCase("Vendor")) {
            return "Supplier";
        }
        return "Customer";
    }

    private record InvoiceContext(boolean isSupplier) {}

    private List<Map<String, Object>> dedupeLatestBySourceOrder(List<Map<String, Object>> invoices) {
        if (invoices == null || invoices.size() < 2) {
            return invoices == null ? List.of() : invoices;
        }
        Map<String, Map<String, Object>> bestBySourceOrder = new HashMap<>();
        List<Map<String, Object>> passthrough = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            if (invoice == null) {
                continue;
            }
            String sourceOrder = asText(invoice.get("aas_source_sales_order")).trim();
            if (sourceOrder.isBlank()) {
                passthrough.add(invoice);
                continue;
            }
            Map<String, Object> best = bestBySourceOrder.get(sourceOrder);
            if (best == null || compareInvoiceRecency(invoice, best) > 0) {
                bestBySourceOrder.put(sourceOrder, invoice);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(passthrough.size() + bestBySourceOrder.size());
        result.addAll(passthrough);
        result.addAll(bestBySourceOrder.values());
        result.sort((a, b) -> {
            int posting = asText(b.get("posting_date")).compareTo(asText(a.get("posting_date")));
            if (posting != 0) {
                return posting;
            }
            return -compareInvoiceRecency(a, b);
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

    public byte[] downloadPdf(String invoiceId) {
        Map<String, Object> invoiceDoc = unwrap(erpNextClient.getResource(DOCTYPE, invoiceId));
        backfillCategoryDueSnapshot(invoiceId, invoiceDoc);
        correctPostingDateFromSourceOrder(invoiceId, invoiceDoc);
        String printFormat = resolveInvoicePrintFormat(invoiceId);
        String privilegedSession = operationalReadSession();
        byte[] pdf = printFormat.isBlank()
                ? (privilegedSession == null
                        ? erpNextClient.downloadPdf(DOCTYPE, invoiceId)
                        : erpNextClient.downloadPdfWithSession(DOCTYPE, invoiceId, Map.of(), privilegedSession))
                : erpNextClient.downloadPdfWithSession(DOCTYPE, invoiceId, Map.of("format", printFormat), privilegedSession);
        // Guard: ERPNext may return an HTML/JSON error payload (still 200) if print format fails.
        if (pdf == null || pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            String snippet = pdf == null ? "" : new String(pdf, 0, Math.min(pdf.length, 240));
            throw new IllegalStateException("ERPNext did not return a valid PDF for " + invoiceId + ". " + snippet);
        }
        return pdf;
    }

    private void backfillCategoryDueSnapshot(String invoiceId, Map<String, Object> invoiceDoc) {
        if (invoiceDoc == null || invoiceDoc.isEmpty()) {
            return;
        }
        String customer = asText(invoiceDoc.get("customer"));
        String categoryId = asText(invoiceDoc.get("aas_category"));
        if (customer.isBlank() || categoryId.isBlank()) {
            return;
        }
        double storedPreviousDue = asDouble(invoiceDoc.get("aas_previous_due"));
        double storedCurrentPending = asDouble(invoiceDoc.get("aas_current_pending"));
        try {
            Map<String, Object> ledger = branchOpsService.getBranchLedgerByCategory(customer, categoryId);
            double dueAmount = asDouble(ledger.get("balance"));
            double currentInvoiceContribution = currentInvoiceDueBase(invoiceDoc);
            double previousDue = Math.max(0.0, round(dueAmount - currentInvoiceContribution));
            double currentPending = round(dueAmount);
            if (closeEnough(storedPreviousDue, previousDue) && closeEnough(storedCurrentPending, currentPending)) {
                return;
            }
            erpNextClient.updateResource(DOCTYPE, invoiceId, Map.of(
                    "aas_previous_due", previousDue,
                    "aas_current_pending", currentPending));
        } catch (Exception ignore) {
            // Best-effort backfill for older invoices; never block PDF generation.
        }
    }

    private void correctPostingDateFromSourceOrder(String invoiceId, Map<String, Object> invoiceDoc) {
        if (invoiceDoc == null || invoiceDoc.isEmpty()) {
            return;
        }
        // Only correct Draft invoices (docstatus=0); submitted/cancelled docs cannot be amended here.
        if (asDouble(invoiceDoc.get("docstatus")) != 0) {
            return;
        }
        String sourceOrderId = asText(invoiceDoc.get("aas_source_sales_order")).trim();
        if (sourceOrderId.isBlank()) {
            return;
        }
        String currentPostingDate = asText(invoiceDoc.get("posting_date")).trim();
        try {
            Map<String, Object> order = unwrap(erpNextClient.getResource("Sales Order", sourceOrderId));
            String orderDate = asText(order.get("transaction_date")).trim();
            if (orderDate.isBlank() || orderDate.equals(currentPostingDate)) {
                return;
            }
            erpNextClient.updateResource(DOCTYPE, invoiceId,
                    Map.of("posting_date", orderDate, "set_posting_time", 1));
        } catch (Exception ignore) {
            // Best-effort correction; never block PDF generation.
        }
    }

    public Map<String, Object> bulkFixPostingDates() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"posting_date\",\"docstatus\",\"aas_source_sales_order\"]");
        params.put("filters", "[[\"docstatus\",\"=\",\"0\"],[\"aas_source_sales_order\",\"!=\",\"\"]]");
        params.put("limit_page_length", 500);
        List<Map<String, Object>> invoices = listInvoicesResource(params);
        int fixed = 0;
        int skipped = 0;
        for (Map<String, Object> invoice : invoices) {
            String invoiceId = asText(invoice.get("name")).trim();
            String currentDate = asText(invoice.get("posting_date")).trim();
            String sourceOrderId = asText(invoice.get("aas_source_sales_order")).trim();
            if (invoiceId.isBlank() || sourceOrderId.isBlank()) {
                skipped++;
                continue;
            }
            try {
                Map<String, Object> order = unwrap(erpNextClient.getResource("Sales Order", sourceOrderId));
                String orderDate = asText(order.get("transaction_date")).trim();
                if (orderDate.isBlank() || orderDate.equals(currentDate)) {
                    skipped++;
                    continue;
                }
                erpNextClient.updateResource(DOCTYPE, invoiceId,
                        Map.of("posting_date", orderDate, "set_posting_time", 1));
                fixed++;
            } catch (Exception ignore) {
                skipped++;
            }
        }
        return Map.of("fixed", fixed, "skipped", skipped, "total", invoices.size());
    }

    private List<Map<String, Object>> listInvoicesResource(Map<String, Object> params) {
        String privilegedSession = operationalReadSession();
        return privilegedSession == null
                ? erpNextClient.listResources(DOCTYPE, params)
                : erpNextClient.listResourcesWithSession(DOCTYPE, params, privilegedSession);
    }

    private String operationalReadSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String role = authentication == null
                ? ""
                : authentication.getAuthorities().stream()
                        .map(Object::toString)
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring("ROLE_".length()))
                        .findFirst()
                        .orElse("");
        if (!"HELPER".equalsIgnoreCase(role)) {
            return null;
        }
        if (erpSetupUsername == null || erpSetupUsername.isBlank() || erpSetupPassword == null || erpSetupPassword.isBlank()) {
            return null;
        }
        return erpNextClient.login(erpSetupUsername, erpSetupPassword);
    }

    private double currentInvoiceDueBase(Map<String, Object> invoiceDoc) {
        int docstatus = asInt(invoiceDoc.get("docstatus"));
        if (docstatus == 0) {
            return currentInvoiceGrandTotal(invoiceDoc);
        }
        double outstandingAmount = asDouble(invoiceDoc.get("outstanding_amount"));
        return outstandingAmount > 0.0001 ? round(outstandingAmount) : 0.0;
    }

    private double currentInvoiceGrandTotal(Map<String, Object> invoiceDoc) {
        double invoiceTotal = asDouble(invoiceDoc.get("grand_total"));
        double roundingAdjustment = asDouble(firstNonNull(
                invoiceDoc.get("aas_rounding_adjustment"),
                invoiceDoc.get("rounding_adjustment")));
        return round(invoiceTotal + roundingAdjustment);
    }

    public Map<String, Object> deleteInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new IllegalArgumentException("Invoice id is required.");
        }
        try {
            Map<String, Object> invoice = unwrap(erpNextClient.getResource(DOCTYPE, invoiceId));
            if (invoice.isEmpty()) {
                throw new IllegalArgumentException("Invoice not found.");
            }
            int existingDocstatus = asInt(invoice.get("docstatus"));
            String existingStatus = asText(invoice.get("status"));
            if (existingDocstatus == 2 || "Cancelled".equalsIgnoreCase(existingStatus)) {
                return Map.of(
                        "status", "cancelled",
                        "message", "Invoice was already cancelled and has been removed from the AAS invoice list.",
                        "invoiceId", invoiceId);
            }
            String customer = asText(invoice.get("customer"));
            List<Map<String, Object>> linkedPayments = findLinkedPayments(invoiceId, customer);
            for (Map<String, Object> payment : linkedPayments) {
                deletePaymentEntry(asText(payment.get("name")), asInt(payment.get("docstatus")));
            }
            int docstatus = existingDocstatus;
            if (docstatus == 1) {
                erpNextClient.cancelResource(DOCTYPE, invoiceId);
            }
            deletePaymentLedgerEntriesForInvoice(invoiceId);
            try {
                return erpNextClient.deleteResource(DOCTYPE, invoiceId);
            } catch (FeignException ex) {
                String message = summarizeFeignMessage(ex);
                if (isLedgerRetentionBlock(message)) {
                    return Map.of(
                            "status", "cancelled",
                            "message", "Invoice cancelled and removed from AAS list. ERP kept the cancelled ledger record for audit integrity.",
                            "invoiceId", invoiceId);
                }
                if (isPaymentLinkBlock(message)) {
                    throw new IllegalStateException(buildInvoiceDeletionBlockedMessage(invoiceId, message), ex);
                }
                throw ex;
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            String detail = firstMeaningfulMessage(ex);
            if (isPaymentLinkBlock(detail)) {
                detail = buildInvoiceDeletionBlockedMessage(invoiceId, detail);
            }
            throw new IllegalStateException(detail.isBlank() ? "Unable to delete invoice." : detail, ex);
        }
    }

    private List<Map<String, Object>> findLinkedPayments(String invoiceId, String customer) {
        if (invoiceId == null || invoiceId.isBlank() || customer == null || customer.isBlank()) {
            return List.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"docstatus\",\"party\",\"party_type\",\"posting_date\"]");
        params.put("filters", "[[\"party_type\",\"=\",\"Customer\"],[\"party\",\"=\",\"" + escape(customer) + "\"]]");
        params.put("limit_page_length", 500);
        List<Map<String, Object>> payments = erpNextClient.listResources(PAYMENT_ENTRY, params);
        if (payments == null || payments.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> linked = new ArrayList<>();
        for (Map<String, Object> payment : payments) {
            String paymentId = asText(payment.get("name"));
            if (paymentId.isBlank()) {
                continue;
            }
            Map<String, Object> paymentDoc = unwrap(erpNextClient.getResource(PAYMENT_ENTRY, paymentId));
            List<Map<String, Object>> references = childItems(paymentDoc.get("references"));
            boolean matches = references.stream().anyMatch(reference ->
                    DOCTYPE.equalsIgnoreCase(asText(reference.get("reference_doctype")))
                            && invoiceId.equals(asText(reference.get("reference_name"))));
            if (matches) {
                linked.add(Map.of(
                        "name", paymentId,
                        "docstatus", asInt(paymentDoc.get("docstatus"))));
            }
        }
        return linked;
    }

    private void deletePaymentEntry(String paymentId, int docstatus) {
        if (paymentId == null || paymentId.isBlank()) {
            return;
        }
        try {
            if (docstatus == 1) {
                erpNextClient.cancelResource(PAYMENT_ENTRY, paymentId);
            }
            deletePaymentLedgerEntriesForVoucher(paymentId);
            erpNextClient.deleteResource(PAYMENT_ENTRY, paymentId);
        } catch (FeignException ex) {
            String message = summarizeFeignMessage(ex);
            if (isPaymentLinkBlock(message)) {
                throw new IllegalStateException(buildPaymentEntryBlockedMessage(paymentId), ex);
            }
            throw ex;
        }
    }

    private void deletePaymentLedgerEntriesForVoucher(String voucherNo) {
        if (voucherNo == null || voucherNo.isBlank()) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"voucher_no\"]");
        params.put("filters", "[[\"voucher_no\",\"=\",\"" + escape(voucherNo) + "\"]]");
        params.put("limit_page_length", 500);
        List<Map<String, Object>> entries = erpNextClient.listResources(PAYMENT_LEDGER_ENTRY, params);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (Map<String, Object> entry : entries) {
            String entryId = asText(entry.get("name"));
            if (!entryId.isBlank()) {
                erpNextClient.deleteResource(PAYMENT_LEDGER_ENTRY, entryId);
            }
        }
    }

    private void deletePaymentLedgerEntriesForInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"voucher_no\",\"against_voucher_no\"]");
        params.put("filters", "[[\"against_voucher_no\",\"=\",\"" + escape(invoiceId) + "\"]]");
        params.put("limit_page_length", 500);
        List<Map<String, Object>> entries = erpNextClient.listResources(PAYMENT_LEDGER_ENTRY, params);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (Map<String, Object> entry : entries) {
            String entryId = asText(entry.get("name"));
            if (!entryId.isBlank()) {
                erpNextClient.deleteResource(PAYMENT_LEDGER_ENTRY, entryId);
            }
        }
    }

    private String resolveInvoicePrintFormat(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> invoice = erpNextClient.getResource(DOCTYPE, invoiceId);
            Map<String, Object> invoiceDoc = unwrap(invoice);
            String company = asText(invoiceDoc.get("company"));
            if (company.isBlank()) {
                return "";
            }
            Map<String, Object> companyResp = erpNextClient.getResource("Company", company);
            Map<String, Object> companyDoc = unwrap(companyResp);
            return asText(companyDoc.get("aas_sales_invoice_print_format"));
        } catch (Exception ex) {
            // Never block downloads due to missing optional configuration.
            return "";
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

    private void validateFields(InvoiceRequest request) {
        Map<String, Object> fields = request == null ? null : request.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Invoice fields are required.");
        }
        Object customer = fields.get("customer");
        Object company = fields.get("company");
        Object items = fields.get("items");
        if (customer == null || customer.toString().isBlank()) {
            throw new IllegalArgumentException("Invoice customer is required.");
        }
        if (company == null || company.toString().isBlank()) {
            throw new IllegalArgumentException("Invoice company is required.");
        }
        if (!(items instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Invoice items are required.");
        }
    }

    private int resolveCreditDays(String customer) {
        if (customer == null || customer.isBlank()) {
            return 0;
        }
        Map<String, Object> response = erpNextClient.getResource("Customer", customer);
        Map<String, Object> customerDoc = unwrap(response);
        int creditDays = asInt(customerDoc.get("aas_credit_days"));
        if (creditDays <= 0) {
            creditDays = asInt(customerDoc.get("credit_days"));
        }
        return Math.max(creditDays, 0);
    }

    private String resolveCompanyCurrency(String company) {
        Map<String, Object> response = erpNextClient.getResource("Company", company);
        Map<String, Object> companyDoc = unwrap(response);
        return asText(companyDoc.get("default_currency"));
    }

    private LocalDate resolveBaseDate(Object value) {
        if (value == null) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (DateTimeParseException ex) {
            return LocalDate.now();
        }
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        return raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("1") || raw.equalsIgnoreCase("yes");
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
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

    private boolean hasValue(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean closeEnough(double left, double right) {
        return Math.abs(left - right) <= 0.01;
    }

    private String firstMeaningfulMessage(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank() && !"Request failed.".equalsIgnoreCase(message.trim())) {
                return message;
            }
            current = current.getCause();
        }
        return "";
    }

    private boolean isLedgerRetentionBlock(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        return normalized.contains("gl entry") || normalized.contains("payment ledger entry");
    }

    private boolean isPaymentLinkBlock(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        return normalized.contains("linkexistserror")
                || normalized.contains("cannot delete or cancel because payment entry")
                || (normalized.contains("payment entry") && normalized.contains("linked"));
    }

    private String buildInvoiceDeletionBlockedMessage(String invoiceId, String rawMessage) {
        String paymentId = extractPaymentEntryId(rawMessage);
        if (!paymentId.isBlank()) {
            return "Invoice " + invoiceId + " cannot be deleted because payment " + paymentId + " is linked to it. Remove or unlink the payment first.";
        }
        return "Invoice " + invoiceId + " cannot be deleted because a linked payment exists. Remove or unlink the payment first.";
    }

    private String buildPaymentEntryBlockedMessage(String paymentId) {
        return "Payment " + paymentId + " could not be removed automatically because it is linked to another accounting record. Please unlink it in ERP before deleting the invoice.";
    }

    private String extractPaymentEntryId(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        Matcher matcher = PAYMENT_ENTRY_ID_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String summarizeFeignMessage(FeignException ex) {
        if (ex == null) {
            return "";
        }
        String content = ex.contentUTF8();
        if (content != null && !content.isBlank()) {
            return content;
        }
        return ex.getMessage() == null ? "" : ex.getMessage();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childItems(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
            return rows;
        }
        return List.of();
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
