package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdjustmentNoteErpService {

    public static final String JOURNAL_ENTRY = "Journal Entry";
    public static final String FILE = "File";
    public static final String STATUS_UNDER_REVIEW = "UNDER_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String DIRECTION_GIVE = "GIVE";
    public static final String DIRECTION_TAKE = "TAKE";
    public static final String PARTY_CUSTOMER = "Customer";
    public static final String PARTY_SUPPLIER = "Supplier";
    public static final String FIELD_REVIEW_STATUS = "aas_adjustment_review_status";
    public static final String FIELD_REVIEW_NOTES = "aas_adjustment_review_notes";
    public static final String FIELD_CREATED_BY = "aas_adjustment_created_by";
    public static final String FIELD_CREATED_AT = "aas_adjustment_created_at";
    public static final String FIELD_REVIEWED_BY = "aas_adjustment_reviewed_by";
    public static final String FIELD_REVIEWED_AT = "aas_adjustment_reviewed_at";
    public static final String FIELD_CATEGORY = "aas_category";
    public static final String FIELD_DUE_AMOUNT = "aas_due_amount";
    public static final String FIELD_DIRECTION = "aas_adjustment_direction";
    public static final String FIELD_NOTE_TYPE = "aas_adjustment_note_type";
    public static final String FIELD_PARTY_TYPE = "aas_adjustment_party_type";
    public static final String FIELD_PARTY = "aas_adjustment_party";
    public static final String FIELD_AMOUNT = "aas_adjustment_amount";
    public static final String FIELD_REASON = "aas_adjustment_reason";
    public static final String FIELD_REFERENCE_INVOICE = "aas_reference_invoice";
    public static final String FIELD_REFERENCE_INVOICE_DOCTYPE = "aas_reference_invoice_doctype";

    private final ErpNextClient erpNextClient;

    public AdjustmentNoteErpService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public List<Map<String, Object>> listNotesByStatus(String status, String partyType) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"posting_date\",\"docstatus\",\"remark\","
                        + "\"" + FIELD_REVIEW_STATUS + "\",\"" + FIELD_CREATED_BY + "\",\"" + FIELD_CREATED_AT + "\","
                        + "\"" + FIELD_REVIEWED_BY + "\",\"" + FIELD_REVIEWED_AT + "\",\"" + FIELD_CATEGORY + "\","
                        + "\"" + FIELD_DUE_AMOUNT + "\",\"" + FIELD_DIRECTION + "\",\"" + FIELD_NOTE_TYPE + "\","
                        + "\"" + FIELD_PARTY_TYPE + "\",\"" + FIELD_PARTY + "\",\"" + FIELD_AMOUNT + "\","
                        + "\"" + FIELD_REASON + "\",\"" + FIELD_REFERENCE_INVOICE + "\",\"" + FIELD_REFERENCE_INVOICE_DOCTYPE + "\"]");
        params.put("limit_page_length", 500);
        params.put("order_by", "modified desc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of(FIELD_REVIEW_STATUS, "=", normalizeStatus(status)));
        String normalizedPartyType = normalizePartyType(partyType);
        if (!normalizedPartyType.isBlank()) {
            filters.add(List.of(FIELD_PARTY_TYPE, "=", normalizedPartyType));
        }
        if (STATUS_APPROVED.equalsIgnoreCase(status)) {
            filters.add(List.of("docstatus", "=", "1"));
        } else {
            filters.add(List.of("docstatus", "=", "0"));
        }
        params.put("filters", toJson(filters));
        return erpNextClient.listResources(JOURNAL_ENTRY, params);
    }

    public Map<String, Object> getNote(String noteId) {
        return unwrapDoc(erpNextClient.getResource(JOURNAL_ENTRY, noteId));
    }

    public List<Map<String, Object>> listNoteAttachments(String noteId) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"file_name\",\"file_url\",\"is_private\",\"creation\"]");
        params.put("limit_page_length", 100);
        params.put("order_by", "creation desc");
        params.put("filters", toJson(List.of(
                List.of("attached_to_doctype", "=", JOURNAL_ENTRY),
                List.of("attached_to_name", "=", noteId))));
        List<Map<String, Object>> files = erpNextClient.listResources(FILE, params);
        return files.stream().map(file -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", asText(file.get("name")));
            out.put("name", asText(file.get("file_name")));
            out.put("url", toProxyFileUrl(asText(file.get("file_url"))));
            out.put("isPrivate", asInt(file.get("is_private")) == 1);
            out.put("createdAt", asText(file.get("creation")));
            return out;
        }).toList();
    }

    public BigDecimal approvedDueImpact(String partyType, String partyId, String categoryId) {
        return sumByStatus(partyType, partyId, categoryId, STATUS_APPROVED);
    }

    public BigDecimal pendingAmount(String partyType, String partyId, String categoryId, String excludeId) {
        return sumByStatus(partyType, partyId, categoryId, STATUS_UNDER_REVIEW, excludeId);
    }

    private BigDecimal sumByStatus(String partyType, String partyId, String categoryId, String status) {
        return sumByStatus(partyType, partyId, categoryId, status, null);
    }

    private BigDecimal sumByStatus(String partyType, String partyId, String categoryId, String status, String excludeId) {
        String normalizedPartyType = normalizePartyType(partyType);
        String normalizedPartyId = asText(partyId);
        String normalizedCategoryId = asText(categoryId);
        if (normalizedPartyType.isBlank() || normalizedPartyId.isBlank() || normalizedCategoryId.isBlank()) {
            return BigDecimal.ZERO;
        }
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"docstatus\",\"" + FIELD_AMOUNT + "\",\"" + FIELD_DIRECTION + "\",\"" + FIELD_PARTY_TYPE + "\"]");
        params.put("limit_page_length", 500);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of(FIELD_REVIEW_STATUS, "=", normalizeStatus(status)));
        filters.add(List.of(FIELD_PARTY_TYPE, "=", normalizedPartyType));
        filters.add(List.of(FIELD_PARTY, "=", normalizedPartyId));
        filters.add(List.of(FIELD_CATEGORY, "=", normalizedCategoryId));
        filters.add(List.of("docstatus", "=", STATUS_APPROVED.equalsIgnoreCase(status) ? "1" : "0"));
        if (excludeId != null && !excludeId.isBlank()) {
            filters.add(List.of("name", "!=", excludeId.trim()));
        }
        params.put("filters", toJson(filters));
        List<Map<String, Object>> rows = erpNextClient.listResources(JOURNAL_ENTRY, params);
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal amount = asDecimal(row.get(FIELD_AMOUNT));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String direction = normalizeDirection(row.get(FIELD_DIRECTION));
            total = total.add(signedImpact(normalizedPartyType, direction, amount));
        }
        return total;
    }

    public BigDecimal signedImpact(String partyType, String direction, BigDecimal amount) {
        String normalizedPartyType = normalizePartyType(partyType);
        String normalizedDirection = normalizeDirection(direction);
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (PARTY_SUPPLIER.equals(normalizedPartyType)) {
            return DIRECTION_GIVE.equals(normalizedDirection) ? value : value.negate();
        }
        return DIRECTION_GIVE.equals(normalizedDirection) ? value.negate() : value;
    }

    public String normalizePartyType(String value) {
        String normalized = asText(value);
        if (normalized.equalsIgnoreCase("Supplier") || normalized.equalsIgnoreCase("Vendor")) {
            return PARTY_SUPPLIER;
        }
        if (normalized.equalsIgnoreCase("Customer") || normalized.equalsIgnoreCase("Branch")) {
            return PARTY_CUSTOMER;
        }
        return "";
    }

    public String normalizeDirection(Object value) {
        String normalized = asText(value).toUpperCase();
        if (DIRECTION_TAKE.equals(normalized)) {
            return DIRECTION_TAKE;
        }
        return DIRECTION_GIVE;
    }

    public String noteTypeForDirection(String direction) {
        return DIRECTION_TAKE.equals(normalizeDirection(direction)) ? "DEBIT_NOTE" : "CREDIT_NOTE";
    }

    public String normalizeStatus(String status) {
        String normalized = asText(status).toUpperCase();
        if (STATUS_APPROVED.equals(normalized)) {
            return STATUS_APPROVED;
        }
        if (STATUS_REJECTED.equals(normalized)) {
            return STATUS_REJECTED;
        }
        return STATUS_UNDER_REVIEW;
    }

    public String toJson(List<List<String>> filters) {
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

    public String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public int asInt(Object value) {
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

    public BigDecimal asDecimal(Object value) {
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

    @SuppressWarnings("unchecked")
    public Map<String, Object> unwrapDoc(Map<String, Object> response) {
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

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toProxyFileUrl(String fileUrl) {
        String value = asText(fileUrl);
        if (value.isBlank()) {
            return value;
        }
        if (value.startsWith("/api/files/") || value.startsWith("/api/private/files/")) {
            return value;
        }
        String path = value;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                path = java.net.URI.create(value).getPath();
            } catch (Exception ignored) {
                return value;
            }
        }
        if (path.startsWith("/private/files/")) {
            return "/api" + path;
        }
        if (path.startsWith("/files/")) {
            return "/api" + path;
        }
        return value;
    }
}
