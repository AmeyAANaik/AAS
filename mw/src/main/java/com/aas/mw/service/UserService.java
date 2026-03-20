package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class UserService {

    private final ErpNextClient erpNextClient;

    public UserService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> getUserProfile(String username) {
        return getUserProfileById(username);
    }

    public Map<String, Object> getUserProfileById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Map.of("name", "", "full_name", "", "email", "");
        }
        Map<String, Object> user = erpNextClient.getResource("User", userId);
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
        return profile;
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

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
