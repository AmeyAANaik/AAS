package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
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
import static org.mockito.Mockito.never;

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
    void createsCategoryPaymentWithMatchingInvoiceAllocation() {
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_cash_account", "CASH-ACC")));
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-1",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("100.00"),
                        "aas_category", "Grocery")));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-1"))
                .thenReturn(
                        Map.of("data", Map.of(
                                "name", "ACC-SINV-1",
                                "docstatus", 0,
                                "customer", "SHOP-1",
                                "company", "aas",
                                "grand_total", new BigDecimal("100.00"),
                                "aas_category", "Grocery")),
                        Map.of("data", Map.of(
                                "name", "ACC-SINV-1",
                                "docstatus", 1,
                                "customer", "SHOP-1",
                                "company", "aas",
                                "grand_total", new BigDecimal("100.00"),
                                "outstanding_amount", new BigDecimal("100.00"),
                                "aas_category", "Grocery")));
        when(erpNextClient.submitDoc(org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("name", "ACC-SINV-1", "docstatus", 1)));
        when(paymentDueService.dueByCategory("Customer", "SHOP-1", "Grocery"))
                .thenReturn(Map.of("dueAmount", new BigDecimal("100.00")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-CAT")));
        when(erpNextClient.getResource("Payment Entry", "PAY-CAT"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-CAT")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("SHOP-1");
        request.setCompany("aas");
        request.setAmount(new BigDecimal("100.00"));
        request.setPartyType("Customer");
        request.setCategoryId("Grocery");

        paymentService.createPayment(request, "helper", false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> references = (List<Map<String, Object>>) payload.get("references");

        assertEquals(1, references.size());
        assertEquals("Sales Invoice", references.get(0).get("reference_doctype"));
        assertEquals("ACC-SINV-1", references.get(0).get("reference_name"));
        assertEquals(new BigDecimal("100.00"), references.get(0).get("allocated_amount"));
        assertEquals("Grocery", payload.get("aas_category"));
        assertEquals(new BigDecimal("100.00"), payload.get("aas_due_amount"));
        org.mockito.Mockito.verify(erpNextClient).submitDoc(org.mockito.Mockito.argThat(doc -> "ACC-SINV-1".equals(doc.get("name"))));
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

    @Test
    void resolvesCompanyFromAbbrBeforeLoadingDefaults() {
        when(erpNextClient.getResource("Company", "aas")).thenReturn(Map.of());
        when(erpNextClient.listResources("Company", Map.of(
                "fields", "[\"name\",\"abbr\",\"company_name\"]",
                "limit_page_length", 500)))
                .thenReturn(java.util.List.of(Map.of(
                        "name", "Shree Siddhivinayak Suppliers",
                        "abbr", "AAS",
                        "company_name", "Shree Siddhivinayak Suppliers")));
        when(erpNextClient.getResource("Company", "Shree Siddhivinayak Suppliers"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_cash_account", "CASH-ACC")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-ABBR")));
        when(erpNextClient.getResource("Payment Entry", "PAY-ABBR"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-ABBR")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("SHOP-1");
        request.setCompany("aas");
        request.setAmount(new BigDecimal("10.00"));
        request.setPartyType("Customer");

        paymentService.createPayment(request, "shop-user", false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        assertEquals("Shree Siddhivinayak Suppliers", payloadCaptor.getValue().get("company"));
    }

    @Test
    void createsMissingRtgsModeBeforeUsingItOnPaymentEntry() {
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_bank_account", "BANK-ACC")));
        when(erpNextClient.getResource("Mode of Payment", "RTGS")).thenReturn(Map.of());
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Mode of Payment"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("name", "RTGS")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-RTGS")));
        when(erpNextClient.getResource("Payment Entry", "PAY-RTGS"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-RTGS")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("SHOP-1");
        request.setCompany("aas");
        request.setAmount(new BigDecimal("102530.00"));
        request.setPartyType("Customer");
        request.setModeOfPayment("RTGS");

        paymentService.createPayment(request, "admin", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> modeCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Mode of Payment"), modeCaptor.capture());
        assertEquals("RTGS", modeCaptor.getValue().get("mode_of_payment"));
        assertEquals("Bank", modeCaptor.getValue().get("type"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        assertEquals("RTGS", payloadCaptor.getValue().get("mode_of_payment"));
    }

    @Test
    void omitsModeWhenErpRejectsMissingModeCreation() {
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_receivable_account", "REC-ACC",
                        "default_bank_account", "BANK-ACC")));
        when(erpNextClient.getResource("Mode of Payment", "RTGS")).thenReturn(Map.of());
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Mode of Payment"), org.mockito.Mockito.anyMap()))
                .thenThrow(new IllegalStateException("Could not create Mode of Payment"));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-NO-MODE")));
        when(erpNextClient.getResource("Payment Entry", "PAY-NO-MODE"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-NO-MODE")));

        PaymentRequest request = new PaymentRequest();
        request.setCustomer("SHOP-1");
        request.setCompany("aas");
        request.setAmount(new BigDecimal("102530.00"));
        request.setPartyType("Customer");
        request.setModeOfPayment("RTGS");

        paymentService.createPayment(request, "admin", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(payloadCaptor.getValue()).doesNotContainKey("mode_of_payment");
        verify(erpNextClient, never()).updateResource(org.mockito.Mockito.eq("Mode of Payment"), org.mockito.Mockito.anyString(), org.mockito.Mockito.anyMap());
    }
}
