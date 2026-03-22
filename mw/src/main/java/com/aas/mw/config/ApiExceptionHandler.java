package com.aas.mw.config;

import feign.FeignException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        Map<String, Object> body = new HashMap<>();
        body.put("error", "validation_error");
        body.put("details", details);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "bad_request");
        body.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleState(IllegalStateException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "invalid_state");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "request_failed");
        body.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeign(FeignException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "request_failed");
        body.put("message", extractFeignMessage(ex));
        return ResponseEntity.status(HttpStatus.valueOf(ex.status())).body(body);
    }

    private String extractFeignMessage(FeignException ex) {
        String content = ex.contentUTF8();
        if (content != null && !content.isBlank()) {
            try {
                Map<String, Object> payload = objectMapper.readValue(content, new TypeReference<>() {});
                String message = sanitizeMessage(firstText(payload.get("message"), payload.get("error")));
                if (!message.isBlank()) {
                    return message;
                }
            } catch (Exception ignored) {
                String sanitized = sanitizeMessage(content);
                if (!sanitized.isBlank()) {
                    return sanitized;
                }
            }
        }
        String message = sanitizeMessage(ex.getMessage());
        return message.isBlank() ? "Request failed." : message;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String sanitizeMessage(String raw) {
        if (raw == null) {
            return "";
        }
        String message = raw.trim();
        String lower = message.toLowerCase();
        if (lower.contains("field not permitted in query:")) {
            return "System setup is incomplete. Please run setup again and refresh the page.";
        }
        if (lower.contains("traceback") || lower.contains("feignclient#") || lower.contains("/api/resource/")) {
            String shortened = friendlyMessageFromRaw(message);
            if (!shortened.isBlank()) {
                return shortened;
            }
        }
        message = message.replace("ERPNext", "system");
        message = message.replace("erpnext", "system");
        message = message.replaceAll("https?://\\S+", "");
        message = message.replaceAll("//host\\.docker\\.internal:\\d+\\S*", "");
        message = message.replaceAll("(?i)\\[\\d+\\s+[A-Z_ ]+\\].*?:\\s*", "");
        message = message.replaceAll("(?i).*FeignClient#[^(]+\\([^)]*\\):\\s*", "");
        message = message.replaceAll("(?i)traceback.*", "");
        message = message.replaceAll("\\s+", " ").trim();
        if (message.startsWith("{") || message.startsWith("[")) {
            return "";
        }
        return message;
    }

    private String friendlyMessageFromRaw(String raw) {
        String lower = raw == null ? "" : raw.toLowerCase();
        if (lower.contains("field not permitted in query:")) {
            return "System setup is incomplete. Please run setup again and refresh the page.";
        }
        if (lower.contains("uommustbeintegererror")) {
            return "Purchase order could not be created because at least one item has a fractional quantity with UOM Nos. Update the item UOM mapping and try again.";
        }
        if (lower.contains("partydisabled") && lower.contains("customer")) {
            return "The selected branch is inactive. Choose an active branch or reactivate this customer in Branches.";
        }
        if (lower.contains("duplicate entry") && lower.contains("for key 'primary'")) {
            return "Some new items already exist in the system. Refresh the order and try the upload again.";
        }
        if (lower.contains("doctype") && lower.contains("deleted")) {
            return "System data is incomplete. Please re-run setup and try again.";
        }
        if (lower.contains("traceback")) {
            return "The system could not complete this request. Please refresh or contact an administrator.";
        }
        return "";
    }
}
