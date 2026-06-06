package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.ErpSetupProperties;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class UserAliasService {

    private final ErpNextClient erpNextClient;
    private final ErpSessionStore erpSessionStore;
    private final String erpSetupUsername;
    private final String erpSetupPassword;
    private final Map<String, String> aliasToUserId = new ConcurrentHashMap<>();

    public UserAliasService(
            ErpNextClient erpNextClient,
            ErpSessionStore erpSessionStore,
            ErpSetupProperties erpSetupProperties) {
        this.erpNextClient = erpNextClient;
        this.erpSessionStore = erpSessionStore;
        this.erpSetupUsername = erpSetupProperties.getFullName();
        this.erpSetupPassword = erpSetupProperties.getPassword();
    }

    @PostConstruct
    void warmCache() {
        refreshFromPrivilegedSession();
    }

    public String resolveLoginId(String loginId) {
        if (loginId == null) {
            return null;
        }
        String trimmed = loginId.trim();
        if (trimmed.isBlank() || trimmed.contains("@")) {
            return trimmed;
        }
        String mapped = aliasToUserId.get(normalize(trimmed));
        return mapped == null || mapped.isBlank() ? trimmed : mapped;
    }

    public void recordUserAliases(String sessionCookie, String canonicalUserId) {
        if (sessionCookie == null || sessionCookie.isBlank() || canonicalUserId == null || canonicalUserId.isBlank()) {
            return;
        }
        try {
            Map<String, Object> user = unwrapResource(erpNextClient.getResourceWithSession("User", canonicalUserId, sessionCookie));
            cacheUser(user, canonicalUserId);
        } catch (Exception ignored) {
            // Best-effort only; email login must continue to work even if alias caching fails.
        }
    }

    public void refreshFromPrivilegedSession() {
        String sessionCookie = getPrivilegedSessionCookie();
        if (sessionCookie == null || sessionCookie.isBlank()) {
            return;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"email\",\"username\",\"full_name\",\"enabled\"]");
            params.put("filters", "[[\"User\",\"enabled\",\"=\",1]]");
            params.put("limit_page_length", 1000);
            List<Map<String, Object>> rows = erpNextClient.listResourcesWithSession("User", params, sessionCookie);
            for (Map<String, Object> row : rows) {
                cacheUser(row, asText(row.get("name")));
            }
        } catch (Exception ignored) {
            // Alias warm-up is helpful but not required for the app to start.
        }
    }

    private void cacheUser(Map<String, Object> user, String fallbackUserId) {
        String userId = firstNonBlank(
                asText(user.get("name")),
                asText(user.get("email")),
                fallbackUserId);
        if (userId.isBlank()) {
            return;
        }
        putAlias(userId, userId);
        putAlias(asText(user.get("email")), userId);
        putAlias(asText(user.get("username")), userId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private String getPrivilegedSessionCookie() {
        if (erpSetupUsername == null || erpSetupUsername.isBlank() || erpSetupPassword == null || erpSetupPassword.isBlank()) {
            return null;
        }
        return erpSessionStore.get(erpSetupUsername)
                .orElseGet(() -> {
                    String sessionCookie = erpNextClient.login(erpSetupUsername, erpSetupPassword);
                    if (sessionCookie != null && !sessionCookie.isBlank()) {
                        erpSessionStore.put(erpSetupUsername, sessionCookie);
                    }
                    return sessionCookie;
                });
    }

    private void putAlias(String alias, String userId) {
        String normalizedAlias = normalize(alias);
        if (normalizedAlias.isBlank() || userId == null || userId.isBlank()) {
            return;
        }
        aliasToUserId.put(normalizedAlias, userId.trim());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
