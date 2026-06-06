package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.ErpSetupProperties;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.AuthRequest;
import com.aas.mw.dto.AuthResponse;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthenticationServiceTest {

    private ErpNextClient erpNextClient;
    private JwtService jwtService;
    private ErpSessionStore erpSessionStore;
    private UserAliasService userAliasService;
    private AuthenticationService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        jwtService = mock(JwtService.class);
        erpSessionStore = mock(ErpSessionStore.class);
        userAliasService = mock(UserAliasService.class);
        ErpSetupProperties erpSetupProperties = new ErpSetupProperties();
        erpSetupProperties.setFullName("Administrator");
        erpSetupProperties.setPassword("admin");
        service = new AuthenticationService(
                erpNextClient,
                jwtService,
                erpSessionStore,
                new RoleResolver("Administrator", "Supplier", "Customer", "Stock User"),
                userAliasService,
                erpSetupProperties);
    }

    @Test
    void logsInUsingAuthenticatedUserSession() {
        AuthRequest request = new AuthRequest();
        request.setUsername("helper@example.com");
        request.setPassword("helper123");

        when(userAliasService.resolveLoginId("helper@example.com")).thenReturn("helper@example.com");
        when(erpNextClient.login("helper@example.com", "helper123")).thenReturn("sid=user");
        when(erpNextClient.getLoggedInUser("sid=user")).thenReturn("helper@example.com");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(List.of("Stock User"));
        when(jwtService.generateToken("helper@example.com", AppRole.HELPER)).thenReturn("jwt");

        AuthResponse response = service.login(request);

        assertEquals("jwt", response.getAccessToken());
        assertEquals("helper", response.getRole());
    }

    @Test
    void rejectsAliasWhenErpDoesNotAuthenticateThatIdentifier() {
        AuthRequest request = new AuthRequest();
        request.setUsername("Tapan");
        request.setPassword("tapan@123");

        when(userAliasService.resolveLoginId("Tapan")).thenReturn("Tapan");
        when(erpNextClient.login("Tapan", "tapan@123")).thenReturn(null);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.login(request));

        assertEquals(401, exception.getStatusCode().value());
    }

    @Test
    void rejectsLoginWhenNoSupportedRoleExists() {
        AuthRequest request = new AuthRequest();
        request.setUsername("helper@example.com");
        request.setPassword("helper123");

        when(userAliasService.resolveLoginId("helper@example.com")).thenReturn("helper@example.com");
        when(erpNextClient.login("helper@example.com", "helper123")).thenReturn("sid=user");
        when(erpNextClient.getLoggedInUser("sid=user")).thenReturn("helper@example.com");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.login(request));

        assertEquals(403, exception.getStatusCode().value());
    }

    @Test
    void infersHelperRoleFromUserProfileWhenErpHidesRoleRows() {
        AuthRequest request = new AuthRequest();
        request.setUsername("helper@example.com");
        request.setPassword("helper123");

        when(userAliasService.resolveLoginId("helper@example.com")).thenReturn("helper@example.com");
        when(erpNextClient.login("helper@example.com", "helper123")).thenReturn("sid=user");
        when(erpNextClient.getLoggedInUser("sid=user")).thenReturn("helper@example.com");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(List.of());
        when(erpNextClient.getResourceWithSession("User", "helper@example.com", "sid=user"))
                .thenReturn(Map.of("data", Map.of(
                        UserFeatureService.FEATURE_ALLOW_FIELD, "[\"orders.view\"]",
                        UserFeatureService.FEATURE_DENY_FIELD, "[\"company_settings.view\"]")));
        when(jwtService.generateToken("helper@example.com", AppRole.HELPER)).thenReturn("jwt");

        AuthResponse response = service.login(request);

        assertEquals("jwt", response.getAccessToken());
        assertEquals("helper", response.getRole());
    }

    @Test
    void logsInUsingGenericAliasMappedToErpUser() {
        AuthRequest request = new AuthRequest();
        request.setUsername("Tapan");
        request.setPassword("tapan@123");

        when(userAliasService.resolveLoginId("Tapan")).thenReturn("helper@example.com");
        when(erpNextClient.login("helper@example.com", "tapan@123")).thenReturn("sid=user");
        when(erpNextClient.getLoggedInUser("sid=user")).thenReturn("helper@example.com");
        when(erpNextClient.getUserRoles("sid=user", "helper@example.com")).thenReturn(List.of("Stock User"));
        when(jwtService.generateToken("helper@example.com", AppRole.HELPER)).thenReturn("jwt");

        AuthResponse response = service.login(request);

        assertEquals("jwt", response.getAccessToken());
        assertEquals("helper", response.getRole());
    }
}
