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
    private AdjustmentNoteErpService adjustmentNoteErpService;
    private BranchOpsService branchOpsService;
    private PaymentDueService paymentDueService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        adjustmentNoteErpService = mock(AdjustmentNoteErpService.class);
        branchOpsService = mock(BranchOpsService.class);
        when(adjustmentNoteErpService.approvedDueImpact(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString()))
                .thenReturn(BigDecimal.ZERO);
        when(adjustmentNoteErpService.pendingAmount(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(), org.mockito.Mockito.any()))
                .thenReturn(BigDecimal.ZERO);
        paymentDueService = new PaymentDueService(erpNextClient, adjustmentNoteErpService, branchOpsService);
    }

    // ── Customer tests ──────────────────────────────────────────────────────────
    // Customer due is now the GL-based category ledger balance (single source of
    // truth), so tests mock BranchOpsService.getBranchLedgerByCategory.

    @Test
    void customerDueEqualsLedgerBalance() {
        when(branchOpsService.getBranchLedgerByCategory("SHOP-1", "CAT-A"))
                .thenReturn(Map.of("balance", new BigDecimal("225650.00")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(new BigDecimal("225650.00"), response.get("dueAmount"));
    }

    @Test
    void customerDueIsZeroWhenLedgerBalanceIsZero() {
        when(branchOpsService.getBranchLedgerByCategory("SHOP-1", "CAT-A"))
                .thenReturn(Map.of("balance", BigDecimal.ZERO));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(BigDecimal.ZERO, response.get("dueAmount"));
    }

    @Test
    void customerDueNeverGoesNegative() {
        when(branchOpsService.getBranchLedgerByCategory("SHOP-1", "CAT-A"))
                .thenReturn(Map.of("balance", new BigDecimal("-100.00")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(BigDecimal.ZERO, response.get("dueAmount"));
    }

    @Test
    void customerDueIncludesApprovedAdjustmentImpact() {
        when(branchOpsService.getBranchLedgerByCategory("SHOP-1", "CAT-A"))
                .thenReturn(Map.of("balance", new BigDecimal("1000.00")));
        when(adjustmentNoteErpService.approvedDueImpact("Customer", "SHOP-1", "CAT-A"))
                .thenReturn(new BigDecimal("-200.00"));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "CAT-A");

        assertEquals(new BigDecimal("800.00"), response.get("dueAmount"));
    }

    @Test
    void customerAvailableDueSubtractsUnderReviewPayments() {
        when(branchOpsService.getBranchLedgerByCategory("SHOP-1", "Grocery"))
                .thenReturn(Map.of("balance", new BigDecimal("200.00")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    String filters = String.valueOf(params.get("filters"));
                    if (filters.contains("UNDER_REVIEW")) {
                        return List.of(Map.of("name", "PAY-1", "received_amount", new BigDecimal("50.00")));
                    }
                    return List.of();
                });

        Map<String, Object> response = paymentDueService.dueByCategory("Customer", "SHOP-1", "Grocery");

        assertEquals(new BigDecimal("200.00"), response.get("dueAmount"));
        assertEquals(new BigDecimal("50.00"), response.get("underReviewAmount"));
        assertEquals(new BigDecimal("50.00"), response.get("pendingAdminApprovalAmount"));
        assertEquals(new BigDecimal("150.00"), response.get("availableDueAmount"));
    }

    // ── Supplier tests ──────────────────────────────────────────────────────────
    // Supplier due is still computed from purchase invoices minus category payments.

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

    @Test
    void supplierDueSubtractsSubmittedCategoryPayments() {
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PINV-GROCERY",
                        "docstatus", 1,
                        "outstanding_amount", new BigDecimal("1000.00"),
                        "grand_total", new BigDecimal("1000.00"),
                        "aas_invoice_version_status", "CURRENT",
                        "aas_category", "Grocery")));
        when(erpNextClient.getResource("Purchase Invoice", "PINV-GROCERY"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PINV-GROCERY",
                        "aas_source_sales_order", "",
                        "aas_category", "Grocery",
                        "items", List.of())));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    String filters = String.valueOf(params.get("filters"));
                    if (filters.contains("UNDER_REVIEW")) {
                        return List.of();
                    }
                    return List.of(Map.of(
                            "name", "PAY-GROCERY",
                            "paid_amount", new BigDecimal("400.00"),
                            "docstatus", 1,
                            "party_type", "Supplier",
                            "party", "SUP-1",
                            "aas_category", "Grocery"));
                });

        Map<String, Object> response = paymentDueService.dueByCategory("Supplier", "SUP-1", "Grocery");

        assertEquals(new BigDecimal("600.00"), response.get("dueAmount"));
        assertEquals(new BigDecimal("600.00"), response.get("availableDueAmount"));
    }

    @Test
    void supplierDueExcludesOldAndReplacedPurchaseInvoices() {
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-CURRENT",
                                "docstatus", 1,
                                "outstanding_amount", new BigDecimal("500.00"),
                                "grand_total", new BigDecimal("500.00"),
                                "aas_invoice_version_status", "CURRENT",
                                "aas_category", "Grocery"),
                        Map.of(
                                "name", "PINV-OLD",
                                "docstatus", 1,
                                "outstanding_amount", new BigDecimal("1000.00"),
                                "grand_total", new BigDecimal("1000.00"),
                                "aas_invoice_version_status", "OLD",
                                "aas_category", "Grocery"),
                        Map.of(
                                "name", "PINV-REPLACED",
                                "docstatus", 0,
                                "outstanding_amount", new BigDecimal("700.00"),
                                "grand_total", new BigDecimal("700.00"),
                                "aas_replaced_by", "PINV-CURRENT",
                                "aas_category", "Grocery")));
        when(erpNextClient.getResource("Purchase Invoice", "PINV-CURRENT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PINV-CURRENT",
                        "aas_source_sales_order", "",
                        "aas_category", "Grocery",
                        "items", List.of())));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = paymentDueService.dueByCategory("Supplier", "SUP-1", "Grocery");

        assertEquals(new BigDecimal("500.00"), response.get("dueAmount"));
    }
}
