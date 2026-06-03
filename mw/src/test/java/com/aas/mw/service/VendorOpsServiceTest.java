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
class VendorOpsServiceTest {

    @Mock
    private ErpNextClient erpNextClient;

    @InjectMocks
    private VendorOpsService vendorOpsService;

    @Test
    void summaryPendingBillAmountIncludesOutstandingInvoicesAndPreCaptureOrders() {
        when(erpNextClient.listResources(eq("Supplier"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "VENDOR-1",
                        "supplier_name", "FreshHarvest Agro Foods",
                        "disabled", 0)));
        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SO-PDF",
                                "aas_vendor", "VENDOR-1",
                                "aas_status", "VENDOR_PDF_RECEIVED",
                                "aas_vendor_bill_total", 250.0,
                                "modified", "2026-03-03 10:00:00"),
                        Map.of(
                                "name", "SO-SELL",
                                "aas_vendor", "VENDOR-1",
                                "aas_status", "SELL_ORDER_CREATED",
                                "aas_vendor_bill_total", 1000.0,
                                "aas_pi_vendor", "PINV-001",
                                "modified", "2026-03-04 10:00:00")));
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-001",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-04",
                                "outstanding_amount", 1000.0,
                                "grand_total", 1000.0,
                                "bill_no", "BILL-001",
                                "docstatus", 1,
                                "modified", "2026-03-04 11:00:00")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = vendorOpsService.getSummary();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vendors = (List<Map<String, Object>>) response.get("vendors");
        @SuppressWarnings("unchecked")
        Map<String, Object> totals = (Map<String, Object>) response.get("totals");

        assertThat(vendors).hasSize(1);
        assertThat(vendors.get(0).get("pendingBillAmount")).isEqualTo(1250.0);
        assertThat(totals.get("totalPendingBillAmount")).isEqualTo(1250.0);
    }

    @Test
    void summaryPendingBillAmountSubtractsApprovedSupplierPaymentsEvenWhenUnallocated() {
        when(erpNextClient.listResources(eq("Supplier"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "VENDOR-1",
                        "supplier_name", "Sanshray Foods",
                        "disabled", 0)));
        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of());
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-001",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-06-01",
                                "outstanding_amount", 1674704.0,
                                "grand_total", 1674704.0,
                                "bill_no", "BILL-001",
                                "docstatus", 1,
                                "modified", "2026-06-01 10:00:00")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "ACC-PAY-2026-00002",
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-06-02",
                                "paid_amount", 100000.0,
                                "docstatus", 1)));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = vendorOpsService.getSummary();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vendors = (List<Map<String, Object>>) response.get("vendors");

        assertThat(vendors).hasSize(1);
        assertThat(vendors.get(0).get("pendingBillAmount")).isEqualTo(1574704.0);
        assertThat(vendors.get(0).get("ledgerBalance")).isEqualTo(1574704.0);
    }

    @Test
    void summaryPendingBillAmountFallsBackToGrandTotalForDraftInvoices() {
        when(erpNextClient.listResources(eq("Supplier"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "VENDOR-1",
                        "supplier_name", "Pragati Foods",
                        "disabled", 0)));
        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of());
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-DRAFT",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-05-31",
                                "outstanding_amount", 0.0,
                                "grand_total", 289035.0,
                                "bill_no", "OB-SUP-001",
                                "docstatus", 0,
                                "modified", "2026-05-31 11:00:00")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> response = vendorOpsService.getSummary();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vendors = (List<Map<String, Object>>) response.get("vendors");

        assertThat(vendors).hasSize(1);
        assertThat(vendors.get(0).get("pendingBillAmount")).isEqualTo(289035.0);
    }

    @Test
    void getVendorLedgerTreatsInvoicesAsPositivePayableAndPaymentsAsReductions() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));

        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-001",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "bill_no", "BILL-001",
                                "docstatus", 1),
                        Map.of(
                                "name", "PINV-002",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-05",
                                "grand_total", 500.0,
                                "outstanding_amount", 300.0,
                                "bill_no", "BILL-002",
                                "docstatus", 1)));

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PAY-001",
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-03-03",
                                "paid_amount", 400.0,
                                "docstatus", 1)));

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(1100.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0))
                .containsEntry("voucherType", "Purchase Invoice")
                .containsEntry("debit", 0.0)
                .containsEntry("credit", 1000.0)
                .containsEntry("runningBalance", 1000.0);
        assertThat(entries.get(1))
                .containsEntry("voucherType", "Payment Entry")
                .containsEntry("debit", 400.0)
                .containsEntry("credit", 0.0)
                .containsEntry("runningBalance", 600.0);
        assertThat(entries.get(2))
                .containsEntry("voucherType", "Purchase Invoice")
                .containsEntry("debit", 0.0)
                .containsEntry("credit", 500.0)
                .containsEntry("runningBalance", 1100.0);
    }

    @Test
    void getVendorLedgerUsesDirectPurchaseInvoiceCategoryForOpeningBalances() {
        when(erpNextClient.getResource("Supplier", "Sanshray Foods"))
                .thenReturn(Map.of("data", Map.of("name", "Sanshray Foods", "supplier_name", "Sanshray Foods")));
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "ACC-PINV-2026-00003",
                                "supplier", "Sanshray Foods",
                                "posting_date", "2026-06-01",
                                "grand_total", 1674704.0,
                                "outstanding_amount", 1674704.0,
                                "bill_no", "AAS-OPENING-20260601-Sanshray Foods-GROCERY",
                                "aas_category", "Grocery",
                                "docstatus", 1),
                        Map.of(
                                "name", "ACC-PINV-2026-00007",
                                "supplier", "Sanshray Foods",
                                "posting_date", "2026-06-03",
                                "grand_total", 139120.84,
                                "outstanding_amount", 139120.84,
                                "bill_no", "PUR-ORD-2026-00001",
                                "aas_category", "Grocery",
                                "docstatus", 0)));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ACC-PAY-2026-00002",
                        "party", "Sanshray Foods",
                        "party_type", "Supplier",
                        "posting_date", "2026-06-02",
                        "paid_amount", 100000.0,
                        "payment_type", "Pay",
                        "reference_no", "",
                        "docstatus", 1,
                        "aas_category", "Grocery")));

        Map<String, Object> ledger = vendorOpsService.getVendorLedger("Sanshray Foods");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) ledger.get("categorySummary");
        assertThat(categories).containsExactly(Map.of("category", "Grocery", "amount", 1713824.84));
        assertThat(categories).noneMatch(row -> "Uncategorized".equals(row.get("category")));

        Map<String, Object> categoryLedger = vendorOpsService.getVendorLedgerByCategory("Sanshray Foods", "Grocery");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) categoryLedger.get("entries");
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0))
                .containsEntry("voucherType", "Purchase Invoice")
                .containsEntry("voucherNo", "ACC-PINV-2026-00003")
                .containsEntry("reference", "AAS-OPENING-20260601-Sanshray Foods-GROCERY")
                .containsEntry("credit", 1674704.0)
                .containsEntry("runningBalance", 1674704.0);
        assertThat(entries.get(1))
                .containsEntry("voucherType", "Payment Entry")
                .containsEntry("voucherNo", "ACC-PAY-2026-00002")
                .containsEntry("debit", 100000.0)
                .containsEntry("runningBalance", 1574704.0);
        assertThat(entries.get(2))
                .containsEntry("voucherType", "Draft Purchase Invoice")
                .containsEntry("voucherNo", "ACC-PINV-2026-00007")
                .containsEntry("credit", 139120.84)
                .containsEntry("runningBalance", 1713824.84);
    }

    @Test
    void getVendorLedgerIncludesDraftInvoicesButOnlySubmittedPayments() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-001",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "bill_no", "BILL-001",
                                "docstatus", 1),
                        Map.of(
                                "name", "PINV-DRAFT",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-02",
                                "grand_total", 500.0,
                                "outstanding_amount", 500.0,
                                "bill_no", "BILL-DRAFT",
                                "docstatus", 0)));

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PAY-001",
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-03-03",
                                "paid_amount", 400.0,
                                "docstatus", 1),
                        Map.of(
                                "name", "PAY-DRAFT",
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-03-04",
                                "paid_amount", 200.0,
                                "docstatus", 0)));

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(1100.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0))
                .containsEntry("voucherType", "Purchase Invoice")
                .containsEntry("debit", 0.0)
                .containsEntry("credit", 1000.0)
                .containsEntry("runningBalance", 1000.0);
        assertThat(entries.get(1))
                .containsEntry("voucherType", "Draft Purchase Invoice")
                .containsEntry("debit", 0.0)
                .containsEntry("credit", 500.0)
                .containsEntry("runningBalance", 1500.0);
        assertThat(entries.get(2))
                .containsEntry("voucherType", "Payment Entry")
                .containsEntry("debit", 400.0)
                .containsEntry("credit", 0.0)
                .containsEntry("runningBalance", 1100.0);
    }

    @Test
    void getVendorLedgerIncludesSubmittedSupplierPaymentsEvenWithoutInvoiceReferences() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));

        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-001",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "bill_no", "BILL-001",
                                "docstatus", 1)));

        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "ACC-PAY-2026-00003",
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-03-02",
                                "paid_amount", 400.0,
                                "docstatus", 1),
                        Map.of(
                                "name", "ACC-PAY-2026-00001",
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-03-03",
                                "paid_amount", 250.0,
                                "docstatus", 1)));

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(350.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries).hasSize(3);
        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("PINV-001", "ACC-PAY-2026-00003", "ACC-PAY-2026-00001");
    }

    @Test
    void getVendorLedgerLoadsPagedInvoicesAndPayments() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));

        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, Object> params = invocation.getArgument(1);
                    Object start = params.get("limit_start");
                    if (Integer.valueOf(0).equals(start)) {
                        return List.of(Map.of(
                                "name", "PINV-001",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "bill_no", "BILL-001",
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
                                "party", "VENDOR-1",
                                "party_type", "Supplier",
                                "posting_date", "2026-03-03",
                                "paid_amount", 400.0,
                                "docstatus", 1));
                    }
                    return List.of();
                });

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(600.0);
    }

    @Test
    void getVendorLedgerIgnoresOldVersionInvoices() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));
        when(erpNextClient.listResources(eq("Purchase Invoice"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "PINV-OLD",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-01",
                                "grand_total", 1000.0,
                                "outstanding_amount", 1000.0,
                                "bill_no", "BILL-OLD",
                                "docstatus", 1,
                                "aas_invoice_version_status", "OLD"),
                        Map.of(
                                "name", "PINV-CURRENT",
                                "supplier", "VENDOR-1",
                                "posting_date", "2026-03-02",
                                "grand_total", 500.0,
                                "outstanding_amount", 500.0,
                                "bill_no", "BILL-CURRENT",
                                "docstatus", 1,
                                "aas_invoice_version_status", "CURRENT")));
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap())).thenReturn(List.of());

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(500.0);
    }
}
