package com.aas.mw.config;

import com.aas.mw.service.AuthenticationService;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.UserFeatureService;
import com.aas.mw.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class FeatureAuthorizationFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<FeatureRoute> routes = List.of(
            new FeatureRoute("GET", "/api/admin/access/**", UserFeatureService.ADMIN_ACCESS_VIEW),
            new FeatureRoute("PUT", "/api/admin/access/**", UserFeatureService.ADMIN_ACCESS_VIEW),
            new FeatureRoute(null, "/api/master-data-review/**", UserFeatureService.MASTER_DATA_REVIEW_VIEW),
            new FeatureRoute(null, "/api/bill-review/**", UserFeatureService.BILL_REVIEW_VIEW),

            new FeatureRoute("POST", "/api/setup/**", UserFeatureService.ADMIN_ACCESS_VIEW),
            new FeatureRoute("GET", "/api/debug/**", UserFeatureService.ADMIN_ACCESS_VIEW),
            new FeatureRoute("GET", "/api/ocr/health", UserFeatureService.ADMIN_ACCESS_VIEW),

            new FeatureRoute("GET", "/api/vendors/template-model", UserFeatureService.MASTER_DATA_VIEW),
            new FeatureRoute("GET", "/api/vendors/*/invoice-template/**", UserFeatureService.MASTER_DATA_VIEW),
            new FeatureRoute("POST", "/api/vendors/*/invoice-template/**", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("DELETE", "/api/vendors/*/invoice-template", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute(null, "/api/meta/vendors/**", UserFeatureService.MASTER_DATA_VIEW),
            new FeatureRoute("GET", "/api/vendors", UserFeatureService.MASTER_DATA_VIEW),
            new FeatureRoute("POST", "/api/vendors", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("PUT", "/api/vendors/*", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("DELETE", "/api/vendors/*", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("GET", "/api/shops", UserFeatureService.MASTER_DATA_VIEW),
            new FeatureRoute("POST", "/api/shops", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("PUT", "/api/shops/*", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("DELETE", "/api/shops/*", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("GET", "/api/categories", UserFeatureService.MASTER_DATA_VIEW),
            new FeatureRoute("POST", "/api/categories", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("PUT", "/api/categories/*", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute("DELETE", "/api/categories/*", UserFeatureService.MASTER_DATA_VIEW, true),
            new FeatureRoute(null, "/api/uoms", UserFeatureService.ITEMS_MANAGE),
            new FeatureRoute("GET", "/api/items", UserFeatureService.ITEMS_MANAGE),
            new FeatureRoute("GET", "/api/items/**", UserFeatureService.ITEMS_MANAGE),
            new FeatureRoute("POST", "/api/items", UserFeatureService.ITEMS_MANAGE, true),
            new FeatureRoute("POST", "/api/items/**", UserFeatureService.ITEMS_MANAGE, true),
            new FeatureRoute("PUT", "/api/items/*", UserFeatureService.ITEMS_MANAGE, true),
            new FeatureRoute("DELETE", "/api/items/*", UserFeatureService.ITEMS_MANAGE, true),

            new FeatureRoute("POST", "/api/orders", UserFeatureService.ORDERS_CREATE, true),
            new FeatureRoute("POST", "/api/orders/direct-item-flow", UserFeatureService.ORDERS_CREATE, true),
            new FeatureRoute("POST", "/api/orders/branch-image", UserFeatureService.ORDERS_CREATE, true),
            new FeatureRoute("POST", "/api/orders/*/image", UserFeatureService.ORDERS_CREATE, true),
            new FeatureRoute("POST", "/api/orders/*/vendor-pdf", UserFeatureService.ORDERS_CREATE, true),
            new FeatureRoute("DELETE", "/api/orders/*", UserFeatureService.ORDERS_DELETE, true),
            new FeatureRoute("GET", "/api/orders", UserFeatureService.ORDERS_VIEW),
            new FeatureRoute("GET", "/api/orders/**", UserFeatureService.ORDERS_VIEW),
            new FeatureRoute("PUT", "/api/orders/**", UserFeatureService.ORDERS_VIEW, true),
            new FeatureRoute("POST", "/api/orders/**", UserFeatureService.ORDERS_VIEW, true),

            new FeatureRoute(null, "/api/vendor-ops/**", UserFeatureService.VENDOR_OPS_VIEW),
            new FeatureRoute(null, "/api/branch-ops/**", UserFeatureService.BRANCH_OPS_VIEW),
            new FeatureRoute(null, "/api/reports/**", UserFeatureService.REPORTS_VIEW),
            new FeatureRoute(null, "/api/reporting/**", UserFeatureService.REPORTS_VIEW),

            new FeatureRoute(null, "/api/invoices", UserFeatureService.BILLS_VIEW),
            new FeatureRoute(null, "/api/invoices/**", UserFeatureService.BILLS_VIEW),
            new FeatureRoute("GET", "/api/payments", UserFeatureService.BILLS_VIEW),
            new FeatureRoute("GET", "/api/payments/**", UserFeatureService.BILLS_VIEW),
            new FeatureRoute("POST", "/api/payments", UserFeatureService.BILLS_VIEW, true),
            new FeatureRoute("POST", "/api/payments/**", UserFeatureService.BILLS_VIEW, true),
            new FeatureRoute("GET", "/api/adjustment-notes/**", UserFeatureService.BILLS_VIEW),
            new FeatureRoute("POST", "/api/adjustment-notes/**", UserFeatureService.BILLS_VIEW, true),

            new FeatureRoute("GET", "/api/company-context", UserFeatureService.DASHBOARD_VIEW),
            new FeatureRoute("GET", "/api/dashboard/**", UserFeatureService.DASHBOARD_VIEW),
            new FeatureRoute(null, "/api/companies/*/opening-balances/**", UserFeatureService.COMPANY_SETTINGS_VIEW),
            new FeatureRoute("GET", "/api/companies", UserFeatureService.COMPANY_SETTINGS_VIEW),
            new FeatureRoute("GET", "/api/companies/*", UserFeatureService.DASHBOARD_VIEW),
            new FeatureRoute("GET", "/api/companies/**", UserFeatureService.COMPANY_SETTINGS_VIEW),
            new FeatureRoute("PUT", "/api/companies/**", UserFeatureService.COMPANY_SETTINGS_VIEW, true),
            new FeatureRoute("POST", "/api/companies/**", UserFeatureService.COMPANY_SETTINGS_VIEW, true),
            new FeatureRoute("GET", "/api/users/**", UserFeatureService.USER_SETTINGS_VIEW),
            new FeatureRoute("PUT", "/api/users/**", UserFeatureService.USER_SETTINGS_VIEW));

    public FeatureAuthorizationFilter(UserService userService, AuthenticationService authenticationService) {
        this.userService = userService;
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        FeatureRoute route = requiredRoute(request);
        if (route == null || route.feature() == null || route.feature().isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        String feature = route.feature();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "" : authentication.getName();
        if (username == null || username.isBlank() || !userService.hasFeature(username, feature)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        if (!usePrivilegedErpSession(request) && route.requiresServiceErpSession()) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Service ERP session is not available. Check ERP setup credentials.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean usePrivilegedErpSession(HttpServletRequest request) {
        try {
            request.setAttribute(ErpSessionStore.REQUEST_ATTR, authenticationService.getSetupSessionCookie());
            return true;
        } catch (RuntimeException ignored) {
            // Feature authorization is the AAS permission boundary. If the service
            // ERP session is unavailable, read routes may still use the user's ERP
            // session; write/workflow routes fail explicitly before ERPNext 403s.
            return false;
        }
    }

    private FeatureRoute requiredRoute(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        for (FeatureRoute route : routes) {
            if (route.matches(method, path, pathMatcher)) {
                return route;
            }
        }
        return null;
    }

    private record FeatureRoute(String method, String pattern, String feature, boolean requiresServiceErpSession) {
        FeatureRoute(String method, String pattern, String feature) {
            this(method, pattern, feature, false);
        }

        boolean matches(String requestMethod, String path, AntPathMatcher pathMatcher) {
            return (method == null || method.equalsIgnoreCase(requestMethod))
                    && pathMatcher.match(pattern, path);
        }
    }
}
