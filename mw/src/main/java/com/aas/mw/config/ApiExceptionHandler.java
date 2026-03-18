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
        message = message.replace("ERPNext", "system");
        message = message.replace("erpnext", "system");
        message = message.replaceAll("(?i)\\[\\d+\\s+[A-Z_ ]+\\].*?:\\s*", "");
        message = message.replaceAll("(?i).*FeignClient#[^(]+\\([^)]*\\):\\s*", "");
        message = message.replaceAll("\\s+", " ").trim();
        if (message.startsWith("{") || message.startsWith("[")) {
            return "";
        }
        return message;
    }
}
