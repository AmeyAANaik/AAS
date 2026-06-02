package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BranchOpsServiceTest {

    @Mock
    private ErpNextClient erpNextClient;

    @InjectMocks
    private BranchOpsService branchOpsService;

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

        Map<String, Object> response = branchOpsService.getBranchLedgerByCategory("BRANCH-1", "Dairy");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("ACC-SINV-2026-00012", "ACC-PAY-2026-00003");
        assertThat(entries.get(0)).containsEntry("runningBalance", 159940.0);
        assertThat(entries.get(1)).containsEntry("runningBalance", 59940.0);
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
