package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentDueServiceTest {

    private ErpNextClient erpNextClient;
    private PaymentDueService paymentDueService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        paymentDueService = new PaymentDueService(erpNextClient);
    }

    @Test
    void returnsZeroDueWhenNoInvoices() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(BigDecimal.ZERO, response.get("dueAmount"));
    }

    @Test
    void allocatesOutstandingProportionallyAcrossCategories() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-1",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("100.00"))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-1",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("100.00"),
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("60.00")),
                                Map.of("item_code", "ITEM-2", "amount", new BigDecimal("40.00"))))));
        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-A")));
        when(erpNextClient.getResource("Item", "ITEM-2"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-B")));

        Map<String, Object> responseA = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");
        Map<String, Object> responseB = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-B");

        assertEquals(new BigDecimal("60.000000"), responseA.get("dueAmount"));
        assertEquals(new BigDecimal("40.000000"), responseB.get("dueAmount"));
    }

    @Test
    void usesGrandTotalForDraftInvoices() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-DRAFT",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("200.00"),
                        "status", "Draft")));
        when(erpNextClient.getResource("Sales Invoice", "SINV-DRAFT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-DRAFT",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("200.00"),
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("200.00"))))));
        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-A")));

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(new BigDecimal("200.000000"), response.get("dueAmount"));
    }

    @Test
    void ignoresSubmittedInvoicesEvenIfReturned() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-SUB",
                        "docstatus", 1,
                        "grand_total", new BigDecimal("999.00"))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-SUB"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-SUB",
                        "docstatus", 1,
                        "grand_total", new BigDecimal("999.00"),
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("999.00"))))));
        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-A")));

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(BigDecimal.ZERO, response.get("dueAmount"));
    }

    @Test
    void allocatesSupplierDueUsingSourceSalesOrderCategoryWeights() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Purchase Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PINV-1",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("100.00"),
                        "ignored", "x")));
        when(erpNextClient.getResource("Purchase Invoice", "PINV-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PINV-1",
                        "aas_source_sales_order", "SO-1")));
        when(erpNextClient.getResource("Sales Order", "SO-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SO-1",
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "qty", new BigDecimal("2"), "aas_vendor_rate", new BigDecimal("10.00"), "item_group", "CAT-A"),
                                Map.of("item_code", "ITEM-2", "qty", new BigDecimal("4"), "rate", new BigDecimal("20.00"), "item_group", "CAT-B")
                        ))));

        Map<String, Object> responseA = paymentDueService.dueByCategory("Supplier", "SUP-1", "CAT-A");
        Map<String, Object> responseB = paymentDueService.dueByCategory("Supplier", "SUP-1", "CAT-B");

        assertEquals(new BigDecimal("20.000000"), responseA.get("dueAmount"));
        assertEquals(new BigDecimal("80.000000"), responseB.get("dueAmount"));
    }

    @Test
    void allocatesSupplierDueToUncategorizedWhenSourceOrderMissing() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Purchase Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PINV-2",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("50.00"),
                        "ignored", "x")));
        when(erpNextClient.getResource("Purchase Invoice", "PINV-2"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PINV-2",
                        "aas_source_sales_order", "")));

        Map<String, Object> response = paymentDueService.dueByCategory("Supplier", "SUP-1", "CAT-A");
        Map<String, Object> uncategorized = paymentDueService.dueByCategory("Supplier", "SUP-1", "Uncategorized");

        assertEquals(BigDecimal.ZERO, response.get("dueAmount"));
        assertEquals(new BigDecimal("50.00"), uncategorized.get("dueAmount"));
    }
}
