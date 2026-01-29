package com.aas.mw.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class ErpNextClient {

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
        Map<String, Object> user = getResourceWithSession("User", username, sessionCookie);
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

    public Map<String, Object> getResource(String doctype, String id) {
        return feignClient.getResource(doctype, id);
    }

    public List<Map<String, Object>> listResources(String doctype, Map<String, Object> params) {
        Map<String, Object> body = feignClient.listResources(doctype, params == null ? Collections.emptyMap() : params);
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

    public Map<String, Object> createResource(String doctype, Map<String, Object> payload) {
        return feignClient.createResource(doctype, payload);
    }

    public Map<String, Object> updateResource(String doctype, String id, Map<String, Object> payload) {
        return feignClient.updateResource(doctype, id, payload);
    }

    public byte[] downloadPdf(String doctype, String name) {
        return feignClient.downloadPdf(doctype, name);
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
