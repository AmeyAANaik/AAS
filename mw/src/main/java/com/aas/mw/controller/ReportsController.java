package com.aas.mw.controller;

import com.aas.mw.service.ReportService;
import com.aas.mw.util.CsvUtil;
import java.util.List;
import java.util.Map;
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
}
