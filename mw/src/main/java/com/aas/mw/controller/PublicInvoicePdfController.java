package com.aas.mw.controller;

import com.aas.mw.service.InvoiceService;
import com.aas.mw.service.PdfTokenStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/invoices")
public class PublicInvoicePdfController {

    private final PdfTokenStore pdfTokenStore;
    private final InvoiceService invoiceService;

    public PublicInvoicePdfController(PdfTokenStore pdfTokenStore, InvoiceService invoiceService) {
        this.pdfTokenStore = pdfTokenStore;
        this.invoiceService = invoiceService;
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String token) {
        String invoiceId = pdfTokenStore.resolveInvoiceId(token);
        if (invoiceId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        byte[] pdf = invoiceService.downloadPdf(invoiceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + invoiceId + ".pdf\"")
                .contentType(MediaType.parseMediaType("application/pdf"))
                .body(pdf);
    }
}
