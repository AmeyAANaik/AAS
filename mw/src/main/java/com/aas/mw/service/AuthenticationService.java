package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AuthRequest;
import com.aas.mw.dto.AuthResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final ErpNextClient erpNextClient;
    private final JwtService jwtService;

    public AuthenticationService(ErpNextClient erpNextClient, JwtService jwtService) {
        this.erpNextClient = erpNextClient;
        this.jwtService = jwtService;
    }

    public AuthResponse login(AuthRequest request) {
        erpNextClient.login(request.getUsername(), request.getPassword());
        String token = jwtService.generateToken(request.getUsername());
        return new AuthResponse(token, "Bearer");
    }
}
