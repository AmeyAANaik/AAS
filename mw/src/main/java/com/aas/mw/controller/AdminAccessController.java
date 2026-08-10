package com.aas.mw.controller;

import com.aas.mw.config.AppRole;
import com.aas.mw.dto.UserAccessUpdateRequest;
import com.aas.mw.dto.UserCreateRequest;
import com.aas.mw.dto.UserEnabledRequest;
import com.aas.mw.service.UserFeatureService;
import com.aas.mw.service.UserService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/access")
public class AdminAccessController {

    private final UserService userService;
    private final UserFeatureService userFeatureService;

    public AdminAccessController(UserService userService, UserFeatureService userFeatureService) {
        this.userService = userService;
        this.userFeatureService = userFeatureService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> accessOverview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("features", userFeatureService.featureCatalog());
        body.put("users", userService.listUserAccessProfiles());
        body.put("defaultsByRole", defaultsByRole());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<Map<String, Object>> setUserEnabled(
            @PathVariable String id,
            @RequestBody UserEnabledRequest request,
            Authentication authentication) {
        boolean enabled = request != null && request.isEnabled();
        String actingUserId = authentication == null ? "" : authentication.getName();
        return ResponseEntity.ok(userService.setUserEnabled(id, enabled, actingUserId));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUserAccess(
            @PathVariable String id,
            @RequestBody UserAccessUpdateRequest request) {
        List<String> allowFeatures = request == null ? List.of() : request.getAllowFeatures();
        List<String> denyFeatures = request == null ? List.of() : request.getDenyFeatures();
        return ResponseEntity.ok(userService.updateUserAccess(id, allowFeatures, denyFeatures));
    }

    /** Per-role baseline features, so the create form can preview defaults before a user exists. */
    private Map<String, Object> defaultsByRole() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        for (AppRole role : AppRole.values()) {
            defaults.put(role.asKey(), userFeatureService.featuresForRole(role.asKey()));
        }
        return defaults;
    }
}
