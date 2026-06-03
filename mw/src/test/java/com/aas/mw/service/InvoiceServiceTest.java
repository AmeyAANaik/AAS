package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        invoiceService = new InvoiceService(erpNextClient, mock(PaymentDueService.class), "", "Administrator", "admin");
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

    @Test
    void listInvoicesExcludesOldVersionInvoices() {
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "SINV-CURRENT", "docstatus", 1, "status", "Unpaid", "aas_invoice_version_status", "CURRENT"),
                        Map.of("name", "SINV-OLD", "docstatus", 1, "status", "Unpaid", "aas_invoice_version_status", "OLD")));

        List<Map<String, Object>> invoices = invoiceService.listInvoices(null, null, null);

        assertEquals(1, invoices.size());
        assertEquals("SINV-CURRENT", invoices.get(0).get("name"));
    }

    @Test
    void listInvoicesExcludesOpeningInvoices() {
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "SINV-OPENING", "docstatus", 1, "status", "Unpaid", "is_opening", "Yes", "aas_invoice_version_status", "CURRENT"),
                        Map.of("name", "SINV-NORMAL", "docstatus", 1, "status", "Unpaid", "is_opening", "No", "aas_invoice_version_status", "CURRENT")));

        List<Map<String, Object>> invoices = invoiceService.listInvoices(null, null, null);

        assertEquals(1, invoices.size());
        assertEquals("SINV-NORMAL", invoices.get(0).get("name"));
    }

    @Test
    void downloadPdfBackfillsMissingCategoryDueSnapshot() {
        PaymentDueService paymentDueService = mock(PaymentDueService.class);
        invoiceService = new InvoiceService(erpNextClient, paymentDueService, "", "Administrator", "admin");

        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00020"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00020",
                        "customer", "Sukhkarta Pure Veg Dining Hall, Kothrud",
                        "company", "Shree Siddhivinayak Suppliers",
                        "grand_total", 148790.55,
                        "rounding_adjustment", 0.45,
                        "outstanding_amount", 148791.0,
                        "docstatus", 0,
                        "aas_category", "Grocery",
                        "aas_previous_due", 0.0,
                        "aas_current_pending", 0.0)));
        when(paymentDueService.dueByCategory("Customer", "Sukhkarta Pure Veg Dining Hall, Kothrud", "Grocery"))
                .thenReturn(Map.of("dueAmount", 375575.251006));
        when(erpNextClient.getResource("Company", "Shree Siddhivinayak Suppliers"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "Shree Siddhivinayak Suppliers",
                        "aas_sales_invoice_print_format", "AAS Sales Invoice Print")));
        when(erpNextClient.downloadPdfWithSession(
                eq("Sales Invoice"),
                eq("ACC-SINV-2026-00020"),
                eq(Map.of("format", "AAS Sales Invoice Print")),
                isNull()))
                .thenReturn("%PDF-test".getBytes());

        byte[] pdf = invoiceService.downloadPdf("ACC-SINV-2026-00020");

        assertEquals("%PDF-test", new String(pdf));
        verify(erpNextClient).updateResource("Sales Invoice", "ACC-SINV-2026-00020", Map.of(
                "aas_previous_due", 226784.25,
                "aas_current_pending", 375575.25));
    }

    @Test
    void downloadPdfRepairsStaleCategoryDueSnapshot() {
        PaymentDueService paymentDueService = mock(PaymentDueService.class);
        invoiceService = new InvoiceService(erpNextClient, paymentDueService, "", "Administrator", "admin");

        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00023"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00023",
                        "customer", "Sukhkarta Pure Veg Dining Hall, Kothrud",
                        "company", "Shree Siddhivinayak Suppliers",
                        "grand_total", 148790.55,
                        "rounding_adjustment", 0.45,
                        "outstanding_amount", 148791.0,
                        "docstatus", 0,
                        "aas_category", "Grocery",
                        "aas_previous_due", 670077.45,
                        "aas_current_pending", 818868.0)));
        when(paymentDueService.dueByCategory("Customer", "Sukhkarta Pure Veg Dining Hall, Kothrud", "Grocery"))
                .thenReturn(Map.of("dueAmount", 276602.0));
        when(erpNextClient.getResource("Company", "Shree Siddhivinayak Suppliers"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "Shree Siddhivinayak Suppliers",
                        "aas_sales_invoice_print_format", "AAS Sales Invoice Print")));
        when(erpNextClient.downloadPdfWithSession(
                eq("Sales Invoice"),
                eq("ACC-SINV-2026-00023"),
                eq(Map.of("format", "AAS Sales Invoice Print")),
                isNull()))
                .thenReturn("%PDF-test".getBytes());

        byte[] pdf = invoiceService.downloadPdf("ACC-SINV-2026-00023");

        assertEquals("%PDF-test", new String(pdf));
        verify(erpNextClient).updateResource("Sales Invoice", "ACC-SINV-2026-00023", Map.of(
                "aas_previous_due", 127811.0,
                "aas_current_pending", 276602.0));
    }
}
