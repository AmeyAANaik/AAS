package com.aas.mw.controller;

import com.aas.mw.service.ReportService;
import com.aas.mw.util.CsvUtil;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<Map<String, Object>>> catalog() {
        return ResponseEntity.ok(List.of(
                catalogItem(
                        "branch-sales-profit",
                        "Branchwise Sales & Profit",
                        "Sales, cost, and profit totals grouped by branch for a date range.",
                        "company/branch-sales-profit"),
                catalogItem(
                        "overall-sales-profit",
                        "Company Overall Sales & Profit",
                        "Overall sales, cost, and profit totals across all branches for a date range.",
                        "company/overall-sales-profit"),
                catalogItemWithGroupBy(
                        "sales-profit-trend",
                        "Sales & Profit Trend",
                        "Sales, cost, and profit totals grouped by day/week/month for a date range.",
                        "company/sales-profit-trend",
                        List.of("day", "week", "month"),
                        "day"),
                catalogItem(
                        "item-price-trend",
                        "Item Price Changes",
                        "All items with their previous vs current sell price, sorted by biggest movers first.",
                        "item-price-trend"),
                catalogItem(
                        "item-price-funnel",
                        "Item Price Funnel",
                        "Distribution of items across selling price bands based on latest item prices in the selected date range.",
                        "item-price-funnel"),
                catalogItem(
                        "supplier-expenses",
                        "Supplier Expenses",
                        "Expenses grouped by supplier for a date range.",
                        "company/supplier-expenses"),
                catalogItem(
                        "branch-income",
                        "Branch Income",
                        "Income grouped by branch for a date range.",
                        "company/branch-income")));
    }

    @GetMapping("/item-price-trend")
    public ResponseEntity<List<Map<String, Object>>> itemPriceTrend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.itemPriceTrend(from, to));
    }

    @GetMapping("/item-price-trend/export")
    public ResponseEntity<String> itemPriceTrendExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = CsvUtil.toCsv(reportService.itemPriceTrend(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"item-price-trend.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/item-price-funnel")
    public ResponseEntity<List<Map<String, Object>>> itemPriceFunnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.itemPriceFunnel(from, to));
    }

    @GetMapping("/item-price-funnel/export")
    public ResponseEntity<String> itemPriceFunnelExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = CsvUtil.toCsv(reportService.itemPriceFunnel(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"item-price-funnel.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/company/branch-sales-profit")
    public ResponseEntity<List<Map<String, Object>>> companyBranchSalesProfit(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.companyBranchSalesProfit(from, to));
    }

    @GetMapping("/company/branch-sales-profit/export")
    public ResponseEntity<String> companyBranchSalesProfitExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = CsvUtil.toCsv(reportService.companyBranchSalesProfit(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"branch-sales-profit.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/company/overall-sales-profit")
    public ResponseEntity<List<Map<String, Object>>> companyOverallSalesProfit(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.companyOverallSalesProfit(from, to));
    }

    @GetMapping("/company/overall-sales-profit/export")
    public ResponseEntity<String> companyOverallSalesProfitExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = CsvUtil.toCsv(reportService.companyOverallSalesProfit(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"overall-sales-profit.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/company/supplier-expenses")
    public ResponseEntity<List<Map<String, Object>>> companySupplierExpenses(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.companySupplierExpenses(from, to));
    }

    @GetMapping("/company/supplier-expenses/export")
    public ResponseEntity<String> companySupplierExpensesExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = CsvUtil.toCsv(reportService.companySupplierExpenses(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"supplier-expenses.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/company/branch-income")
    public ResponseEntity<List<Map<String, Object>>> companyBranchIncome(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(reportService.companyBranchIncome(from, to));
    }

    @GetMapping("/company/branch-income/export")
    public ResponseEntity<String> companyBranchIncomeExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = CsvUtil.toCsv(reportService.companyBranchIncome(from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"branch-income.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/company/sales-profit-trend")
    public ResponseEntity<List<Map<String, Object>>> companySalesProfitTrend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String groupBy) {
        return ResponseEntity.ok(reportService.companySalesProfitTrend(from, to, groupBy));
    }

    @GetMapping("/company/sales-profit-trend/export")
    public ResponseEntity<String> companySalesProfitTrendExport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String groupBy) {
        String csv = CsvUtil.toCsv(reportService.companySalesProfitTrend(from, to, groupBy));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-profit-trend.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/vendor-orders")
    public ResponseEntity<List<Map<String, Object>>> vendorOrders(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(reportService.vendorOrdersByShop(vendor, month));
    }

    @GetMapping("/vendor-orders/export")
    public ResponseEntity<String> vendorOrdersExport(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String month) {
        String csv = CsvUtil.toCsv(reportService.vendorOrdersByShop(vendor, month));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor-orders.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/vendor-billing")
    public ResponseEntity<List<Map<String, Object>>> vendorBilling(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(reportService.vendorBilling(vendor, month));
    }

    @GetMapping("/vendor-billing/export")
    public ResponseEntity<String> vendorBillingExport(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String month) {
        String csv = CsvUtil.toCsv(reportService.vendorBilling(vendor, month));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor-billing.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/vendor-payments")
    public ResponseEntity<List<Map<String, Object>>> vendorPayments(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(reportService.vendorPayments(vendor, month));
    }

    @GetMapping("/vendor-payments/export")
    public ResponseEntity<String> vendorPaymentsExport(
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String month) {
        String csv = CsvUtil.toCsv(reportService.vendorPayments(vendor, month));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor-payments.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/shop-billing")
    public ResponseEntity<List<Map<String, Object>>> shopBilling(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(reportService.shopBilling(customer, month));
    }

    @GetMapping("/shop-billing/export")
    public ResponseEntity<String> shopBillingExport(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String month) {
        String csv = CsvUtil.toCsv(reportService.shopBilling(customer, month));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shop-billing.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/shop-payments")
    public ResponseEntity<List<Map<String, Object>>> shopPayments(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(reportService.shopPayments(customer, month));
    }

    @GetMapping("/shop-payments/export")
    public ResponseEntity<String> shopPaymentsExport(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String month) {
        String csv = CsvUtil.toCsv(reportService.shopPayments(customer, month));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shop-payments.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/shop-category")
    public ResponseEntity<List<Map<String, Object>>> shopCategory(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(reportService.shopCategory(customer, month));
    }

    @GetMapping("/shop-category/export")
    public ResponseEntity<String> shopCategoryExport(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String month) {
        String csv = CsvUtil.toCsv(reportService.shopCategory(customer, month));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shop-category.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    private static Map<String, Object> catalogItem(String id, String label, String description, String path) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(path, "path");
        return Map.of(
                "id", id,
                "label", label,
                "description", description,
                "path", path,
                "supportsExport", true);
    }

    private static Map<String, Object> catalogItemWithGroupBy(
            String id,
            String label,
            String description,
            String path,
            List<String> supportedGroupBy,
            String defaultGroupBy) {
        Objects.requireNonNull(supportedGroupBy, "supportedGroupBy");
        Objects.requireNonNull(defaultGroupBy, "defaultGroupBy");
        Map<String, Object> base = new java.util.LinkedHashMap<>(catalogItem(id, label, description, path));
        base.put("supportedGroupBy", supportedGroupBy);
        base.put("defaultGroupBy", defaultGroupBy);
        return Map.copyOf(base);
    }
}
