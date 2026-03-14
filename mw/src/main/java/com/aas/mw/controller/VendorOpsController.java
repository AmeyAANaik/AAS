package com.aas.mw.controller;

import com.aas.mw.service.VendorOpsService;
import java.util.List;
import java.util.Map;
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
}
