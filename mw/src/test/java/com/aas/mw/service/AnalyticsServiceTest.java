package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AnalyticsQueryRequest;
import com.aas.mw.dto.AnalyticsQueryResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    private ErpNextClient erpNextClient;
    private InvoiceService invoiceService;
    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        erpNextClient = mock(ErpNextClient.class);
        invoiceService = mock(InvoiceService.class);
        analyticsService = new AnalyticsService(erpNextClient, invoiceService);
    }

    @Test
    void usesPurchaseInvoiceCostWhenSalesOrderVendorRateMissing() {
        when(invoiceService.listInvoices("Customer", null, "2026-06-09", "2026-06-15"))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-1",
                                "customer", "Branch A",
                                "posting_date", "2026-06-10",
                                "grand_total", 15700.0,
                                "aas_source_sales_order", "SO-1",
                                "aas_category", "Cat A")));

        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of(Map.of("name", "SO-1", "aas_vendor", "Vendor A", "aas_category", "Cat A")));
        when(erpNextClient.listResources(eq("Sales Order Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "parent", "SO-1",
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "item_group", "Cat A",
                        "qty", 2,
                        "aas_vendor_rate", 0)));
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PINV-1",
                        "posting_date", "2026-06-10",
                        "grand_total", 12000.0,
                        "docstatus", 1,
                        "status", "Submitted",
                        "modified", "2026-06-10 10:00:00",
                        "creation", "2026-06-10 09:00:00",
                        "aas_source_sales_order", "SO-1",
                        "aas_replaced_by", "",
                        "aas_invoice_version_status", "CURRENT")));
        when(erpNextClient.listResources(eq("Sales Invoice Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "parent", "SINV-1",
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "item_group", "Cat A",
                        "qty", 2,
                        "rate", 7850.0,
                        "amount", 15700.0,
                        "aas_vendor_rate", 0)));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(Map.of("name", "ITEM-1", "item_group", "Cat A", "aas_vendor", "Vendor A")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-06-09");
        request.setDateTo("2026-06-15");
        request.setDimensions(List.of("date"));
        request.setMetrics(List.of("revenue", "cost", "profit", "orders"));

        AnalyticsQueryResponse response = analyticsService.query(request);

        assertEquals(1, response.getRows().size());
        assertEquals(15700.0, ((Number) response.getRows().get(0).get("revenue")).doubleValue());
        assertEquals(12000.0, ((Number) response.getRows().get(0).get("cost")).doubleValue());
        assertEquals(3700.0, ((Number) response.getRows().get(0).get("profit")).doubleValue());
    }

    @Test
    void warnsWhenNeitherSalesNorPurchaseCostExists() {
        when(invoiceService.listInvoices("Customer", null, "2026-06-09", "2026-06-15"))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-1",
                                "customer", "Branch A",
                                "posting_date", "2026-06-10",
                                "grand_total", 15700.0,
                                "aas_source_sales_order", "SO-1",
                                "aas_category", "Cat A")));

        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of(Map.of("name", "SO-1", "aas_vendor", "Vendor A", "aas_category", "Cat A")));
        when(erpNextClient.listResources(eq("Sales Order Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "parent", "SO-1",
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "item_group", "Cat A",
                        "qty", 2,
                        "aas_vendor_rate", 0)));
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of());
        when(erpNextClient.listResources(eq("Sales Invoice Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "parent", "SINV-1",
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "item_group", "Cat A",
                        "qty", 2,
                        "rate", 7850.0,
                        "amount", 15700.0,
                        "aas_vendor_rate", 0)));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(Map.of("name", "ITEM-1", "item_group", "Cat A", "aas_vendor", "Vendor A")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-06-09");
        request.setDateTo("2026-06-15");
        request.setDimensions(List.of("date"));
        request.setMetrics(List.of("revenue", "cost", "profit"));

        AnalyticsQueryResponse response = analyticsService.query(request);

        assertEquals(0.0, ((Number) response.getRows().get(0).get("cost")).doubleValue());
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("neither vendor rate nor purchase invoice cost")));
    }

    @Test
    void gstr1B2bBuildsRegisteredCustomerInvoiceRows() {
        when(invoiceService.listInvoices("Customer", null, "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-1",
                        "customer", "Branch A",
                        "company", "AAS",
                        "posting_date", "2026-04-10",
                        "grand_total", 1050.0)));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of("items", List.of(
                        Map.of(
                                "item_code", "ITEM-1",
                                "item_name", "Item 1",
                                "uom", "Nos",
                                "qty", 1,
                                "amount", 500.0,
                                "aas_gst_percent", 5.0),
                        Map.of(
                                "item_code", "ITEM-2",
                                "item_name", "Item 2",
                                "uom", "Nos",
                                "qty", 1,
                                "amount", 500.0,
                                "aas_gst_percent", 5.0)))));
        when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "Branch A",
                        "customer_name", "Branch A",
                        "tax_id", "27ABCDE1234F1Z5")));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "ITEM-1",
                                "item_name", "Item 1",
                                "stock_uom", "Nos",
                                "aas_vendor_hsn_code", "100100",
                                "aas_gst_percent", 5.0),
                        Map.of(
                                "name", "ITEM-2",
                                "item_name", "Item 2",
                                "stock_uom", "Nos",
                                "aas_vendor_hsn_code", "100200",
                                "aas_gst_percent", 5.0)));
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of("tax_id", "27AAAAA0000A1Z5")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-04-01");
        request.setDateTo("2026-04-30");
        request.setFilters(Map.of("gstReport", "b2b"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(2, response.getRows().size());
        Map<String, Object> row = response.getRows().get(0);
        assertEquals("27ABCDE1234F1Z5", row.get("Receiver GSTIN/UIN * (Required)"));
        assertEquals("27-Maharashtra", row.get("Place of Supply * (Required)"));
        assertEquals(500.0, ((Number) row.get("Taxable Value * (Required)")).doubleValue());
        assertEquals(12.5, ((Number) row.get("CGST Amount")).doubleValue());
        assertEquals(12.5, ((Number) row.get("SGST/UT Amount")).doubleValue());
        assertEquals(1050.0, ((Number) response.getTotalsRow().get("Total Invoice Value * (Required)")).doubleValue());
        assertEquals(1000.0, ((Number) response.getTotalsRow().get("Taxable Value * (Required)")).doubleValue());
        assertFalse(response.getTotalsRow().containsKey("HSN/SAC"));
    }

    @Test
    void gstr1B2bFallsBackToCustomerDocumentWhenGstinIsNotListable() {
        when(invoiceService.listInvoices("Customer", null, "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-1",
                        "customer", "Branch A",
                        "company", "AAS",
                        "posting_date", "2026-04-10",
                        "grand_total", 1050.0)));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of("items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "uom", "Nos",
                        "qty", 2,
                        "amount", 1000.0,
                        "aas_gst_percent", 5.0)))));
        when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<?, ?> params = invocation.getArgument(1);
                    if (String.valueOf(params.get("fields")).contains("gstin")) {
                        throw new RuntimeException("Unknown column gstin");
                    }
                    return List.of(Map.of(
                            "name", "Branch A",
                            "customer_name", "Branch A",
                            "tax_id", ""));
                });
        when(erpNextClient.getResource("Customer", "Branch A"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "Branch A",
                        "customer_name", "Branch A",
                        "gstin", "27ABCDE1234F1Z5")));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ITEM-1",
                        "item_name", "Item 1",
                        "stock_uom", "Nos",
                        "aas_vendor_hsn_code", "100100",
                        "aas_gst_percent", 5.0)));
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of("tax_id", "27AAAAA0000A1Z5")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-04-01");
        request.setDateTo("2026-04-30");
        request.setFilters(Map.of("gstReport", "b2b"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(1, response.getRows().size());
        assertEquals("27ABCDE1234F1Z5", response.getRows().get(0).get("Receiver GSTIN/UIN * (Required)"));
    }

    @Test
    void gstr1B2bWarnsWhenInvoicesHaveNoRegisteredCustomerGstin() {
        when(invoiceService.listInvoices("Customer", null, "2026-04-01", "2026-04-30"))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-1",
                        "customer", "Branch A",
                        "company", "AAS",
                        "posting_date", "2026-04-10",
                        "grand_total", 1050.0)));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of("items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "uom", "Nos",
                        "qty", 2,
                        "amount", 1000.0,
                        "aas_gst_percent", 5.0)))));
        when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "Branch A",
                        "customer_name", "Branch A",
                        "tax_id", "")));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ITEM-1",
                        "item_name", "Item 1",
                        "stock_uom", "Nos",
                        "aas_vendor_hsn_code", "100100",
                        "aas_gst_percent", 5.0)));
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of("tax_id", "27AAAAA0000A1Z5")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-04-01");
        request.setDateTo("2026-04-30");
        request.setFilters(Map.of("gstReport", "b2b"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(0, response.getRows().size());
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("Customer master")));
    }

    @Test
    void gstr1CdnrWarnsWhenApprovedCustomerNotesHaveNoGstin() {
        when(erpNextClient.listResources(eq("Journal Entry"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-JV-2026-00013",
                        "posting_date", "2026-08-12",
                        "docstatus", 1,
                        "aas_adjustment_party_type", "Customer",
                        "aas_adjustment_party", "Sukhkarta Pure Veg Dining Hall, Deccan",
                        "aas_adjustment_review_status", "APPROVED",
                        "aas_adjustment_amount", 1578.0,
                        "aas_adjustment_note_type", "CREDIT_NOTE",
                        "aas_reference_invoice", "ACC-SINV-1",
                        "aas_adjustment_item_name", "BAJRI ATTA")));
        when(erpNextClient.getResource("Customer", "Sukhkarta Pure Veg Dining Hall, Deccan"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "Sukhkarta Pure Veg Dining Hall, Deccan",
                        "customer_name", "Sukhkarta Pure Veg Dining Hall, Deccan")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-08-01");
        request.setDateTo("2026-08-24");
        request.setFilters(Map.of("gstReport", "cdnr"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(0, response.getRows().size());
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("Customer master")));
    }

    @Test
    void gstr1B2cFallsBackToItemNameWhenInvoiceItemCodeDoesNotMatchItemMaster() {
        when(invoiceService.listInvoices("Customer", null, "2026-07-01", "2026-07-31"))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-1",
                        "customer", "Branch A",
                        "company", "AAS",
                        "posting_date", "2026-07-10",
                        "grand_total", 1050.0)));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of("items", List.of(Map.of(
                        "item_code", "Legacy Paneer Code",
                        "item_name", "Paneer",
                        "uom", "Kg",
                        "qty", 2,
                        "amount", 1000.0,
                        "aas_gst_percent", 5.0)))));
        when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "Branch A",
                        "customer_name", "Branch A",
                        "tax_id", "")));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<?, ?> params = invocation.getArgument(1);
                    String filters = String.valueOf(params.get("filters"));
                    if (filters.contains("\"item_name\"")) {
                        return List.of(Map.of(
                                "name", "ITEM-PANEER",
                                "item_name", "Paneer",
                                "stock_uom", "Kg",
                                "aas_vendor_hsn_code", "04061000",
                                "aas_gst_percent", 5.0));
                    }
                    return List.of();
                });
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of("tax_id", "27AAAAA0000A1Z5")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-07-01");
        request.setDateTo("2026-07-31");
        request.setFilters(Map.of("gstReport", "b2c"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(1, response.getRows().size());
        Map<String, Object> row = response.getRows().get(0);
        assertEquals("04061000", row.get("HSN/SAC"));
        assertEquals("Paneer", row.get("Item Description"));
        assertEquals("Kg", row.get("UQC"));
    }

    @Test
    void gstr1B2cBlanksInvalidAndMissingHsnAndDoesNotTotalTaxRate() {
        when(invoiceService.listInvoices("Customer", null, "2026-07-01", "2026-07-31"))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-1",
                        "customer", "Branch A",
                        "company", "AAS",
                        "posting_date", "2026-07-10",
                        "grand_total", 1575.0)));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of("items", List.of(
                        Map.of(
                                "item_code", "ITEM-BAD",
                                "item_name", "Cow Milk",
                                "uom", "Litre",
                                "qty", 10,
                                "amount", 1000.0,
                                "aas_gst_percent", 5.0),
                        Map.of(
                                "item_code", "ITEM-MISSING",
                                "item_name", "Transport Charges",
                                "uom", "Nos",
                                "qty", 1,
                                "amount", 500.0,
                                "aas_gst_percent", 5.0)))));
        when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "Branch A",
                        "customer_name", "Branch A",
                        "tax_id", "")));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<?, ?> params = invocation.getArgument(1);
                    String filters = String.valueOf(params.get("filters"));
                    if (filters.contains("\"name\"")) {
                        return List.of(
                                Map.of(
                                        "name", "ITEM-BAD",
                                        "item_name", "Cow Milk",
                                        "stock_uom", "Litre",
                                        "aas_vendor_hsn_code", "0001",
                                        "aas_gst_percent", 5.0),
                                Map.of(
                                        "name", "ITEM-MISSING",
                                        "item_name", "Transport Charges",
                                        "stock_uom", "Nos",
                                        "aas_gst_percent", 5.0));
                    }
                    return List.of();
                });
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of("tax_id", "27AAAAA0000A1Z5")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-07-01");
        request.setDateTo("2026-07-31");
        request.setFilters(Map.of("gstReport", "b2c"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(2, response.getRows().size());
        assertTrue(response.getRows().stream().allMatch(row -> "".equals(row.get("HSN/SAC"))));
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("invalid or suspicious HSN")));
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("no HSN in invoice or item master")));
        assertFalse(response.getTotalsRow().containsKey("Tax Rate * (Required)"));
    }

    @Test
    void gstr1DocsWarnsThatSequenceGapsAreNotCounted() {
        when(invoiceService.listInvoices("Customer", null, "2026-07-01", "2026-07-31"))
                .thenReturn(List.of(Map.of("name", "SINV-1"), Map.of("name", "SINV-3")));

        AnalyticsQueryRequest request = new AnalyticsQueryRequest();
        request.setDateFrom("2026-07-01");
        request.setDateTo("2026-07-31");
        request.setFilters(Map.of("gstReport", "docs"));

        AnalyticsQueryResponse response = analyticsService.gstr1Report(request);

        assertEquals(1, response.getRows().size());
        assertEquals(2, response.getRows().get(0).get("Total No of Invoices * (Required)"));
        assertTrue(response.getWarnings().stream().anyMatch(message -> message.contains("cancelled/deleted sequence gaps are not counted")));
    }
}
