package com.aas.mw.controller;

import com.aas.mw.dto.AdjustmentNoteRequest;
import com.aas.mw.service.AdjustmentNoteService;
import com.aas.mw.service.ErpSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/adjustment-notes")
public class AdjustmentNotesController {

    private final AdjustmentNoteService adjustmentNoteService;
    private final ObjectMapper objectMapper;

    public AdjustmentNotesController(
            AdjustmentNoteService adjustmentNoteService,
            ObjectMapper objectMapper) {
        this.adjustmentNoteService = adjustmentNoteService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/with-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createWithAttachments(
            @RequestPart("note") MultipartFile note,
            @RequestPart("files") MultipartFile[] files,
            HttpServletRequest request,
            Authentication authentication) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Session expired. Please log out and log in again.",
                    "errorCode", "ERP_SESSION_MISSING"));
        }
        AdjustmentNoteRequest payload = parseRequest(note);
        String actor = authentication == null ? "" : String.valueOf(authentication.getName());
        return ResponseEntity.ok(adjustmentNoteService.createDraftWithAttachments(payload, files, actor, sessionCookie));
    }

    private AdjustmentNoteRequest parseRequest(MultipartFile note) {
        if (note == null || note.isEmpty()) {
            throw new IllegalArgumentException("Missing adjustment note payload.");
        }
        try {
            return objectMapper.readValue(note.getBytes(), AdjustmentNoteRequest.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid adjustment note payload.", ex);
        }
    }
}
