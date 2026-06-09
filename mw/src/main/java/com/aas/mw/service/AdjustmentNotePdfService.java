package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.ErpSetupProperties;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdjustmentNotePdfService {

    static final String PRINT_FORMAT_NAME = "AAS Adjustment Note Print";

    private final ErpNextClient erpNextClient;
    private final String erpSetupUsername;
    private final String erpSetupPassword;

    public AdjustmentNotePdfService(ErpNextClient erpNextClient, ErpSetupProperties erpSetupProperties) {
        this.erpNextClient = erpNextClient;
        this.erpSetupUsername = erpSetupProperties.getFullName();
        this.erpSetupPassword = erpSetupProperties.getPassword();
    }

    public byte[] downloadPdf(String noteId) {
        if (noteId == null || noteId.isBlank()) {
            throw new IllegalArgumentException("Adjustment note id is required.");
        }
        Map<String, Object> note = unwrap(erpNextClient.getResource(AdjustmentNoteErpService.JOURNAL_ENTRY, noteId));
        if (note.isEmpty()) {
            throw new IllegalArgumentException("Adjustment note not found.");
        }
        int docstatus = asInt(note.get("docstatus"));
        String reviewStatus = asText(note.get(AdjustmentNoteErpService.FIELD_REVIEW_STATUS));
        if (docstatus != 1 || !AdjustmentNoteErpService.STATUS_APPROVED.equalsIgnoreCase(reviewStatus)) {
            throw new IllegalArgumentException("Credit/debit note receipt is available only after approval.");
        }

        String privilegedSession = operationalReadSession();
        byte[] pdf = privilegedSession == null || privilegedSession.isBlank()
                ? erpNextClient.downloadPdf(AdjustmentNoteErpService.JOURNAL_ENTRY, noteId, Map.of("format", PRINT_FORMAT_NAME))
                : erpNextClient.downloadPdfWithSession(
                        AdjustmentNoteErpService.JOURNAL_ENTRY,
                        noteId,
                        Map.of("format", PRINT_FORMAT_NAME),
                        privilegedSession);

        if (!looksLikePdf(pdf)) {
            String snippet = pdf == null ? "" : new String(pdf, 0, Math.min(pdf.length, 240), StandardCharsets.UTF_8);
            throw new IllegalStateException("ERPNext did not return a valid PDF for " + noteId + ". " + snippet);
        }
        return pdf;
    }

    private String operationalReadSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String role = authentication == null
                ? ""
                : authentication.getAuthorities().stream()
                        .map(Object::toString)
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring("ROLE_".length()))
                        .findFirst()
                        .orElse("");
        if (!"HELPER".equalsIgnoreCase(role)) {
            return null;
        }
        if (erpSetupUsername == null || erpSetupUsername.isBlank() || erpSetupPassword == null || erpSetupPassword.isBlank()) {
            return null;
        }
        return erpNextClient.login(erpSetupUsername, erpSetupPassword);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private boolean looksLikePdf(byte[] bytes) {
        return bytes != null
                && bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }
}
