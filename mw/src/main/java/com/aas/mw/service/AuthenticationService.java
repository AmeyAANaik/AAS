package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.AuthRequest;
import com.aas.mw.dto.AuthResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AuthenticationService {

    private final ErpNextClient erpNextClient;
    private final JwtService jwtService;
    private final ErpSessionStore erpSessionStore;
    private final RoleResolver roleResolver;

    public AuthenticationService(
            ErpNextClient erpNextClient,
            JwtService jwtService,
            ErpSessionStore erpSessionStore,
            RoleResolver roleResolver) {
        this.erpNextClient = erpNextClient;
        this.jwtService = jwtService;
        this.erpSessionStore = erpSessionStore;
        this.roleResolver = roleResolver;
    }

    public AuthResponse login(AuthRequest request) {
        String sessionCookie = erpNextClient.login(request.getUsername(), request.getPassword());
        if (sessionCookie == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ERP session missing");
        }
        erpSessionStore.put(request.getUsername(), sessionCookie);
        AppRole role = roleResolver.resolve(erpNextClient.getUserRoles(sessionCookie, request.getUsername()));
        String token = jwtService.generateToken(request.getUsername(), role);
        return new AuthResponse(token, "Bearer", role.asKey());
    }
}
