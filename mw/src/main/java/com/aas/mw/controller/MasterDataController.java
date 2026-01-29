package com.aas.mw.controller;

import com.aas.mw.service.MasterDataService;
import com.aas.mw.dto.FieldsRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MasterDataController {

    private final MasterDataService masterDataService;

    public MasterDataController(MasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    @GetMapping("/items")
    public ResponseEntity<List<Map<String, Object>>> listItems() {
        return ResponseEntity.ok(masterDataService.listItems());
    }

    @GetMapping("/vendors")
    public ResponseEntity<List<Map<String, Object>>> listVendors() {
        return ResponseEntity.ok(masterDataService.listVendors());
    }

    @PostMapping("/vendors")
    public ResponseEntity<Map<String, Object>> createVendor(@Valid @RequestBody FieldsRequest request) {
        return ResponseEntity.ok(masterDataService.createVendor(request));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> listCategories() {
        return ResponseEntity.ok(masterDataService.listCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> createCategory(@Valid @RequestBody FieldsRequest request) {
        return ResponseEntity.ok(masterDataService.createCategory(request));
    }

    @GetMapping("/shops")
    public ResponseEntity<List<Map<String, Object>>> listShops() {
        return ResponseEntity.ok(masterDataService.listShops());
    }

    @PostMapping("/shops")
    public ResponseEntity<Map<String, Object>> createShop(@Valid @RequestBody FieldsRequest request) {
        return ResponseEntity.ok(masterDataService.createShop(request));
    }

    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> createItem(@Valid @RequestBody FieldsRequest request) {
        return ResponseEntity.ok(masterDataService.createItem(request));
    }
}
