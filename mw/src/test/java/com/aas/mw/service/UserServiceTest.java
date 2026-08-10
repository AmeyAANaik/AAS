package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.AppRole;
import com.aas.mw.config.RoleResolver;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.dto.UserCreateRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final ErpNextClient erpNextClient = mock(ErpNextClient.class);
    private final UserAliasService userAliasService = mock(UserAliasService.class);
    private final UserFeatureService userFeatureService = new UserFeatureService();
    private final RoleResolver roleResolver =
            new RoleResolver("Administrator", "Supplier", "Customer", "Stock User", "Accounts User,Sales User");
    private final UserService userService =
            new UserService(erpNextClient, userFeatureService, roleResolver, userAliasService);

    private UserCreateRequest request(String email, String role) {
        UserCreateRequest request = new UserCreateRequest();
        request.setEmail(email);
        request.setFullName("Asha Patel");
        request.setPassword("secret123");
        request.setRole(role);
        return request;
    }

    /** The doc read back after create; also what {@code resourceExists} sees on the second call. */
    private Map<String, Object> existingUserDoc(String email) {
        return Map.of("data", Map.of(
                "name", email,
                "full_name", "Asha Patel",
                "email", email,
                "enabled", 1,
                "roles", List.of(Map.of("role", "Stock User"))));
    }

    @Test
    void createsUserWithMappedErpRolesAndEncodedFeatureOverrides() {
        UserCreateRequest request = request("asha@example.com", "helper");
        request.setAllowFeatures(List.of(UserFeatureService.BILL_REVIEW_VIEW, "not.a.real.feature"));
        request.setDenyFeatures(List.of(UserFeatureService.ORDERS_DELETE));
        // First call is the duplicate check (absent), second is the profile read after create.
        when(erpNextClient.getResource("User", "asha@example.com"))
                .thenReturn(Map.of())
                .thenReturn(existingUserDoc("asha@example.com"));
        when(erpNextClient.listResources(eq("User"), any())).thenReturn(List.of());

        Map<String, Object> profile = userService.createUser(request);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("User"), payload.capture());
        Map<String, Object> created = payload.getValue();

        assertEquals("asha@example.com", created.get("email"));
        assertEquals("Asha", created.get("first_name"));
        assertEquals("Patel", created.get("last_name"));
        assertEquals("Asha", created.get("username"));
        assertEquals(1, created.get("enabled"));
        assertEquals(0, created.get("send_welcome_email"));
        assertEquals("secret123", created.get("new_password"));
        assertEquals("System User", created.get("user_type"));
        assertEquals(
                List.of(Map.of("role", "Stock User"), Map.of("role", "Accounts User"), Map.of("role", "Sales User")),
                created.get("roles"));
        // The unknown key is dropped by encodeFeatureOverrides.
        assertEquals("[\"bill_review.view\"]", created.get(UserFeatureService.FEATURE_ALLOW_FIELD));
        assertEquals("[\"orders.delete\"]", created.get(UserFeatureService.FEATURE_DENY_FIELD));
        assertEquals("asha@example.com", profile.get("id"));
        assertEquals("helper", created.get(AppRole.FIELD));
        verify(userAliasService).refreshFromPrivilegedSession();
    }

    /**
     * Login reads the User doc with the caller's own ERP session, and a Website User cannot
     * see their own roles child table. Without this field a vendor resolved to HELPER and
     * received 13 features instead of 5.
     */
    @Test
    void stampsAppRoleSoLoginCanResolveWithoutTheRolesChildTable() {
        UserCreateRequest request = request("ravi@example.com", "vendor");
        request.setSupplier("FreshHarvest Agro Foods");
        when(erpNextClient.getResource("User", "ravi@example.com"))
                .thenReturn(Map.of())
                .thenReturn(existingUserDoc("ravi@example.com"));
        when(erpNextClient.listResources(eq("User"), any())).thenReturn(List.of());

        userService.createUser(request);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("User"), payload.capture());
        assertEquals("vendor", payload.getValue().get(AppRole.FIELD));
        assertEquals(List.of(Map.of("role", "Supplier")), payload.getValue().get("roles"));
    }

    /** An empty override list must not be written as "[]", which reads as a real override. */
    @Test
    void encodesEmptyFeatureOverridesAsBlankNotAnEmptyJsonArray() {
        UserCreateRequest request = request("blank@example.com", "helper");
        request.setAllowFeatures(List.of());
        request.setDenyFeatures(null);
        when(erpNextClient.getResource("User", "blank@example.com"))
                .thenReturn(Map.of())
                .thenReturn(existingUserDoc("blank@example.com"));
        when(erpNextClient.listResources(eq("User"), any())).thenReturn(List.of());

        userService.createUser(request);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("User"), payload.capture());
        assertEquals("", payload.getValue().get(UserFeatureService.FEATURE_ALLOW_FIELD));
        assertEquals("", payload.getValue().get(UserFeatureService.FEATURE_DENY_FIELD));
    }

    /**
     * The stock User doctype has no supplier/customer field, so writing those keys was
     * silently discarded — a vendor was required to have a supplier that then vanished.
     */
    @Test
    void persistsPartyLinksOnTheAasCustomFields() {
        UserCreateRequest request = request("ravi@example.com", "vendor");
        request.setSupplier("FreshHarvest Agro Foods");
        when(erpNextClient.getResource("User", "ravi@example.com"))
                .thenReturn(Map.of())
                .thenReturn(existingUserDoc("ravi@example.com"));
        when(erpNextClient.listResources(eq("User"), any())).thenReturn(List.of());

        userService.createUser(request);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("User"), payload.capture());
        assertEquals("FreshHarvest Agro Foods", payload.getValue().get(UserService.SUPPLIER_FIELD));
        assertEquals("", payload.getValue().get(UserService.CUSTOMER_FIELD));
        // The stock keys must not be sent; ERPNext would drop them anyway.
        assertTrue(!payload.getValue().containsKey("supplier"));
    }

    @Test
    void profileReadsPartyLinksFromTheAasCustomFields() {
        when(erpNextClient.getResource("User", "ravi@example.com")).thenReturn(Map.of("data", Map.of(
                "name", "ravi@example.com",
                "email", "ravi@example.com",
                "enabled", 1,
                UserService.SUPPLIER_FIELD, "FreshHarvest Agro Foods",
                "roles", List.of(Map.of("role", "Supplier")))));

        Map<String, Object> profile = userService.getUserProfile("ravi@example.com");
        assertEquals("FreshHarvest Agro Foods", profile.get("supplier"));
        assertEquals("", profile.get("customer"));
    }

    /** The User Settings page posts "supplier"/"customer"; they must land on the AAS fields. */
    @Test
    void profileUpdateRoutesPartyLinksToTheAasCustomFields() {
        when(erpNextClient.getResource("User", "ravi@example.com")).thenReturn(existingUserDoc("ravi@example.com"));
        FieldsRequest request = new FieldsRequest();
        request.setFields(Map.of("supplier", "FreshHarvest Agro Foods", "customer", ""));

        userService.updateUserProfile("ravi@example.com", request);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("User"), eq("ravi@example.com"), payload.capture());
        assertEquals("FreshHarvest Agro Foods", payload.getValue().get(UserService.SUPPLIER_FIELD));
        assertEquals("", payload.getValue().get(UserService.CUSTOMER_FIELD));
    }

    /** The stamped role wins over ERP roles, so the admin list agrees with what login returns. */
    @Test
    void profilePrefersStampedAppRoleOverErpRoles() {
        when(erpNextClient.getResource("User", "ravi@example.com")).thenReturn(Map.of("data", Map.of(
                "name", "ravi@example.com",
                "email", "ravi@example.com",
                "enabled", 1,
                AppRole.FIELD, "vendor",
                // ERP roles alone would resolve HELPER; the stamped role must win.
                "roles", List.of(Map.of("role", "Stock User")))));

        assertEquals("vendor", userService.getUserProfile("ravi@example.com").get("role"));
    }

    @Test
    void profileFallsBackToErpRolesWhenAppRoleIsMissingOrUnrecognised() {
        when(erpNextClient.getResource("User", "old@example.com")).thenReturn(Map.of("data", Map.of(
                "name", "old@example.com",
                "email", "old@example.com",
                "enabled", 1,
                AppRole.FIELD, "not-a-role",
                "roles", List.of(Map.of("role", "Supplier")))));

        assertEquals("vendor", userService.getUserProfile("old@example.com").get("role"));
    }

    @Test
    void normalizesEmailAndFallsBackToEmailLocalPartWhenUsernameTaken() {
        when(erpNextClient.getResource("User", "asha@example.com"))
                .thenReturn(Map.of())
                .thenReturn(existingUserDoc("asha@example.com"));
        // "Asha" is taken; the email local part is free.
        when(erpNextClient.listResources(eq("User"), any()))
                .thenReturn(List.of(Map.of("name", "someone@example.com")))
                .thenReturn(List.of());

        userService.createUser(request("  Asha@Example.com  ", "helper"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("User"), payload.capture());
        assertEquals("asha@example.com", payload.getValue().get("email"));
        assertEquals("asha", payload.getValue().get("username"));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(erpNextClient.getResource("User", "asha@example.com")).thenReturn(existingUserDoc("asha@example.com"));

        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> userService.createUser(request("asha@example.com", "helper")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(erpNextClient, never()).createResource(anyString(), any());
    }

    @Test
    void rejectsVendorWithoutSupplierLink() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> userService.createUser(request("v@example.com", "vendor")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("supplier"));
        verify(erpNextClient, never()).createResource(anyString(), any());
    }

    @Test
    void rejectsBranchWithoutCustomerLink() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> userService.createUser(request("b@example.com", "shop")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("customer"));
    }

    @Test
    void rejectsUnknownRole() {
        ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> userService.createUser(request("x@example.com", "superuser")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(erpNextClient, never()).createResource(anyString(), any());
    }

    @Test
    void disablesUser() {
        when(erpNextClient.getResource("User", "asha@example.com")).thenReturn(existingUserDoc("asha@example.com"));

        userService.setUserEnabled("asha@example.com", false, "admin@example.com");

        verify(erpNextClient).updateResource("User", "asha@example.com", Map.of("enabled", 0));
    }

    @Test
    void rejectsDisablingYourself() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.setUserEnabled("admin@example.com", false, "Admin@Example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(erpNextClient, never()).updateResource(anyString(), anyString(), any());
    }

    @Test
    void rejectsDisablingAdministrator() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.setUserEnabled("Administrator", false, "admin@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(erpNextClient, never()).updateResource(anyString(), anyString(), any());
    }

    @Test
    void allowsReEnablingYourself() {
        when(erpNextClient.getResource("User", "admin@example.com")).thenReturn(existingUserDoc("admin@example.com"));

        userService.setUserEnabled("admin@example.com", true, "admin@example.com");

        verify(erpNextClient).updateResource("User", "admin@example.com", Map.of("enabled", 1));
    }
}
