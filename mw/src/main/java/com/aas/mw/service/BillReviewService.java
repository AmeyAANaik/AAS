package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BillReviewService {

    private static final String PAYMENT_ENTRY = "Payment Entry";
    private static final String FILE = "File";
    private static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String FIELD_REVIEW_STATUS = "aas_payment_review_status";
    private static final String FIELD_REVIEW_NOTES = "aas_payment_review_notes";
    private static final String FIELD_CREATED_BY = "aas_payment_created_by";
    private static final String FIELD_CREATED_AT = "aas_payment_created_at";
    private static final String FIELD_REVIEWED_BY = "aas_payment_reviewed_by";
    private static final String FIELD_REVIEWED_AT = "aas_payment_reviewed_at";
    private static final String FIELD_CATEGORY = "aas_category";
    private static final String FIELD_DUE_AMOUNT = "aas_due_amount";
    private static final DateTimeFormatter ERP_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ErpNextClient erpNextClient;
    private final PaymentDueService paymentDueService;

    public BillReviewService(ErpNextClient erpNextClient, PaymentDueService paymentDueService) {
        this.erpNextClient = erpNextClient;
        this.paymentDueService = paymentDueService;
    }

    public Map<String, Object> getPendingCount() {
        List<Map<String, Object>> pending = listPaymentsByStatus(STATUS_UNDER_REVIEW);
        return Map.of("pendingCount", pending.size());
    }

    public List<Map<String, Object>> listPaymentsByStatus(String status) {
        return listPaymentsByStatus(status, null);
    }

    public List<Map<String, Object>> listPaymentsByStatus(String status, String partyType) {
        String normalized = normalizeStatus(status);
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"party\",\"party_type\",\"posting_date\",\"paid_amount\",\"received_amount\",\"payment_type\","
                        + "\"mode_of_payment\",\"reference_no\",\"docstatus\",\"creation\",\"modified\","
                        + "\"" + FIELD_REVIEW_STATUS + "\",\"" + FIELD_CREATED_BY + "\",\"" + FIELD_CREATED_AT + "\","
                        + "\"" + FIELD_REVIEWED_BY + "\",\"" + FIELD_REVIEWED_AT + "\","
                        + "\"" + FIELD_CATEGORY + "\",\"" + FIELD_DUE_AMOUNT + "\"]");
        params.put("limit_page_length", 500);
        params.put("order_by", "modified desc");

        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of(FIELD_REVIEW_STATUS, "=", normalized));
        String normalizedPartyType = normalizePartyType(partyType);
        if (!normalizedPartyType.isBlank()) {
            filters.add(List.of("party_type", "=", normalizedPartyType));
        }
        if (STATUS_UNDER_REVIEW.equalsIgnoreCase(normalized) || STATUS_REJECTED.equalsIgnoreCase(normalized)) {
            filters.add(List.of("docstatus", "=", "0"));
        }
        if (STATUS_APPROVED.equalsIgnoreCase(normalized)) {
            filters.add(List.of("docstatus", "=", "1"));
        }
        params.put("filters", toJson(filters));

        List<Map<String, Object>> rows = erpNextClient.listResources(PAYMENT_ENTRY, params);
        Map<String, String> partyNames = resolvePartyNames(rows);
        return rows.stream().map(row -> toListRow(row, partyNames)).toList();
    }

    public Map<String, Object> getPaymentDetail(String paymentId) {
        String id = paymentId == null ? "" : paymentId.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("paymentId is required.");
        }
        Map<String, Object> payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        List<Map<String, Object>> attachments = listPaymentAttachments(id);
        return Map.of(
                "payment", payment,
                "attachments", attachments);
    }

    public Map<String, Object> approve(String paymentId, String notes, String reviewedBy) {
        return approvePayment(paymentId, notes, reviewedBy);
    }

    public Map<String, Object> reject(String paymentId, String notes, String reviewedBy) {
        if (notes == null || notes.trim().isBlank()) {
            throw new IllegalArgumentException("notes is required when rejecting a payment.");
        }
        return rejectPayment(paymentId, notes, reviewedBy);
    }

    private Map<String, Object> approvePayment(String paymentId, String notes, String reviewedBy) {
        String id = paymentId == null ? "" : paymentId.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("paymentId is required.");
        }
        Map<String, Object> payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        int docstatus = asInt(payment.get("docstatus"));
        String reviewStatus = asText(payment.get(FIELD_REVIEW_STATUS));
        if (docstatus != 0 || !STATUS_UNDER_REVIEW.equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("Only draft payments under review can be approved.");
        }
        List<Map<String, Object>> attachments = listPaymentAttachments(id);
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("Evidence is required before approving a payment.");
        }

        Map<String, Object> warningPayload = computeDueWarning(payment, id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) warningPayload.getOrDefault("warnings", List.of());

        String now = LocalDateTime.now(ZoneId.systemDefault()).format(ERP_DATETIME);
        String actor = reviewedBy == null ? "" : reviewedBy.trim();

        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_REVIEW_STATUS, STATUS_APPROVED);
        if (notes != null && !notes.trim().isBlank()) {
            update.put(FIELD_REVIEW_NOTES, notes.trim());
        }
        if (!actor.isBlank()) {
            update.put(FIELD_REVIEWED_BY, actor);
        }
        update.put(FIELD_REVIEWED_AT, now);
        erpNextClient.updateResource(PAYMENT_ENTRY, id, update);
        payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        try {
            Map<String, Object> submitted = unwrapDoc(erpNextClient.submitDoc(payment));
            if (!submitted.isEmpty()) {
                payment = submitted;
            }
        } catch (Exception ex) {
            // Best-effort revert so the queue remains consistent.
            try {
                erpNextClient.updateResource(PAYMENT_ENTRY, id, Map.of(FIELD_REVIEW_STATUS, STATUS_UNDER_REVIEW));
            } catch (Exception ignored) {
                // ignore
            }
            throw ex;
        }
        payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        return Map.of(
                "payment", payment,
                "attachments", attachments,
                "warnings", warnings,
                "currentDueAmount", warningPayload.getOrDefault("currentDueAmount", java.math.BigDecimal.ZERO),
                "currentAvailableDueAmount", warningPayload.getOrDefault("currentAvailableDueAmount", java.math.BigDecimal.ZERO));
    }

    private Map<String, Object> rejectPayment(String paymentId, String notes, String reviewedBy) {
        String id = paymentId == null ? "" : paymentId.trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("paymentId is required.");
        }
        Map<String, Object> payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        int docstatus = asInt(payment.get("docstatus"));
        String reviewStatus = asText(payment.get(FIELD_REVIEW_STATUS));
        if (docstatus != 0 || !STATUS_UNDER_REVIEW.equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("Only draft payments under review can be rejected.");
        }
        String now = LocalDateTime.now(ZoneId.systemDefault()).format(ERP_DATETIME);
        String actor = reviewedBy == null ? "" : reviewedBy.trim();

        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_REVIEW_STATUS, STATUS_REJECTED);
        update.put(FIELD_REVIEW_NOTES, notes.trim());
        if (!actor.isBlank()) {
            update.put(FIELD_REVIEWED_BY, actor);
        }
        update.put(FIELD_REVIEWED_AT, now);
        erpNextClient.updateResource(PAYMENT_ENTRY, id, update);
        payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        return Map.of(
                "payment", payment,
                "attachments", listPaymentAttachments(id));
    }

    private Map<String, Object> computeDueWarning(Map<String, Object> payment, String paymentId) {
        String partyType = asText(payment.get("party_type"));
        String party = asText(payment.get("party"));
        String categoryId = asText(payment.get(FIELD_CATEGORY));
        java.math.BigDecimal recordedDue = asDecimal(payment.get(FIELD_DUE_AMOUNT));
        java.math.BigDecimal amount = asDecimal(firstNonBlank(payment.get("paid_amount"), payment.get("received_amount")));
        if (partyType.isBlank() || party.isBlank() || categoryId.isBlank()) {
            return Map.of("warnings", List.of(), "currentDueAmount", recordedDue, "currentAvailableDueAmount", recordedDue);
        }
        java.math.BigDecimal currentDue = recordedDue;
        java.math.BigDecimal currentAvailable = recordedDue;
        List<Map<String, Object>> warnings = new ArrayList<>();
        try {
            Map<String, Object> due = paymentDueService == null ? Map.of() : paymentDueService.dueByCategory(partyType, party, categoryId);
            currentDue = asDecimal(due.get("dueAmount"));
            java.math.BigDecimal underReviewAmount = asDecimal(due.get("underReviewAmount"));
            java.math.BigDecimal underReviewExcluding = underReviewAmount.subtract(amount).max(java.math.BigDecimal.ZERO);
            currentAvailable = currentDue.subtract(underReviewExcluding).max(java.math.BigDecimal.ZERO);
            if (currentDue.compareTo(recordedDue) != 0) {
                warnings.add(Map.of(
                        "code", "DUE_CHANGED",
                        "recordedDue", recordedDue,
                        "currentDue", currentDue));
            }
        } catch (Exception ignored) {
            // Ignore warning failures; approval should still work.
        }
        return Map.of(
                "warnings", warnings,
                "currentDueAmount", currentDue,
                "currentAvailableDueAmount", currentAvailable);
    }

    private Object firstNonBlank(Object first, Object fallback) {
        String firstText = asText(first);
        if (!firstText.isBlank()) {
            return first;
        }
        return fallback;
    }

    private Map<String, Object> toListRow(Map<String, Object> row, Map<String, String> partyNames) {
        Map<String, Object> out = new LinkedHashMap<>();
        String paymentId = asText(row.get("name"));
        String partyType = asText(row.get("party_type"));
        String party = asText(row.get("party"));
        out.put("paymentId", paymentId);
        out.put("partyType", partyType);
        out.put("party", party);
        out.put("partyName", partyNames.getOrDefault(partyType + "::" + party, party));
        out.put("postingDate", asText(row.get("posting_date")));
        out.put("paidAmount", asDecimal(row.get("paid_amount")));
        out.put("receivedAmount", asDecimal(row.get("received_amount")));
        out.put("categoryId", asText(row.get(FIELD_CATEGORY)));
        out.put("dueAmount", asDecimal(row.get(FIELD_DUE_AMOUNT)));
        out.put("modeOfPayment", asText(row.get("mode_of_payment")));
        out.put("referenceNo", asText(row.get("reference_no")));
        out.put("docstatus", asInt(row.get("docstatus")));
        out.put("createdAt", asText(row.get(FIELD_CREATED_AT)));
        out.put("createdBy", asText(row.get(FIELD_CREATED_BY)));
        out.put("reviewStatus", asText(row.get(FIELD_REVIEW_STATUS)));
        out.put("reviewedAt", asText(row.get(FIELD_REVIEWED_AT)));
        out.put("reviewedBy", asText(row.get(FIELD_REVIEWED_BY)));
        return out;
    }

    private Map<String, String> resolvePartyNames(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String partyType = asText(row.get("party_type"));
            String party = asText(row.get("party"));
            if (partyType.isBlank() || party.isBlank()) {
                continue;
            }
            String key = partyType + "::" + party;
            if (resolved.containsKey(key)) {
                continue;
            }
            String name = party;
            try {
                String doctype = partyType.equalsIgnoreCase("Supplier") ? "Supplier" : "Customer";
                Map<String, Object> doc = unwrapDoc(erpNextClient.getResource(doctype, party));
                if ("Supplier".equalsIgnoreCase(partyType)) {
                    name = firstNonBlank(asText(doc.get("supplier_name")), party);
                } else {
                    name = firstNonBlank(asText(doc.get("customer_name")), party);
                }
            } catch (Exception ignored) {
                name = party;
            }
            resolved.put(key, name);
        }
        return resolved;
    }

    private String normalizePartyType(String partyType) {
        String value = partyType == null ? "" : partyType.trim();
        if (value.equalsIgnoreCase("Supplier") || value.equalsIgnoreCase("Vendor")) {
            return "Supplier";
        }
        if (value.equalsIgnoreCase("Customer") || value.equalsIgnoreCase("Branch")) {
            return "Customer";
        }
        return "";
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback == null ? "" : fallback;
    }

    private List<Map<String, Object>> listPaymentAttachments(String paymentId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"file_name\",\"file_url\",\"is_private\",\"creation\"]");
        params.put("limit_page_length", 100);
        params.put("order_by", "creation desc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("attached_to_doctype", "=", PAYMENT_ENTRY));
        filters.add(List.of("attached_to_name", "=", paymentId));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> files = erpNextClient.listResources(FILE, params);
        return files.stream()
                .map(file -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", asText(file.get("name")));
                    out.put("name", asText(file.get("file_name")));
                    out.put("url", asText(file.get("file_url")));
                    out.put("isPrivate", asInt(file.get("is_private")) == 1);
                    out.put("createdAt", asText(file.get("creation")));
                    return out;
                })
                .toList();
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim();
        if (value.equalsIgnoreCase(STATUS_APPROVED)) {
            return STATUS_APPROVED;
        }
        if (value.equalsIgnoreCase(STATUS_REJECTED)) {
            return STATUS_REJECTED;
        }
        return STATUS_UNDER_REVIEW;
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private java.math.BigDecimal asDecimal(Object value) {
        if (value instanceof java.math.BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (Exception ex) {
            return java.math.BigDecimal.ZERO;
        }
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
}
