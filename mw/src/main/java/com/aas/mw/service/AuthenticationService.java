package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.AuthRequest;
import com.aas.mw.dto.AuthResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthenticationService {

    private final ErpNextClient erpNextClient;
    private final JwtService jwtService;
    private final ErpSessionStore erpSessionStore;
    private final RoleResolver roleResolver;
    private final String erpSetupUsername;
    private final String erpSetupPassword;

    public AuthenticationService(
            ErpNextClient erpNextClient,
            JwtService jwtService,
            ErpSessionStore erpSessionStore,
            RoleResolver roleResolver,
            @Value("${erp.setup.full-name:Administrator}") String erpSetupUsername,
            @Value("${erp.setup.password:admin}") String erpSetupPassword) {
        this.erpNextClient = erpNextClient;
        this.jwtService = jwtService;
        this.erpSessionStore = erpSessionStore;
        this.roleResolver = roleResolver;
        this.erpSetupUsername = erpSetupUsername;
        this.erpSetupPassword = erpSetupPassword;
    }

    public AuthResponse login(AuthRequest request) {
        String sessionCookie = erpNextClient.login(request.getUsername(), request.getPassword());
        if (sessionCookie == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ERP session missing");
        }
        erpSessionStore.put(request.getUsername(), sessionCookie);
        AppRole role;
        try {
            role = roleResolver.resolve(resolveUserRoles(sessionCookie, request.getUsername()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        }
        String token = jwtService.generateToken(request.getUsername(), role);
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

    private List<String> resolveUserRoles(String sessionCookie, String username) {
        List<String> roles = erpNextClient.getUserRoles(sessionCookie, username);
        if (roles != null && !roles.isEmpty()) {
            return roles;
        }
        if (erpSetupUsername == null || erpSetupUsername.isBlank() || erpSetupPassword == null || erpSetupPassword.isBlank()) {
            return roles;
        }
        String adminSessionCookie = erpNextClient.login(erpSetupUsername, erpSetupPassword);
        if (adminSessionCookie == null) {
            return roles;
        }
        return erpNextClient.getUserRoles(adminSessionCookie, username);
    }
}
