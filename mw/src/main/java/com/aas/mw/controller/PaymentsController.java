package com.aas.mw.controller;

import com.aas.mw.dto.PaymentRequest;
import com.aas.mw.dto.UploadedFileInfo;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.service.PaymentDueService;
import com.aas.mw.service.PaymentService;
import com.aas.mw.service.ErpSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/payments")
public class PaymentsController {

    private final PaymentService paymentService;
    private final PaymentDueService paymentDueService;
    private final ErpNextFileService fileService;
    private final ObjectMapper objectMapper;

    public PaymentsController(
            PaymentService paymentService,
            PaymentDueService paymentDueService,
            ErpNextFileService fileService,
            ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.paymentDueService = paymentDueService;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/due-by-category")
    public ResponseEntity<Map<String, Object>> dueByCategory(
            @RequestParam String partyType,
            @RequestParam String partyId,
            @RequestParam String categoryId) {
        return ResponseEntity.ok(paymentDueService.dueByCategory(partyType, partyId, categoryId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestBody PaymentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Evidence upload is required. Use /api/payments/with-attachments.",
                "errorCode", "EVIDENCE_REQUIRED"));
    }

    @PostMapping(value = "/with-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createPaymentWithAttachments(
            @RequestPart("payment") MultipartFile payment,
            @RequestPart("files") MultipartFile[] files,
            HttpServletRequest request,
            Authentication authentication) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Evidence upload is required.",
                    "errorCode", "EVIDENCE_REQUIRED"));
        }
        boolean hasAnyFile = false;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                hasAnyFile = true;
                break;
            }
        }
        if (!hasAnyFile) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Evidence upload is required.",
                    "errorCode", "EVIDENCE_REQUIRED"));
        }

        PaymentRequest paymentRequest = parsePaymentRequest(payment);
        if (paymentRequest == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing or invalid payment payload.",
                    "errorCode", "INVALID_PAYMENT_PAYLOAD"));
        }
        String customer = paymentRequest.getCustomer() == null ? "" : paymentRequest.getCustomer().trim();
        String company = paymentRequest.getCompany() == null ? "" : paymentRequest.getCompany().trim();
        if (customer.isBlank() || company.isBlank() || paymentRequest.getAmount() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Missing required payment fields (customer/company/amount).",
                    "errorCode", "INVALID_PAYMENT_PAYLOAD"));
        }

        String partyType = paymentRequest.getPartyType() == null ? "" : paymentRequest.getPartyType().trim();
        String categoryId = paymentRequest.getCategoryId() == null ? "" : paymentRequest.getCategoryId().trim();
        if (!categoryId.isBlank()) {
            Map<String, Object> due = paymentDueService.dueByCategory(partyType, customer, categoryId);
            java.math.BigDecimal dueAmount = asDecimal(due.get("dueAmount"));
            java.math.BigDecimal underReviewAmount = asDecimal(due.get("underReviewAmount"));
            java.math.BigDecimal availableDueAmount = asDecimal(due.get("availableDueAmount"));
            java.math.BigDecimal requested = paymentRequest.getAmount() == null ? java.math.BigDecimal.ZERO : paymentRequest.getAmount();
            if (requested.compareTo(java.math.BigDecimal.ZERO) <= 0
                    || availableDueAmount.compareTo(java.math.BigDecimal.ZERO) <= 0
                    || requested.compareTo(availableDueAmount) > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "Payment amount exceeds available due for this category while another payment is under review.",
                        "errorCode", "OVERPAYMENT_NOT_ALLOWED",
                        "dueAmount", dueAmount,
                        "underReviewAmount", underReviewAmount,
                        "availableDueAmount", availableDueAmount,
                        "requestedAmount", requested));
            }
        }

        String createdBy = authentication == null ? "" : String.valueOf(authentication.getName());
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equalsIgnoreCase(String.valueOf(authority.getAuthority())));

        Map<String, Object> createdPayment = paymentService.createPaymentDraft(paymentRequest, createdBy, isAdmin);
        String paymentId = String.valueOf(createdPayment.getOrDefault("name", "")).trim();
        if (paymentId.isBlank()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Unable to create payment draft.",
                    "errorCode", "PAYMENT_CREATE_FAILED"));
        }

        List<UploadedFileInfo> uploaded = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String filename = resolveAttachmentName(file);
                uploaded.add(fileService.uploadFile("Payment Entry", paymentId, filename, file, sessionCookie));
            }
        } catch (Exception ex) {
            paymentService.deletePaymentDraft(paymentId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Payment draft created but evidence upload failed. The draft has been removed. Please try again.",
                    "errorCode", "EVIDENCE_UPLOAD_FAILED"));
        }

        if (uploaded.isEmpty()) {
            paymentService.deletePaymentDraft(paymentId);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Evidence upload is required.",
                    "errorCode", "EVIDENCE_REQUIRED"));
        }

        Map<String, Object> paymentDoc = createdPayment;
        return ResponseEntity.ok(Map.of(
                "payment", paymentDoc,
                "files", uploaded));
    }

    private PaymentRequest parsePaymentRequest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(file.getBytes(), PaymentRequest.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPaymentAttachments(
            @PathVariable String id,
            @RequestPart("files") MultipartFile[] files,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "Session expired. Please log out and log in again.",
                            "errorCode", "ERP_SESSION_MISSING"));
        }
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "No files uploaded."));
        }
        List<UploadedFileInfo> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String filename = resolveAttachmentName(file);
            uploaded.add(fileService.uploadFile("Payment Entry", id, filename, file, sessionCookie));
        }
        return ResponseEntity.ok(Map.of(
                "paymentId", id,
                "files", uploaded));
    }

    private String resolveAttachmentName(MultipartFile file) {
        String original = file == null ? "" : String.valueOf(file.getOriginalFilename());
        if (original == null || original.isBlank()) {
            return "payment_voucher";
        }
        return original;
    }

    private java.math.BigDecimal asDecimal(Object value) {
        if (value instanceof java.math.BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(value.toString());
        } catch (Exception ex) {
            return java.math.BigDecimal.ZERO;
        }
    }
}
