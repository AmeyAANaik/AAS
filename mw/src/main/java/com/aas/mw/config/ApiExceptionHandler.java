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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import com.aas.mw.service.OpeningBalanceValidationException;

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

    @ExceptionHandler(OpeningBalanceValidationException.class)
    public ResponseEntity<Map<String, Object>> handleOpeningBalanceValidation(OpeningBalanceValidationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "validation_error");
        body.put("message", ex.getMessage());
        body.put("details", ex.getErrors());
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
        FeignErrorInfo info = extractFeignInfo(ex);
        Map<String, Object> body = new HashMap<>();
        body.put("error", "request_failed");
        body.put("message", info.message());
        if (!info.details().isEmpty()) {
            body.put("details", info.details());
        }
        return ResponseEntity.status(HttpStatus.valueOf(ex.status())).body(body);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<Map<String, Object>> handleUploadSize(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "file_too_large");
        body.put("message", "File size exceeds the maximum allowed size.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    private FeignErrorInfo extractFeignInfo(FeignException ex) {
        List<String> details = new java.util.ArrayList<>();
        String content = ex.contentUTF8();
        if (content != null && !content.isBlank()) {
            try {
                Map<String, Object> payload = objectMapper.readValue(content, new TypeReference<>() {});
                String message = sanitizeMessage(extractErpMessage(payload, details));
                if (!message.isBlank()) {
                    return new FeignErrorInfo(message, List.copyOf(details));
                }
            } catch (Exception ignored) {
                String sanitized = sanitizeMessage(content);
                if (!sanitized.isBlank()) {
                    return new FeignErrorInfo(sanitized, List.of());
                }
            }
        }
        String message = sanitizeMessage(ex.getMessage());
        return new FeignErrorInfo(message.isBlank() ? "Request failed." : message, List.copyOf(details));
    }

    private String extractErpMessage(Map<String, Object> payload, List<String> details) {
        if (payload == null) {
            return "";
        }

        // ERPNext sometimes includes server messages as JSON strings.
        details.addAll(extractServerMessages(payload.get("_server_messages")));
        String reportFromDetails = extractReportNameFromDetails(details);
        if (!reportFromDetails.isBlank()) {
            return "Report \"" + reportFromDetails + "\" not found in the system. Create the Query Report and try again.";
        }

        String exception = firstText(payload.get("exception"), payload.get("exc"), payload.get("exc_type"));
        String message = firstText(payload.get("message"), payload.get("error"), payload.get("exc_type"), exception);

        String lower = (exception + " " + message).toLowerCase();
        if (lower.contains("field not permitted in query:")) {
            return "System setup is incomplete. Please run setup again and refresh the page.";
        }
        if (lower.contains("permissionerror") || lower.contains("not permitted") || lower.contains("not allowed")) {
            return "You don’t have permission to run this report.";
        }
        if (lower.contains("doesnotexisterror") && lower.contains("report")) {
            String reportName = extractReportNameFromError(exception + " " + message);
            if (!reportName.isBlank()) {
                return "Report \"" + reportName + "\" not found in the system. Create the Query Report and try again.";
            }
            return "The requested report was not found in the system. Create the Query Report and try again.";
        }
        if (lower.contains("report") && lower.contains("not found")) {
            String reportName = extractReportNameFromError(exception + " " + message);
            if (!reportName.isBlank()) {
                return "Report \"" + reportName + "\" not found in the system. Create the Query Report and try again.";
            }
        }

        if (!details.isEmpty()) {
            // Prefer a concise top-level message if we already have structured details.
            return firstText(payload.get("message"), payload.get("error"), "Request failed.");
        }

        return message;
    }

    private String extractReportNameFromDetails(List<String> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        for (String detail : details) {
            String name = extractReportNameFromError(detail);
            if (!name.isBlank()) {
                return name;
            }
            if (detail == null) {
                continue;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)report\\s+([^\\\"]+?)\\s+not\\s+found")
                    .matcher(detail);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private List<String> extractServerMessages(Object raw) {
        if (raw == null) {
            return List.of();
        }
        String text = String.valueOf(raw).trim();
        if (text.isBlank()) {
            return List.of();
        }
        try {
            List<String> messages = objectMapper.readValue(text, new TypeReference<List<String>>() {});
            List<String> flattened = new java.util.ArrayList<>();
            for (String entry : messages) {
                if (entry == null || entry.isBlank()) {
                    continue;
                }
                try {
                    Map<String, Object> msg = objectMapper.readValue(entry, new TypeReference<Map<String, Object>>() {});
                    String message = firstText(msg.get("message"), msg.get("title"));
                    if (!message.isBlank()) {
                        flattened.add(message);
                    }
                } catch (Exception ignored) {
                    flattened.add(entry);
                }
            }
            return List.copyOf(flattened);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String extractReportNameFromError(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        // Heuristic: Report "NAME" or Report NAME not found
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)report\\s+\\\"([^\\\"]+)\\\"")
                .matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        matcher = java.util.regex.Pattern
                .compile("(?i)report\\s+([A-Za-z0-9 _\\-]+)\\s+not\\s+found")
                .matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
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

    private record FeignErrorInfo(String message, List<String> details) {}
}
