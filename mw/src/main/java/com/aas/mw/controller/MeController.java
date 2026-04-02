package com.aas.mw.controller;

import com.aas.mw.service.UserService;
import com.aas.mw.service.UserFeatureService;
import com.aas.mw.dto.FieldsRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserService userService;
    private final UserFeatureService userFeatureService;

    public MeController(UserService userService, UserFeatureService userFeatureService) {
        this.userService = userService;
        this.userFeatureService = userFeatureService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? null : auth.getName();
        String role = auth == null ? null : auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()).toLowerCase())
                .findFirst()
                .orElse("admin");
        Map<String, Object> profile = userService.getUserProfile(username);
        profile.put("role", role);
        profile.put("default_features", userFeatureService.featuresForRole(role));
        profile.put("features", userFeatureService.resolveFeatures(
                role,
                userFeatureService.parseFeatureOverrides(profile.get("allow_features")),
                userFeatureService.parseFeatureOverrides(profile.get("deny_features"))));
        profile.put("homeRoute", userFeatureService.homeRouteForRole(role));
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMe(@RequestBody FieldsRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? null : auth.getName();
        Map<String, Object> profile = userService.updateUserProfile(username, request);
        String role = auth == null ? null : auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()).toLowerCase())
                .findFirst()
                .orElse("admin");
        profile.put("role", role);
        profile.put("default_features", userFeatureService.featuresForRole(role));
        profile.put("features", userFeatureService.resolveFeatures(
                role,
                userFeatureService.parseFeatureOverrides(profile.get("allow_features")),
                userFeatureService.parseFeatureOverrides(profile.get("deny_features"))));
        profile.put("homeRoute", userFeatureService.homeRouteForRole(role));
        return ResponseEntity.ok(profile);
    }
}
