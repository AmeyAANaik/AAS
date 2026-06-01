package com.aas.mw.controller;

import com.aas.mw.service.OpeningBalanceImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/companies/{id}/opening-balances")
public class OpeningBalancesController {

    private final OpeningBalanceImportService openingBalanceImportService;

    public OpeningBalancesController(OpeningBalanceImportService openingBalanceImportService) {
        this.openingBalanceImportService = openingBalanceImportService;
    }

    @GetMapping("/template")
    public ResponseEntity<String> template(@PathVariable("id") String companyId) {
        String csv = openingBalanceImportService.templateCsv(companyId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"opening-balances-template.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> preview(
            @PathVariable("id") String companyId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("cutoverDate") String cutoverDate) {
        return ResponseEntity.ok(openingBalanceImportService.preview(companyId, file, cutoverDate));
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> apply(
            @PathVariable("id") String companyId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("cutoverDate") String cutoverDate) {
        return ResponseEntity.ok(openingBalanceImportService.apply(companyId, file, cutoverDate));
    }

    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> openingInvoices(
            @PathVariable("id") String companyId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(openingBalanceImportService.listOpeningInvoices(companyId, fromDate, toDate));
    }
}
