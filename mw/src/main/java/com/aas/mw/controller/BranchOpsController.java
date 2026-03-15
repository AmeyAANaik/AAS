package com.aas.mw.controller;

import com.aas.mw.service.BranchOpsService;
import com.aas.mw.util.CsvUtil;
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
    public ResponseEntity<String> exportAllBranchLedgers() {
        String csv = CsvUtil.toCsv(branchOpsService.getAllBranchLedgerEntries());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"branch-ledger-all.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
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
    public ResponseEntity<Map<String, Object>> branchLedger(@PathVariable String branchId) {
        return ResponseEntity.ok(branchOpsService.getBranchLedger(branchId));
    }

    @GetMapping("/{branchId}/ledger/export")
    public ResponseEntity<String> exportBranchLedger(@PathVariable String branchId) {
        Map<String, Object> ledger = branchOpsService.getBranchLedger(branchId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getOrDefault("entries", List.of());
        String csv = CsvUtil.toCsv(entries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"branch-ledger-" + sanitizeFileName(branchId) + ".csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
