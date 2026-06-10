package com.aas.mw.config;

import com.aas.mw.service.UserFeatureService;
import com.aas.mw.service.UserService;
import com.aas.mw.service.AuthenticationService;
import com.aas.mw.service.ErpSessionStore;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureAuthorizationFilterTest {

    private final UserService userService = mock(UserService.class);
    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final FeatureAuthorizationFilter filter = new FeatureAuthorizationFilter(userService, authenticationService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsMappedApiWhenUserHasFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.MASTER_DATA_VIEW)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenReturn("sid=setup");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vendors");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("sid=setup", request.getAttribute(ErpSessionStore.REQUEST_ATTR));
        verify(userService).hasFeature("helper@example.com", UserFeatureService.MASTER_DATA_VIEW);
    }

    @Test
    void allowsExactItemsApiWhenUserHasItemsFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.ITEMS_MANAGE)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenReturn("sid=setup");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("sid=setup", request.getAttribute(ErpSessionStore.REQUEST_ATTR));
        verify(userService).hasFeature("helper@example.com", UserFeatureService.ITEMS_MANAGE);
    }

    @Test
    void allowsVendorPdfUploadWhenUserHasCreateOrdersFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.ORDERS_CREATE)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenReturn("sid=setup");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/SAL-ORD-2026-00027/vendor-pdf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(userService).hasFeature("helper@example.com", UserFeatureService.ORDERS_CREATE);
    }

    @Test
    void blocksVendorPdfUploadWhenUserLacksCreateOrdersFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.ORDERS_CREATE)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/SAL-ORD-2026-00027/vendor-pdf");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        verify(userService).hasFeature("helper@example.com", UserFeatureService.ORDERS_CREATE);
    }

    @Test
    void blocksVendorPdfUploadWhenServiceSessionIsUnavailable() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.ORDERS_CREATE)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenThrow(new IllegalStateException("setup unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/SAL-ORD-2026-00027/vendor-pdf");
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, "sid=helper");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertEquals("sid=helper", request.getAttribute(ErpSessionStore.REQUEST_ATTR));
    }

    @Test
    void allowsCompanyContextWhenUserHasDashboardFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.DASHBOARD_VIEW)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenReturn("sid=setup");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/company-context");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("sid=setup", request.getAttribute(ErpSessionStore.REQUEST_ATTR));
        verify(userService).hasFeature("helper@example.com", UserFeatureService.DASHBOARD_VIEW);
    }

    @Test
    void allowsCompanyProfileReadWithDashboardFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.DASHBOARD_VIEW)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenReturn("sid=setup");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/companies/Shree%20Siddhivinayak%20Suppliers");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(userService).hasFeature("helper@example.com", UserFeatureService.DASHBOARD_VIEW);
    }

    @Test
    void blocksMappedFeatureApiWhenServiceSessionIsUnavailable() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.DASHBOARD_VIEW)).thenReturn(true);
        when(authenticationService.getSetupSessionCookie()).thenThrow(new IllegalStateException("setup unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/company-context");
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, "sid=helper");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertEquals("sid=helper", request.getAttribute(ErpSessionStore.REQUEST_ATTR));
    }

    @Test
    void blocksMappedApiWhenUserLacksFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.BILL_REVIEW_VIEW)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bill-review/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        verify(userService).hasFeature("helper@example.com", UserFeatureService.BILL_REVIEW_VIEW);
        verify(authenticationService, never()).getSetupSessionCookie();
    }

    @Test
    void ignoresUnmappedApiRoutes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(userService, never()).hasFeature(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(authenticationService, never()).getSetupSessionCookie();
    }
}
