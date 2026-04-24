package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final String BILL_ITEM_CODE = "AAS-VENDOR-BILL";
    private static final String RECORD_ACCOUNT = "ACCOUNT";
    private static final String RECORD_SUPPLIER = "SUPPLIER";
    private static final String RECORD_CUSTOMER = "CUSTOMER";

    private final ErpNextClient erpNextClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpeningBalanceImportService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
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
        ensureBillItem();

        Map<String, Object> created = new LinkedHashMap<>();
        if (!validation.accountEntries().isEmpty()) {
            created.put("journalEntry", unwrapDoc(erpNextClient.createResource("Journal Entry", buildJournalEntryPayload(
                    normalizedCompany,
                    postingDate,
                    validation.accountEntries()))));
        } else {
            created.put("journalEntry", Map.of());
        }

        List<Map<String, Object>> purchaseInvoices = new ArrayList<>();
        for (PartyAmount row : validation.supplierEntries()) {
            purchaseInvoices.add(unwrapDoc(erpNextClient.createResource(
                    "Purchase Invoice",
                    buildPurchaseInvoicePayload(normalizedCompany, postingDate, row))));
        }
        created.put("purchaseInvoices", List.copyOf(purchaseInvoices));

        List<Map<String, Object>> salesInvoices = new ArrayList<>();
        for (PartyAmount row : validation.customerEntries()) {
            salesInvoices.add(unwrapDoc(erpNextClient.createResource(
                    "Sales Invoice",
                    buildSalesInvoicePayload(normalizedCompany, postingDate, row))));
        }
        created.put("salesInvoices", List.copyOf(salesInvoices));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("companyId", normalizedCompany);
        response.put("cutoverDate", postingDate);
        response.put("created", created);
        return response;
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
                        "posting_date", postingDate,
                        "bill_no", row.reference(),
                        "grand_total", row.amount()))
                .toList());

        planned.put("salesInvoices", validation.customerEntries().stream()
                .map(row -> Map.of(
                        "doctype", "Sales Invoice",
                        "customer", row.partyId(),
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
            Map<String, Object> account = unwrapResourceSafe("Account", row.account());
            if (account.isEmpty()) {
                errors.add(error(row.rowNumber(), "account", "Account not found: " + row.account()));
                continue;
            }
            String accountCompany = asText(account.get("company"));
            if (!companyId.equals(accountCompany)) {
                errors.add(error(row.rowNumber(), "account", "Account does not belong to company " + companyId + ": " + row.account()));
                continue;
            }

            accounts.add(new AccountAmount(row.account(), debit, credit, row.costCenter()));
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
            Map<String, Object> supplier = unwrapResourceSafe("Supplier", row.partyId());
            if (supplier.isEmpty()) {
                errors.add(error(row.rowNumber(), "party_id", "Supplier not found: " + row.partyId()));
                continue;
            }
            suppliers.add(new PartyAmount("Supplier", row.partyId(), row.amount(), resolveReference("SUPPLIER", postingDate, row.partyId(), row.reference())));
        }

        List<PartyAmount> customers = new ArrayList<>();
        for (PartyCsvRow row : parsed.customers()) {
            if (row.amount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add(error(row.rowNumber(), "amount", "Amount must be > 0."));
                continue;
            }
            Map<String, Object> customer = unwrapResourceSafe("Customer", row.partyId());
            if (customer.isEmpty()) {
                errors.add(error(row.rowNumber(), "party_id", "Customer not found: " + row.partyId()));
                continue;
            }
            customers.add(new PartyAmount("Customer", row.partyId(), row.amount(), resolveReference("CUSTOMER", postingDate, row.partyId(), row.reference())));
        }

        return new ValidationResult(List.copyOf(accounts), List.copyOf(suppliers), List.copyOf(customers), List.copyOf(errors));
    }

    private String resolveReference(String type, String postingDate, String partyId, String provided) {
        String candidate = provided == null ? "" : provided.trim();
        if (!candidate.isBlank()) {
            return candidate;
        }
        LocalDate date = LocalDate.parse(postingDate);
        return "AAS-OPENING-" + REF_DATE.format(date) + "-" + partyId.trim();
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", row.partyId());
        payload.put("company", companyId);
        payload.put("posting_date", postingDate);
        payload.put("bill_no", row.reference());
        payload.put("currency", companyCurrency);
        payload.put("conversion_rate", 1.0);
        payload.put("price_list_currency", companyCurrency);
        payload.put("plc_conversion_rate", 1.0);
        payload.put("items", List.of(Map.of(
                "item_code", BILL_ITEM_CODE,
                "qty", 1,
                "rate", row.amount(),
                "amount", row.amount())));
        payload.put("remarks", row.reference());
        return payload;
    }

    private Map<String, Object> buildSalesInvoicePayload(String companyId, String postingDate, PartyAmount row) {
        String companyCurrency = resolveCompanyCurrency(companyId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer", row.partyId());
        payload.put("company", companyId);
        payload.put("posting_date", postingDate);
        payload.put("currency", companyCurrency);
        payload.put("conversion_rate", 1.0);
        payload.put("price_list_currency", companyCurrency);
        payload.put("plc_conversion_rate", 1.0);
        payload.put("items", List.of(Map.of(
                "item_code", BILL_ITEM_CODE,
                "qty", 1,
                "rate", row.amount(),
                "amount", row.amount())));
        payload.put("po_no", row.reference());
        payload.put("remarks", row.reference());
        return payload;
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

    private void ensureBillItem() {
        try {
            Map<String, Object> existing = unwrap(erpNextClient.getResource("Item", BILL_ITEM_CODE));
            boolean disabled = asFlag(existing.get("disabled"));
            if (disabled) {
                erpNextClient.updateResource("Item", BILL_ITEM_CODE, Map.of("disabled", 0));
            }
        } catch (Exception ex) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("item_code", BILL_ITEM_CODE);
            payload.put("item_name", "Opening Balance Item");
            payload.put("item_group", "All Item Groups");
            payload.put("stock_uom", "Nos");
            payload.put("is_stock_item", 0);
            payload.put("is_sales_item", 1);
            payload.put("is_purchase_item", 1);
            payload.put("disabled", 0);
            payload.put("description", "Synthetic item for opening balance invoices created by AAS.");
            erpNextClient.createResource("Item", payload);
        }
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
                            String partyId = value(record, "party_id");
                            BigDecimal amount = asDecimal(value(record, "amount"));
                            String billNo = value(record, "bill_no");
                            suppliers.add(new PartyCsvRow(rowNumber, partyId, amount, billNo));
                        }
                        case RECORD_CUSTOMER -> {
                            requireHeader(parser, "party_id");
                            requireHeader(parser, "amount");
                            String partyId = value(record, "party_id");
                            BigDecimal amount = asDecimal(value(record, "amount"));
                            String invoiceRef = value(record, "invoice_ref");
                            customers.add(new PartyCsvRow(rowNumber, partyId, amount, invoiceRef));
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

    private record PartyCsvRow(long rowNumber, String partyId, BigDecimal amount, String reference) {}

    private record AccountAmount(String account, BigDecimal debit, BigDecimal credit, String costCenter) {}

    private record PartyAmount(String partyType, String partyId, BigDecimal amount, String reference) {}

    private record ParsedRows(List<AccountCsvRow> accounts, List<PartyCsvRow> suppliers, List<PartyCsvRow> customers) {}

    private record ValidationResult(
            List<AccountAmount> accountEntries,
            List<PartyAmount> supplierEntries,
            List<PartyAmount> customerEntries,
            List<Map<String, Object>> errors) {}
}
