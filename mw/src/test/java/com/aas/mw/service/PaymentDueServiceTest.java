package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
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
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("100.00"),
                        "grand_total", new BigDecimal("100.00"))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-1"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-1",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("100.00"),
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
    void usesOutstandingAmountForSubmittedInvoices() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-SUB",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("200.00"),
                        "grand_total", new BigDecimal("999.00"),
                        "status", "Unpaid")));
        when(erpNextClient.getResource("Sales Invoice", "SINV-SUB"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-SUB",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("200.00"),
                        "grand_total", new BigDecimal("999.00"),
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("200.00"))))));
        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-A")));

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(new BigDecimal("200.000000"), response.get("dueAmount"));
    }

    @Test
    void includesDraftInvoicesUsingGrandTotal() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-DRAFT",
                        "docstatus", 0,
                        "outstanding_amount", new BigDecimal("999.00"),
                        "grand_total", new BigDecimal("999.00"))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-DRAFT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-DRAFT",
                        "docstatus", 0,
                        "outstanding_amount", new BigDecimal("999.00"),
                        "grand_total", new BigDecimal("999.00"),
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("999.00"))))));
        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-A")));

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(new BigDecimal("999.000000"), response.get("dueAmount"));
    }

    @Test
    void skipsSubmittedInvoicesWithZeroOutstanding() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Sales Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-SUB",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("0.00"),
                        "grand_total", new BigDecimal("200.00"))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-SUB"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-SUB",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("0.00"),
                        "grand_total", new BigDecimal("200.00"),
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("200.00"))))));
        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "CAT-A")));

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(BigDecimal.ZERO, response.get("dueAmount"));
    }

    @Test
    void customerDueExcludesOldReplacedInvoicesAndSubtractsSubmittedCategoryPayments() {
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-CURRENT",
                                "docstatus", 1,
                                "outstanding_amount", new BigDecimal("227811.00"),
                                "grand_total", new BigDecimal("227811.00"),
                                "aas_invoice_version_status", "CURRENT"),
                        Map.of(
                                "name", "SINV-OLD",
                                "docstatus", 0,
                                "outstanding_amount", new BigDecimal("148791.00"),
                                "grand_total", new BigDecimal("148791.00"),
                                "aas_invoice_version_status", "OLD",
                                "aas_replaced_by", "SINV-NEWER"),
                        Map.of(
                                "name", "SINV-NEWER",
                                "docstatus", 0,
                                "outstanding_amount", new BigDecimal("148791.00"),
                                "grand_total", new BigDecimal("148791.00"),
                                "aas_replaced_by", "SINV-LATEST"),
                        Map.of(
                                "name", "SINV-LATEST",
                                "docstatus", 0,
                                "outstanding_amount", new BigDecimal("148791.00"),
                                "grand_total", new BigDecimal("148791.00"),
                                "aas_invoice_version_status", "CURRENT")));
        when(erpNextClient.getResource("Sales Invoice", "SINV-CURRENT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-CURRENT",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("227811.00"),
                        "grand_total", new BigDecimal("227811.00"),
                        "aas_invoice_version_status", "CURRENT",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-1",
                                "item_group", "Grocery",
                                "amount", new BigDecimal("227811.00"))))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-OLD"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-OLD",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("148791.00"),
                        "aas_invoice_version_status", "OLD",
                        "aas_replaced_by", "SINV-NEWER",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-OLD",
                                "item_group", "Grocery",
                                "amount", new BigDecimal("148791.00"))))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-NEWER"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-NEWER",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("148791.00"),
                        "aas_replaced_by", "SINV-LATEST",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-NEWER",
                                "item_group", "Grocery",
                                "amount", new BigDecimal("148791.00"))))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-LATEST"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-LATEST",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("148791.00"),
                        "aas_invoice_version_status", "CURRENT",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-LATEST",
                                "item_group", "Grocery",
                                "amount", new BigDecimal("148791.00"))))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    String filters = String.valueOf(params.get("filters"));
                    if (filters.contains("UNDER_REVIEW")) {
                        return List.of();
                    }
                    return List.of(Map.of(
                            "name", "PAY-GROCERY",
                            "paid_amount", new BigDecimal("100000.00")));
                });

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "Grocery");

        assertEquals(new BigDecimal("276602.000000"), response.get("dueAmount"));
    }

    @Test
    void customerDueAssignsTransportLineToInvoiceCategory() {
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "SINV-TRANSPORT",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("110.00"),
                        "outstanding_amount", new BigDecimal("110.00"),
                        "aas_invoice_version_status", "CURRENT")));
        when(erpNextClient.getResource("Sales Invoice", "SINV-TRANSPORT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-TRANSPORT",
                        "docstatus", 0,
                        "grand_total", new BigDecimal("110.00"),
                        "aas_category", "Grocery",
                        "items", List.of(
                                Map.of(
                                        "item_code", "GROCERY-1",
                                        "item_group", "Grocery",
                                        "amount", new BigDecimal("100.00")),
                                Map.of(
                                        "item_code", "AAS-TRANSPORT-CHARGE",
                                        "item_group", "All Item Groups",
                                        "amount", new BigDecimal("10.00"))))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "Grocery");

        assertEquals(new BigDecimal("110.000000"), response.get("dueAmount"));
    }

    @Test
    void allocatesSupplierDueUsingSourceSalesOrderCategoryWeights() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Purchase Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PINV-1",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("100.00"),
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
    void allocatesSupplierDueAcrossInvoiceItemGroupsWhenSourceOrderMissing() {
        when(erpNextClient.listResources(org.mockito.Mockito.eq("Purchase Invoice"), org.mockito.Mockito.anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PINV-2",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("50.00"),
                        "grand_total", new BigDecimal("50.00"),
                        "ignored", "x")));
        when(erpNextClient.getResource("Purchase Invoice", "PINV-2"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PINV-2",
                        "aas_source_sales_order", "",
                        "items", List.of(
                                Map.of("item_code", "ITEM-1", "amount", new BigDecimal("30.00"), "item_group", "CAT-A"),
                                Map.of("item_code", "ITEM-2", "amount", new BigDecimal("20.00"), "item_group", "CAT-B")
                        ))));

        Map<String, Object> responseA = paymentDueService.dueByCategory("Supplier", "SUP-1", "CAT-A");
        Map<String, Object> responseB = paymentDueService.dueByCategory("Supplier", "SUP-1", "CAT-B");

        assertEquals(new BigDecimal("30.000000"), responseA.get("dueAmount"));
        assertEquals(new BigDecimal("20.000000"), responseB.get("dueAmount"));
    }
}
