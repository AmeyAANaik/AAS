package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AnalyticsQueryRequest;
import com.aas.mw.dto.AnalyticsQueryResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        "tax_id", "27ABCDE1234F1Z5")));
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
        Map<String, Object> row = response.getRows().get(0);
        assertEquals("27ABCDE1234F1Z5", row.get("Receiver GSTIN/UIN * (Required)"));
        assertEquals("27-Maharashtra", row.get("Place of Supply * (Required)"));
        assertEquals(1000.0, ((Number) row.get("Taxable Value * (Required)")).doubleValue());
        assertEquals(25.0, ((Number) row.get("CGST Amount")).doubleValue());
        assertEquals(25.0, ((Number) row.get("SGST/UT Amount")).doubleValue());
    }
}
