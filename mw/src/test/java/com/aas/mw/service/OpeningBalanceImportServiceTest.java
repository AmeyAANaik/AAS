package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpeningBalanceImportServiceTest {

    private ErpNextClient erpNextClient;
    private OpeningBalanceImportService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        service = new OpeningBalanceImportService(erpNextClient);
    }

    @Test
    void previewFlagsImbalancedAccounts() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                """
                record_type,account,debit,credit,cost_center,party_id,amount,bill_no,invoice_ref
                ACCOUNT,ACC-1,100,0,CC-1,,,,
                """.getBytes(StandardCharsets.UTF_8));

        when(erpNextClient.getResource("Account", "ACC-1"))
                .thenReturn(Map.of("data", Map.of("company", "AAS")));

        Map<String, Object> preview = service.preview("AAS", file, "2026-04-22");

        assertEquals("AAS", preview.get("companyId"));
        assertEquals("2026-04-22", preview.get("cutoverDate"));
        assertEquals(false, preview.get("isValid"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) preview.get("errors");
        assertNotNull(errors);
        assertFalse(errors.isEmpty());
    }

    @Test
    void applyCreatesDraftOpeningDocs() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                """
                record_type,account,debit,credit,cost_center,party_id,amount,bill_no,invoice_ref
                ACCOUNT,ACC-1,100,0,CC-1,,,,
                ACCOUNT,ACC-2,0,100,,,,
                SUPPLIER,,,,,SUP-1,500,,
                CUSTOMER,,,,,CUST-1,600,,
                """.getBytes(StandardCharsets.UTF_8));

        when(erpNextClient.getResource("Account", "ACC-1"))
                .thenReturn(Map.of("data", Map.of("company", "AAS")));
        when(erpNextClient.getResource("Account", "ACC-2"))
                .thenReturn(Map.of("data", Map.of("company", "AAS")));
        when(erpNextClient.getResource("Supplier", "SUP-1"))
                .thenReturn(Map.of("data", Map.of("name", "SUP-1")));
        when(erpNextClient.getResource("Customer", "CUST-1"))
                .thenReturn(Map.of("data", Map.of("name", "CUST-1")));
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of("default_currency", "INR")));
        when(erpNextClient.getResource("Item", "AAS-VENDOR-BILL"))
                .thenReturn(Map.of("data", Map.of("disabled", 0)));

        when(erpNextClient.listResources(eq("Journal Entry"), anyMap())).thenReturn(List.of());
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap())).thenReturn(List.of());
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap())).thenReturn(List.of());

        when(erpNextClient.createResource(eq("Journal Entry"), anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Journal Entry", "name", "JE-0001")));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Purchase Invoice", "name", "PINV-0001")));
        when(erpNextClient.createResource(eq("Sales Invoice"), anyMap()))
                .thenReturn(Map.of("data", Map.of("doctype", "Sales Invoice", "name", "SINV-0001")));

        Map<String, Object> response = service.apply("AAS", file, "2026-04-22");

        assertEquals("AAS", response.get("companyId"));
        assertEquals("2026-04-22", response.get("cutoverDate"));

        verify(erpNextClient, times(1)).createResource(eq("Journal Entry"), anyMap());
        verify(erpNextClient, times(1)).createResource(eq("Purchase Invoice"), anyMap());
        verify(erpNextClient, times(1)).createResource(eq("Sales Invoice"), anyMap());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> journalCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Journal Entry"), journalCaptor.capture());
        Map<String, Object> journalPayload = journalCaptor.getValue();
        assertEquals("Opening Entry", journalPayload.get("voucher_type"));
        assertEquals("AAS", journalPayload.get("company"));
        assertEquals("2026-04-22", journalPayload.get("posting_date"));
        assertTrue(String.valueOf(journalPayload.get("remark")).contains("AAS Opening Balance Import"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) journalPayload.get("accounts");
        assertEquals(2, lines.size());
    }

    @Test
    void applyThrowsValidationExceptionWhenInvalid() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "opening.csv",
                "text/csv",
                """
                record_type,account,debit,credit,cost_center,party_id,amount,bill_no,invoice_ref
                ACCOUNT,ACC-1,10,10,CC-1,,,,
                """.getBytes(StandardCharsets.UTF_8));

        when(erpNextClient.getResource("Account", "ACC-1"))
                .thenReturn(Map.of("data", Map.of("company", "AAS")));

        assertThrows(OpeningBalanceValidationException.class, () -> service.apply("AAS", file, "2026-04-22"));
    }
}

