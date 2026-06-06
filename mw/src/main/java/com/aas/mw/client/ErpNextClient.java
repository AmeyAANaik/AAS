package com.aas.mw.client;

import feign.FeignException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class ErpNextClient {

    private static final List<String> OPTIONAL_QUERY_FIELDS = List.of(
            "aas_category",
            "aas_category_code",
            "aas_margin_percent",
            "aas_gst_percent",
            "aas_invoice_version_status",
            "aas_replaced_by",
            "aas_vendor_code",
            "aas_is_deleted",
            "aas_deleted_at",
            "aas_invoice_email",
            "aas_transport_charge",
            "aas_rounding_adjustment",
            "aas_review_status",
            "aas_review_source_order",
            "aas_review_source_invoice_ref",
            "aas_review_created_at",
            "aas_review_created_by",
            "aas_review_notes",
            "aas_review_default_margin_used",
            "aas_whatsapp_number",
            "aas_food_license_no",
            "tax_id");

    private final ErpNextFeignClient feignClient;

    public ErpNextClient(ErpNextFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public String login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("usr", username);
        form.add("pwd", password);
        ResponseEntity<Void> response = feignClient.login(form);
        return extractSessionCookie(response.getHeaders());
    }

    public Map<String, Object> getResourceWithSession(String doctype, String id, String sessionCookie) {
        return feignClient.getResourceWithCookie(doctype, id, sessionCookie);
    }

    public List<String> getUserRoles(String sessionCookie, String username) {
        Map<String, Object> user = unwrapResource(getResourceWithSession("User", username, sessionCookie));
        Object roles = user == null ? null : user.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(entry -> entry instanceof Map<?, ?> map ? map.get("role") : null)
                    .filter(r -> r != null)
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    public String getLoggedInUser(String sessionCookie) {
        if (sessionCookie == null || sessionCookie.isBlank()) {
            return "";
        }
        Map<String, Object> response = feignClient.getMethodWithCookie(
                "frappe.auth.get_logged_user",
                Collections.emptyMap(),
                sessionCookie);
        Object message = response == null ? null : response.get("message");
        return asText(message);
    }

    public String resolveUserId(String sessionCookie, String loginId) {
        String normalizedLogin = asText(loginId);
        if (normalizedLogin.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> user = unwrapResource(getResourceWithSession("User", normalizedLogin, sessionCookie));
            String name = asText(user == null ? null : user.get("name"));
            if (!name.isBlank()) {
                return name;
            }
        } catch (Exception ignored) {
            // Fall back to email/username lookup.
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"email\",\"username\"]");
        params.put("limit_page_length", "1");
        params.put("or_filters", "[[\"User\",\"email\",\"=\",\"" + escapeJson(normalizedLogin) + "\"],"
                + "[\"User\",\"username\",\"=\",\"" + escapeJson(normalizedLogin) + "\"]]");
        List<Map<String, Object>> rows = listResourcesWithSession("User", params, sessionCookie);
        if (rows.isEmpty()) {
            return "";
        }
        return asText(rows.get(0).get("name"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public Map<String, Object> getResource(String doctype, String id) {
        return feignClient.getResource(doctype, id);
    }

    public List<Map<String, Object>> listResources(String doctype, Map<String, Object> params) {
        return listResourcesInternal(doctype, params, null);
    }

    public List<Map<String, Object>> listResourcesWithSession(String doctype, Map<String, Object> params, String sessionCookie) {
        return listResourcesInternal(doctype, params, sessionCookie);
    }

    private List<Map<String, Object>> listResourcesInternal(String doctype, Map<String, Object> params, String sessionCookie) {
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
        Map<String, Object> body = null;
        Map<String, Object> currentParams = safeParams;
        while (true) {
            try {
                body = sessionCookie == null || sessionCookie.isBlank()
                        ? feignClient.listResources(doctype, currentParams)
                        : feignClient.listResourcesWithCookie(doctype, currentParams, sessionCookie);
                break;
            } catch (FeignException ex) {
                Map<String, Object> fallbackParams = removeUnsupportedOptionalField(currentParams, ex);
                if (fallbackParams == null || fallbackParams.equals(currentParams)) {
                    throw ex;
                }
                currentParams = fallbackParams;
            }
        }
        if (body == null || !body.containsKey("data")) {
            return Collections.emptyList();
        }
        Object data = body.get("data");
        if (data instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> casted = (List<Map<String, Object>>) data;
            return casted;
        }
        return Collections.emptyList();
    }

    private Map<String, Object> removeUnsupportedOptionalField(Map<String, Object> params, FeignException ex) {
        String message = ex.contentUTF8();
        if (message == null || message.isBlank()) {
            message = ex.getMessage();
        }
        if (message == null || message.isBlank()) {
            return null;
        }
        for (String field : OPTIONAL_QUERY_FIELDS) {
            if (!message.contains("Field not permitted in query: " + field)) {
                continue;
            }
            Map<String, Object> updated = new HashMap<>(params);
            Object fields = updated.get("fields");
            if (fields instanceof String fieldList) {
                updated.put("fields", stripJsonStringToken(fieldList, field));
            }
            Object filters = updated.get("filters");
            if (filters instanceof String filterList) {
                updated.put("filters", stripFilterToken(filterList, field));
            }
            Object orFilters = updated.get("or_filters");
            if (orFilters instanceof String filterList) {
                updated.put("or_filters", stripFilterToken(filterList, field));
            }
            return updated;
        }
        return null;
    }

    private String stripJsonStringToken(String json, String token) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String updated = json
                .replace("\"" + token + "\",", "")
                .replace(",\"" + token + "\"", "")
                .replace("\"" + token + "\"", "");
        updated = updated.replace("[,", "[").replace(",]", "]");
        return updated;
    }

    private String stripFilterToken(String json, String token) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String escaped = Pattern.quote(token);
        String updated = json.replaceAll(",?\\[\\[[^\\]]*\"" + escaped + "\"[^\\]]*\\]\\]", "");
        updated = updated.replace("[,", "[").replace(",]", "]");
        return updated;
    }

    public Map<String, Object> createResource(String doctype, Map<String, Object> payload) {
        return feignClient.createResource(doctype, payload);
    }

    public Map<String, Object> updateResource(String doctype, String id, Map<String, Object> payload) {
        return feignClient.updateResource(doctype, id, payload);
    }

    public Map<String, Object> submitDoc(Map<String, Object> doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("doc", doc);
        return feignClient.submit(payload);
    }

    public Map<String, Object> cancelResource(String doctype, String id) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("doctype", doctype);
        payload.put("name", id);
        return feignClient.cancel(payload);
    }

    public Map<String, Object> deleteResource(String doctype, String id) {
        return feignClient.deleteResource(doctype, id);
    }

    public Map<String, Object> postMethod(String method, Map<String, Object> payload) {
        return feignClient.postMethod(method, payload == null ? Collections.emptyMap() : payload);
    }

    public Map<String, Object> getMethod(String method, Map<String, Object> params) {
        return feignClient.getMethod(method, params == null ? Collections.emptyMap() : params);
    }

    public Map<String, Object> postCommand(String command, Map<String, Object> payload) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("cmd", command);
        if (payload != null) {
            payload.forEach((key, value) -> form.add(key, value == null ? "" : value.toString()));
        }
        return feignClient.postCommand(form);
    }

    public byte[] downloadPdf(String doctype, String name) {
        return feignClient.downloadPdf(doctype, name);
    }

    public byte[] downloadPdf(String doctype, String name, Map<String, Object> params) {
        return downloadPdfWithSession(doctype, name, params, null);
    }

    public byte[] downloadPdfWithSession(String doctype, String name, Map<String, Object> params, String sessionCookie) {
        Map<String, Object> query = new HashMap<>();
        query.put("doctype", doctype);
        query.put("name", name);
        if (params != null && !params.isEmpty()) {
            query.putAll(params);
        }
        return sessionCookie == null || sessionCookie.isBlank()
                ? feignClient.downloadPdfWithParams(query)
                : feignClient.downloadPdfWithParamsAndCookie(query, sessionCookie);
    }

    public long getCount(String doctype, Map<String, Object> params) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("doctype", doctype);
        if (params != null && !params.isEmpty()) {
            if (params.containsKey("filters")) {
                payload.put("filters", params.get("filters"));
            }
            if (params.containsKey("or_filters")) {
                payload.put("or_filters", params.get("or_filters"));
            }
        }
        Map<String, Object> response = feignClient.getCount(payload);
        if (response == null) {
            return 0;
        }
        Object message = response.get("message");
        if (message instanceof Number number) {
            return number.longValue();
        }
        if (message != null) {
            try {
                return Long.parseLong(message.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String extractSessionCookie(HttpHeaders headers) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        for (String cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            String trimmed = cookie.trim();
            int delimiter = trimmed.indexOf(';');
            String token = delimiter == -1 ? trimmed : trimmed.substring(0, delimiter);
            if (token.startsWith("sid=")) {
                return token;
            }
        }
        return null;
    }
}
