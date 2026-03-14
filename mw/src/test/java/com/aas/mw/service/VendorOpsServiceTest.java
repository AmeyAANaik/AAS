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
}
