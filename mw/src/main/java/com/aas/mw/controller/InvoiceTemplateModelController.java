package com.aas.mw.controller;

import com.aas.mw.service.InvoiceTemplateModelService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendors")
public class InvoiceTemplateModelController {

    private final InvoiceTemplateModelService invoiceTemplateModelService;

    public InvoiceTemplateModelController(InvoiceTemplateModelService invoiceTemplateModelService) {
        this.invoiceTemplateModelService = invoiceTemplateModelService;
    }

    @GetMapping("/template-model")
    public ResponseEntity<Map<String, Object>> getTemplateModel() {
        return ResponseEntity.ok(invoiceTemplateModelService.describeModel());
    }
}
