package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.ErpSetupProperties;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.AuthRequest;
import com.aas.mw.dto.AuthResponse;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthenticationService {

    private final ErpNextClient erpNextClient;
    private final JwtService jwtService;
    private final ErpSessionStore erpSessionStore;
    private final RoleResolver roleResolver;
    private final UserAliasService userAliasService;
    private final String erpSetupUsername;
    private final String erpSetupPassword;

    public AuthenticationService(
            ErpNextClient erpNextClient,
            JwtService jwtService,
            ErpSessionStore erpSessionStore,
            RoleResolver roleResolver,
            UserAliasService userAliasService,
            ErpSetupProperties erpSetupProperties) {
        this.erpNextClient = erpNextClient;
        this.jwtService = jwtService;
        this.erpSessionStore = erpSessionStore;
        this.roleResolver = roleResolver;
        this.userAliasService = userAliasService;
        this.erpSetupUsername = erpSetupProperties.getFullName();
        this.erpSetupPassword = erpSetupProperties.getPassword();
    }

    public AuthResponse login(AuthRequest request) {
        String loginUserId = userAliasService.resolveLoginId(request.getUsername());
        String sessionCookie = erpNextClient.login(loginUserId, request.getPassword());
        if (sessionCookie == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ERP session missing");
        }
        String canonicalUserId = erpNextClient.getLoggedInUser(sessionCookie);
        if (canonicalUserId == null || canonicalUserId.isBlank()) {
            canonicalUserId = erpNextClient.resolveUserId(sessionCookie, loginUserId);
        }
        if (canonicalUserId == null || canonicalUserId.isBlank()) {
            canonicalUserId = loginUserId;
        }
        erpSessionStore.put(canonicalUserId, sessionCookie);
        userAliasService.recordUserAliases(sessionCookie, canonicalUserId);
        AppRole role = resolveAppRole(sessionCookie, canonicalUserId);
        String token = jwtService.generateToken(canonicalUserId, role);
        return new AuthResponse(token, "Bearer", role.asKey());
    }

    public String getSetupSessionCookie() {
        if (erpSetupUsername == null || erpSetupUsername.isBlank() || erpSetupPassword == null || erpSetupPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "ERP setup credentials are not configured.");
        }
        return erpSessionStore.get(erpSetupUsername)
                .orElseGet(() -> {
                    String sessionCookie = erpNextClient.login(erpSetupUsername, erpSetupPassword);
                    if (sessionCookie == null || sessionCookie.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to establish ERP session.");
                    }
                    erpSessionStore.put(erpSetupUsername, sessionCookie);
                    return sessionCookie;
                });
    }

    private AppRole resolveAppRole(String sessionCookie, String canonicalUserId) {
        try {
            return roleResolver.resolve(erpNextClient.getUserRoles(sessionCookie, canonicalUserId));
        } catch (IllegalStateException ignored) {
            // Fall through to self-profile inference below.
        }

        Map<String, Object> user = unwrapResource(erpNextClient.getResourceWithSession("User", canonicalUserId, sessionCookie));
        AppRole explicitRole = resolveExplicitAppRole(user.get("aas_app_role"));
        if (explicitRole != null) {
            return explicitRole;
        }
        if (hasText(user.get("supplier"))) {
            return AppRole.VENDOR;
        }
        if (hasText(user.get("customer"))) {
            return AppRole.SHOP;
        }
        if (hasText(user.get(UserFeatureService.FEATURE_ALLOW_FIELD)) || hasText(user.get(UserFeatureService.FEATURE_DENY_FIELD))) {
            return AppRole.HELPER;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No supported ERP role assigned.");
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

    private AppRole resolveExplicitAppRole(Object rawRole) {
        if (!hasText(rawRole)) {
            return null;
        }
        String normalized = rawRole.toString().trim().toUpperCase(Locale.ROOT);
        try {
            return AppRole.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().trim().isBlank();
    }

}
