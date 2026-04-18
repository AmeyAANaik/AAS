package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.PaymentRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private ErpNextClient erpNextClient;
    private PaymentDueService paymentDueService;
    private PaymentService paymentService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        paymentDueService = mock(PaymentDueService.class);
        paymentService = new PaymentService(erpNextClient, paymentDueService);
    }

    @Test
    void createsPaymentEntryWithAllocationAndSurplus() {
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-1",
                        "docstatus", 1,
                        "customer", "SHOP-1",
                        "company", "aas",
                        "outstanding_amount", new BigDecimal("100.00"))));
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_cash_account", "CASH-ACC")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-1")));
        when(erpNextClient.getResource("Payment Entry", "PAY-1"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-1")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("IGNORED");
        request.setCompany("IGNORED");
        request.setInvoiceId("ACC-SINV-1");
        request.setAmount(new BigDecimal("150.00"));
        request.setPaymentDate("2026-04-14");
        request.setModeOfPayment("Cash");
        request.setPartyType("Customer");
        request.setReferenceNo("REF-1");
        request.setReferenceDate("2026-04-14");

        paymentService.createPayment(request, "shop-user", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();

        assertEquals("Receive", payload.get("payment_type"));
        assertEquals("Customer", payload.get("party_type"));
        assertEquals("SHOP-1", payload.get("party"));
        assertEquals("aas", payload.get("company"));
        assertEquals(new BigDecimal("150.00"), payload.get("paid_amount"));
        assertEquals(new BigDecimal("150.00"), payload.get("received_amount"));
        assertEquals("REC-ACC", payload.get("paid_from"));
        assertEquals("CASH-ACC", payload.get("paid_to"));
        assertEquals("2026-04-14", payload.get("posting_date"));
        assertEquals("Cash", payload.get("mode_of_payment"));
        assertEquals("REF-1", payload.get("reference_no"));
        assertEquals("2026-04-14", payload.get("reference_date"));
        assertEquals(new BigDecimal("50.00"), payload.get("unallocated_amount"));
        assertTrue(payload.containsKey("references"));
        assertEquals("UNDER_REVIEW", payload.get("aas_payment_review_status"));
        assertEquals("shop-user", payload.get("aas_payment_created_by"));
        assertTrue(String.valueOf(payload.get("aas_payment_created_at")).length() >= 10);
        org.mockito.Mockito.verify(erpNextClient, org.mockito.Mockito.never()).submitDoc(org.mockito.Mockito.anyMap());
    }

    @Test
    void skipsZeroAllocationReferencesWhenInvoiceFullyPaid() {
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-PAID"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-PAID",
                        "docstatus", 1,
                        "customer", "SHOP-1",
                        "company", "aas",
                        "outstanding_amount", BigDecimal.ZERO)));
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_cash_account", "CASH-ACC")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-2")));
        when(erpNextClient.getResource("Payment Entry", "PAY-2"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-2")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("SHOP-1");
        request.setCompany("aas");
        request.setInvoiceId("ACC-SINV-PAID");
        request.setAmount(new BigDecimal("25.00"));
        request.setPartyType("Customer");

        paymentService.createPayment(request, "shop-user", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();

        assertFalse(payload.containsKey("references"));
        assertEquals(new BigDecimal("25.00"), payload.get("unallocated_amount"));
    }

    @Test
    void createsDraftPaymentForNonAdminAndDoesNotSubmit() {
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_cash_account", "CASH-ACC")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-DRAFT")));
        when(erpNextClient.getResource("Payment Entry", "PAY-DRAFT"))
                .thenReturn(Map.of("data", Map.of(
                        "doctype", "Payment Entry",
                        "name", "PAY-DRAFT",
                        "docstatus", 0,
                        "aas_payment_review_status", "UNDER_REVIEW")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("SHOP-1");
        request.setCompany("aas");
        request.setAmount(new BigDecimal("10.00"));
        request.setPartyType("Customer");

        Map<String, Object> result = paymentService.createPayment(request, "shop-user", false);

        assertEquals("PAY-DRAFT", result.get("name"));
        assertEquals(0, ((Number) result.get("docstatus")).intValue());
        assertEquals("UNDER_REVIEW", result.get("aas_payment_review_status"));
        org.mockito.Mockito.verify(erpNextClient, org.mockito.Mockito.never()).submitDoc(org.mockito.Mockito.anyMap());
    }
}
