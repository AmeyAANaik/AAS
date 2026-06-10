package com.aas.mw.service;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserFeatureServiceTest {

    private final UserFeatureService service = new UserFeatureService();

    @Test
    void helperHasAllFeaturesExceptApprovalQueues() {
        Set<String> features = service.featuresForRole("helper");

        assertTrue(features.contains(UserFeatureService.DASHBOARD_VIEW));
        assertTrue(features.contains(UserFeatureService.ORDERS_VIEW));
        assertTrue(features.contains(UserFeatureService.ORDERS_CREATE));
        assertTrue(features.contains(UserFeatureService.ORDERS_DELETE));
        assertTrue(features.contains(UserFeatureService.STOCK_VIEW));
        assertTrue(features.contains(UserFeatureService.BILLS_VIEW));
        assertTrue(features.contains(UserFeatureService.REPORTS_VIEW));
        assertTrue(features.contains(UserFeatureService.VENDOR_OPS_VIEW));
        assertTrue(features.contains(UserFeatureService.BRANCH_OPS_VIEW));
        assertTrue(features.contains(UserFeatureService.MASTER_DATA_VIEW));
        assertTrue(features.contains(UserFeatureService.ITEMS_MANAGE));
        assertTrue(features.contains(UserFeatureService.COMPANY_SETTINGS_VIEW));
        assertFalse(features.contains(UserFeatureService.ADMIN_ACCESS_VIEW));
        assertFalse(features.contains(UserFeatureService.MASTER_DATA_REVIEW_VIEW));
        assertFalse(features.contains(UserFeatureService.BILL_REVIEW_VIEW));
    }

    @Test
    void adminHasApprovalAndAccessManagementFeaturesByDefault() {
        Set<String> features = service.featuresForRole("admin");

        assertTrue(features.contains(UserFeatureService.MASTER_DATA_REVIEW_VIEW));
        assertTrue(features.contains(UserFeatureService.BILL_REVIEW_VIEW));
        assertTrue(features.contains(UserFeatureService.ADMIN_ACCESS_VIEW));
    }

    @Test
    void roleHomesMatchWorkspaceExpectations() {
        assertEquals("/admin/dashboard", service.homeRouteForRole("admin"));
        assertEquals("/admin/dashboard", service.homeRouteForRole("helper"));
        assertEquals("/vendor-ops", service.homeRouteForRole("vendor"));
        assertEquals("/branch-ops", service.homeRouteForRole("shop"));
    }

    @Test
    void overridesCanAddAndRemoveUiFeatures() {
        Set<String> features = service.resolveFeatures(
                "helper",
                java.util.List.of(UserFeatureService.MASTER_DATA_VIEW, UserFeatureService.ORDERS_CREATE),
                java.util.List.of(UserFeatureService.REPORTS_VIEW));

        assertTrue(features.contains(UserFeatureService.MASTER_DATA_VIEW));
        assertTrue(features.contains(UserFeatureService.ORDERS_CREATE));
        assertFalse(features.contains(UserFeatureService.REPORTS_VIEW));
    }

    @Test
    void shopCanCreateOrdersByDefaultButCannotDeleteOrders() {
        Set<String> features = service.featuresForRole("shop");

        assertTrue(features.contains(UserFeatureService.ORDERS_CREATE));
        assertFalse(features.contains(UserFeatureService.ORDERS_DELETE));
    }
}
