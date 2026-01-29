package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final ErpNextClient erpNextClient;

    public UserService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> getUserProfile(String username) {
        if (username == null || username.isBlank()) {
            return Map.of("name", "", "full_name", "", "email", "");
        }
        Map<String, Object> user = erpNextClient.getResource("User", username);
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", user.getOrDefault("name", username));
        profile.put("full_name", user.getOrDefault("full_name", username));
        profile.put("email", user.getOrDefault("email", ""));
        profile.put("customer", user.getOrDefault("customer", ""));
        profile.put("supplier", user.getOrDefault("supplier", ""));
        return profile;
    }
}
