package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void approvalRefreshesStaleInvoiceReferenceToCurrentUnpaidCategoryInvoice() {
        Map<String, Object> draftPayment = Map.ofEntries(
                Map.entry("doctype", "Payment Entry"),
                Map.entry("name", "ACC-PAY-2026-00023"),
                Map.entry("docstatus", 0),
                Map.entry("party_type", "Customer"),
                Map.entry("party", "BRANCH-1"),
                Map.entry("paid_amount", new BigDecimal("131000.00")),
                Map.entry("received_amount", new BigDecimal("131000.00")),
                Map.entry("aas_payment_review_status", "UNDER_REVIEW"),
                Map.entry("aas_category", "Grocery"),
                Map.entry("aas_due_amount", new BigDecimal("274508.00")),
                Map.entry("references", List.of(Map.of(
                        "reference_doctype", "Sales Invoice",
                        "reference_name", "ACC-SINV-2026-00048",
                        "allocated_amount", new BigDecimal("131000.00")))));
        Map<String, Object> reallocatedPayment = new java.util.HashMap<>(draftPayment);
        reallocatedPayment.put("references", List.of(Map.of(
                "reference_doctype", "Sales Invoice",
                "reference_name", "ACC-SINV-2026-00049",
                "allocated_amount", new BigDecimal("131000.00"))));
        reallocatedPayment.put("unallocated_amount", BigDecimal.ZERO);

        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00023"))
                .thenReturn(
                        Map.of("data", draftPayment),
                        Map.of("data", reallocatedPayment),
                        Map.of("data", reallocatedPayment),
                        Map.of("data", Map.of(
                                "doctype", "Payment Entry",
                                "name", "ACC-PAY-2026-00023",
                                "docstatus", 1,
                                "aas_payment_review_status", "APPROVED")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("File"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of("name", "FILE-1", "file_name", "voucher.png", "file_url", "/files/voucher.png", "is_private", 0, "creation", "2026-06-11 10:00:00")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "ACC-SINV-2026-00048", "docstatus", 1, "outstanding_amount", BigDecimal.ZERO, "aas_category", "Grocery"),
                        Map.of("name", "ACC-SINV-2026-00049", "docstatus", 1, "outstanding_amount", new BigDecimal("143508.00"), "aas_category", "Grocery")));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00048"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00048",
                        "docstatus", 1,
                        "outstanding_amount", BigDecimal.ZERO,
                        "aas_category", "Grocery")));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00049"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00049",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("143508.00"),
                        "aas_category", "Grocery")));
        when(paymentDueService.dueByCategory("Customer", "BRANCH-1", "Grocery"))
                .thenReturn(Map.of(
                        "dueAmount", new BigDecimal("274508.00"),
                        "underReviewAmount", new BigDecimal("131000.00"),
                        "availableDueAmount", new BigDecimal("143508.00")));
        when(erpNextClient.updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("ACC-PAY-2026-00023"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", reallocatedPayment));
        when(erpNextClient.submitDoc(org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "ACC-PAY-2026-00023", "docstatus", 1)));

        billReviewService.approve(BillReviewService.ITEM_TYPE_PAYMENT, "ACC-PAY-2026-00023", "ok", "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient, org.mockito.Mockito.atLeastOnce())
                .updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("ACC-PAY-2026-00023"), updateCaptor.capture());
        List<Map<String, Object>> updates = new ArrayList<>(updateCaptor.getAllValues());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refreshedReferences = (List<Map<String, Object>>) updates.stream()
                .filter(update -> update.containsKey("references"))
                .findFirst()
                .orElseThrow()
                .get("references");

        assertEquals("ACC-SINV-2026-00049", refreshedReferences.get(0).get("reference_name"));
        assertEquals(new BigDecimal("131000.00"), refreshedReferences.get(0).get("allocated_amount"));
        verify(erpNextClient).submitDoc(org.mockito.Mockito.argThat(doc ->
                String.valueOf(doc.get("references")).contains("ACC-SINV-2026-00049")
                        && !String.valueOf(doc.get("references")).contains("ACC-SINV-2026-00048")));
    }

    @Test
    void approvalRefreshesStaleInvoiceReferenceToOpeningCategoryBalance() {
        Map<String, Object> draftPayment = Map.ofEntries(
                Map.entry("doctype", "Payment Entry"),
                Map.entry("name", "ACC-PAY-2026-00024"),
                Map.entry("docstatus", 0),
                Map.entry("party_type", "Customer"),
                Map.entry("party", "BRANCH-1"),
                Map.entry("paid_amount", new BigDecimal("131000.00")),
                Map.entry("received_amount", new BigDecimal("131000.00")),
                Map.entry("aas_payment_review_status", "UNDER_REVIEW"),
                Map.entry("aas_category", "Grocery"),
                Map.entry("aas_due_amount", new BigDecimal("274508.00")),
                Map.entry("references", List.of(Map.of(
                        "reference_doctype", "Sales Invoice",
                        "reference_name", "ACC-SINV-2026-00048",
                        "allocated_amount", new BigDecimal("131000.00")))));
        Map<String, Object> reallocatedPayment = new java.util.HashMap<>(draftPayment);
        reallocatedPayment.put("references", List.of(Map.of(
                "reference_doctype", "Sales Invoice",
                "reference_name", "OPEN-SINV-GROCERY",
                "allocated_amount", new BigDecimal("131000.00"))));
        reallocatedPayment.put("unallocated_amount", BigDecimal.ZERO);

        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00024"))
                .thenReturn(
                        Map.of("data", draftPayment),
                        Map.of("data", reallocatedPayment),
                        Map.of("data", reallocatedPayment),
                        Map.of("data", Map.of(
                                "doctype", "Payment Entry",
                                "name", "ACC-PAY-2026-00024",
                                "docstatus", 1,
                                "aas_payment_review_status", "APPROVED")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("File"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of("name", "FILE-1", "file_name", "voucher.png", "file_url", "/files/voucher.png", "is_private", 0, "creation", "2026-06-11 10:00:00")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "ACC-SINV-2026-00048", "docstatus", 1, "outstanding_amount", BigDecimal.ZERO, "aas_category", "Grocery", "is_opening", "No"),
                        Map.of("name", "OPEN-SINV-GROCERY", "docstatus", 1, "outstanding_amount", new BigDecimal("274508.00"), "aas_category", "Grocery", "is_opening", "Yes")));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00048"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00048",
                        "docstatus", 1,
                        "outstanding_amount", BigDecimal.ZERO,
                        "aas_category", "Grocery",
                        "is_opening", "No")));
        when(erpNextClient.getResource("Sales Invoice", "OPEN-SINV-GROCERY"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "OPEN-SINV-GROCERY",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("274508.00"),
                        "aas_category", "Grocery",
                        "is_opening", "Yes")));
        when(paymentDueService.dueByCategory("Customer", "BRANCH-1", "Grocery"))
                .thenReturn(Map.of(
                        "dueAmount", new BigDecimal("274508.00"),
                        "underReviewAmount", new BigDecimal("131000.00"),
                        "availableDueAmount", new BigDecimal("143508.00")));
        when(erpNextClient.updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("ACC-PAY-2026-00024"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", reallocatedPayment));
        when(erpNextClient.submitDoc(org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "ACC-PAY-2026-00024", "docstatus", 1)));

        billReviewService.approve(BillReviewService.ITEM_TYPE_PAYMENT, "ACC-PAY-2026-00024", "ok", "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient, org.mockito.Mockito.atLeastOnce())
                .updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("ACC-PAY-2026-00024"), updateCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> refreshedReferences = (List<Map<String, Object>>) updateCaptor.getAllValues().stream()
                .filter(update -> update.containsKey("references"))
                .findFirst()
                .orElseThrow()
                .get("references");

        assertEquals("OPEN-SINV-GROCERY", refreshedReferences.get(0).get("reference_name"));
        assertEquals(new BigDecimal("131000.00"), refreshedReferences.get(0).get("allocated_amount"));
        verify(erpNextClient).submitDoc(org.mockito.Mockito.argThat(doc ->
                String.valueOf(doc.get("references")).contains("OPEN-SINV-GROCERY")
                        && !String.valueOf(doc.get("references")).contains("ACC-SINV-2026-00048")));
    }

    @Test
    void approvalSubmitsDraftReferencedInvoiceBeforePaymentEntry() {
        Map<String, Object> draftPayment = Map.ofEntries(
                Map.entry("doctype", "Payment Entry"),
                Map.entry("name", "ACC-PAY-2026-00087"),
                Map.entry("docstatus", 0),
                Map.entry("party_type", "Customer"),
                Map.entry("party", "BRANCH-1"),
                Map.entry("paid_amount", new BigDecimal("125000.00")),
                Map.entry("received_amount", new BigDecimal("125000.00")),
                Map.entry("aas_payment_review_status", "UNDER_REVIEW"),
                Map.entry("aas_category", "Grocery"),
                Map.entry("aas_due_amount", new BigDecimal("408693.00")));
        Map<String, Object> reallocatedPayment = new java.util.HashMap<>(draftPayment);
        reallocatedPayment.put("references", List.of(Map.of(
                "reference_doctype", "Sales Invoice",
                "reference_name", "ACC-SINV-2026-00410",
                "allocated_amount", new BigDecimal("125000.00"))));
        reallocatedPayment.put("unallocated_amount", BigDecimal.ZERO);
        Map<String, Object> draftInvoice = Map.of(
                "doctype", "Sales Invoice",
                "name", "ACC-SINV-2026-00410",
                "docstatus", 0,
                "grand_total", new BigDecimal("408693.00"),
                "outstanding_amount", BigDecimal.ZERO,
                "aas_category", "Grocery");
        Map<String, Object> submittedInvoice = Map.of(
                "doctype", "Sales Invoice",
                "name", "ACC-SINV-2026-00410",
                "docstatus", 1,
                "grand_total", new BigDecimal("408693.00"),
                "outstanding_amount", new BigDecimal("408693.00"),
                "aas_category", "Grocery");

        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00087"))
                .thenReturn(
                        Map.of("data", draftPayment),
                        Map.of("data", reallocatedPayment),
                        Map.of("data", reallocatedPayment),
                        Map.of("data", Map.of(
                                "doctype", "Payment Entry",
                                "name", "ACC-PAY-2026-00087",
                                "docstatus", 1,
                                "aas_payment_review_status", "APPROVED")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("File"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of("name", "FILE-1", "file_name", "voucher.jpeg", "file_url", "/files/voucher.jpeg", "is_private", 0)));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00410",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("408693.00"),
                        "outstanding_amount", BigDecimal.ZERO,
                        "aas_category", "Grocery")));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00410"))
                .thenReturn(Map.of("data", draftInvoice), Map.of("data", submittedInvoice));
        when(paymentDueService.dueByCategory("Customer", "BRANCH-1", "Grocery"))
                .thenReturn(Map.of(
                        "dueAmount", new BigDecimal("408693.00"),
                        "underReviewAmount", new BigDecimal("125000.00"),
                        "availableDueAmount", new BigDecimal("283693.00")));
        when(erpNextClient.updateResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.eq("ACC-PAY-2026-00087"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", reallocatedPayment));
        when(erpNextClient.submitDoc(org.mockito.Mockito.anyMap()))
                .thenReturn(
                        Map.of("data", submittedInvoice),
                        Map.of("data", Map.of("doctype", "Payment Entry", "name", "ACC-PAY-2026-00087", "docstatus", 1)));

        billReviewService.approve(BillReviewService.ITEM_TYPE_PAYMENT, "ACC-PAY-2026-00087", "ok", "admin");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(erpNextClient);
        inOrder.verify(erpNextClient).submitDoc(org.mockito.Mockito.argThat(doc ->
                "Sales Invoice".equals(doc.get("doctype"))
                        && "ACC-SINV-2026-00410".equals(doc.get("name"))));
        inOrder.verify(erpNextClient).submitDoc(org.mockito.Mockito.argThat(doc ->
                "Payment Entry".equals(doc.get("doctype"))
                        && "ACC-PAY-2026-00087".equals(doc.get("name"))));
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

    @Test
    void approvalBlocksCreditNoteAboveAvailableDue() {
        when(adjustmentNoteErpService.getNote("NOTE-1"))
                .thenReturn(Map.ofEntries(
                        Map.entry("doctype", "Journal Entry"),
                        Map.entry("name", "NOTE-1"),
                        Map.entry("docstatus", 0),
                        Map.entry("posting_date", "2026-06-07"),
                        Map.entry("aas_adjustment_review_status", "UNDER_REVIEW"),
                        Map.entry("aas_adjustment_party_type", "Customer"),
                        Map.entry("aas_adjustment_party", "BRANCH-1"),
                        Map.entry("aas_category", "Grocery"),
                        Map.entry("aas_adjustment_direction", "GIVE"),
                        Map.entry("aas_adjustment_amount", 200000.0),
                        Map.entry("aas_due_amount", 118422.0)));
        when(adjustmentNoteErpService.listNoteAttachments("NOTE-1"))
                .thenReturn(List.of(Map.of("name", "FILE-1")));
        when(adjustmentNoteErpService.asInt(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(adjustmentNoteErpService.asText(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(adjustmentNoteErpService.asDecimal(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(adjustmentNoteErpService.normalizePartyType(org.mockito.ArgumentMatchers.anyString())).thenCallRealMethod();
        when(adjustmentNoteErpService.normalizeDirection(org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
        when(adjustmentNoteErpService.signedImpact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenCallRealMethod();
        when(paymentDueService.dueByCategory("Customer", "BRANCH-1", "Grocery"))
                .thenReturn(Map.of(
                        "dueAmount", new java.math.BigDecimal("118422.00"),
                        "availableDueAmount", new java.math.BigDecimal("118422.00"),
                        "pendingAdjustmentAmount", java.math.BigDecimal.ZERO));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> billReviewService.approve(BillReviewService.ITEM_TYPE_CREDIT_NOTE, "NOTE-1", "ok", "admin"));

        assertEquals("Adjustment amount exceeds available due for this category.", ex.getMessage());
        verify(erpNextClient, never()).submitDoc(org.mockito.Mockito.anyMap());
    }
}
