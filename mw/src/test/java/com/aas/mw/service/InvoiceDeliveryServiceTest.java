package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.InvoiceDeliveryRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceDeliveryServiceTest {

    private ErpNextClient erpNextClient;
    private InvoiceService invoiceService;
    private ErpNextEmailInvoiceAdapter emailAdapter;
    private InvoiceDeliveryService invoiceDeliveryService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        invoiceService = mock(InvoiceService.class);
        emailAdapter = new ErpNextEmailInvoiceAdapter(erpNextClient, "finance@aas.local");
        invoiceDeliveryService = new InvoiceDeliveryService(
                erpNextClient,
                invoiceService,
                List.of(
                        emailAdapter,
                        new WhatsAppInvoiceAdapter("", "")));

        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-1",
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "posting_date", "2026-04-11",
                        "grand_total", 1234.5,
                        "outstanding_amount", 1234.5)));
        when(erpNextClient.getResource("Customer", "Sukarta Aundh"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "Sukarta Aundh",
                        "customer_name", "Sukarta Aundh",
                        "aas_invoice_email", "billing@sukarta.example",
                        "aas_whatsapp_number", "+919405925917")));
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "AAS",
                        "aas_sales_invoice_print_format", "AAS Sales Invoice Print")));
        when(erpNextClient.listResources(eq("Email Account"), anyMap()))
                .thenReturn(List.of(Map.of("name", "AAS Mail", "enable_outgoing", 1)));
        when(invoiceService.downloadPdf("ACC-SINV-1")).thenReturn("%PDF".getBytes());
        when(erpNextClient.postMethod(eq("frappe.core.doctype.communication.email.make"), anyMap()))
                .thenReturn(Map.of("name", "COMM-0001"));
    }

    @Test
    void previewResolvesConfiguredRecipients() {
        Map<String, Object> preview = invoiceDeliveryService.preview("ACC-SINV-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> email = (Map<String, Object>) preview.get("email");
        @SuppressWarnings("unchecked")
        Map<String, Object> whatsapp = (Map<String, Object>) preview.get("whatsapp");

        assertEquals("billing@sukarta.example", email.get("recipient"));
        assertEquals("+919405925917", whatsapp.get("recipient"));
        assertEquals(Boolean.TRUE, email.get("configured"));
        assertEquals(Boolean.FALSE, whatsapp.get("configured"));
    }

    @Test
    void sendEmailUsesErpCommunicationMethod() {
        Map<String, Object> response = invoiceDeliveryService.sendEmail("ACC-SINV-1", new InvoiceDeliveryRequest());

        assertEquals("email", response.get("channel"));
        assertEquals("billing@sukarta.example", response.get("recipient"));
        verify(erpNextClient).postMethod(eq("frappe.core.doctype.communication.email.make"), anyMap());
    }

    @Test
    void sendEmailRejectsMissingRecipient() {
        when(erpNextClient.getResource("Customer", "Sukarta Aundh"))
                .thenReturn(Map.of("data", Map.of("name", "Sukarta Aundh", "customer_name", "Sukarta Aundh")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> invoiceDeliveryService.sendEmail("ACC-SINV-1", new InvoiceDeliveryRequest()));

        assertEquals("No email recipient is configured for this branch.", error.getMessage());
    }
}
