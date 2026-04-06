package com.aas.mw.controller;

import com.aas.mw.dto.ApproveMasterDataReviewRequest;
import com.aas.mw.service.MasterDataReviewService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/master-data-review")
public class MasterDataReviewController {

    private final MasterDataReviewService masterDataReviewService;

    public MasterDataReviewController(MasterDataReviewService masterDataReviewService) {
        this.masterDataReviewService = masterDataReviewService;
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        return ResponseEntity.ok(masterDataReviewService.getPendingCount());
    }

    @GetMapping("/items")
    public ResponseEntity<List<Map<String, Object>>> listReviewItems(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(masterDataReviewService.listReviewItems(status));
    }

    @GetMapping("/items/{itemId}")
    public ResponseEntity<Map<String, Object>> getReviewDetail(@PathVariable String itemId) {
        return ResponseEntity.ok(masterDataReviewService.getReviewDetail(itemId));
    }

    @PutMapping("/items/{itemId}/approve")
    public ResponseEntity<Map<String, Object>> approveReviewItem(
            @PathVariable String itemId,
            @RequestBody ApproveMasterDataReviewRequest request) {
        return ResponseEntity.ok(masterDataReviewService.approveReviewItem(itemId, request));
    }
}
