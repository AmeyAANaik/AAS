package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchOpsServiceTest {

    @Mock
    private ErpNextClient erpNextClient;
    @Mock
    private AdjustmentNoteErpService adjustmentNoteErpService;

    @InjectMocks
    private BranchOpsService branchOpsService;

    @BeforeEach
    void setup() {
        lenient().when(erpNextClient.listResources(eq("Journal Entry"), anyMap())).thenReturn(List.of());
        lenient().when(adjustmentNoteErpService.signedImpact(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    String direction = String.valueOf((Object) invocation.getArgument(1));
                    Object rawAmount = invocation.getArgument(2);
                    java.math.BigDecimal amount;
                    if (rawAmount instanceof java.math.BigDecimal bigDecimal) {
                        amount = bigDecimal;
                    } else if (rawAmount instanceof Number number) {
                        amount = java.math.BigDecimal.valueOf(number.doubleValue());
                    } else if (rawAmount == null) {
                        amount = java.math.BigDecimal.ZERO;
                    } else {
                        amount = new java.math.BigDecimal(String.valueOf(rawAmount));
                    }
                    if ("TAKE".equalsIgnoreCase(direction)) {
                        return amount;
                    }
                    return amount.negate();
                });
        lenient().when(adjustmentNoteErpService.normalizeDirection(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    String value = String.valueOf((Object) invocation.getArgument(0));
                    return "TAKE".equalsIgnoreCase(value) ? "TAKE" : "GIVE";
                });
        lenient().when(adjustmentNoteErpService.asDecimal(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    Object rawValue = invocation.getArgument(0);
                    if (rawValue instanceof java.math.BigDecimal bigDecimal) {
                        return bigDecimal;
                    }
                    if (rawValue instanceof Number number) {
                        return java.math.BigDecimal.valueOf(number.doubleValue());
                    }
                    if (rawValue == null) {
                        return java.math.BigDecimal.ZERO;
                    }
                    return new java.math.BigDecimal(String.valueOf(rawValue));
                });
    }

    @Test
    void getBranchLedgerIncludesDraftInvoicesButOnlySubmittedPayments() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));
        when(erpNextClient.getResource("Payment Entry", "PAY-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "SINV-001")))));
        when(erpNextClient.getResource("Payment Entry", "PAY-DRAFT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-DRAFT",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "SINV-001")))));

        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "docstatus", 1),
                        Map.of(
                                "name", "SINV-DRAFT",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-02",
                                "grand_total", 500.0,
                                "outstanding_amount", 500.0,
                                "docstatus", 0)));

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PAY-001",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-03",
                                "paid_amount", 400.0,
                                "docstatus", 1),
                        Map.of(
                                "name", "PAY-DRAFT",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-04",
                                "paid_amount", 200.0,
                                "docstatus", 0)));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        assertThat(response.get("balance")).isEqualTo(1100.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0))
                .containsEntry("voucherType", "Sales Invoice")
                .containsEntry("debit", 1000.0)
                .containsEntry("credit", 0.0)
                .containsEntry("runningBalance", 1000.0);
        assertThat(entries.get(1))
                .containsEntry("voucherType", "Draft Sales Invoice")
                .containsEntry("debit", 500.0)
                .containsEntry("credit", 0.0)
                .containsEntry("runningBalance", 1500.0);
        assertThat(entries.get(2))
                .containsEntry("voucherType", "Payment Entry")
                .containsEntry("debit", 0.0)
                .containsEntry("credit", 400.0)
                .containsEntry("runningBalance", 1100.0);
    }

    @Test
    void getBranchLedgerCategorySummaryDoesNotExposeAllItemGroups() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));

        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 100.0,
                                "outstanding_amount", 100.0,
                                "docstatus", 1)));

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        when(erpNextClient.getResource("Sales Invoice", "SINV-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-001",
                        "customer", "BRANCH-1",
                        "grand_total", 100.0,
                        "outstanding_amount", 100.0,
                        "items", List.of(Map.of(
                                "item_code", "ITEM-1",
                                "item_group", "All Item Groups",
                                "amount", 100.0)))));

        when(erpNextClient.getResource("Item", "ITEM-1"))
                .thenReturn(Map.of("data", Map.of("item_group", "Raw Material")));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("categorySummary");

        assertThat(categories)
                .extracting(row -> String.valueOf(row.get("category")))
                .doesNotContain("All Item Groups")
                .contains("Raw Material");
    }

    @Test
    void allBranchCategorySummariesIncludeApprovedCustomerAdjustments() {
        when(erpNextClient.listResources(eq("Customer"), anyMap()))
                .thenReturn(List.of(Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "docstatus", 1)));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PAY-001",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-02",
                                "paid_amount", 300.0,
                                "docstatus", 1,
                                "aas_category", "Grocery")));
        when(erpNextClient.listResources(eq("Journal Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.ofEntries(
                                Map.entry("name", "ACC-JV-001"),
                                Map.entry("posting_date", "2026-03-03"),
                                Map.entry("docstatus", 1),
                                Map.entry("modified", "2026-03-03 10:00:00"),
                                Map.entry("aas_adjustment_party", "BRANCH-1"),
                                Map.entry("aas_adjustment_party_type", "Customer"),
                                Map.entry("aas_category", "Grocery"),
                                Map.entry("aas_adjustment_direction", "GIVE"),
                                Map.entry("aas_adjustment_amount", 100.0),
                                Map.entry("aas_adjustment_reason", "Return"),
                                Map.entry("aas_reference_invoice", "SINV-001"))));
        when(erpNextClient.getResource("Sales Invoice", "SINV-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-001",
                        "customer", "BRANCH-1",
                        "grand_total", 1000.0,
                        "outstanding_amount", 1000.0,
                        "items", List.of(Map.of(
                                "item_code", "ITEM-1",
                                "item_group", "Grocery",
                                "amount", 1000.0)))));

        List<Map<String, Object>> rows = branchOpsService.getAllBranchCategorySummaries();
        assertThat(rows).containsExactly(Map.of(
                "branchId", "BRANCH-1",
                "branchName", "Downtown Branch",
                "category", "Grocery",
                "amount", 600.0));
    }

    @Test
    void getBranchLedgerCategorySummaryIncludesSettledCategoryHistory() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 100.0,
                                "outstanding_amount", 100.0,
                                "docstatus", 1)));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PAY-001",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-02",
                                "paid_amount", 100.0,
                                "docstatus", 1,
                                "aas_category", "Grocery")));
        when(erpNextClient.getResource("Sales Invoice", "SINV-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SINV-001",
                        "customer", "BRANCH-1",
                        "grand_total", 100.0,
                        "outstanding_amount", 100.0,
                        "items", List.of(Map.of(
                                "item_code", "ITEM-1",
                                "item_group", "Grocery",
                                "amount", 100.0)))));
        when(erpNextClient.getResource("Payment Entry", "PAY-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "SINV-001")))));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("categorySummary");

        assertThat(categories).anySatisfy(row -> assertThat(row)
                .containsEntry("category", "Grocery")
                .containsEntry("amount", 100.0)
                .containsEntry("balance", 0.0));
    }

    @Test
    void getBranchLedgerRoundsTinyCategorySettlementResidualToZero() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Sukarta Aundh")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-09",
                        "grand_total", 150838.12,
                        "rounded_total", 150838.0,
                        "rounding_adjustment", -0.12,
                        "outstanding_amount", 150838.0,
                        "docstatus", 1)));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00033"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-09",
                        "grand_total", 150838.12,
                        "rounded_total", 150838.0,
                        "rounding_adjustment", -0.12,
                        "outstanding_amount", 150838.0,
                        "docstatus", 1,
                        "aas_category", "Grocery",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-ITEM",
                                "item_group", "Grocery",
                                "amount", 150838.0)))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "PAY-GROCERY",
                        "party", "BRANCH-1",
                        "party_type", "Customer",
                        "posting_date", "2026-06-10",
                        "paid_amount", 150838.12,
                        "docstatus", 1,
                        "aas_category", "Grocery")));
        when(erpNextClient.getResource("Payment Entry", "PAY-GROCERY"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-GROCERY",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "ACC-SINV-2026-00033")))));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1", "2026-06-03", "2026-06-10");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("categorySummary");
        assertThat(categories).anySatisfy(row -> assertThat(row)
                .containsEntry("category", "Grocery")
                .containsEntry("amount", 150838.0)
                .containsEntry("balance", 0.0));

        Map<String, Object> categoryLedger = branchOpsService.getBranchLedgerByCategory(
                "BRANCH-1",
                "Grocery",
                "2026-06-03",
                "2026-06-10");
        assertThat(categoryLedger)
                .containsEntry("balance", 0.0)
                .containsEntry("closingBalance", 0.0);
    }

    @Test
    void getBranchCategoryLedgerIncludesPaymentLinkedToCategoryInvoice() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Sukarta Aundh")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-09",
                        "grand_total", 150838.12,
                        "rounded_total", 150838.0,
                        "rounding_adjustment", -0.12,
                        "outstanding_amount", 150838.0,
                        "docstatus", 1)));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00033"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-09",
                        "grand_total", 150838.12,
                        "rounded_total", 150838.0,
                        "rounding_adjustment", -0.12,
                        "outstanding_amount", 150838.0,
                        "docstatus", 1,
                        "aas_category", "Grocery",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-ITEM",
                                "item_group", "Grocery",
                                "amount", 150838.0)))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    if (String.valueOf(params.get("filters")).contains("aas_category")) {
                        return List.of();
                    }
                    return List.of(Map.of(
                            "name", "PAY-LINKED",
                            "party", "BRANCH-1",
                            "party_type", "Customer",
                            "posting_date", "2026-06-10",
                            "paid_amount", 150838.12,
                            "docstatus", 1));
                });
        when(erpNextClient.getResource("Payment Entry", "PAY-LINKED"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-LINKED",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "ACC-SINV-2026-00033",
                                "allocated_amount", 150838.0)))));

        Map<String, Object> categoryLedger = branchOpsService.getBranchLedgerByCategory(
                "BRANCH-1",
                "Grocery",
                "2026-06-03",
                "2026-06-10");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) categoryLedger.get("entries");
        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("ACC-SINV-2026-00033", "PAY-LINKED");
        assertThat(entries.get(0))
                .containsEntry("voucherType", "Sales Invoice")
                .containsEntry("debit", 150838.0)
                .containsEntry("runningBalance", 150838.0);
        assertThat(entries.get(1))
                .containsEntry("voucherType", "Payment Entry")
                .containsEntry("credit", 150838.0)
                .containsEntry("runningBalance", 0.0);
        assertThat(categoryLedger)
                .containsEntry("balance", 0.0)
                .containsEntry("closingBalance", 0.0);
    }

    @Test
    void getBranchLedgerOnlyUsesPaymentsLinkedToBranchInvoices() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));

        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "docstatus", 1)));

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "ACC-PAY-2026-00003",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-02",
                                "paid_amount", 400.0,
                                "docstatus", 1),
                        Map.of(
                                "name", "ACC-PAY-2026-00001",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-03",
                                "paid_amount", 250.0,
                                "docstatus", 1)));

        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00003"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-PAY-2026-00003",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "SINV-001")))));
        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-PAY-2026-00001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "SINV-OTHER")))));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        assertThat(response.get("balance")).isEqualTo(600.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("SINV-001", "ACC-PAY-2026-00003");
    }

    @Test
    void getBranchCategoryLedgerOrdersSameDayInvoiceBeforePayment() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Sukhhkarta Pure Veg Dining Hall")));
        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of());
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00012",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-01",
                        "grand_total", 159940.0,
                        "outstanding_amount", 159940.0,
                        "docstatus", 1)));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00012"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00012",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-01",
                        "grand_total", 159940.0,
                        "outstanding_amount", 159940.0,
                        "docstatus", 1,
                        "items", List.of(Map.of(
                                "item_code", "DAIRY-ITEM",
                                "item_group", "Dairy",
                                "amount", 159940.0)))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-PAY-2026-00003",
                        "party", "BRANCH-1",
                        "party_type", "Customer",
                        "posting_date", "2026-06-01",
                        "paid_amount", 100000.0,
                        "docstatus", 1,
                        "aas_category", "Dairy")));
        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00003"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-PAY-2026-00003",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "ACC-SINV-2026-00012")))));

        Map<String, Object> response = branchOpsService.getBranchLedgerByCategory("BRANCH-1", "Dairy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("ACC-SINV-2026-00012", "ACC-PAY-2026-00003");
        assertThat(entries.get(0)).containsEntry("runningBalance", 159940.0);
        assertThat(entries.get(1)).containsEntry("runningBalance", 59940.0);

        Map<String, Object> branchLedger = branchOpsService.getBranchLedger("BRANCH-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) branchLedger.get("categorySummary");

        assertThat(branchLedger.get("balance")).isEqualTo(59940.0);
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0))
                .containsEntry("category", "Dairy")
                .containsEntry("amount", 159940.0)
                .containsEntry("balance", 59940.0);

        Map<String, Object> detail = branchOpsService.getBranchDetail("BRANCH-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> kpis = (Map<String, Object>) detail.get("kpis");
        assertThat(kpis)
                .containsEntry("openReceivableAmount", 59940.0)
                .containsEntry("invoicedAmount", 159940.0)
                .containsEntry("paymentCollectionRate", 62.52);
    }

    @Test
    void getBranchLedgerAssignsTransportLineToInvoiceCategoryInsteadOfUncategorized() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Kothrud Branch")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00023",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-04",
                        "grand_total", 148790.55,
                        "outstanding_amount", 148791.0,
                        "docstatus", 0)));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00023"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00023",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-04",
                        "grand_total", 148790.55,
                        "outstanding_amount", 148791.0,
                        "docstatus", 0,
                        "aas_category", "Grocery",
                        "items", List.of(
                                Map.of(
                                        "item_code", "ITEM-1",
                                        "item_group", "Grocery",
                                        "amount", 143977.78),
                                Map.of(
                                        "item_code", "AAS-TRANSPORT-CHARGE",
                                        "item_group", "All Item Groups",
                                        "amount", 1000.0)))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("categorySummary");
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0))
                .containsEntry("category", "Grocery")
                .containsEntry("amount", 148791.0)
                .containsEntry("balance", 148791.0);
        assertThat(categories).noneMatch(row -> "Uncategorized".equals(row.get("category")));

        Map<String, Object> categoryLedger = branchOpsService.getBranchLedgerByCategory("BRANCH-1", "Grocery");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) categoryLedger.get("entries");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0))
                .containsEntry("voucherNo", "ACC-SINV-2026-00023")
                .containsEntry("debit", 148791.0)
                .containsEntry("runningBalance", 148791.0);
    }

    @Test
    void getBranchLedgerUsesInvoiceCategoryWhenItemRowsHaveNoLedgerCategory() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Sukarta Aundh")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-09",
                        "grand_total", 150838.0,
                        "outstanding_amount", 150838.0,
                        "docstatus", 0)));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00033"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-09",
                        "grand_total", 150838.0,
                        "outstanding_amount", 150838.0,
                        "docstatus", 0,
                        "aas_category", "Grocery",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-ITEM",
                                "item_group", "All Item Groups",
                                "amount", 150838.0)))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1", "2026-05-01", "2026-06-10");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("categorySummary");
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0))
                .containsEntry("category", "Grocery")
                .containsEntry("amount", 150838.0)
                .containsEntry("balance", 150838.0);

        Map<String, Object> categoryLedger = branchOpsService.getBranchLedgerByCategory(
                "BRANCH-1",
                "Grocery",
                "2026-05-01",
                "2026-06-10");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) categoryLedger.get("entries");
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0))
                .containsEntry("voucherNo", "ACC-SINV-2026-00033")
                .containsEntry("debit", 150838.0);
    }

    @Test
    void getBranchLedgerIncludesApprovedCreditNotesAndReducesBalance() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Sukarta Aundh")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-07",
                        "grand_total", 118422.0,
                        "outstanding_amount", 118422.0,
                        "docstatus", 1)));
        when(erpNextClient.getResource("Sales Invoice", "ACC-SINV-2026-00033"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-SINV-2026-00033",
                        "customer", "BRANCH-1",
                        "posting_date", "2026-06-07",
                        "grand_total", 118422.0,
                        "outstanding_amount", 118422.0,
                        "docstatus", 1,
                        "aas_category", "Grocery",
                        "items", List.of(Map.of(
                                "item_code", "GROCERY-1",
                                "item_group", "Grocery",
                                "amount", 118422.0)))));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());
        when(erpNextClient.listResources(eq("Journal Entry"), anyMap()))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("name", "ACC-JV-2026-00001"),
                        Map.entry("posting_date", "2026-06-07"),
                        Map.entry("docstatus", 1),
                        Map.entry("modified", "2026-06-07 17:30:00"),
                        Map.entry("aas_adjustment_party", "BRANCH-1"),
                        Map.entry("aas_adjustment_party_type", "Customer"),
                        Map.entry("aas_category", "Grocery"),
                        Map.entry("aas_adjustment_direction", "GIVE"),
                        Map.entry("aas_adjustment_amount", 10000.0),
                        Map.entry("aas_adjustment_reason", "Credit note"),
                        Map.entry("aas_reference_invoice", "ACC-SINV-2026-00033"),
                        Map.entry("aas_adjustment_review_status", "APPROVED"))));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries)
                .extracting(entry -> entry.get("voucherType"))
                .containsExactly("Sales Invoice", "Credit Note");
        assertThat(entries.get(1))
                .containsEntry("credit", 10000.0)
                .containsEntry("runningBalance", 108422.0);
        assertThat(response.get("balance")).isEqualTo(108422.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) response.get("categorySummary");
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0))
                .containsEntry("category", "Grocery")
                .containsEntry("amount", 118422.0)
                .containsEntry("balance", 108422.0);
    }

    @Test
    void getBranchLedgerLoadsPagedInvoicesAndPayments() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));

        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    Object start = params.get("limit_start");
                    if (Integer.valueOf(0).equals(start)) {
                        return List.of(Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "docstatus", 1));
                    }
                    return List.of();
                });

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    Object start = params.get("limit_start");
                    if (Integer.valueOf(0).equals(start)) {
                        return List.of(Map.of(
                                "name", "PAY-001",
                                "party", "BRANCH-1",
                                "party_type", "Customer",
                                "posting_date", "2026-03-03",
                                "paid_amount", 400.0,
                                "docstatus", 1));
                    }
                    return List.of();
                });

        when(erpNextClient.getResource("Payment Entry", "PAY-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Sales Invoice",
                                "reference_name", "SINV-001")))));

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        assertThat(response.get("balance")).isEqualTo(600.0);
    }

    @Test
    void getBranchLedgerIgnoresOldVersionInvoices() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-OLD",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "docstatus", 1,
                                "aas_invoice_version_status", "OLD"),
                        Map.of(
                                "name", "SINV-CURRENT",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-03-02",
                                "grand_total", 500.0,
                                "outstanding_amount", 500.0,
                                "docstatus", 1,
                                "aas_invoice_version_status", "CURRENT")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        assertThat(response.get("balance")).isEqualTo(500.0);
    }

    @Test
    void getBranchLedgerExcludesReplacedInvoices() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-OLD",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-05-12",
                                "grand_total", 6922.0,
                                "outstanding_amount", 6922.0,
                                "docstatus", 0,
                                "status", "Draft",
                                "modified", "2026-05-12 10:00:00",
                                "aas_source_sales_order", "SAL-ORD-2026-00001",
                                "aas_replaced_by", "SINV-NEW"),
                        Map.of(
                                "name", "SINV-NEW",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-05-31",
                                "grand_total", 116841.61,
                                "outstanding_amount", 116841.61,
                                "docstatus", 0,
                                "status", "Draft",
                                "modified", "2026-05-31 11:00:00",
                                "aas_source_sales_order", "SAL-ORD-2026-00001",
                                "aas_replaced_by", "")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("SINV-NEW");
    }

    @Test
    void getBranchLedgerDedupesLatestBySourceSalesOrder() {
        when(erpNextClient.getResource("Customer", "BRANCH-1"))
                .thenReturn(Map.of("data", Map.of("name", "BRANCH-1", "customer_name", "Downtown Branch")));
        when(erpNextClient.listResources(eq("Sales Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SINV-001",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-05-31",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "docstatus", 0,
                                "status", "Draft",
                                "modified", "2026-05-31 10:00:00",
                                "aas_source_sales_order", "SAL-ORD-2026-00001",
                                "aas_replaced_by", ""),
                        Map.of(
                                "name", "SINV-002",
                                "customer", "BRANCH-1",
                                "posting_date", "2026-05-31",
                                "grand_total", 1200.0,
                                "outstanding_amount", 1200.0,
                                "docstatus", 0,
                                "status", "Draft",
                                "modified", "2026-05-31 12:00:00",
                                "aas_source_sales_order", "SAL-ORD-2026-00001",
                                "aas_replaced_by", "")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        Map<String, Object> response = branchOpsService.getBranchLedger("BRANCH-1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("SINV-002");
    }
}
