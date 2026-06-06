package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.AuthRequest;
import com.aas.mw.dto.AuthResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthenticationServiceTest {

    private ErpNextClient erpNextClient;
    private JwtService jwtService;
    private ErpSessionStore erpSessionStore;
    private AuthenticationService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        jwtService = mock(JwtService.class);
        erpSessionStore = mock(ErpSessionStore.class);
        service = new AuthenticationService(
                erpNextClient,
                jwtService,
                erpSessionStore,
                new RoleResolver("Administrator", "Supplier", "Customer", "Stock User"),
                "Administrator",
                "admin");
    }

    @Test
    void fallsBackToPrivilegedRoleLookupWhenSelfLookupIsEmpty() {
        AuthRequest request = new AuthRequest();
        request.setUsername("helper@example.com");
        request.setPassword("helper123");

        when(erpNextClient.login("helper@example.com", "helper123")).thenReturn("sid=user");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(Collections.emptyList());
        when(erpNextClient.login("Administrator", "admin")).thenReturn("sid=admin");
        when(erpNextClient.getUserRoles("sid=admin", "helper@example.com")).thenReturn(List.of("Stock User"));
        when(jwtService.generateToken("helper@example.com", AppRole.HELPER)).thenReturn("jwt");

        AuthResponse response = service.login(request);

        assertEquals("jwt", response.getAccessToken());
        assertEquals("helper", response.getRole());
    }

    @Test
    void resolvesUsernameAliasBeforeErpLogin() {
        AuthRequest request = new AuthRequest();
        request.setUsername("Tapan");
        request.setPassword("tapan@123");

        when(erpNextClient.login("Administrator", "admin")).thenReturn("sid=admin");
        when(erpNextClient.resolveUserId("sid=admin", "Tapan")).thenReturn("helper@example.com");
        when(erpNextClient.login("helper@example.com", "tapan@123")).thenReturn("sid=user");
        when(erpNextClient.resolveUserId("sid=user", "helper@example.com")).thenReturn("helper@example.com");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(List.of("Stock User"));
        when(jwtService.generateToken("helper@example.com", AppRole.HELPER)).thenReturn("jwt");

        AuthResponse response = service.login(request);

        assertEquals("jwt", response.getAccessToken());
        assertEquals("helper", response.getRole());
    }

    @Test
    void rejectsLoginWhenNoSupportedRoleExistsEvenAfterFallback() {
        AuthRequest request = new AuthRequest();
        request.setUsername("helper@example.com");
        request.setPassword("helper123");

        when(erpNextClient.login("helper@example.com", "helper123")).thenReturn("sid=user");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(Collections.emptyList());
        when(erpNextClient.login("Administrator", "admin")).thenReturn("sid=admin");
        when(erpNextClient.getUserRoles("sid=admin", "helper@example.com")).thenReturn(Collections.emptyList());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.login(request));

        assertEquals(403, exception.getStatusCode().value());
    }
}
