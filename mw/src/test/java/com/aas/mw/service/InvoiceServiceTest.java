package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceServiceTest {

    private ErpNextClient erpNextClient;
    private InvoiceService invoiceService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        invoiceService = new InvoiceService(erpNextClient, "", "Administrator", "admin");
    }

    @Test
    void deletesDraftInvoice() {
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-1"))
                .thenReturn(Map.of("data", Map.of("name", "ACC-SINV-1", "status", "Draft")));
        when(erpNextClient.deleteResource("Sales Invoice", "ACC-SINV-1"))
                .thenReturn(Map.of("message", "ok"));

        Map<String, Object> response = invoiceService.deleteInvoice("ACC-SINV-1");

        assertEquals("ok", response.get("message"));
        verify(erpNextClient).deleteResource("Sales Invoice", "ACC-SINV-1");
    }

    @Test
    void rejectsDeletingSubmittedInvoice() {
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2",
                        "status", "Paid",
                        "docstatus", 1,
                        "customer", "Sukarta Aundh")));
        when(erpNextClient.listResources("Payment Entry", Map.of(
                "fields", "[\"name\",\"docstatus\",\"party\",\"party_type\",\"posting_date\"]",
                "filters", "[[\"party_type\",\"=\",\"Customer\"],[\"party\",\"=\",\"Sukarta Aundh\"]]",
                "limit_page_length", 500)))
                .thenReturn(List.of(Map.of("name", "PAY-1", "docstatus", 1)));
        when(erpNextClient.getResource("Payment Entry", "PAY-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-1",
                        "docstatus", 1,
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "ACC-SINV-2")))));
        when(erpNextClient.cancelResource("Payment Entry", "PAY-1")).thenReturn(Map.of());
        when(erpNextClient.deleteResource("Payment Entry", "PAY-1")).thenReturn(Map.of());
        when(erpNextClient.cancelResource("Sales Invoice", "ACC-SINV-2")).thenReturn(Map.of());
        when(erpNextClient.deleteResource("Sales Invoice", "ACC-SINV-2")).thenReturn(Map.of("message", "ok"));

        Map<String, Object> response = invoiceService.deleteInvoice("ACC-SINV-2");

        assertEquals("ok", response.get("message"));
        verify(erpNextClient).cancelResource("Payment Entry", "PAY-1");
        verify(erpNextClient).deleteResource("Payment Entry", "PAY-1");
        verify(erpNextClient).cancelResource("Sales Invoice", "ACC-SINV-2");
        verify(erpNextClient).deleteResource("Sales Invoice", "ACC-SINV-2");
    }

    @Test
    void skipsUnrelatedPaymentsWhenDeletingInvoice() {
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-3"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-3",
                        "status", "Draft",
                        "docstatus", 0,
                        "customer", "Sukarta Aundh")));
        when(erpNextClient.listResources("Payment Entry", Map.of(
                "fields", "[\"name\",\"docstatus\",\"party\",\"party_type\",\"posting_date\"]",
                "filters", "[[\"party_type\",\"=\",\"Customer\"],[\"party\",\"=\",\"Sukarta Aundh\"]]",
                "limit_page_length", 500)))
                .thenReturn(List.of(Map.of("name", "PAY-2", "docstatus", 1)));
        when(erpNextClient.getResource("Payment Entry", "PAY-2"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-2",
                        "docstatus", 1,
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "OTHER-INVOICE")))));
        when(erpNextClient.deleteResource("Sales Invoice", "ACC-SINV-3")).thenReturn(Map.of("message", "ok"));

        Map<String, Object> response = invoiceService.deleteInvoice("ACC-SINV-3");

        assertEquals("ok", response.get("message"));
        verify(erpNextClient, never()).cancelResource("Payment Entry", "PAY-2");
        verify(erpNextClient, never()).deleteResource("Payment Entry", "PAY-2");
    }
}
