package com.aas.mw.controller;

import com.aas.mw.dto.InvoiceRequest;
import com.aas.mw.dto.InvoiceDeliveryRequest;
import com.aas.mw.service.InvoiceDeliveryService;
import com.aas.mw.service.InvoiceService;
import com.aas.mw.util.CsvUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceDeliveryService invoiceDeliveryService;

    public InvoiceController(InvoiceService invoiceService, InvoiceDeliveryService invoiceDeliveryService) {
        this.invoiceService = invoiceService;
        this.invoiceDeliveryService = invoiceDeliveryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.createInvoice(request));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listInvoices(
            @RequestParam(required = false) String partyType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        String resolvedPartyId = partyId;
        if (resolvedPartyId == null || resolvedPartyId.isBlank()) {
            resolvedPartyId = customer;
        }
        return ResponseEntity.ok(invoiceService.listInvoices(partyType, resolvedPartyId, fromDate, toDate));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportInvoices(
            @RequestParam(required = false) String partyType,
            @RequestParam(required = false) String partyId,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        String resolvedPartyId = partyId;
        if (resolvedPartyId == null || resolvedPartyId.isBlank()) {
            resolvedPartyId = customer;
        }
        String csv = CsvUtil.toCsv(invoiceService.listInvoices(partyType, resolvedPartyId, fromDate, toDate));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String id,
            @RequestParam(required = false) String partyType) {
        byte[] pdf = invoiceService.downloadPdf(id, partyType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/fix-posting-dates")
    public ResponseEntity<Map<String, Object>> fixPostingDates() {
        return ResponseEntity.ok(invoiceService.bulkFixPostingDates());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteInvoice(@PathVariable String id) {
        return ResponseEntity.ok(invoiceService.deleteInvoice(id));
    }

    @GetMapping("/{id}/delivery-preview")
    public ResponseEntity<Map<String, Object>> deliveryPreview(@PathVariable String id) {
        return ResponseEntity.ok(invoiceDeliveryService.preview(id));
    }

    @PostMapping("/{id}/send-email")
    public ResponseEntity<Map<String, Object>> sendEmail(
            @PathVariable String id,
            @RequestBody(required = false) InvoiceDeliveryRequest request) {
        return ResponseEntity.ok(invoiceDeliveryService.sendEmail(id, request));
    }

    @PostMapping("/{id}/send-whatsapp")
    public ResponseEntity<Map<String, Object>> sendWhatsApp(
            @PathVariable String id,
            @RequestBody(required = false) InvoiceDeliveryRequest request) {
        return ResponseEntity.ok(invoiceDeliveryService.sendWhatsApp(id, request));
    }
}
