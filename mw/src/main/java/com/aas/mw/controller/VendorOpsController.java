package com.aas.mw.controller;

import com.aas.mw.service.VendorOpsService;
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
@RequestMapping("/api/vendor-ops")
public class VendorOpsController {

    private final VendorOpsService vendorOpsService;

    public VendorOpsController(VendorOpsService vendorOpsService) {
        this.vendorOpsService = vendorOpsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(vendorOpsService.getSummary());
    }

    @GetMapping("/ledger/export")
    public ResponseEntity<String> exportAllVendorLedgers() {
        String csv = CsvUtil.toCsv(vendorOpsService.getAllVendorLedgerEntries());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor-ledger-all.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/{vendorId}")
    public ResponseEntity<Map<String, Object>> vendorDetail(@PathVariable String vendorId) {
        return ResponseEntity.ok(vendorOpsService.getVendorDetail(vendorId));
    }

    @GetMapping("/{vendorId}/orders")
    public ResponseEntity<List<Map<String, Object>>> vendorOrders(
            @PathVariable String vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(vendorOpsService.getVendorOrders(vendorId, status, branch, fromDate, toDate));
    }

    @GetMapping("/{vendorId}/analytics")
    public ResponseEntity<Map<String, Object>> vendorAnalytics(@PathVariable String vendorId) {
        return ResponseEntity.ok(vendorOpsService.getVendorAnalytics(vendorId));
    }

    @GetMapping("/{vendorId}/ledger")
    public ResponseEntity<Map<String, Object>> vendorLedger(@PathVariable String vendorId) {
        return ResponseEntity.ok(vendorOpsService.getVendorLedger(vendorId));
    }

    @GetMapping("/{vendorId}/ledger/export")
    public ResponseEntity<String> exportVendorLedger(@PathVariable String vendorId) {
        Map<String, Object> ledger = vendorOpsService.getVendorLedger(vendorId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getOrDefault("entries", List.of());
        String csv = CsvUtil.toCsv(entries);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendor-ledger-" + sanitizeFileName(vendorId) + ".csv\"")
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
