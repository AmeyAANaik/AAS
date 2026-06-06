package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillReviewServiceTest {

    private ErpNextClient erpNextClient;
    private PaymentDueService paymentDueService;
    private AdjustmentNoteErpService adjustmentNoteErpService;
    private BillReviewService billReviewService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        paymentDueService = mock(PaymentDueService.class);
        adjustmentNoteErpService = mock(AdjustmentNoteErpService.class);
        when(adjustmentNoteErpService.listNotesByStatus(org.mockito.Mockito.anyString(), org.mockito.Mockito.any()))
                .thenReturn(List.of());
        billReviewService = new BillReviewService(erpNextClient, paymentDueService, adjustmentNoteErpService);
    }

    @Test
    void countsUnderReviewPayments() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "PAY-1", "party_type", "Customer", "party", "SHOP-1", "aas_payment_review_status", "UNDER_REVIEW"),
                        Map.of("name", "PAY-2", "party_type", "Supplier", "party", "SUP-1", "aas_payment_review_status", "UNDER_REVIEW")));

        Map<String, Object> result = billReviewService.getPendingCount();

        assertEquals(2, ((Number) result.get("pendingCount")).intValue());
    }

    @Test
    void approvesAndSubmitsPaymentEntry() {
        when(erpNextClient.getResource("Payment Entry", "PAY-1"))
                .thenReturn(
                        Map.of("data", Map.of(
                                "doctype", "Payment Entry",
                                "name", "PAY-1",
                                "docstatus", 0,
                                "party_type", "Customer",
                                "party", "SHOP-1",
                                "paid_amount", 10,
                                "aas_payment_review_status", "UNDER_REVIEW",
                                "aas_category", "Raw Material",
                                "aas_due_amount", 100)),
                        Map.of("data", Map.of(
                                "doctype", "Payment Entry",
                                "name", "PAY-1",
                                "docstatus", 1,
                                "aas_payment_review_status", "APPROVED")));
        when(erpNextClient.submitDoc(org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-1", "docstatus", 1)));
        when(erpNextClient.updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("PAY-1"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-1")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("File"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of("name", "FILE-1", "file_name", "voucher.png", "file_url", "/files/voucher.png", "is_private", 0, "creation", "2026-04-17 10:00:00")));
        when(paymentDueService.dueByCategory("Customer", "SHOP-1", "Raw Material"))
                .thenReturn(Map.of("dueAmount", 100, "underReviewAmount", 10, "availableDueAmount", 90));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = billReviewService.approve(BillReviewService.ITEM_TYPE_PAYMENT, "PAY-1", "ok", "admin");
        Map<String, Object> payment = (Map<String, Object>) response.get("payment");

        assertEquals("PAY-1", payment.get("name"));
        assertEquals(1, ((Number) payment.get("docstatus")).intValue());
        verify(erpNextClient).submitDoc(org.mockito.Mockito.anyMap());
    }

    @Test
    void rejectsPaymentEntryWithoutSubmitting() {
        when(erpNextClient.updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("PAY-2"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-2")));
        when(erpNextClient.getResource("Payment Entry", "PAY-2"))
                .thenReturn(Map.of("data", Map.of(
                        "doctype", "Payment Entry",
                        "name", "PAY-2",
                        "docstatus", 0,
                        "aas_payment_review_status", "UNDER_REVIEW")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("File"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = billReviewService.reject(BillReviewService.ITEM_TYPE_PAYMENT, "PAY-2", "no", "admin");
        Map<String, Object> payment = (Map<String, Object>) response.get("payment");

        assertEquals("PAY-2", payment.get("name"));
        assertEquals(0, ((Number) payment.get("docstatus")).intValue());
        verify(erpNextClient, never()).submitDoc(org.mockito.Mockito.anyMap());
    }

    @Test
    void canFilterByPartyType() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) invocation.getArguments()[1];
                    String filters = String.valueOf(params.getOrDefault("filters", ""));
                    if (filters.contains("\"party_type\",\"=\",\"Supplier\"")) {
                        return List.of(Map.of("name", "PAY-2", "party_type", "Supplier", "party", "SUP-1", "aas_payment_review_status", "UNDER_REVIEW"));
                    }
                    if (filters.contains("\"party_type\",\"=\",\"Customer\"")) {
                        return List.of(Map.of("name", "PAY-1", "party_type", "Customer", "party", "SHOP-1", "aas_payment_review_status", "UNDER_REVIEW"));
                    }
                    return List.of();
                });
        when(erpNextClient.getResource("Customer", "SHOP-1"))
                .thenReturn(Map.of("data", Map.of("customer_name", "Sukarta Aundh")));
        when(erpNextClient.getResource("Supplier", "SUP-1"))
                .thenReturn(Map.of("data", Map.of("supplier_name", "Sanshray Foods")));

        List<Map<String, Object>> vendors = billReviewService.listPaymentsByStatus("UNDER_REVIEW", "Supplier");
        List<Map<String, Object>> branches = billReviewService.listPaymentsByStatus("UNDER_REVIEW", "Customer");

        assertEquals(1, vendors.size());
        assertEquals(1, branches.size());
        assertEquals("Sanshray Foods", vendors.get(0).get("partyName"));
        assertEquals("Sukarta Aundh", branches.get(0).get("partyName"));
    }
}
