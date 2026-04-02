package com.aas.mw.controller;

import com.aas.mw.dto.UserAccessUpdateRequest;
import com.aas.mw.service.UserFeatureService;
import com.aas.mw.service.UserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        return ResponseEntity.ok(body);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUserAccess(
            @PathVariable String id,
            @RequestBody UserAccessUpdateRequest request) {
        List<String> allowFeatures = request == null ? List.of() : request.getAllowFeatures();
        List<String> denyFeatures = request == null ? List.of() : request.getDenyFeatures();
        return ResponseEntity.ok(userService.updateUserAccess(id, allowFeatures, denyFeatures));
    }
}
