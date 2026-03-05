package com.aas.mw.controller;

import com.aas.mw.dto.UploadedFileInfo;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.client.ErpNextClient;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.OcrService;
import com.aas.mw.service.VendorInvoiceTemplateCatalog;
import com.aas.mw.service.VendorInvoiceTemplateParser;
import com.aas.mw.service.VendorPdfParser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vendors")
public class VendorInvoiceTemplateController {

    private static final String SUPPLIER = "Supplier";

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService fileService;
    private final OcrService ocrService;
    private final VendorInvoiceTemplateCatalog templateCatalog;
    private final VendorInvoiceTemplateParser templateParser;
    private final VendorPdfParser heuristicParser;

    public VendorInvoiceTemplateController(
            ErpNextClient erpNextClient,
            ErpNextFileService fileService,
            OcrService ocrService,
            VendorInvoiceTemplateCatalog templateCatalog,
            VendorInvoiceTemplateParser templateParser,
            VendorPdfParser heuristicParser) {
        this.erpNextClient = erpNextClient;
        this.fileService = fileService;
        this.ocrService = ocrService;
        this.templateCatalog = templateCatalog;
        this.templateParser = templateParser;
        this.heuristicParser = heuristicParser;
    }

    @PostMapping(value = "/{id}/invoice-template/sample", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSamplePdf(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "ERPNext session not found. Please log out and log in again (middleware restart clears ERP session cache).",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sample PDF file is required."));
        }

        // Upload the sample file to ERPNext and store the link on Supplier.
        UploadedFileInfo info = fileService.uploadFile(SUPPLIER, id, "invoice_template.pdf", file, sessionCookie);

        // Auto-detect which built-in template fits best by OCRing the sample.
        String chosenKey = VendorInvoiceTemplateCatalog.KEY_HEURISTIC_V1;
        int detectedItems = 0;
        try {
            byte[] pdfBytes = file.getBytes();
            String text = ocrService.extractTextFromPdf(pdfBytes);
            if (text != null && !text.isBlank()) {
                // Try table parser first; otherwise fallback to heuristic.
                var table = templateCatalog.byKey(VendorInvoiceTemplateCatalog.KEY_TABLE_V1);
                if (table.isPresent()) {
                    int count = templateParser.parseItems(text, table.get()).size();
                    if (count > 0) {
                        chosenKey = VendorInvoiceTemplateCatalog.KEY_TABLE_V1;
                        detectedItems = count;
                    }
                }
                if (detectedItems == 0) {
                    detectedItems = heuristicParser.parseItems(text).size();
                }
            }
        } catch (Exception ignored) {
            chosenKey = VendorInvoiceTemplateCatalog.KEY_HEURISTIC_V1;
            detectedItems = 0;
        }

        Map<String, Object> update = new HashMap<>();
        if (info.fileUrl() != null) {
            update.put("aas_invoice_template_sample_pdf", info.fileUrl());
        }
        update.put("aas_invoice_template_enabled", 1);
        update.put("aas_invoice_template_key", chosenKey);
        Map<String, Object> supplier = erpNextClient.updateResource(SUPPLIER, id, update);

        Map<String, Object> response = new HashMap<>();
        response.put("vendor", supplier);
        response.put("file", Map.of(
                "fileName", info.fileName(),
                "fileUrl", info.fileUrl(),
                "fileId", info.fileId()));
        response.put("templateChosen", Map.of(
                "key", chosenKey,
                "detectedItems", detectedItems));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/invoice-template")
    public ResponseEntity<Map<String, Object>> clearTemplate(
            @PathVariable String id,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "ERPNext session not found. Please log out and log in again (middleware restart clears ERP session cache).",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (id == null || id.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vendor id is required."));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("aas_invoice_template_enabled", 0);
        update.put("aas_invoice_template_key", "");
        update.put("aas_invoice_template_sample_pdf", "");
        Map<String, Object> supplier = erpNextClient.updateResource(SUPPLIER, id, update);
        return ResponseEntity.ok(Map.of("vendor", supplier));
    }
}
