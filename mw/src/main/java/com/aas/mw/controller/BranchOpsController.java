package com.aas.mw.controller;

import com.aas.mw.service.BranchOpsService;
import java.util.List;
import java.util.Map;
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
}
