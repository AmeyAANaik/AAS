package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceCompanyReportsTest {

    private ErpNextClient erpNextClient;
    private ReportService reportService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        reportService = new ReportService(erpNextClient);
    }

    @Test
    void resolvesDateRangeWhenOnlyFromProvided() {
        ReportService.DateRange range = reportService.resolveDateRange("2026-04-10", null);
        assertEquals("2026-04-10", range.start());
        assertEquals("2026-04-10", range.end());
    }

    @Test
    void resolvesDateRangeWhenSwapped() {
        ReportService.DateRange range = reportService.resolveDateRange("2026-04-10", "2026-04-01");
        assertEquals("2026-04-01", range.start());
        assertEquals("2026-04-10", range.end());
    }

    @Test
    void branchSalesProfitAggregatesAcrossBranches() {
        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of(
                        Map.of(
                                "name", "SO-1",
                                "customer", "Branch A",
                                "grand_total", 1000.0,
                                "aas_is_deleted", 0,
                                "aas_status", "INVOICED"),
                        Map.of(
                                "name", "SO-2",
                                "customer", "Branch B",
                                "grand_total", 500.0,
                                "aas_is_deleted", 0,
                                "aas_status", "INVOICED"),
                        Map.of(
                                "name", "SO-3",
                                "customer", "Branch A",
                                "grand_total", 250.0,
                                "aas_is_deleted", 0,
                                "aas_status", "INVOICED")));

        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1")))
                .thenReturn(Map.of("data", Map.of(
                        "items",
                        List.of(
                                Map.of("qty", 10.0, "aas_vendor_rate", 60.0, "rate", 100.0, "amount", 1000.0)))));
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-2")))
                .thenReturn(Map.of("data", Map.of(
                        "items",
                        List.of(
                                Map.of("qty", 5.0, "aas_vendor_rate", 40.0, "rate", 100.0, "amount", 500.0)))));
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-3")))
                .thenReturn(Map.of("data", Map.of(
                        "items",
                        List.of(
                                Map.of("qty", 5.0, "aas_vendor_rate", 20.0, "rate", 50.0, "amount", 250.0)))));

        List<Map<String, Object>> rows = reportService.companyBranchSalesProfit("2026-04-01", "2026-04-30");
        assertEquals(2, rows.size());

        Map<String, Object> branchA = rows.stream()
                .filter(r -> "Branch A".equals(r.get("shop")))
                .findFirst()
                .orElseThrow();
        assertEquals(2, branchA.get("orders"));
        assertEquals(1250.0, (Double) branchA.get("sales_total"));
        assertEquals(700.0, (Double) branchA.get("cost_total"));
        assertEquals(550.0, (Double) branchA.get("profit_total"));

        Map<String, Object> branchB = rows.stream()
                .filter(r -> "Branch B".equals(r.get("shop")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, branchB.get("orders"));
        assertEquals(500.0, (Double) branchB.get("sales_total"));
        assertEquals(200.0, (Double) branchB.get("cost_total"));
        assertEquals(300.0, (Double) branchB.get("profit_total"));
        assertTrue(((Double) branchB.get("profit_percent")) > 0.0);
    }

    @Test
    void supplierExpensesGroupsPayments() {
        when(erpNextClient.listResources(eq("Payment Entry"), anyMap()))
                .thenReturn(List.of(
                        Map.of("party", "Vendor A", "paid_amount", 100.0),
                        Map.of("party", "Vendor A", "paid_amount", 25.0),
                        Map.of("party", "Vendor B", "paid_amount", 10.0)));

        List<Map<String, Object>> rows = reportService.companySupplierExpenses("2026-04-01", "2026-04-30");
        assertEquals(2, rows.size());
        Map<String, Object> vendorA = rows.stream().filter(r -> "Vendor A".equals(r.get("vendor"))).findFirst().orElseThrow();
        assertEquals(125.0, (Double) vendorA.get("paid_total"));
    }
}

