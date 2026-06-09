package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.ErpSetupProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdjustmentNotePdfServiceTest {

    private ErpNextClient erpNextClient;
    private AdjustmentNotePdfService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        ErpSetupProperties erpSetupProperties = new ErpSetupProperties();
        erpSetupProperties.setFullName("Administrator");
        erpSetupProperties.setPassword("admin");
        service = new AdjustmentNotePdfService(erpNextClient, erpSetupProperties);
    }

    @Test
    void downloadPdfAllowsApprovedAdjustmentNotes() {
        when(erpNextClient.getResource("Journal Entry", "ACC-JV-2026-00001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-JV-2026-00001",
                        "docstatus", 1,
                        "aas_adjustment_review_status", "APPROVED")));
        byte[] pdfBytes = "%PDF-test".getBytes();
        when(erpNextClient.downloadPdf(
                eq("Journal Entry"),
                eq("ACC-JV-2026-00001"),
                eq(Map.of("format", "AAS Adjustment Note Print"))))
                .thenReturn(pdfBytes);

        byte[] result = service.downloadPdf("ACC-JV-2026-00001");

        assertArrayEquals(pdfBytes, result);
    }

    @Test
    void downloadPdfRejectsPendingAdjustmentNotes() {
        when(erpNextClient.getResource("Journal Entry", "ACC-JV-2026-00002"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-JV-2026-00002",
                        "docstatus", 0,
                        "aas_adjustment_review_status", "UNDER_REVIEW")));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.downloadPdf("ACC-JV-2026-00002"));

        assertEquals("Credit/debit note receipt is available only after approval.", ex.getMessage());
    }
}
