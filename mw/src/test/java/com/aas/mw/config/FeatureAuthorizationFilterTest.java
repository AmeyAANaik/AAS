package com.aas.mw.config;

import com.aas.mw.service.UserFeatureService;
import com.aas.mw.service.UserService;
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
    private final FeatureAuthorizationFilter filter = new FeatureAuthorizationFilter(userService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsMappedApiWhenUserHasFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.MASTER_DATA_VIEW)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/vendors");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(userService).hasFeature("helper@example.com", UserFeatureService.MASTER_DATA_VIEW);
    }

    @Test
    void allowsExactItemsApiWhenUserHasItemsFeature() throws ServletException, IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("helper@example.com", null));
        when(userService.hasFeature("helper@example.com", UserFeatureService.ITEMS_MANAGE)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/items");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(userService).hasFeature("helper@example.com", UserFeatureService.ITEMS_MANAGE);
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
    }

    @Test
    void ignoresUnmappedApiRoutes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(userService, never()).hasFeature(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
