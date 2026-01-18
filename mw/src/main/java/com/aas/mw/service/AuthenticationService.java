package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
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

    public AuthenticationService(ErpNextClient erpNextClient, JwtService jwtService, ErpSessionStore erpSessionStore) {
        this.erpNextClient = erpNextClient;
        this.jwtService = jwtService;
        this.erpSessionStore = erpSessionStore;
    }

    public AuthResponse login(AuthRequest request) {
        String sessionCookie = erpNextClient.login(request.getUsername(), request.getPassword());
        if (sessionCookie == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "ERP session missing");
        }
        erpSessionStore.put(request.getUsername(), sessionCookie);
        String token = jwtService.generateToken(request.getUsername());
        return new AuthResponse(token, "Bearer");
    }
}
