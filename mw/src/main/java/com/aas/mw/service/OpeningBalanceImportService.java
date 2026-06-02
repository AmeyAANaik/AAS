package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpeningBalanceImportService {

    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String RECORD_ACCOUNT = "ACCOUNT";
    private static final String RECORD_SUPPLIER = "SUPPLIER";
    private static final String RECORD_CUSTOMER = "CUSTOMER";
    private static final String OPENING_ITEM_PREFIX = "AAS-OPENING-ITEM-";

    private final ErpNextClient erpNextClient;
    private final PaymentDueService paymentDueService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpeningBalanceImportService(ErpNextClient erpNextClient, PaymentDueService paymentDueService) {
        this.erpNextClient = erpNextClient;
        this.paymentDueService = paymentDueService;
    }

    public String templateCsv(String companyId) {
        normalizeRequired(companyId, "companyId");
        String[] headers = {
                "record_type",
                "account",
                "debit",
                "credit",
                "cost_center",
                "party_id",
                "amount",
                "bill_no",
                "invoice_ref",
                "category"
        };

        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", headers)).append("\n");
        builder.append("ACCOUNT,Cash - ").append(escapeCompany(companyId)).append(",1000,0,Main - ").append(escapeCompany(companyId)).append(",,,,\n");
        builder.append("SUPPLIER,,,,,SUPP-0001,500,OB-001,,GENERAL\n");
        builder.append("CUSTOMER,,,,,CUST-0001,750,,OB-INV-001,GENERAL\n");
        return builder.toString();
    }

    public Map<String, Object> preview(String companyId, MultipartFile file, String cutoverDate) {
        String normalizedCompany = normalizeRequired(companyId, "companyId");
        String postingDate = normalizeDateRequired(cutoverDate, "cutoverDate");
        ParsedRows parsed = parseCsv(file);
        ValidationResult validation = validate(normalizedCompany, postingDate, parsed);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companyId", normalizedCompany);
        response.put("cutoverDate", postingDate);
        response.put("isValid", validation.errors().isEmpty());
        response.put("errors", validation.errors());
        response.put("summary", buildSummary(parsed, validation));
        response.put("planned", buildPlanned(validation, postingDate));
        return response;
    }

    public Map<String, Object> apply(String companyId, MultipartFile file, String cutoverDate) {
        String normalizedCompany = normalizeRequired(companyId, "companyId");
        String postingDate = normalizeDateRequired(cutoverDate, "cutoverDate");
        ParsedRows parsed = parseCsv(file);
        ValidationResult validation = validate(normalizedCompany, postingDate, parsed);
        if (!validation.errors().isEmpty()) {
            throw new OpeningBalanceValidationException("Opening balance import has validation errors.", validation.errors());
        }

        enforceNoDuplicates(validation, normalizedCompany, postingDate);
        ensureOpeningItems(validation);

        Map<String, Object> created = new LinkedHashMap<>();
        if (!validation.accountEntries().isEmpty()) {
            Map<String, Object> journalEntry = unwrapDoc(erpNextClient.createResource("Journal Entry", buildJournalEntryPayload(
                    normalizedCompany,
                    postingDate,
                    validation.accountEntries())));
            created.put("journalEntry", journalEntry);
            created.put("journalEntrySubmitted", submitCreated("Journal Entry", journalEntry));
        } else {
            created.put("journalEntry", Map.of());
            created.put("journalEntrySubmitted", Map.of());
        }

        List<Map<String, Object>> purchaseInvoices = new ArrayList<>();
        List<Map<String, Object>> submittedPurchaseInvoices = new ArrayList<>();
        for (PartyAmount row : validation.supplierEntries()) {
            Map<String, Object> createdInvoice = unwrapDoc(erpNextClient.createResource(
                    "Purchase Invoice",
                    buildPurchaseInvoicePayload(normalizedCompany, postingDate, row)));
            purchaseInvoices.add(createdInvoice);
            submittedPurchaseInvoices.add(submitCreated("Purchase Invoice", createdInvoice));
        }
        created.put("purchaseInvoices", List.copyOf(purchaseInvoices));
        created.put("purchaseInvoicesSubmitted", List.copyOf(submittedPurchaseInvoices));

        List<Map<String, Object>> salesInvoices = new ArrayList<>();
        List<Map<String, Object>> submittedSalesInvoices = new ArrayList<>();
        for (PartyAmount row : validation.customerEntries()) {
            Map<String, Object> createdInvoice = unwrapDoc(erpNextClient.createResource(
                    "Sales Invoice",
                    buildSalesInvoicePayload(normalizedCompany, postingDate, row)));
            salesInvoices.add(createdInvoice);
            applyCategoryDueSnapshot("Sales Invoice", createdInvoice, row);
            submittedSalesInvoices.add(submitCreated("Sales Invoice", createdInvoice));
        }
        created.put("salesInvoices", List.copyOf(salesInvoices));
        created.put("salesInvoicesSubmitted", List.copyOf(submittedSalesInvoices));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companyId", normalizedCompany);
        response.put("cutoverDate", postingDate);
        response.put("created", created);
        return response;
    }

    public Map<String, Object> listOpeningInvoices(String companyId, String fromDate, String toDate) {
        String normalizedCompany = normalizeRequired(companyId, "companyId");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companyId", normalizedCompany);
        response.put("from", fromDate == null ? "" : fromDate.trim());
        response.put("to", toDate == null ? "" : toDate.trim());
        response.put("salesInvoices", listOpeningSalesInvoices(normalizedCompany, fromDate, toDate));
        response.put("purchaseInvoices", listOpeningPurchaseInvoices(normalizedCompany, fromDate, toDate));
        return response;
    }

    private List<Map<String, Object>> listOpeningSalesInvoices(String companyId, String fromDate, String toDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"company\",\"posting_date\",\"due_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"docstatus\",\"is_opening\",\"po_no\",\"remarks\"]");
        params.put("order_by", "posting_date desc");
        params.put("limit_page_length", 500);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("company", "=", companyId));
        filters.add(List.of("is_opening", "=", "Yes"));
        if (fromDate != null && !fromDate.isBlank()) {
            filters.add(List.of("posting_date", ">=", fromDate.trim()));
        }
        if (toDate != null && !toDate.isBlank()) {
            filters.add(List.of("posting_date", "<=", toDate.trim()));
        }
        filters.add(List.of("docstatus", "!=", "2"));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> rows = erpNextClient.listResources("Sales Invoice", params);
        return rows == null ? List.of() : rows.stream()
                .filter(inv -> inv != null && !"Cancelled".equalsIgnoreCase(asText(inv.get("status"))))
                .toList();
    }

    private List<Map<String, Object>> listOpeningPurchaseInvoices(String companyId, String fromDate, String toDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"supplier\",\"company\",\"posting_date\",\"due_date\",\"grand_total\",\"outstanding_amount\",\"status\",\"docstatus\",\"is_opening\",\"bill_no\",\"remarks\"]");
        params.put("order_by", "posting_date desc");
        params.put("limit_page_length", 500);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("company", "=", companyId));
        filters.add(List.of("is_opening", "=", "Yes"));
        if (fromDate != null && !fromDate.isBlank()) {
            filters.add(List.of("posting_date", ">=", fromDate.trim()));
        }
        if (toDate != null && !toDate.isBlank()) {
            filters.add(List.of("posting_date", "<=", toDate.trim()));
        }
        filters.add(List.of("docstatus", "!=", "2"));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> rows = erpNextClient.listResources("Purchase Invoice", params);
        return rows == null ? List.of() : rows.stream()
                .filter(inv -> inv != null && !"Cancelled".equalsIgnoreCase(asText(inv.get("status"))))
                .toList();
    }

    private Map<String, Object> buildSummary(ParsedRows parsed, ValidationResult validation) {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (AccountAmount row : validation.accountEntries()) {
            debitTotal = debitTotal.add(row.debit());
            creditTotal = creditTotal.add(row.credit());
        }
        BigDecimal supplierTotal = sum(validation.supplierEntries());
        BigDecimal customerTotal = sum(validation.customerEntries());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("accountRows", parsed.accounts().size());
        summary.put("supplierRows", parsed.suppliers().size());
        summary.put("customerRows", parsed.customers().size());
        summary.put("debitTotal", debitTotal);
        summary.put("creditTotal", creditTotal);
        summary.put("supplierTotal", supplierTotal);
        summary.put("customerTotal", customerTotal);
        return summary;
    }

    private Map<String, Object> buildPlanned(ValidationResult validation, String postingDate) {
        Map<String, Object> planned = new LinkedHashMap<>();
        planned.put("journalEntry", validation.accountEntries().isEmpty()
                ? Map.of()
                : Map.of(
                        "doctype", "Journal Entry",
                        "voucher_type", "Opening Entry",
                        "posting_date", postingDate,
                        "accountsCount", validation.accountEntries().size(),
                        "remark", buildOpeningRemark(postingDate)));

        planned.put("purchaseInvoices", validation.supplierEntries().stream()
                .map(row -> Map.of(
                        "doctype", "Purchase Invoice",
                        "supplier", row.partyId(),
                        "category", row.category(),
                        "posting_date", postingDate,
                        "bill_no", row.reference(),
                        "grand_total", row.amount()))
                .toList());

        planned.put("salesInvoices", validation.customerEntries().stream()
                .map(row -> Map.of(
                        "doctype", "Sales Invoice",
                        "customer", row.partyId(),
                        "category", row.category(),
                        "posting_date", postingDate,
                        "po_no", row.reference(),
                        "grand_total", row.amount()))
                .toList());
        return planned;
    }

    private void enforceNoDuplicates(ValidationResult validation, String companyId, String postingDate) {
        String remark = buildOpeningRemark(postingDate);
        if (!validation.accountEntries().isEmpty() && journalEntryExists(companyId, postingDate, remark)) {
            throw new IllegalStateException("Opening Journal Entry already exists for this company and cutover date.");
        }
        for (PartyAmount row : validation.supplierEntries()) {
            if (purchaseInvoiceExists(companyId, postingDate, row.partyId(), row.reference())) {
                throw new IllegalStateException("Opening Purchase Invoice already exists for supplier " + row.partyId() + " and cutover date.");
            }
        }
        for (PartyAmount row : validation.customerEntries()) {
            if (salesInvoiceExists(companyId, postingDate, row.partyId(), row.reference())) {
                throw new IllegalStateException("Opening Sales Invoice already exists for customer " + row.partyId() + " and cutover date.");
            }
        }
    }

    private boolean journalEntryExists(String companyId, String postingDate, String remark) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"company\",\"posting_date\",\"voucher_type\",\"remark\"]");
        params.put("limit_page_length", 5);
        params.put("filters", toJson(List.of(
                List.of("company", "=", companyId),
                List.of("posting_date", "=", postingDate),
                List.of("voucher_type", "=", "Opening Entry"),
                List.of("remark", "=", remark))));
        List<Map<String, Object>> rows = erpNextClient.listResources("Journal Entry", params);
        return rows != null && !rows.isEmpty();
    }

    private boolean purchaseInvoiceExists(String companyId, String postingDate, String supplierId, String billNo) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"company\",\"posting_date\",\"supplier\",\"bill_no\"]");
        params.put("limit_page_length", 5);
        params.put("filters", toJson(List.of(
                List.of("company", "=", companyId),
                List.of("posting_date", "=", postingDate),
                List.of("supplier", "=", supplierId),
                List.of("bill_no", "=", billNo))));
        List<Map<String, Object>> rows = erpNextClient.listResources("Purchase Invoice", params);
        return rows != null && !rows.isEmpty();
    }

    private boolean salesInvoiceExists(String companyId, String postingDate, String customerId, String reference) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"company\",\"posting_date\",\"customer\",\"po_no\"]");
        params.put("limit_page_length", 5);
        params.put("filters", toJson(List.of(
                List.of("company", "=", companyId),
                List.of("posting_date", "=", postingDate),
                List.of("customer", "=", customerId),
                List.of("po_no", "=", reference))));
        try {
            List<Map<String, Object>> rows = erpNextClient.listResources("Sales Invoice", params);
            return rows != null && !rows.isEmpty();
        } catch (Exception ex) {
            // Some ERPNext instances may not permit filtering by optional fields; fall back to a broad check.
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("fields", "[\"name\",\"company\",\"posting_date\",\"customer\"]");
            fallback.put("limit_page_length", 5);
            fallback.put("filters", toJson(List.of(
                    List.of("company", "=", companyId),
                    List.of("posting_date", "=", postingDate),
                    List.of("customer", "=", customerId))));
            List<Map<String, Object>> rows = erpNextClient.listResources("Sales Invoice", fallback);
            return rows != null && !rows.isEmpty();
        }
    }

    private ValidationResult validate(String companyId, String postingDate, ParsedRows parsed) {
        List<Map<String, Object>> errors = new ArrayList<>();
        NameResolver resolver = new NameResolver(erpNextClient);

        List<AccountAmount> accounts = new ArrayList<>();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (AccountCsvRow row : parsed.accounts()) {
            BigDecimal debit = row.debit();
            BigDecimal credit = row.credit();
            boolean debitPositive = debit.compareTo(BigDecimal.ZERO) > 0;
            boolean creditPositive = credit.compareTo(BigDecimal.ZERO) > 0;
            if (debitPositive == creditPositive) {
                errors.add(error(row.rowNumber(), "debit/credit", "Exactly one of debit or credit must be > 0."));
                continue;
            }
            ResolveResult accountResolved = resolver.resolveAccount(companyId, row.account());
            if (!accountResolved.isOk()) {
                errors.add(error(row.rowNumber(), "account", accountResolved.message()));
                continue;
            }

            accounts.add(new AccountAmount(accountResolved.id(), debit, credit, row.costCenter()));
            debitTotal = debitTotal.add(debit);
            creditTotal = creditTotal.add(credit);
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            errors.add(error(0, "accounts", "Account rows must be balanced: total debit must equal total credit."));
        }

        List<PartyAmount> suppliers = new ArrayList<>();
        for (PartyCsvRow row : parsed.suppliers()) {
            if (row.amount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(error(row.rowNumber(), "amount", "Amount must be > 0."));
                continue;
            }
            String category = row.category() == null ? "" : row.category().trim();
            if (category.isBlank()) {
                errors.add(error(row.rowNumber(), "category", "category is required."));
                continue;
            }
            ResolveResult categoryResolved = resolver.resolveItemGroup(category);
            if (!categoryResolved.isOk()) {
                errors.add(error(row.rowNumber(), "category", categoryResolved.message()));
                continue;
            }
            ResolveResult supplierResolved = resolver.resolveSupplier(row.partyId());
            if (!supplierResolved.isOk()) {
                errors.add(error(row.rowNumber(), "party_id", supplierResolved.message()));
                continue;
            }
            String reference = resolveReference("SUPPLIER", postingDate, supplierResolved.id(), categoryResolved.id(), row.reference());
            suppliers.add(new PartyAmount("Supplier", supplierResolved.id(), row.amount(), reference, categoryResolved.id()));
        }

        List<PartyAmount> customers = new ArrayList<>();
        for (PartyCsvRow row : parsed.customers()) {
            if (row.amount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(error(row.rowNumber(), "amount", "Amount must be > 0."));
                continue;
            }
            String category = row.category() == null ? "" : row.category().trim();
            if (category.isBlank()) {
                errors.add(error(row.rowNumber(), "category", "category is required."));
                continue;
            }
            ResolveResult categoryResolved = resolver.resolveItemGroup(category);
            if (!categoryResolved.isOk()) {
                errors.add(error(row.rowNumber(), "category", categoryResolved.message()));
                continue;
            }
            ResolveResult customerResolved = resolver.resolveCustomer(row.partyId());
            if (!customerResolved.isOk()) {
                errors.add(error(row.rowNumber(), "party_id", customerResolved.message()));
                continue;
            }
            String reference = resolveReference("CUSTOMER", postingDate, customerResolved.id(), categoryResolved.id(), row.reference());
            customers.add(new PartyAmount("Customer", customerResolved.id(), row.amount(), reference, categoryResolved.id()));
        }

        return new ValidationResult(List.copyOf(accounts), List.copyOf(suppliers), List.copyOf(customers), List.copyOf(errors));
    }

    private String resolveReference(String type, String postingDate, String partyId, String category, String provided) {
        String candidate = provided == null ? "" : provided.trim();
        if (!candidate.isBlank()) {
            return candidate;
        }
        LocalDate date = LocalDate.parse(postingDate);
        return "AAS-OPENING-" + REF_DATE.format(date) + "-" + partyId.trim() + "-" + slug(category);
    }

    private Map<String, Object> buildJournalEntryPayload(String companyId, String postingDate, List<AccountAmount> rows) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("voucher_type", "Opening Entry");
        payload.put("posting_date", postingDate);
        payload.put("company", companyId);
        payload.put("remark", buildOpeningRemark(postingDate));

        List<Map<String, Object>> accounts = new ArrayList<>();
        for (AccountAmount row : rows) {
            Map<String, Object> line = new HashMap<>();
            line.put("account", row.account());
            if (row.debit().compareTo(BigDecimal.ZERO) > 0) {
                line.put("debit_in_account_currency", row.debit());
            }
            if (row.credit().compareTo(BigDecimal.ZERO) > 0) {
                line.put("credit_in_account_currency", row.credit());
            }
            if (row.costCenter() != null && !row.costCenter().isBlank()) {
                line.put("cost_center", row.costCenter());
            }
            accounts.add(line);
        }
        payload.put("accounts", accounts);
        return payload;
    }

    private Map<String, Object> buildPurchaseInvoicePayload(String companyId, String postingDate, PartyAmount row) {
        String companyCurrency = resolveCompanyCurrency(companyId);
        String temporaryOpeningAccount = resolveTemporaryOpeningAccount(companyId);
        String itemCode = openingItemCode(row.category());
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", row.partyId());
        payload.put("company", companyId);
        payload.put("posting_date", postingDate);
        payload.put("due_date", postingDate);
        payload.put("set_posting_time", 1);
        payload.put("posting_time", "00:00:00");
        payload.put("is_opening", "Yes");
        payload.put("bill_no", row.reference());
        payload.put("currency", companyCurrency);
        payload.put("conversion_rate", 1.0);
        payload.put("price_list_currency", companyCurrency);
        payload.put("plc_conversion_rate", 1.0);
        payload.put("items", List.of(Map.of(
                "item_code", itemCode,
                "qty", 1,
                "rate", row.amount(),
                "amount", row.amount(),
                "expense_account", temporaryOpeningAccount)));
        payload.put("remarks", row.reference());
        return payload;
    }

    private Map<String, Object> buildSalesInvoicePayload(String companyId, String postingDate, PartyAmount row) {
        String companyCurrency = resolveCompanyCurrency(companyId);
        String temporaryOpeningAccount = resolveTemporaryOpeningAccount(companyId);
        String itemCode = openingItemCode(row.category());
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer", row.partyId());
        payload.put("company", companyId);
        payload.put("posting_date", postingDate);
        payload.put("due_date", postingDate);
        payload.put("set_posting_time", 1);
        payload.put("posting_time", "00:00:00");
        payload.put("is_opening", "Yes");
        payload.put("aas_category", row.category());
        payload.put("currency", companyCurrency);
        payload.put("conversion_rate", 1.0);
        payload.put("price_list_currency", companyCurrency);
        payload.put("plc_conversion_rate", 1.0);
        payload.put("items", List.of(Map.of(
                "item_code", itemCode,
                "qty", 1,
                "rate", row.amount(),
                "amount", row.amount(),
                "income_account", temporaryOpeningAccount)));
        payload.put("po_no", row.reference());
        payload.put("remarks", row.reference());

        BigDecimal previousDue = fetchPreviousDue("Customer", row.partyId(), row.category());
        if (previousDue.compareTo(BigDecimal.ZERO) > 0) {
            payload.put("aas_previous_due", previousDue);
        } else {
            payload.put("aas_previous_due", BigDecimal.ZERO);
        }
        return payload;
    }

    private String resolveTemporaryOpeningAccount(String companyId) {
        Map<String, Object> company = unwrap(erpNextClient.getResource("Company", companyId));
        String account = asText(company.get("temporary_opening_account"));
        if (account.isBlank()) {
            throw new IllegalStateException(
                    "Temporary Opening Account is not configured for company " + companyId
                            + ". Run /api/setup or set Company.temporary_opening_account in ERPNext.");
        }
        return account;
    }

    private Map<String, Object> submitCreated(String doctype, Map<String, Object> createdDoc) {
        if (createdDoc == null || createdDoc.isEmpty()) {
            return Map.of();
        }
        String id = asText(createdDoc.get("name"));
        if (id.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> doc = unwrapDoc(erpNextClient.getResource(doctype, id));
            if (doc.isEmpty()) {
                doc = new HashMap<>(createdDoc);
                doc.putIfAbsent("doctype", doctype);
                doc.putIfAbsent("name", id);
            }
            Map<String, Object> submitted = unwrapDoc(erpNextClient.submitDoc(doc));
            return submitted == null ? Map.of() : submitted;
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().trim();
            if (message.isBlank()) {
                message = "Failed to submit " + doctype + " " + id + ".";
            }
            String normalized = message.toLowerCase(Locale.ROOT);
            if (normalized.contains("temporary opening") || normalized.contains("temporary_opening")) {
                message = "Failed to submit " + doctype + " " + id + " because Temporary Opening Account is not configured for the company. "
                        + "Run /api/setup (Setup -> Ensure Setup) or configure Company -> Temporary Opening Account in ERPNext, then retry.";
            }
            throw new IllegalStateException(message, ex);
        }
    }

    private void applyCategoryDueSnapshot(String doctype, Map<String, Object> createdDoc, PartyAmount row) {
        if (createdDoc == null || createdDoc.isEmpty()) {
            return;
        }
        String invoiceId = asText(createdDoc.get("name"));
        if (invoiceId.isBlank()) {
            return;
        }
        String partyId = row.partyId();
        String categoryId = row.category();
        if (partyId == null || partyId.isBlank() || categoryId == null || categoryId.isBlank()) {
            return;
        }

        BigDecimal previousDue = fetchPreviousDue("Customer", partyId, categoryId);
        BigDecimal invoiceTotal = asDecimalObject(createdDoc.get("grand_total"));
        if (invoiceTotal.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> refreshed = unwrapDoc(erpNextClient.getResource(doctype, invoiceId));
            invoiceTotal = asDecimalObject(refreshed.get("grand_total"));
        }
        BigDecimal roundingAdjustment = asDecimalObject(firstNonNull(createdDoc.get("aas_rounding_adjustment"), createdDoc.get("rounding_adjustment")));
        BigDecimal grandTotal = invoiceTotal.add(roundingAdjustment);
        BigDecimal currentPending = previousDue.add(grandTotal);

        try {
            erpNextClient.updateResource(doctype, invoiceId, Map.of(
                    "aas_previous_due", previousDue,
                    "aas_current_pending", currentPending));
        } catch (Exception ignored) {
            // Best effort snapshot fields for print format.
        }
    }

    private BigDecimal fetchPreviousDue(String partyType, String partyId, String categoryId) {
        if (paymentDueService == null) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> due = paymentDueService.dueByCategory(partyType, partyId, categoryId);
            BigDecimal amount = asDecimalObject(due.get("dueAmount"));
            return roundCurrency(amount);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal roundCurrency(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return value.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal asDecimalObject(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String normalized = value.toString().trim();
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String buildOpeningRemark(String postingDate) {
        LocalDate date = LocalDate.parse(postingDate);
        return "AAS Opening Balance Import " + REF_DATE.format(date);
    }

    private String resolveCompanyCurrency(String companyId) {
        try {
            Map<String, Object> company = unwrap(erpNextClient.getResource("Company", companyId));
            String currency = asText(company.get("default_currency"));
            return currency.isBlank() ? "INR" : currency;
        } catch (Exception ignored) {
            return "INR";
        }
    }

    private void ensureOpeningItems(ValidationResult validation) {
        List<String> categories = new ArrayList<>();
        for (PartyAmount row : validation.supplierEntries()) {
            if (row.category() != null && !row.category().isBlank()) {
                categories.add(row.category().trim());
            }
        }
        for (PartyAmount row : validation.customerEntries()) {
            if (row.category() != null && !row.category().isBlank()) {
                categories.add(row.category().trim());
            }
        }
        for (String category : categories.stream().distinct().toList()) {
            ensureOpeningItemForCategory(category);
        }
    }

    private void ensureOpeningItemForCategory(String category) {
        String normalizedCategory = normalizeRequired(category, "category");
        String itemCode = openingItemCode(normalizedCategory);
        try {
            Map<String, Object> existing = unwrap(erpNextClient.getResource("Item", itemCode));
            if (existing == null || existing.isEmpty()) {
                throw new IllegalStateException("Item lookup returned empty for " + itemCode);
            }
            String existingGroup = asText(existing.get("item_group"));
            if (!existingGroup.isBlank() && !existingGroup.equals(normalizedCategory) && !existingGroup.equalsIgnoreCase(normalizedCategory)) {
                throw new IllegalStateException("Opening balance item " + itemCode + " is mapped to a different category: " + existingGroup);
            }
            boolean disabled = asFlag(existing.get("disabled"));
            if (disabled) {
                erpNextClient.updateResource("Item", itemCode, Map.of("disabled", 0));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", itemCode);
            payload.put("item_name", "Opening Balance - " + normalizedCategory);
            payload.put("item_group", normalizedCategory);
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 1);
            payload.put("disabled", 0);
            payload.put("description", "Synthetic item for opening balance invoices created by AAS.");
            erpNextClient.createResource("Item", payload);
        }
    }

    private String openingItemCode(String category) {
        String code = OPENING_ITEM_PREFIX + slug(category);
        return code.length() > 140 ? code.substring(0, 140) : code;
    }

    private String slug(String value) {
        String raw = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (raw.isBlank()) {
            return "NA";
        }
        String normalized = raw.replaceAll("[^A-Z0-9]+", "-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");
        return normalized.isBlank() ? "NA" : normalized;
    }

    private ParsedRows parseCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required.");
        }
        List<AccountCsvRow> accounts = new ArrayList<>();
        List<PartyCsvRow> suppliers = new ArrayList<>();
        List<PartyCsvRow> customers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .build();
            try (CSVParser parser = new CSVParser(reader, format)) {
                if (parser.getHeaderMap() == null || parser.getHeaderMap().isEmpty()) {
                    throw new IllegalArgumentException("CSV header row is required.");
                }
                requireHeader(parser, "record_type");
                for (CSVRecord record : parser) {
                    long rowNumber = record.getRecordNumber() + 1; // +1 to account for header row
                    String type = normalizeType(record.get("record_type"));
                    if (type.isBlank()) {
                        continue;
                    }
                    switch (type) {
                        case RECORD_ACCOUNT -> {
                            requireHeader(parser, "account");
                            requireHeader(parser, "debit");
                            requireHeader(parser, "credit");
                            String account = value(record, "account");
                            BigDecimal debit = asDecimal(value(record, "debit"));
                            BigDecimal credit = asDecimal(value(record, "credit"));
                            String costCenter = value(record, "cost_center");
                            if (account.isBlank()) {
                                accounts.add(new AccountCsvRow(rowNumber, "", debit, credit, costCenter));
                            } else {
                                accounts.add(new AccountCsvRow(rowNumber, account, debit, credit, costCenter));
                            }
                        }
                        case RECORD_SUPPLIER -> {
                            requireHeader(parser, "party_id");
                            requireHeader(parser, "amount");
                            requireHeader(parser, "category");
                            String partyId = value(record, "party_id");
                            BigDecimal amount = asDecimal(value(record, "amount"));
                            String billNo = value(record, "bill_no");
                            String category = value(record, "category");
                            suppliers.add(new PartyCsvRow(rowNumber, partyId, amount, billNo, category));
                        }
                        case RECORD_CUSTOMER -> {
                            requireHeader(parser, "party_id");
                            requireHeader(parser, "amount");
                            requireHeader(parser, "category");
                            String partyId = value(record, "party_id");
                            BigDecimal amount = asDecimal(value(record, "amount"));
                            String invoiceRef = value(record, "invoice_ref");
                            String category = value(record, "category");
                            customers.add(new PartyCsvRow(rowNumber, partyId, amount, invoiceRef, category));
                        }
                        default -> {
                            // Ignore unknown record types.
                        }
                    }
                }
            }
        } catch (OpeningBalanceValidationException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read CSV file.");
        }
        return new ParsedRows(List.copyOf(accounts), List.copyOf(suppliers), List.copyOf(customers));
    }

    private void requireHeader(CSVParser parser, String header) {
        if (!parser.getHeaderMap().containsKey(header)) {
            // commons-csv normalizes header map keys based on input; since we set ignoreHeaderCase, check manually.
            boolean found = parser.getHeaderMap().keySet().stream().anyMatch(key -> key != null && key.equalsIgnoreCase(header));
            if (!found) {
                throw new IllegalArgumentException("Missing required CSV column: " + header);
            }
        }
    }

    private String value(CSVRecord record, String header) {
        try {
            return record.isMapped(header) ? record.get(header) : "";
        } catch (Exception ignored) {
            // Ignore missing columns; validation will catch header requirements separately.
            return "";
        }
    }

    private String normalizeType(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRequired(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return normalized;
    }

    private String escapeCompany(String companyId) {
        // Keep template simple: avoid commas/newlines breaking the CSV.
        String text = companyId == null ? "" : companyId.trim();
        text = text.replace("\n", " ").replace("\r", " ").replace(",", " ");
        return text.isBlank() ? "COMPANY" : text;
    }

    private String normalizeDateRequired(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            LocalDate.parse(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " must be in yyyy-MM-dd format.");
        }
        return normalized;
    }

    private BigDecimal sum(List<PartyAmount> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (PartyAmount row : rows) {
            total = total.add(row.amount());
        }
        return total;
    }

    private BigDecimal asDecimal(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapDoc(Map<String, Object> response) {
        Map<String, Object> doc = unwrap(response);
        Object inner = doc.get("data");
        if (inner instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return doc;
    }

    private Map<String, Object> unwrapResourceSafe(String doctype, String id) {
        try {
            Map<String, Object> resource = unwrap(erpNextClient.getResource(doctype, id));
            return resource == null ? Map.of() : resource;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean asFlag(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private Map<String, Object> error(long rowNumber, String field, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("row", rowNumber);
        err.put("field", field);
        err.put("message", message);
        return err;
    }

    private record AccountCsvRow(long rowNumber, String account, BigDecimal debit, BigDecimal credit, String costCenter) {}

    private record PartyCsvRow(long rowNumber, String partyId, BigDecimal amount, String reference, String category) {}

    private record AccountAmount(String account, BigDecimal debit, BigDecimal credit, String costCenter) {}

    private record PartyAmount(String partyType, String partyId, BigDecimal amount, String reference, String category) {}

    private record ParsedRows(List<AccountCsvRow> accounts, List<PartyCsvRow> suppliers, List<PartyCsvRow> customers) {}

    private record ValidationResult(
            List<AccountAmount> accountEntries,
            List<PartyAmount> supplierEntries,
            List<PartyAmount> customerEntries,
            List<Map<String, Object>> errors) {}

    record ResolveResult(boolean ok, String id, String message) {
        static ResolveResult ok(String id) {
            return new ResolveResult(true, id, "");
        }

        static ResolveResult error(String message) {
            return new ResolveResult(false, "", message);
        }

        boolean isOk() {
            return ok && id != null && !id.isBlank();
        }
    }

    static class NameResolver {
        private final ErpNextClient erpNextClient;
        private final ObjectMapper mapper = new ObjectMapper();
        private final Map<String, ResolveResult> cache = new HashMap<>();

        NameResolver(ErpNextClient erpNextClient) {
            this.erpNextClient = erpNextClient;
        }

        ResolveResult resolveAccount(String companyId, String input) {
            String raw = normalizeInput(input);
            if (raw.isBlank()) {
                return ResolveResult.error("Account is required.");
            }
            String key = "Account:" + companyId + ":" + raw;
            ResolveResult cached = cache.get(key);
            if (cached != null) {
                return cached;
            }

            Map<String, Object> byId = Map.of();
            try {
                byId = unwrapSafe(erpNextClient.getResource("Account", raw));
            } catch (Exception ignored) {
                byId = Map.of();
            }
            if (!byId.isEmpty()) {
                String accountCompany = asText(byId.get("company"));
                if (!companyId.equals(accountCompany)) {
                    ResolveResult res = ResolveResult.error("Account does not belong to company " + companyId + ": " + raw);
                    cache.put(key, res);
                    return res;
                }
                ResolveResult res = ResolveResult.ok(asText(byId.get("name")).isBlank() ? raw : asText(byId.get("name")));
                cache.put(key, res);
                return res;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"account_name\",\"company\"]");
            params.put("limit_page_length", 50);
            params.put("filters", toJson(List.of(
                    List.of("account_name", "=", raw),
                    List.of("company", "=", companyId))));
            List<Map<String, Object>> rows;
            try {
                rows = safeList(erpNextClient.listResources("Account", params));
            } catch (Exception ignored) {
                rows = List.of();
            }
            ResolveResult res = pickUnique("Account", raw, "account_name", rows);
            cache.put(key, res);
            return res;
        }

        ResolveResult resolveCustomer(String input) {
            return resolveByNameOrId("Customer", "customer_name", input);
        }

        ResolveResult resolveSupplier(String input) {
            return resolveByNameOrId("Supplier", "supplier_name", input);
        }

        ResolveResult resolveItemGroup(String input) {
            return resolveByNameOrId("Item Group", "item_group_name", input);
        }

        private ResolveResult resolveByNameOrId(String doctype, String nameField, String input) {
            String raw = normalizeInput(input);
            if (raw.isBlank()) {
                return ResolveResult.error(doctype + " is required.");
            }
            String key = doctype + ":" + raw;
            ResolveResult cached = cache.get(key);
            if (cached != null) {
                return cached;
            }

            Map<String, Object> byId = Map.of();
            try {
                byId = unwrapSafe(erpNextClient.getResource(doctype, raw));
            } catch (Exception ignored) {
                byId = Map.of();
            }
            if (!byId.isEmpty()) {
                String id = asText(byId.get("name"));
                ResolveResult res = ResolveResult.ok(id.isBlank() ? raw : id);
                cache.put(key, res);
                return res;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"" + nameField + "\"]");
            params.put("limit_page_length", 50);
            params.put("filters", toJson(List.of(List.of(nameField, "=", raw))));
            List<Map<String, Object>> rows;
            try {
                rows = safeList(erpNextClient.listResources(doctype, params));
            } catch (Exception ignored) {
                rows = List.of();
            }
            ResolveResult res = pickUnique(doctype, raw, nameField, rows);
            cache.put(key, res);
            return res;
        }

        private ResolveResult pickUnique(String doctype, String input, String nameField, List<Map<String, Object>> rows) {
            if (rows == null || rows.isEmpty()) {
                return ResolveResult.error(doctype + " not found by name/id: " + input);
            }
            if (rows.size() == 1) {
                String id = asText(rows.get(0).get("name"));
                if (id.isBlank()) {
                    return ResolveResult.error(doctype + " lookup returned empty id for: " + input);
                }
                return ResolveResult.ok(id);
            }
            List<String> matches = rows.stream()
                    .filter(Objects::nonNull)
                    .map(row -> {
                        String id = asText(row.get("name"));
                        String label = asText(row.get(nameField));
                        if (label.isBlank() || label.equals(id)) {
                            return id;
                        }
                        return id + " (" + label + ")";
                    })
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .limit(10)
                    .toList();
            return ResolveResult.error(doctype + " name is ambiguous: " + input + ". Matches: " + String.join(", ", matches));
        }

        private String normalizeInput(String value) {
            return value == null ? "" : value.trim();
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> unwrapSafe(Map<String, Object> response) {
            if (response == null || response.isEmpty()) {
                return Map.of();
            }
            Object data = response.get("data");
            if (data instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
            return response;
        }

        private List<Map<String, Object>> safeList(List<Map<String, Object>> rows) {
            return rows == null ? List.of() : rows;
        }

        private String asText(Object value) {
            return value == null ? "" : String.valueOf(value).trim();
        }

        private String toJson(Object value) {
            try {
                return mapper.writeValueAsString(value);
            } catch (Exception ex) {
                return "[]";
            }
        }
    }
}
