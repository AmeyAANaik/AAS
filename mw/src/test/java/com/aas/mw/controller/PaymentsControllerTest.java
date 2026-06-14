package com.aas.mw.controller;

import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.PaymentDueService;
import com.aas.mw.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentsControllerTest {

    private PaymentDueService paymentDueService;
    private PaymentsController paymentsController;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        PaymentService paymentService = mock(PaymentService.class);
        paymentDueService = mock(PaymentDueService.class);
        ErpNextFileService fileService = mock(ErpNextFileService.class);
        objectMapper = new ObjectMapper();
        paymentsController = new PaymentsController(paymentService, paymentDueService, fileService, objectMapper);
    }

    @Test
    void createPaymentWithAttachmentsRejectsSupplierPaymentAboveCorrectedAvailableDue() throws Exception {
        when(paymentDueService.dueByCategory("Supplier", "SUP-1", "Grocery"))
                .thenReturn(Map.of(
                        "dueAmount", new BigDecimal("600.00"),
                        "underReviewAmount", BigDecimal.ZERO,
                        "availableDueAmount", new BigDecimal("600.00")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, "sid=1");
        MockMultipartFile payment = new MockMultipartFile(
                "payment",
                "payment.json",
                "application/json",
                objectMapper.writeValueAsBytes(Map.of(
                        "customer", "SUP-1",
                        "company", "AAS",
                        "amount", new BigDecimal("700.00"),
                        "partyType", "Supplier",
                        "categoryId", "Grocery")));
        MockMultipartFile evidence = new MockMultipartFile("files", "proof.pdf", "application/pdf", new byte[] {1});

        ResponseEntity<Map<String, Object>> response = paymentsController.createPaymentWithAttachments(
                payment,
                new MockMultipartFile[] {evidence},
                request,
                null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("OVERPAYMENT_NOT_ALLOWED", response.getBody().get("errorCode"));
        assertEquals(new BigDecimal("600.00"), response.getBody().get("availableDueAmount"));
        assertEquals(new BigDecimal("700.00"), response.getBody().get("requestedAmount"));
    }
}
