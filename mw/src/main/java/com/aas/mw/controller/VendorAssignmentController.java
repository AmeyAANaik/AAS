package com.aas.mw.controller;

import com.aas.mw.dto.VendorAssignmentRequest;
import com.aas.mw.service.VendorAssignmentService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class VendorAssignmentController {

    private final VendorAssignmentService vendorAssignmentService;

    public VendorAssignmentController(VendorAssignmentService vendorAssignmentService) {
        this.vendorAssignmentService = vendorAssignmentService;
    }

    @PostMapping("/{id}/assign-vendor")
    public ResponseEntity<Map<String, Object>> assignVendor(
            @PathVariable String id,
            @Valid @RequestBody VendorAssignmentRequest request) {
        return ResponseEntity.ok(vendorAssignmentService.assignVendor(id, request));
    }
}
