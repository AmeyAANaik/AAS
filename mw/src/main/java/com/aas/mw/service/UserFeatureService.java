package com.aas.mw.service;

import com.aas.mw.config.AppRole;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class UserFeatureService {

    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String ORDERS_VIEW = "orders.view";
    public static final String ORDERS_CREATE = "orders.create";
    public static final String ORDERS_DELETE = "orders.delete";
    public static final String STOCK_VIEW = "stock.view";
    public static final String BILLS_VIEW = "bills.view";
    public static final String REPORTS_VIEW = "reports.view";
    public static final String VENDOR_OPS_VIEW = "vendor_ops.view";
    public static final String BRANCH_OPS_VIEW = "branch_ops.view";
    public static final String MASTER_DATA_VIEW = "master_data.view";
    public static final String MASTER_DATA_REVIEW_VIEW = "master_data_review.view";
    public static final String BILL_REVIEW_VIEW = "bill_review.view";
    public static final String COMPANY_SETTINGS_VIEW = "company_settings.view";
    public static final String USER_SETTINGS_VIEW = "user_settings.view";
    public static final String FEATURE_ALLOW_FIELD = "aas_feature_allow_json";
    public static final String FEATURE_DENY_FIELD = "aas_feature_deny_json";

    private static final List<FeatureDefinition> FEATURE_CATALOG = List.of(
            new FeatureDefinition(DASHBOARD_VIEW, "Dashboard", "Workspace", "Open the dashboard landing view."),
            new FeatureDefinition(ORDERS_VIEW, "Orders", "Workspace", "Open order workflow screens."),
            new FeatureDefinition(ORDERS_CREATE, "Create Orders", "Workspace", "Create new orders from images or item selection."),
            new FeatureDefinition(ORDERS_DELETE, "Delete Orders", "Workspace", "Delete or archive existing orders."),
            new FeatureDefinition(STOCK_VIEW, "Stock", "Workspace", "Open inventory and stock screens."),
            new FeatureDefinition(BILLS_VIEW, "Bills & Invoices", "Workspace", "Open billing and invoice screens."),
            new FeatureDefinition(REPORTS_VIEW, "Reports", "Workspace", "Open reporting and export pages."),
            new FeatureDefinition(VENDOR_OPS_VIEW, "Vendor Operations", "Operations", "Open vendor operations queues."),
            new FeatureDefinition(BRANCH_OPS_VIEW, "Branch Operations", "Operations", "Open branch operations queues."),
            new FeatureDefinition(MASTER_DATA_VIEW, "Master Data", "Administration", "Open vendors, branches, categories, and items."),
            new FeatureDefinition(MASTER_DATA_REVIEW_VIEW, "Master Data Review", "Administration", "Open the master data review queue."),
            new FeatureDefinition(BILL_REVIEW_VIEW, "Bill Review", "Administration", "Review and approve recorded payments."),
            new FeatureDefinition(COMPANY_SETTINGS_VIEW, "Company Settings", "Administration", "Open company settings and admin controls."),
            new FeatureDefinition(USER_SETTINGS_VIEW, "User Settings", "Profile", "Open the personal user details screen."));

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Set<String> featuresForRole(String role) {
        AppRole appRole = resolveRole(role);
        LinkedHashSet<String> features = new LinkedHashSet<>();
        features.add(DASHBOARD_VIEW);
        features.add(USER_SETTINGS_VIEW);

        switch (appRole) {
            case ADMIN -> {
                features.add(ORDERS_VIEW);
                features.add(ORDERS_CREATE);
                features.add(ORDERS_DELETE);
                features.add(STOCK_VIEW);
                features.add(BILLS_VIEW);
                features.add(REPORTS_VIEW);
                features.add(VENDOR_OPS_VIEW);
                features.add(BRANCH_OPS_VIEW);
                features.add(MASTER_DATA_VIEW);
                features.add(MASTER_DATA_REVIEW_VIEW);
                features.add(BILL_REVIEW_VIEW);
                features.add(COMPANY_SETTINGS_VIEW);
            }
            case HELPER -> {
                features.add(ORDERS_VIEW);
                features.add(STOCK_VIEW);
                features.add(BILLS_VIEW);
                features.add(REPORTS_VIEW);
                features.add(VENDOR_OPS_VIEW);
                features.add(BRANCH_OPS_VIEW);
            }
            case VENDOR -> {
                features.add(ORDERS_VIEW);
                features.add(REPORTS_VIEW);
                features.add(VENDOR_OPS_VIEW);
            }
            case SHOP -> {
                features.add(ORDERS_VIEW);
                features.add(STOCK_VIEW);
                features.add(BILLS_VIEW);
                features.add(REPORTS_VIEW);
                features.add(BRANCH_OPS_VIEW);
                features.add(ORDERS_CREATE);
            }
        }

        return Set.copyOf(features);
    }

    public Set<String> resolveFeatures(String role, List<String> allowOverrides, List<String> denyOverrides) {
        LinkedHashSet<String> features = new LinkedHashSet<>(featuresForRole(role));
        sanitizeFeatureList(allowOverrides).forEach(features::add);
        sanitizeFeatureList(denyOverrides).forEach(features::remove);
        return Set.copyOf(features);
    }

    public List<String> parseFeatureOverrides(Object rawValue) {
        if (rawValue instanceof List<?> list) {
            return sanitizeFeatureList(list.stream().map(String::valueOf).toList());
        }
        if (rawValue == null) {
            return List.of();
        }
        String text = String.valueOf(rawValue).trim();
        if (text.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(text, new TypeReference<List<String>>() {});
            return sanitizeFeatureList(parsed);
        } catch (Exception ignored) {
            return sanitizeFeatureList(List.of(text.split(",")));
        }
    }

    public String encodeFeatureOverrides(List<String> features) {
        List<String> sanitized = sanitizeFeatureList(features);
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public List<FeatureDefinition> featureCatalog() {
        return FEATURE_CATALOG;
    }

    public boolean isKnownFeature(String feature) {
        return FEATURE_CATALOG.stream().anyMatch(item -> item.key().equals(feature));
    }

    public String homeRouteForRole(String role) {
        AppRole appRole = resolveRole(role);
        return switch (appRole) {
            case ADMIN, HELPER -> "/admin/dashboard";
            case VENDOR -> "/vendor-ops";
            case SHOP -> "/branch-ops";
        };
    }

    private AppRole resolveRole(String role) {
        if (role == null || role.isBlank()) {
            return AppRole.ADMIN;
        }
        try {
            return AppRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AppRole.ADMIN;
        }
    }

    private List<String> sanitizeFeatureList(List<String> features) {
        if (features == null || features.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : features) {
            String feature = value == null ? "" : value.trim();
            if (feature.isBlank() || !isKnownFeature(feature) || sanitized.contains(feature)) {
                continue;
            }
            sanitized.add(feature);
        }
        return List.copyOf(sanitized);
    }

    public record FeatureDefinition(String key, String label, String group, String description) {}
}
