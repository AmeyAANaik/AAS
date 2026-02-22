package com.aas.mw.controller;

import com.aas.mw.dto.VendorFieldMeta;
import com.aas.mw.meta.VendorFieldRegistry;
import com.aas.mw.meta.VendorFieldSpec;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta/vendors")
public class VendorMetaController {

    private final VendorFieldRegistry vendorFieldRegistry;

    public VendorMetaController(VendorFieldRegistry vendorFieldRegistry) {
        this.vendorFieldRegistry = vendorFieldRegistry;
    }

    @GetMapping("/fields")
    public ResponseEntity<List<VendorFieldMeta>> listVendorFields() {
        List<VendorFieldMeta> fields = vendorFieldRegistry.vendorFields().stream()
                .map(this::toMeta)
                .toList();
        return ResponseEntity.ok(fields);
    }

    private VendorFieldMeta toMeta(VendorFieldSpec spec) {
        return new VendorFieldMeta(
                spec.key(),
                spec.label(),
                spec.fieldtype(),
                spec.options(),
                spec.required());
    }
}

