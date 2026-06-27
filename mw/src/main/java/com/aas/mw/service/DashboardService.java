package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Assembles the admin Dashboard snapshot entirely on the backend so the UI receives compact,
 * pre-computed metrics instead of raw invoice/item lists. Reuses the existing operational services
 * (orders, vendor-ops, branch-ops, invoices, items) and the ERPNext stock ledger (Bin).
 */
@Service
public class DashboardService {

    private final OrderService orderService;
    private final VendorOpsService vendorOpsService;
    private final BranchOpsService branchOpsService;
    private final InvoiceService invoiceService;
    private final MasterDataService masterDataService;
    private final ErpNextClient erpNextClient;

    public DashboardService(
            OrderService orderService,
            VendorOpsService vendorOpsService,
            BranchOpsService branchOpsService,
            InvoiceService invoiceService,
            MasterDataService masterDataService,
            ErpNextClient erpNextClient) {
        this.orderService = orderService;
        this.vendorOpsService = vendorOpsService;
        this.branchOpsService = branchOpsService;
        this.invoiceService = invoiceService;
        this.masterDataService = masterDataService;
        this.erpNextClient = erpNextClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getSummary(String from, String to) {
        LocalDate fromDate = parseDate(from);
        LocalDate toDate = parseDate(to);

        // Orders — exact per-status counts (no page cap).
        List<Map<String, Object>> orderStatus =
                orderService.orderStatusCounts(null, null, null, null, null, from, to, null);

        // Vendor operations + outstanding bills.
        Map<String, Object> vendorSummary = vendorOpsService.getSummary();
        Map<String, Object> vendorTotals = asMap(vendorSummary.get("totals"));
        List<Map<String, Object>> vendorRows = (List<Map<String, Object>>) vendorSummary.getOrDefault("vendors", List.of());
        List<Map<String, Object>> billsByVendor = dueRows(vendorRows, "vendorName", "vendorId", "pendingBillAmount");
        long vendorsWithDues = billsByVendor.size();

        // Branch operations + open receivables.
        Map<String, Object> branchSummary = branchOpsService.getSummary();
        Map<String, Object> branchTotals = asMap(branchSummary.get("totals"));
        List<Map<String, Object>> branchRows = (List<Map<String, Object>>) branchSummary.getOrDefault("branches", List.of());
        List<Map<String, Object>> billsByBranch = dueRows(branchRows, "branchName", "branchId", "openReceivableAmount");
        long branchesWithDues = billsByBranch.size();

        // Sales — net revenue (GST excluded) by day across the range.
        List<Map<String, Object>> invoices = invoiceService.listInvoices("Customer", null, from, to);
        List<Map<String, Object>> revenueSeries = buildRevenueSeries(invoices, fromDate, toDate);
        Map<String, Object> salesSummary = buildSalesSummary(invoices.size(), revenueSeries, from, to);

        // Stock — item count + real on-hand quantity from the ERPNext stock ledger.
        Map<String, Object> stockSnapshot = new LinkedHashMap<>();
        stockSnapshot.put("totalItems", masterDataService.listItems().size());
        stockSnapshot.put("totalQuantity", totalStockOnHand());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderStatus", orderStatus);
        snapshot.put("billsByBranch", billsByBranch);
        snapshot.put("billsByVendor", billsByVendor);
        snapshot.put("stockSnapshot", stockSnapshot);
        snapshot.put("salesSummary", salesSummary);
        snapshot.put("revenueSeries", revenueSeries);
        snapshot.put("branchesWithDues", branchesWithDues);
        snapshot.put("vendorsWithDues", vendorsWithDues);
        snapshot.put("vendorOperations", vendorTotals);
        snapshot.put("branchOperations", branchTotals);
        snapshot.put("periodLabel", periodLabel(fromDate));
        return snapshot;
    }

    // Filter to parties with a positive due, map to {name, total}, sort by total desc.
    private List<Map<String, Object>> dueRows(
            List<Map<String, Object>> rows, String nameKey, String idKey, String amountKey) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            double total = asDouble(row.get(amountKey));
            if (total <= 0) {
                continue;
            }
            String name = asText(row.get(nameKey));
            if (name.isBlank()) {
                name = asText(row.get(idKey));
            }
            if (name.isBlank()) {
                name = "Unknown";
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("total", round(total));
            result.add(out);
        }
        result.sort((a, b) -> Double.compare(asDouble(b.get("total")), asDouble(a.get("total"))));
        return result;
    }

    private List<Map<String, Object>> buildRevenueSeries(
            List<Map<String, Object>> invoices, LocalDate from, LocalDate to) {
        Map<String, Double> totalsByDay = new TreeMap<>();
        for (Map<String, Object> invoice : invoices) {
            String rawDate = asText(invoice.get("posting_date"));
            if (rawDate.isBlank()) {
                continue;
            }
            String day = rawDate.length() >= 10 ? rawDate.substring(0, 10) : rawDate;
            totalsByDay.merge(day, asDouble(invoice.get("net_total")), (a, b) -> a + b);
        }

        List<Map<String, Object>> points = new ArrayList<>();
        if (from == null || to == null) {
            return points;
        }
        DateTimeFormatter iso = DateTimeFormatter.ISO_LOCAL_DATE;
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            String isoDay = cursor.format(iso);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", isoDay);
            point.put("shortLabel", shortDay(cursor));
            point.put("value", round(totalsByDay.getOrDefault(isoDay, 0.0)));
            points.add(point);
        }
        return points;
    }

    private Map<String, Object> buildSalesSummary(
            int invoiceCount, List<Map<String, Object>> revenueSeries, String from, String to) {
        double totalRevenue = 0.0;
        double peakRevenue = 0.0;
        String peakLabel = null;
        for (Map<String, Object> point : revenueSeries) {
            double value = asDouble(point.get("value"));
            totalRevenue += value;
            if (peakLabel == null || value > peakRevenue) {
                peakRevenue = value;
                peakLabel = asText(point.get("label"));
            }
        }
        double averageDailyRevenue = revenueSeries.isEmpty() ? 0.0 : totalRevenue / revenueSeries.size();
        String rangeLabel = from + " to " + to;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("invoiceCount", invoiceCount);
        summary.put("totalRevenue", round(totalRevenue));
        summary.put("dateRangeLabel", rangeLabel);
        summary.put("averageDailyRevenue", round(averageDailyRevenue));
        summary.put("peakRevenue", round(peakRevenue));
        summary.put("peakRevenueLabel", peakLabel == null ? rangeLabel : peakLabel);
        return summary;
    }

    // Sum on-hand quantity across the ERPNext stock ledger (all warehouses). 0 = no limit.
    private double totalStockOnHand() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"actual_qty\"]");
        params.put("limit_page_length", 0);
        double total = 0.0;
        for (Map<String, Object> bin : erpNextClient.listResources("Bin", params)) {
            total += asDouble(bin.get("actual_qty"));
        }
        return round(total);
    }

    private String periodLabel(LocalDate date) {
        if (date == null) {
            return "";
        }
        String month = date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return month + " " + date.getYear();
    }

    private String shortDay(LocalDate date) {
        String month = date.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return String.format("%02d %s", date.getDayOfMonth(), month);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim().substring(0, Math.min(10, value.trim().length())));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
