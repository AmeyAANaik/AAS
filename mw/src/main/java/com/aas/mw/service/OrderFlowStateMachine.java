package com.aas.mw.service;

import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OrderFlowStateMachine {

    private static final String DRAFT = "DRAFT";
    private static final String VENDOR_ASSIGNED = "VENDOR_ASSIGNED";
    private static final String VENDOR_PDF_RECEIVED = "VENDOR_PDF_RECEIVED";
    private static final String VENDOR_BILL_CAPTURED = "VENDOR_BILL_CAPTURED";
    private static final String SELL_ORDER_CREATED = "SELL_ORDER_CREATED";
    private static final String INVOICED = "INVOICED";

    public void ensureCanAssignVendor(String currentStatus) {
        String normalized = normalize(currentStatus);
        if (!DRAFT.equals(normalized)) {
            throw new IllegalStateException("Vendor can only be assigned when order status is DRAFT.");
        }
    }

    public void ensureCanUploadVendorPdf(String currentStatus) {
        String normalized = normalize(currentStatus);
        if (!VENDOR_ASSIGNED.equals(normalized)) {
            throw new IllegalStateException("Vendor PDF can only be uploaded when order status is VENDOR_ASSIGNED.");
        }
    }

    public void ensureCanCaptureVendorBill(String currentStatus) {
        String normalized = normalize(currentStatus);
        if (!VENDOR_ASSIGNED.equals(normalized) && !VENDOR_PDF_RECEIVED.equals(normalized)) {
            throw new IllegalStateException(
                    "Vendor bill can only be captured when status is VENDOR_ASSIGNED or VENDOR_PDF_RECEIVED.");
        }
    }

    public void ensureCanCreateSellOrder(String currentStatus) {
        String normalized = normalize(currentStatus);
        if (!VENDOR_BILL_CAPTURED.equals(normalized)) {
            throw new IllegalStateException("Sell order can only be created when status is VENDOR_BILL_CAPTURED.");
        }
    }

    public void ensureCanDeleteOrder(String currentStatus) {
        String normalized = normalize(currentStatus);
        if (!DRAFT.equals(normalized) && !VENDOR_ASSIGNED.equals(normalized) && !VENDOR_PDF_RECEIVED.equals(normalized)) {
            throw new IllegalStateException("Order can only be deleted when status is DRAFT, VENDOR_ASSIGNED, or VENDOR_PDF_RECEIVED.");
        }
    }

    public void ensureTransitionAllowed(String from, String to) {
        String normalizedFrom = normalize(from);
        String normalizedTo = normalize(to);
        if (normalizedFrom.equals(normalizedTo)) {
            return;
        }
        Map<String, Set<String>> transitions = Map.of(
                DRAFT, Set.of(VENDOR_ASSIGNED),
                VENDOR_ASSIGNED, Set.of(VENDOR_PDF_RECEIVED, VENDOR_BILL_CAPTURED),
                VENDOR_PDF_RECEIVED, Set.of(VENDOR_BILL_CAPTURED, INVOICED),
                VENDOR_BILL_CAPTURED, Set.of(SELL_ORDER_CREATED, INVOICED));
        Set<String> allowed = transitions.getOrDefault(normalizedFrom, Set.of());
        if (!allowed.contains(normalizedTo)) {
            throw new IllegalStateException("Invalid status transition from " + normalizedFrom + " to " + normalizedTo + ".");
        }
    }

    public String normalize(String status) {
        if (status == null || status.isBlank()) {
            return DRAFT;
        }
        return status.trim().toUpperCase();
    }
}
