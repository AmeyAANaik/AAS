package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.FieldsRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final ErpNextClient erpNextClient;
    private final UserFeatureService userFeatureService;
    private final RoleResolver roleResolver;

    public UserService(
            ErpNextClient erpNextClient,
            UserFeatureService userFeatureService,
            RoleResolver roleResolver) {
        this.erpNextClient = erpNextClient;
        this.userFeatureService = userFeatureService;
        this.roleResolver = roleResolver;
    }

    public Map<String, Object> getUserProfile(String username) {
        return getUserProfileById(username);
    }

    public Map<String, Object> getUserProfileById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Map.of("name", "", "full_name", "", "email", "");
        }
        Map<String, Object> user = unwrapResource(erpNextClient.getResource("User", userId));
        if (user == null || user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getOrDefault("name", userId));
        profile.put("name", user.getOrDefault("name", userId));
        profile.put("full_name", user.getOrDefault("full_name", userId));
        profile.put("email", user.getOrDefault("email", ""));
        profile.put("mobile_no", user.getOrDefault("mobile_no", ""));
        profile.put("location", user.getOrDefault("location", ""));
        profile.put("customer", user.getOrDefault("customer", ""));
        profile.put("supplier", user.getOrDefault("supplier", ""));
        profile.put("company", user.getOrDefault("default_company", ""));
        profile.put("enabled", user.getOrDefault("enabled", 1));
        profile.put("user_image", user.getOrDefault("user_image", ""));
        List<String> erpRoles = parseRoles(user.get("roles"));
        String role = resolveAppRole(erpRoles, userId);
        List<String> allowFeatures = userFeatureService.parseFeatureOverrides(user.get(UserFeatureService.FEATURE_ALLOW_FIELD));
        List<String> denyFeatures = userFeatureService.parseFeatureOverrides(user.get(UserFeatureService.FEATURE_DENY_FIELD));
        profile.put("role", role);
        profile.put("erp_roles", erpRoles);
        profile.put("allow_features", allowFeatures);
        profile.put("deny_features", denyFeatures);
        profile.put("default_features", userFeatureService.featuresForRole(role));
        profile.put("features", userFeatureService.resolveFeatures(role, allowFeatures, denyFeatures));
        return profile;
    }

    public List<Map<String, Object>> listUserAccessProfiles() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put("limit_page_length", 200);
        params.put("order_by", "full_name asc");
        List<Map<String, Object>> rows = erpNextClient.listResources("User", params);
        List<Map<String, Object>> profiles = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String name = String.valueOf(row.getOrDefault("name", "")).trim();
            if (name.isBlank() || "Guest".equalsIgnoreCase(name)) {
                continue;
            }
            profiles.add(getUserProfileById(name));
        }
        return profiles;
    }

    public Map<String, Object> updateUserAccess(String userId, List<String> allowFeatures, List<String> denyFeatures) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put(UserFeatureService.FEATURE_ALLOW_FIELD, userFeatureService.encodeFeatureOverrides(allowFeatures));
        payload.put(UserFeatureService.FEATURE_DENY_FIELD, userFeatureService.encodeFeatureOverrides(denyFeatures));
        erpNextClient.updateResource("User", userId, payload);
        return getUserProfileById(userId);
    }

    public Map<String, Object> updateUserProfile(String userId, FieldsRequest request) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required.");
        }
        Map<String, Object> fields = request == null || request.getFields() == null ? Map.of() : request.getFields();
        Map<String, Object> payload = new HashMap<>();
        copyIfPresent(fields, payload, "full_name");
        copyIfPresent(fields, payload, "mobile_no");
        copyIfPresent(fields, payload, "location");
        copyIfPresent(fields, payload, "default_company");
        if (fields.containsKey("company")) {
            payload.put("default_company", fields.get("company"));
        }
        copyIfPresent(fields, payload, "customer");
        copyIfPresent(fields, payload, "supplier");
        if (!payload.isEmpty()) {
            erpNextClient.updateResource("User", userId, payload);
        }
        return getUserProfileById(userId);
    }

    public boolean hasFeature(String username, String featureKey) {
        if (featureKey == null || featureKey.isBlank()) {
            return true;
        }
        Object value = getUserProfile(username).get("features");
        if (!(value instanceof java.util.Collection<?> features)) {
            return false;
        }
        return features.stream().map(String::valueOf).anyMatch(featureKey::equals);
    }

    public void requireFeature(String username, String featureKey) {
        if (!hasFeature(username, featureKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
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

    private List<String> parseRoles(Object rawRoles) {
        if (!(rawRoles instanceof List<?> roles)) {
            return List.of();
        }
        List<String> parsed = new ArrayList<>();
        for (Object value : roles) {
            if (value instanceof Map<?, ?> roleMap) {
                Object role = roleMap.get("role");
                if (role != null) {
                    parsed.add(String.valueOf(role));
                }
            }
        }
        return List.copyOf(parsed);
    }

    private String resolveAppRole(List<String> erpRoles, String userId) {
        try {
            return roleResolver.resolve(erpRoles).asKey();
        } catch (IllegalStateException ignored) {
            return "Administrator".equalsIgnoreCase(userId) ? "admin" : "";
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
