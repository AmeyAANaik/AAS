package com.aas.mw.controller;

import com.aas.mw.service.MasterDataService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> listCategories() {
        return ResponseEntity.ok(masterDataService.listCategories());
    }
}
