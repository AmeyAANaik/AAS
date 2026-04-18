package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.PaymentRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceSupplierTest {

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
    void createsSupplierPayPaymentEntry() {
        when(erpNextClient.getResource("Company", "aas"))
                .thenReturn(Map.of("data", Map.of(
                        "default_payable_account", "PAY-ACC",
                        "default_cash_account", "CASH-ACC")));
        when(erpNextClient.createResource(org.mockito.Mockito.eq("Payment Entry"), org.mockito.Mockito.anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-10")));
        when(erpNextClient.getResource("Payment Entry", "PAY-10"))
                .thenReturn(Map.of("data", Map.of("doctype", "Payment Entry", "name", "PAY-10")));

        PaymentRequest request = new PaymentRequest();
        request.setPartyType("Supplier");
        request.setCustomer("VENDOR-1");
        request.setCompany("aas");
        request.setAmount(new BigDecimal("250.00"));

        paymentService.createPayment(request, "admin", true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(org.mockito.Mockito.eq("Payment Entry"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();

        assertEquals("Pay", payload.get("payment_type"));
        assertEquals("Supplier", payload.get("party_type"));
        assertEquals("VENDOR-1", payload.get("party"));
        assertEquals("aas", payload.get("company"));
        assertEquals("CASH-ACC", payload.get("paid_from"));
        assertEquals("PAY-ACC", payload.get("paid_to"));
        assertEquals(new BigDecimal("250.00"), payload.get("paid_amount"));
        assertEquals(new BigDecimal("250.00"), payload.get("received_amount"));
        assertEquals("UNDER_REVIEW", payload.get("aas_payment_review_status"));
        org.mockito.Mockito.verify(erpNextClient, org.mockito.Mockito.never()).submitDoc(org.mockito.Mockito.anyMap());
    }
}
