package com.aas.mw.controller;

import com.aas.mw.service.BranchOpsService;
import com.aas.mw.util.CsvUtil;
import com.aas.mw.util.LedgerPdfUtil;
import com.aas.mw.util.XlsUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/branch-ops")
public class BranchOpsController {

    private final BranchOpsService branchOpsService;

    public BranchOpsController(BranchOpsService branchOpsService) {
        this.branchOpsService = branchOpsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(branchOpsService.getSummary());
    }

    @GetMapping("/ledger/export")
    public ResponseEntity<byte[]> exportAllBranchLedgers(
            @RequestParam(required = false, defaultValue = "csv") String format) {
        List<Map<String, Object>> rows = branchOpsService.getAllBranchLedgerEntries();
        return buildExportResponse(rows, "branch-ledger-all", format);
    }

    @GetMapping("/{branchId}")
    public ResponseEntity<Map<String, Object>> branchDetail(@PathVariable String branchId) {
        return ResponseEntity.ok(branchOpsService.getBranchDetail(branchId));
    }

    @GetMapping("/{branchId}/orders")
    public ResponseEntity<List<Map<String, Object>>> branchOrders(
            @PathVariable String branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(branchOpsService.getBranchOrders(branchId, status, vendor, fromDate, toDate));
    }

    @GetMapping("/{branchId}/analytics")
    public ResponseEntity<Map<String, Object>> branchAnalytics(@PathVariable String branchId) {
        return ResponseEntity.ok(branchOpsService.getBranchAnalytics(branchId));
    }

    @GetMapping("/{branchId}/ledger")
    public ResponseEntity<Map<String, Object>> branchLedger(
            @PathVariable String branchId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(branchOpsService.getBranchLedger(branchId, fromDate, toDate));
    }

    @GetMapping("/{branchId}/ledger/export")
    public ResponseEntity<byte[]> exportBranchLedger(
            @PathVariable String branchId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        Map<String, Object> ledger = branchOpsService.getBranchLedger(branchId, fromDate, toDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getOrDefault("entries", List.of());
        return buildExportResponse(entries, "branch-ledger-" + sanitizeFileName(branchId), format);
    }

    @GetMapping("/{branchId}/ledger/category")
    public ResponseEntity<Map<String, Object>> branchLedgerByCategory(
            @PathVariable String branchId,
            @RequestParam String categoryId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(branchOpsService.getBranchLedgerByCategory(branchId, categoryId, fromDate, toDate));
    }

    @GetMapping("/{branchId}/ledger/category/export")
    public ResponseEntity<byte[]> exportBranchLedgerByCategory(
            @PathVariable String branchId,
            @RequestParam String categoryId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        Map<String, Object> ledger = branchOpsService.getBranchLedgerByCategory(branchId, categoryId, fromDate, toDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getOrDefault("entries", List.of());
        return buildExportResponse(entries, "branch-ledger-" + sanitizeFileName(branchId) + "-" + sanitizeFileName(categoryId), format);
    }

    @GetMapping("/{branchId}/ledger/categories/export")
    public ResponseEntity<byte[]> exportBranchCategoryLedgerSummary(
            @PathVariable String branchId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        Map<String, Object> ledger = branchOpsService.getBranchLedger(branchId, fromDate, toDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) ledger.getOrDefault("categorySummary", List.of());
        return buildExportResponse(categories, "branch-ledger-categories-" + sanitizeFileName(branchId), format);
    }

    @GetMapping("/ledger/categories/export")
    public ResponseEntity<byte[]> exportAllBranchesCategoryLedgers(
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        List<Map<String, Object>> rows = branchOpsService.getAllBranchCategorySummaries(fromDate, toDate);
        return buildExportResponse(rows, "branch-ledger-categories-all", format);
    }

    private ResponseEntity<byte[]> buildExportResponse(List<Map<String, Object>> rows, String baseName, String format) {
        String fmt = format == null || format.isBlank() ? "csv" : format.toLowerCase();
        try {
            return switch (fmt) {
                case "xlsx" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".xlsx\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(XlsUtil.toXls(rows));
                case "pdf" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(LedgerPdfUtil.toPdf(rows, baseName.replace('-', ' ')));
                default -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".csv\"")
                        .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                        .body(CsvUtil.toCsv(rows).getBytes(StandardCharsets.UTF_8));
            };
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
