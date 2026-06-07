package com.aas.mw.service;

import com.aas.mw.service.AdjustmentNoteErpService;
import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BillReviewService {

    public static final String ITEM_TYPE_PAYMENT = "PAYMENT";
    public static final String ITEM_TYPE_CREDIT_NOTE = "CREDIT_NOTE";
    public static final String ITEM_TYPE_DEBIT_NOTE = "DEBIT_NOTE";

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
    private final AdjustmentNoteErpService adjustmentNoteErpService;

    public BillReviewService(
            ErpNextClient erpNextClient,
            PaymentDueService paymentDueService,
            AdjustmentNoteErpService adjustmentNoteErpService) {
        this.erpNextClient = erpNextClient;
        this.paymentDueService = paymentDueService;
        this.adjustmentNoteErpService = adjustmentNoteErpService;
    }

    public Map<String, Object> getPendingCount() {
        int payments = listPaymentsByStatus(STATUS_UNDER_REVIEW).size();
        int notes = listAdjustmentNotesByStatus(STATUS_UNDER_REVIEW, null).size();
        return Map.of("pendingCount", payments + notes);
    }

    public List<Map<String, Object>> listItemsByStatus(String status, String partyType) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(listPaymentsByStatus(status, partyType));
        items.addAll(listAdjustmentNotesByStatus(status, partyType));
        items.sort(Comparator
                .comparing((Map<String, Object> row) -> sortDate(row), Comparator.reverseOrder())
                .thenComparing(row -> asText(row.get("documentId"))));
        return items;
    }

    public Map<String, Object> getReviewDetail(String itemType, String documentId) {
        String normalizedItemType = normalizeItemType(itemType, Map.of("name", documentId));
        if (ITEM_TYPE_PAYMENT.equals(normalizedItemType)) {
            return getPaymentDetail(documentId);
        }
        return getAdjustmentNoteDetail(documentId);
    }

    public Map<String, Object> approve(String itemType, String documentId, String notes, String reviewedBy) {
        String normalizedItemType = normalizeItemType(itemType, Map.of("name", documentId));
        if (ITEM_TYPE_PAYMENT.equals(normalizedItemType)) {
            return approvePayment(documentId, notes, reviewedBy);
        }
        return approveAdjustmentNote(documentId, notes, reviewedBy);
    }

    public Map<String, Object> reject(String itemType, String documentId, String notes, String reviewedBy) {
        String normalizedItemType = normalizeItemType(itemType, Map.of("name", documentId));
        if (notes == null || notes.trim().isBlank()) {
            throw new IllegalArgumentException("notes is required when rejecting a review item.");
        }
        if (ITEM_TYPE_PAYMENT.equals(normalizedItemType)) {
            return rejectPayment(documentId, notes, reviewedBy);
        }
        return rejectAdjustmentNote(documentId, notes, reviewedBy);
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
        Map<String, String> partyNames = resolvePartyNames(rows, "party_type", "party");
        return rows.stream().map(row -> toPaymentListRow(row, partyNames)).toList();
    }

    public Map<String, Object> getPaymentDetail(String paymentId) {
        String id = requireId(paymentId, "paymentId");
        Map<String, Object> payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        List<Map<String, Object>> attachments = listPaymentAttachments(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("itemType", ITEM_TYPE_PAYMENT);
        detail.put("documentId", id);
        detail.put("document", payment);
        detail.put("payment", payment);
        detail.put("attachments", attachments);
        return detail;
    }

    public Map<String, Object> getAdjustmentNoteDetail(String noteId) {
        String id = requireId(noteId, "documentId");
        Map<String, Object> note = adjustmentNoteErpService.getNote(id);
        List<Map<String, Object>> attachments = adjustmentNoteErpService.listNoteAttachments(id);
        Map<String, Object> warningPayload = computeAdjustmentDueWarning(note, id);
        Map<String, Object> detail = new LinkedHashMap<>();
        String itemType = normalizeItemType(null, note);
        detail.put("itemType", itemType);
        detail.put("documentId", id);
        detail.put("document", note);
        detail.put("attachments", attachments);
        detail.put("warnings", warningPayload.getOrDefault("warnings", List.of()));
        detail.put("currentDueAmount", warningPayload.getOrDefault("currentDueAmount", BigDecimal.ZERO));
        detail.put("expectedDueAfterApproval", warningPayload.getOrDefault("expectedDueAfterApproval", BigDecimal.ZERO));
        detail.put("signedImpact", warningPayload.getOrDefault("signedImpact", BigDecimal.ZERO));
        return detail;
    }

    private List<Map<String, Object>> listAdjustmentNotesByStatus(String status, String partyType) {
        List<Map<String, Object>> rows = adjustmentNoteErpService.listNotesByStatus(status, partyType);
        Map<String, String> partyNames = resolvePartyNames(rows,
                AdjustmentNoteErpService.FIELD_PARTY_TYPE,
                AdjustmentNoteErpService.FIELD_PARTY);
        return rows.stream().map(row -> toAdjustmentListRow(row, partyNames)).toList();
    }

    private Map<String, Object> approvePayment(String paymentId, String notes, String reviewedBy) {
        String id = requireId(paymentId, "paymentId");
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

        Map<String, Object> warningPayload = computePaymentDueWarning(payment, id);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) warningPayload.getOrDefault("warnings", List.of());

        String now = now();
        String actor = safeActor(reviewedBy);

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
            revertPaymentReviewStatus(id);
            throw ex;
        }
        payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("itemType", ITEM_TYPE_PAYMENT);
        detail.put("documentId", id);
        detail.put("document", payment);
        detail.put("payment", payment);
        detail.put("attachments", attachments);
        detail.put("warnings", warnings);
        detail.put("currentDueAmount", warningPayload.getOrDefault("currentDueAmount", BigDecimal.ZERO));
        detail.put("currentAvailableDueAmount", warningPayload.getOrDefault("currentAvailableDueAmount", BigDecimal.ZERO));
        return detail;
    }

    private Map<String, Object> rejectPayment(String paymentId, String notes, String reviewedBy) {
        String id = requireId(paymentId, "paymentId");
        Map<String, Object> payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        int docstatus = asInt(payment.get("docstatus"));
        String reviewStatus = asText(payment.get(FIELD_REVIEW_STATUS));
        if (docstatus != 0 || !STATUS_UNDER_REVIEW.equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("Only draft payments under review can be rejected.");
        }
        Map<String, Object> update = new HashMap<>();
        update.put(FIELD_REVIEW_STATUS, STATUS_REJECTED);
        update.put(FIELD_REVIEW_NOTES, notes.trim());
        if (!safeActor(reviewedBy).isBlank()) {
            update.put(FIELD_REVIEWED_BY, safeActor(reviewedBy));
        }
        update.put(FIELD_REVIEWED_AT, now());
        erpNextClient.updateResource(PAYMENT_ENTRY, id, update);
        payment = unwrapDoc(erpNextClient.getResource(PAYMENT_ENTRY, id));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("itemType", ITEM_TYPE_PAYMENT);
        detail.put("documentId", id);
        detail.put("document", payment);
        detail.put("payment", payment);
        detail.put("attachments", listPaymentAttachments(id));
        return detail;
    }

    private Map<String, Object> approveAdjustmentNote(String noteId, String notes, String reviewedBy) {
        String id = requireId(noteId, "documentId");
        Map<String, Object> note = adjustmentNoteErpService.getNote(id);
        int docstatus = adjustmentNoteErpService.asInt(note.get("docstatus"));
        String reviewStatus = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_REVIEW_STATUS));
        if (docstatus != 0 || !STATUS_UNDER_REVIEW.equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("Only draft notes under review can be approved.");
        }
        List<Map<String, Object>> attachments = adjustmentNoteErpService.listNoteAttachments(id);
        if (attachments.isEmpty()) {
            throw new IllegalArgumentException("Evidence is required before approving a note.");
        }

        Map<String, Object> warningPayload = computeAdjustmentDueWarning(note, id);
        enforceAdjustmentApprovalLimit(note, warningPayload);
        String actor = safeActor(reviewedBy);
        Map<String, Object> update = new HashMap<>();
        update.put(AdjustmentNoteErpService.FIELD_REVIEW_STATUS, STATUS_APPROVED);
        if (notes != null && !notes.trim().isBlank()) {
            update.put(AdjustmentNoteErpService.FIELD_REVIEW_NOTES, notes.trim());
        }
        if (!actor.isBlank()) {
            update.put(AdjustmentNoteErpService.FIELD_REVIEWED_BY, actor);
        }
        update.put(AdjustmentNoteErpService.FIELD_REVIEWED_AT, now());
        erpNextClient.updateResource(AdjustmentNoteErpService.JOURNAL_ENTRY, id, update);
        note = adjustmentNoteErpService.getNote(id);
        try {
            Map<String, Object> submitted = adjustmentNoteErpService.unwrapDoc(erpNextClient.submitDoc(note));
            if (!submitted.isEmpty()) {
                note = submitted;
            }
        } catch (Exception ex) {
            revertAdjustmentReviewStatus(id);
            throw ex;
        }
        note = adjustmentNoteErpService.getNote(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("itemType", normalizeItemType(null, note));
        detail.put("documentId", id);
        detail.put("document", note);
        detail.put("attachments", attachments);
        detail.put("warnings", warningPayload.getOrDefault("warnings", List.of()));
        detail.put("currentDueAmount", warningPayload.getOrDefault("currentDueAmount", BigDecimal.ZERO));
        detail.put("expectedDueAfterApproval", warningPayload.getOrDefault("expectedDueAfterApproval", BigDecimal.ZERO));
        detail.put("signedImpact", warningPayload.getOrDefault("signedImpact", BigDecimal.ZERO));
        return detail;
    }

    private void enforceAdjustmentApprovalLimit(Map<String, Object> note, Map<String, Object> warningPayload) {
        String partyType = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_PARTY_TYPE));
        String direction = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_DIRECTION));
        if (!reducesDue(partyType, direction)) {
            return;
        }
        BigDecimal amount = adjustmentNoteErpService.asDecimal(note.get(AdjustmentNoteErpService.FIELD_AMOUNT));
        BigDecimal available = adjustmentNoteErpService.asDecimal(warningPayload.get("currentAvailableReducibleDue"));
        if (amount.compareTo(available.max(BigDecimal.ZERO)) > 0) {
            throw new IllegalArgumentException("Adjustment amount exceeds available due for this category.");
        }
    }

    private Map<String, Object> rejectAdjustmentNote(String noteId, String notes, String reviewedBy) {
        String id = requireId(noteId, "documentId");
        Map<String, Object> note = adjustmentNoteErpService.getNote(id);
        int docstatus = adjustmentNoteErpService.asInt(note.get("docstatus"));
        String reviewStatus = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_REVIEW_STATUS));
        if (docstatus != 0 || !STATUS_UNDER_REVIEW.equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("Only draft notes under review can be rejected.");
        }
        Map<String, Object> update = new HashMap<>();
        update.put(AdjustmentNoteErpService.FIELD_REVIEW_STATUS, STATUS_REJECTED);
        update.put(AdjustmentNoteErpService.FIELD_REVIEW_NOTES, notes.trim());
        if (!safeActor(reviewedBy).isBlank()) {
            update.put(AdjustmentNoteErpService.FIELD_REVIEWED_BY, safeActor(reviewedBy));
        }
        update.put(AdjustmentNoteErpService.FIELD_REVIEWED_AT, now());
        erpNextClient.updateResource(AdjustmentNoteErpService.JOURNAL_ENTRY, id, update);
        note = adjustmentNoteErpService.getNote(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("itemType", normalizeItemType(null, note));
        detail.put("documentId", id);
        detail.put("document", note);
        detail.put("attachments", adjustmentNoteErpService.listNoteAttachments(id));
        return detail;
    }

    private Map<String, Object> computePaymentDueWarning(Map<String, Object> payment, String paymentId) {
        String partyType = asText(payment.get("party_type"));
        String party = asText(payment.get("party"));
        String categoryId = asText(payment.get(FIELD_CATEGORY));
        BigDecimal recordedDue = asDecimal(payment.get(FIELD_DUE_AMOUNT));
        BigDecimal amount = paymentAmount(payment);
        if (partyType.isBlank() || party.isBlank() || categoryId.isBlank()) {
            return Map.of("warnings", List.of(), "currentDueAmount", recordedDue, "currentAvailableDueAmount", recordedDue);
        }
        BigDecimal currentDue = recordedDue;
        BigDecimal currentAvailable = recordedDue;
        List<Map<String, Object>> warnings = new ArrayList<>();
        try {
            Map<String, Object> due = paymentDueService.dueByCategory(partyType, party, categoryId);
            currentDue = asDecimal(due.get("dueAmount"));
            BigDecimal underReviewAmount = asDecimal(due.get("underReviewAmount"));
            BigDecimal underReviewExcluding = underReviewAmount.subtract(amount).max(BigDecimal.ZERO);
            currentAvailable = currentDue.subtract(underReviewExcluding).max(BigDecimal.ZERO);
            if (currentDue.compareTo(recordedDue) != 0) {
                warnings.add(Map.of(
                        "code", "DUE_CHANGED",
                        "recordedDue", recordedDue,
                        "currentDue", currentDue));
            }
        } catch (Exception ignored) {
        }
        return Map.of(
                "warnings", warnings,
                "currentDueAmount", currentDue,
                "currentAvailableDueAmount", currentAvailable);
    }

    private Map<String, Object> computeAdjustmentDueWarning(Map<String, Object> note, String noteId) {
        String partyType = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_PARTY_TYPE));
        String partyId = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_PARTY));
        String categoryId = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_CATEGORY));
        BigDecimal recordedDue = adjustmentNoteErpService.asDecimal(note.get(AdjustmentNoteErpService.FIELD_DUE_AMOUNT));
        BigDecimal amount = adjustmentNoteErpService.asDecimal(note.get(AdjustmentNoteErpService.FIELD_AMOUNT));
        BigDecimal signedImpact = adjustmentNoteErpService.signedImpact(
                partyType,
                note.get(AdjustmentNoteErpService.FIELD_DIRECTION) == null ? "" : String.valueOf(note.get(AdjustmentNoteErpService.FIELD_DIRECTION)),
                amount);
        if (partyType.isBlank() || partyId.isBlank() || categoryId.isBlank()) {
            return Map.of(
                    "warnings", List.of(),
                    "currentDueAmount", recordedDue,
                    "expectedDueAfterApproval", recordedDue.add(signedImpact),
                    "signedImpact", signedImpact);
        }
        BigDecimal currentDue = recordedDue;
        BigDecimal currentAvailableReducibleDue = recordedDue;
        List<Map<String, Object>> warnings = new ArrayList<>();
        try {
            Map<String, Object> due = paymentDueService.dueByCategory(partyType, partyId, categoryId);
            currentDue = adjustmentNoteErpService.asDecimal(due.get("dueAmount"));
            BigDecimal availableDueAmount = adjustmentNoteErpService.asDecimal(due.get("availableDueAmount"));
            BigDecimal pendingAdjustmentAmount = adjustmentNoteErpService.asDecimal(due.get("pendingAdjustmentAmount"));
            BigDecimal pendingExcludingSelf = pendingAdjustmentAmount.subtract(signedImpact);
            currentAvailableReducibleDue = availableDueAmount.add(minZero(pendingExcludingSelf)).max(BigDecimal.ZERO);
            if (currentDue.compareTo(recordedDue) != 0) {
                warnings.add(Map.of(
                        "code", "DUE_CHANGED",
                        "recordedDue", recordedDue,
                        "currentDue", currentDue));
            }
        } catch (Exception ignored) {
        }
        return Map.of(
                "warnings", warnings,
                "currentDueAmount", currentDue,
                "currentAvailableReducibleDue", currentAvailableReducibleDue,
                "expectedDueAfterApproval", currentDue.add(signedImpact),
                "signedImpact", signedImpact);
    }

    private boolean reducesDue(String partyType, String direction) {
        String normalizedPartyType = adjustmentNoteErpService.normalizePartyType(partyType);
        String normalizedDirection = adjustmentNoteErpService.normalizeDirection(direction);
        return AdjustmentNoteErpService.PARTY_SUPPLIER.equals(normalizedPartyType)
                ? AdjustmentNoteErpService.DIRECTION_TAKE.equals(normalizedDirection)
                : AdjustmentNoteErpService.DIRECTION_GIVE.equals(normalizedDirection);
    }

    private BigDecimal minZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) >= 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private Map<String, Object> toPaymentListRow(Map<String, Object> row, Map<String, String> partyNames) {
        String paymentId = asText(row.get("name"));
        String partyType = asText(row.get("party_type"));
        String party = asText(row.get("party"));
        BigDecimal amount = paymentAmount(row);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemType", ITEM_TYPE_PAYMENT);
        out.put("documentId", paymentId);
        out.put("documentLabel", "Payment");
        out.put("paymentId", paymentId);
        out.put("partyType", partyType);
        out.put("party", party);
        out.put("partyName", partyNames.getOrDefault(partyType + "::" + party, party));
        out.put("postingDate", asText(row.get("posting_date")));
        out.put("amount", amount);
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

    private Map<String, Object> toAdjustmentListRow(Map<String, Object> row, Map<String, String> partyNames) {
        String itemType = normalizeItemType(null, row);
        String partyType = adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_PARTY_TYPE));
        String party = adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_PARTY));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemType", itemType);
        out.put("documentId", adjustmentNoteErpService.asText(row.get("name")));
        out.put("documentLabel", ITEM_TYPE_DEBIT_NOTE.equals(itemType) ? "Debit note" : "Credit note");
        out.put("partyType", partyType);
        out.put("party", party);
        out.put("partyName", partyNames.getOrDefault(partyType + "::" + party, party));
        out.put("postingDate", adjustmentNoteErpService.asText(row.get("posting_date")));
        out.put("amount", adjustmentNoteErpService.asDecimal(row.get(AdjustmentNoteErpService.FIELD_AMOUNT)));
        out.put("categoryId", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_CATEGORY)));
        out.put("dueAmount", adjustmentNoteErpService.asDecimal(row.get(AdjustmentNoteErpService.FIELD_DUE_AMOUNT)));
        out.put("direction", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_DIRECTION)));
        out.put("referenceNo", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_REFERENCE_INVOICE)));
        out.put("referenceInvoice", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_REFERENCE_INVOICE)));
        out.put("reason", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_REASON)));
        out.put("docstatus", adjustmentNoteErpService.asInt(row.get("docstatus")));
        out.put("createdAt", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_CREATED_AT)));
        out.put("createdBy", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_CREATED_BY)));
        out.put("reviewStatus", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_REVIEW_STATUS)));
        out.put("reviewedAt", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_REVIEWED_AT)));
        out.put("reviewedBy", adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_REVIEWED_BY)));
        return out;
    }

    private Map<String, String> resolvePartyNames(List<Map<String, Object>> rows, String partyTypeField, String partyField) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String partyType = asText(row.get(partyTypeField));
            String party = asText(row.get(partyField));
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

    private String normalizeItemType(String itemType, Map<String, Object> note) {
        String normalized = asText(itemType).toUpperCase(Locale.ROOT);
        if (ITEM_TYPE_PAYMENT.equals(normalized)) {
            return ITEM_TYPE_PAYMENT;
        }
        if (ITEM_TYPE_CREDIT_NOTE.equals(normalized)) {
            return ITEM_TYPE_CREDIT_NOTE;
        }
        if (ITEM_TYPE_DEBIT_NOTE.equals(normalized)) {
            return ITEM_TYPE_DEBIT_NOTE;
        }
        String noteType = adjustmentNoteErpService.asText(note.get(AdjustmentNoteErpService.FIELD_NOTE_TYPE)).toUpperCase(Locale.ROOT);
        if (ITEM_TYPE_DEBIT_NOTE.equals(noteType) || "DEBIT_NOTE".equals(noteType)) {
            return ITEM_TYPE_DEBIT_NOTE;
        }
        return ITEM_TYPE_CREDIT_NOTE;
    }

    private String sortDate(Map<String, Object> row) {
        String createdAt = asText(row.get("createdAt"));
        if (!createdAt.isBlank()) {
            return createdAt;
        }
        return asText(row.get("postingDate"));
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

    private void revertPaymentReviewStatus(String id) {
        try {
            erpNextClient.updateResource(PAYMENT_ENTRY, id, Map.of(FIELD_REVIEW_STATUS, STATUS_UNDER_REVIEW));
        } catch (Exception ignored) {
        }
    }

    private void revertAdjustmentReviewStatus(String id) {
        try {
            erpNextClient.updateResource(
                    AdjustmentNoteErpService.JOURNAL_ENTRY,
                    id,
                    Map.of(AdjustmentNoteErpService.FIELD_REVIEW_STATUS, STATUS_UNDER_REVIEW));
        } catch (Exception ignored) {
        }
    }

    private String safeActor(String reviewedBy) {
        return reviewedBy == null ? "" : reviewedBy.trim();
    }

    private String now() {
        return LocalDateTime.now(ZoneId.systemDefault()).format(ERP_DATETIME);
    }

    private String requireId(String id, String field) {
        String value = id == null ? "" : id.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private BigDecimal paymentAmount(Map<String, Object> payment) {
        BigDecimal paid = asDecimal(payment.get("paid_amount"));
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return paid;
        }
        return asDecimal(payment.get("received_amount"));
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
