package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.PaymentRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final ErpNextClient erpNextClient;
    private final PaymentDueService paymentDueService;

    private static final String PARTY_CUSTOMER = "Customer";
    private static final String PARTY_SUPPLIER = "Supplier";
    private static final String REVIEW_UNDER_REVIEW = "UNDER_REVIEW";
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final String FIELD_REVIEW_STATUS = "aas_payment_review_status";
    private static final String FIELD_REVIEW_NOTES = "aas_payment_review_notes";
    private static final String FIELD_CREATED_BY = "aas_payment_created_by";
    private static final String FIELD_CREATED_AT = "aas_payment_created_at";
    private static final String FIELD_REVIEWED_BY = "aas_payment_reviewed_by";
    private static final String FIELD_REVIEWED_AT = "aas_payment_reviewed_at";
    private static final String FIELD_CATEGORY = "aas_category";
    private static final String FIELD_DUE_AMOUNT = "aas_due_amount";
    private static final DateTimeFormatter ERP_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PaymentService(ErpNextClient erpNextClient, PaymentDueService paymentDueService) {
        this.erpNextClient = erpNextClient;
        this.paymentDueService = paymentDueService;
    }

    public Map<String, Object> createPayment(PaymentRequest request, String createdBy, boolean isAdmin) {
        return createPaymentDraft(request, createdBy, isAdmin);
    }

    public Map<String, Object> createPaymentDraft(PaymentRequest request, String createdBy, boolean isAdmin) {
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        String partyType = normalizePartyType(request.getPartyType());
        InvoiceContext invoiceContext = resolveInvoiceContext(partyType);

        String invoiceId = request.getInvoiceId();
        Map<String, Object> invoiceDoc = null;
        String party = request.getCustomer();
        String companyName = normalizeCompanyName(request.getCompany());
        if (invoiceId != null && !invoiceId.isBlank()) {
            invoiceDoc = loadInvoice(invoiceContext.invoiceDoctype(), invoiceId);
            String invoiceParty = asString(invoiceDoc.get(invoiceContext.partyField()));
            String invoiceCompany = asString(invoiceDoc.get("company"));
            if (invoiceParty != null && !invoiceParty.isBlank()) {
                party = invoiceParty;
            }
            if (invoiceCompany != null && !invoiceCompany.isBlank()) {
                companyName = invoiceCompany;
            }
        }

        Map<String, Object> response = erpNextClient.getResource("Company", companyName);
        Map<String, Object> company = unwrap(response);
        String paidFromAccount = "";
        String paidToAccount = "";
        String paymentType = "Receive";
        String partyFieldType = PARTY_CUSTOMER;
        String cashOrBank = firstNonBlank(
                asString(company.get("default_cash_account")),
                asString(company.get("default_bank_account")));
        if (cashOrBank == null || cashOrBank.isBlank()) {
            throw new IllegalStateException("Company default accounts missing for " + companyName);
        }
        if (PARTY_SUPPLIER.equals(partyType)) {
            String payable = asString(company.get("default_payable_account"));
            if (payable == null || payable.isBlank()) {
                throw new IllegalStateException("Company default payable account missing for " + companyName);
            }
            paymentType = "Pay";
            partyFieldType = PARTY_SUPPLIER;
            paidFromAccount = cashOrBank;
            paidToAccount = payable;
        } else {
            String receivable = asString(company.get("default_receivable_account"));
            if (receivable == null || receivable.isBlank()) {
                throw new IllegalStateException("Company default receivable account missing for " + companyName);
            }
            paymentType = "Receive";
            partyFieldType = PARTY_CUSTOMER;
            paidFromAccount = receivable;
            paidToAccount = cashOrBank;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("payment_type", paymentType);
        payload.put("party_type", partyFieldType);
        payload.put("party", party);
        payload.put("company", companyName);
        payload.put("paid_amount", amount);
        payload.put("received_amount", amount);
        payload.put("paid_from", paidFromAccount);
        payload.put("paid_to", paidToAccount);
        if (request.getPaymentDate() != null && !request.getPaymentDate().isBlank()) {
            payload.put("posting_date", request.getPaymentDate());
        }
        if (request.getModeOfPayment() != null && !request.getModeOfPayment().isBlank()) {
            String cashAccount = asString(company.get("default_cash_account"));
            String bankAccount = asString(company.get("default_bank_account"));
            ensureModeOfPaymentConfigured(
                    request.getModeOfPayment(),
                    companyName,
                    cashAccount,
                    bankAccount,
                    cashOrBank);
            payload.put("mode_of_payment", request.getModeOfPayment());
        }
        if (request.getReferenceNo() != null && !request.getReferenceNo().isBlank()) {
            payload.put("reference_no", request.getReferenceNo());
        }
        if (request.getReferenceDate() != null && !request.getReferenceDate().isBlank()) {
            payload.put("reference_date", request.getReferenceDate());
        }

        String now = LocalDateTime.now(ZoneId.systemDefault()).format(ERP_DATETIME);
        String actor = createdBy == null ? "" : createdBy.trim();
        if (!actor.isBlank()) {
            payload.put(FIELD_CREATED_BY, actor);
        }
        payload.put(FIELD_CREATED_AT, now);
        payload.put(FIELD_REVIEW_STATUS, REVIEW_UNDER_REVIEW);

        String categoryId = request.getCategoryId() == null ? "" : request.getCategoryId().trim();
        if (!categoryId.isBlank()) {
            payload.put(FIELD_CATEGORY, categoryId);
            BigDecimal dueSnapshot = resolveDueSnapshot(partyType, party, categoryId);
            payload.put(FIELD_DUE_AMOUNT, dueSnapshot);
        }

        if (invoiceDoc != null && invoiceId != null && !invoiceId.isBlank()) {
            BigDecimal outstanding = asDecimal(invoiceDoc.get("outstanding_amount"));
            BigDecimal allocated = amount.min(outstanding);
            if (allocated.compareTo(BigDecimal.ZERO) > 0) {
                payload.put("references", List.of(buildReference(invoiceContext.invoiceDoctype(), invoiceId, allocated)));
            }
            BigDecimal surplus = amount.subtract(allocated.max(BigDecimal.ZERO));
            if (surplus.compareTo(BigDecimal.ZERO) > 0) {
                payload.put("unallocated_amount", surplus);
            }
        }
        Map<String, Object> created = erpNextClient.createResource("Payment Entry", payload);
        return reloadPaymentEntry(unwrapDoc(created));
    }

    private String normalizeCompanyName(String requestedCompany) {
        String normalized = requestedCompany == null ? "" : requestedCompany.trim();
        if (normalized.isBlank()) {
            return normalized;
        }
        try {
            Map<String, Object> exact = unwrap(erpNextClient.getResource("Company", normalized));
            String exactName = asString(exact.get("name"));
            if (exactName != null && !exactName.isBlank()) {
                return exactName;
            }
        } catch (Exception ignored) {
            // Fall back to list lookup below.
        }

        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"abbr\",\"company_name\"]");
        params.put("limit_page_length", 500);
        List<Map<String, Object>> companies = erpNextClient.listResources("Company", params);
        if (companies == null) {
            return normalized;
        }
        for (Map<String, Object> row : companies) {
            String name = asString(row.get("name"));
            String abbr = asString(row.get("abbr"));
            String companyName = asString(row.get("company_name"));
            if (equalsIgnoreCase(normalized, name)
                    || equalsIgnoreCase(normalized, abbr)
                    || equalsIgnoreCase(normalized, companyName)) {
                return name == null || name.isBlank() ? normalized : name;
            }
        }
        return normalized;
    }

    public Map<String, Object> submitPayment(Map<String, Object> paymentDoc) {
        if (paymentDoc == null || paymentDoc.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> submitted = erpNextClient.submitDoc(paymentDoc);
        return unwrapDoc(submitted);
    }

    public void deletePaymentDraft(String paymentId) {
        String id = paymentId == null ? "" : paymentId.trim();
        if (id.isBlank()) {
            return;
        }
        try {
            erpNextClient.deleteResource("Payment Entry", id);
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }

    private BigDecimal resolveDueSnapshot(String partyType, String partyId, String categoryId) {
        if (paymentDueService == null) {
            return BigDecimal.ZERO;
        }
        String normalizedParty = partyId == null ? "" : partyId.trim();
        String normalizedCategory = categoryId == null ? "" : categoryId.trim();
        if (normalizedParty.isBlank() || normalizedCategory.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            Map<String, Object> result = paymentDueService.dueByCategory(partyType, normalizedParty, normalizedCategory);
            if (result == null || result.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return asDecimal(result.get("dueAmount"));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private Map<String, Object> reloadPaymentEntry(Map<String, Object> createdDoc) {
        if (createdDoc == null || createdDoc.isEmpty()) {
            return Map.of();
        }
        String id = asString(createdDoc.get("name"));
        if (id == null || id.isBlank()) {
            return createdDoc;
        }
        try {
            return unwrapDoc(erpNextClient.getResource("Payment Entry", id));
        } catch (Exception ignored) {
            return createdDoc;
        }
    }

    private void ensureModeOfPaymentConfigured(
            String mode,
            String companyName,
            String cashAccount,
            String bankAccount,
            String fallbackAccount) {
        String name = mode == null ? "" : mode.trim();
        if (name.isBlank() || companyName == null || companyName.isBlank()) {
            return;
        }
        String normalizedType = name.equalsIgnoreCase("cash") ? "Cash" : "Bank";
        String preferredAccount = normalizedType.equalsIgnoreCase("cash")
                ? firstNonBlank(cashAccount, fallbackAccount)
                : firstNonBlank(bankAccount, fallbackAccount);
        if (preferredAccount == null || preferredAccount.isBlank()) {
            return;
        }

        Map<String, Object> existing = Map.of();
        try {
            existing = unwrapDoc(erpNextClient.getResource("Mode of Payment", name));
        } catch (Exception ignored) {
            existing = Map.of();
        }

        if (existing.isEmpty() || asString(existing.get("name")) == null || asString(existing.get("name")).isBlank()) {
            Map<String, Object> create = new HashMap<>();
            create.put("mode_of_payment", name);
            create.put("type", normalizedType);
            create.put("enabled", 1);
            create.put("accounts", List.of(Map.of(
                    "company", companyName,
                    "default_account", preferredAccount)));
            try {
                erpNextClient.createResource("Mode of Payment", create);
            } catch (Exception ignored) {
                // Best-effort; payment entry creation will surface any configuration errors.
            }
            return;
        }

        List<Map<String, Object>> accounts = childItems(existing.get("accounts"));
        boolean hasCompanyAccount = accounts.stream().anyMatch(account ->
                companyName.equalsIgnoreCase(asString(account.get("company")))
                        && asString(account.get("default_account")) != null
                        && !asString(account.get("default_account")).isBlank());
        if (hasCompanyAccount) {
            return;
        }
        List<Map<String, Object>> updatedAccounts = new ArrayList<>(accounts);
        updatedAccounts.add(Map.of(
                "company", companyName,
                "default_account", preferredAccount));
        try {
            erpNextClient.updateResource("Mode of Payment", name, Map.of("accounts", updatedAccounts));
        } catch (Exception ignored) {
            // Best-effort; payment entry creation will surface any configuration errors.
        }
    }

    private Map<String, Object> buildReference(String invoiceDoctype, String invoiceId, BigDecimal amount) {
        Map<String, Object> reference = new HashMap<>();
        reference.put("reference_doctype", invoiceDoctype);
        reference.put("reference_name", invoiceId);
        reference.put("allocated_amount", amount);
        return reference;
    }

    private Map<String, Object> loadInvoice(String invoiceDoctype, String invoiceId) {
        Map<String, Object> invoice = unwrapDoc(erpNextClient.getResource(invoiceDoctype, invoiceId));
        int docstatus = asInt(invoice.get("docstatus"));
        if (docstatus == 0) {
            erpNextClient.submitDoc(invoice);
            invoice = unwrapDoc(erpNextClient.getResource(invoiceDoctype, invoiceId));
        }
        return invoice;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizePartyType(String value) {
        if (value == null || value.isBlank()) {
            return PARTY_CUSTOMER;
        }
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase(PARTY_SUPPLIER) || normalized.equalsIgnoreCase("Vendor")) {
            return PARTY_SUPPLIER;
        }
        return PARTY_CUSTOMER;
    }

    private InvoiceContext resolveInvoiceContext(String partyType) {
        if (PARTY_SUPPLIER.equals(partyType)) {
            return new InvoiceContext("Purchase Invoice", "supplier");
        }
        return new InvoiceContext("Sales Invoice", "customer");
    }

    private record InvoiceContext(String invoiceDoctype, String partyField) {}

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
