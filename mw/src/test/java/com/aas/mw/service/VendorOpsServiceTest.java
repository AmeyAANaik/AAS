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
    void getVendorLedgerTreatsInvoicesAsPositivePayableAndPaymentsAsReductions() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));
        when(erpNextClient.getResource("Payment Entry", "PAY-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Purchase Invoice",
                                "reference_name", "PINV-001")))));

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
    void getVendorLedgerIncludesDraftInvoicesButOnlySubmittedPayments() {
        when(erpNextClient.getResource("Supplier", "VENDOR-1"))
                .thenReturn(Map.of("data", Map.of("name", "VENDOR-1", "supplier_name", "FreshHarvest Agro Foods")));
        when(erpNextClient.getResource("Payment Entry", "PAY-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Purchase Invoice",
                                "reference_name", "PINV-001")))));
        when(erpNextClient.getResource("Payment Entry", "PAY-DRAFT"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-DRAFT",
                        "references", List.of(Map.of(
                                "reference_doctype", "Purchase Invoice",
                                "reference_name", "PINV-001")))));

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
    void getVendorLedgerOnlyUsesPaymentsLinkedToVendorInvoices() {
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

        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00003"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-PAY-2026-00003",
                        "references", List.of(Map.of(
                                "reference_doctype", "Purchase Invoice",
                                "reference_name", "PINV-001")))));
        when(erpNextClient.getResource("Payment Entry", "ACC-PAY-2026-00001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "ACC-PAY-2026-00001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Purchase Invoice",
                                "reference_name", "PINV-OTHER")))));

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(600.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(entry -> entry.get("voucherNo"))
                .containsExactly("PINV-001", "ACC-PAY-2026-00003");
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

        when(erpNextClient.getResource("Payment Entry", "PAY-001"))
                .thenReturn(Map.of("data", Map.of(
                        "name", "PAY-001",
                        "references", List.of(Map.of(
                                "reference_doctype", "Purchase Invoice",
                                "reference_name", "PINV-001")))));

        Map<String, Object> response = vendorOpsService.getVendorLedger("VENDOR-1");

        assertThat(response.get("balance")).isEqualTo(600.0);
    }
}
