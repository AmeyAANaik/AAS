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

    @GetMapping("/items")
    public ResponseEntity<Object> listItems(
            @RequestParam(defaultValue = "UNDER_REVIEW") String status,
            @RequestParam(required = false) String partyType) {
        return ResponseEntity.ok(billReviewService.listItemsByStatus(status, partyType));
    }

    @GetMapping("/items/{itemType}/{documentId}")
    public ResponseEntity<Map<String, Object>> itemDetail(
            @PathVariable String itemType,
            @PathVariable String documentId) {
        return ResponseEntity.ok(billReviewService.getReviewDetail(itemType, documentId));
    }

    @PutMapping("/items/{itemType}/{documentId}/approve")
    public ResponseEntity<Map<String, Object>> approveItem(
            @PathVariable String itemType,
            @PathVariable String documentId,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        String notes = body == null ? "" : String.valueOf(body.getOrDefault("notes", "")).trim();
        String actor = authentication == null ? "" : String.valueOf(authentication.getName());
        return ResponseEntity.ok(billReviewService.approve(itemType, documentId, notes, actor));
    }

    @PutMapping("/items/{itemType}/{documentId}/reject")
    public ResponseEntity<Map<String, Object>> rejectItem(
            @PathVariable String itemType,
            @PathVariable String documentId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String notes = body == null ? "" : String.valueOf(body.getOrDefault("notes", "")).trim();
        String actor = authentication == null ? "" : String.valueOf(authentication.getName());
        return ResponseEntity.ok(billReviewService.reject(itemType, documentId, notes, actor));
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
        return ResponseEntity.ok(billReviewService.approve(BillReviewService.ITEM_TYPE_PAYMENT, paymentId, notes, actor));
    }

    @PutMapping("/payments/{paymentId}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable String paymentId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String notes = body == null ? "" : String.valueOf(body.getOrDefault("notes", "")).trim();
        String actor = authentication == null ? "" : String.valueOf(authentication.getName());
        return ResponseEntity.ok(billReviewService.reject(BillReviewService.ITEM_TYPE_PAYMENT, paymentId, notes, actor));
    }
}
