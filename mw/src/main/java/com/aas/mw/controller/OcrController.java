package com.aas.mw.controller;

import com.aas.mw.dto.OcrHealthResponse;
import com.aas.mw.service.OcrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @GetMapping("/health")
    public ResponseEntity<OcrHealthResponse> health() {
        boolean ok = ocrService.healthCheck();
        if (ok) {
            return ResponseEntity.ok(new OcrHealthResponse(true, "OCR ready."));
        }
        return ResponseEntity.status(503).body(new OcrHealthResponse(false, "OCR not available."));
    }
}
