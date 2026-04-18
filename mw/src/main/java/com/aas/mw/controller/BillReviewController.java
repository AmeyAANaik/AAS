package com.aas.mw.controller;

import com.aas.mw.service.BillReviewService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bill-review")
public class BillReviewController {

    private final BillReviewService billReviewService;

    public BillReviewController(BillReviewService billReviewService) {
        this.billReviewService = billReviewService;
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> pendingCount() {
        return ResponseEntity.ok(billReviewService.getPendingCount());
    }

    @GetMapping("/payments")
    public ResponseEntity<Object> listPayments(
            @RequestParam(defaultValue = "UNDER_REVIEW") String status,
            @RequestParam(required = false) String partyType) {
        return ResponseEntity.ok(billReviewService.listPaymentsByStatus(status, partyType));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<Map<String, Object>> paymentDetail(@PathVariable String paymentId) {
        return ResponseEntity.ok(billReviewService.getPaymentDetail(paymentId));
    }

    @PutMapping("/payments/{paymentId}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable String paymentId,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        String notes = body == null ? "" : String.valueOf(body.getOrDefault("notes", "")).trim();
        String actor = authentication == null ? "" : String.valueOf(authentication.getName());
        return ResponseEntity.ok(billReviewService.approve(paymentId, notes, actor));
    }

    @PutMapping("/payments/{paymentId}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String paymentId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String notes = body == null ? "" : String.valueOf(body.getOrDefault("notes", "")).trim();
        String actor = authentication == null ? "" : String.valueOf(authentication.getName());
        return ResponseEntity.ok(billReviewService.reject(paymentId, notes, actor));
    }
}
