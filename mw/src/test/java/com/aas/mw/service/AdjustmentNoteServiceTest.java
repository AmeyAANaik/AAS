package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AdjustmentNoteRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class AdjustmentNoteServiceTest {

    private ErpNextClient erpNextClient;
    private PaymentDueService paymentDueService;
    private ErpNextFileService fileService;
    private AdjustmentNoteErpService adjustmentNoteErpService;
    private AdjustmentNoteService adjustmentNoteService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        paymentDueService = mock(PaymentDueService.class);
        fileService = mock(ErpNextFileService.class);
        adjustmentNoteErpService = new AdjustmentNoteErpService(erpNextClient);
        adjustmentNoteService = new AdjustmentNoteService(erpNextClient, paymentDueService, fileService, adjustmentNoteErpService);
    }

    @Test
    void createDraftBlocksBranchCreditNoteAboveAvailableDue() {
        AdjustmentNoteRequest request = new AdjustmentNoteRequest();
        request.setPartyType("Customer");
        request.setPartyId("BRANCH-1");
        request.setCategoryId("Grocery");
        request.setItemCode("GROCERY");
        request.setDirection("GIVE");
        request.setAmount(new BigDecimal("200000.00"));
        MockMultipartFile file = new MockMultipartFile("files", "proof.png", "image/png", new byte[] {1});

        when(paymentDueService.dueByCategory("Customer", "BRANCH-1", "Grocery"))
                .thenReturn(Map.of(
                        "dueAmount", new BigDecimal("118422.00"),
                        "availableDueAmount", new BigDecimal("118422.00"),
                        "pendingAdjustmentAmount", BigDecimal.ZERO));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adjustmentNoteService.createDraftWithAttachments(request, new MockMultipartFile[] {file}, "admin", "sid=1"));

        assertEquals("Adjustment amount exceeds available due for this category.", ex.getMessage());
        verify(erpNextClient, never()).createResource(eq("Journal Entry"), anyMap());
    }
}
