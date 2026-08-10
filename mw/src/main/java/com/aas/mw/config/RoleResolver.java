package com.aas.mw.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RoleResolver {

    private final String adminRole;
    private final String vendorRole;
    private final String shopRole;
    private final String helperRole;
    private final String helperExtraRoles;

    public RoleResolver(
            @Value("${app.role.admin:Administrator}") String adminRole,
            @Value("${app.role.vendor:Supplier}") String vendorRole,
            @Value("${app.role.shop:Customer}") String shopRole,
            @Value("${app.role.helper:Stock User}") String helperRole,
            @Value("${app.roles.helper-extra:Accounts User,Sales User}") String helperExtraRoles) {
        this.adminRole = adminRole;
        this.vendorRole = vendorRole;
        this.shopRole = shopRole;
        this.helperRole = helperRole;
        this.helperExtraRoles = helperExtraRoles;
    }

    /**
     * Inverse of {@link #resolve(List)}: the ERP role names to write on a User doc so that a
     * later login resolves back to the given app role. Helpers also carry the configured extra
     * roles, since ERPNext gates desk access on those.
     */
    public List<String> erpRolesFor(AppRole role) {
        if (role == null) {
            return List.of();
        }
        String primary = switch (role) {
            case ADMIN -> adminRole;
            case VENDOR -> vendorRole;
            case SHOP -> shopRole;
            case HELPER -> helperRole;
        };
        String combined = role == AppRole.HELPER
                ? primary + "," + (helperExtraRoles == null ? "" : helperExtraRoles)
                : primary;
        return Arrays.stream(combined.split(","))
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    public AppRole resolve(List<String> erpRoles) {
        if (containsRole(erpRoles, adminRole)) {
            return AppRole.ADMIN;
        }
        if (containsRole(erpRoles, vendorRole)) {
            return AppRole.VENDOR;
        }
        if (containsRole(erpRoles, shopRole)) {
            return AppRole.SHOP;
        }
        if (containsRole(erpRoles, helperRole)) {
            return AppRole.HELPER;
        }
        throw new IllegalStateException("No supported ERP role assigned.");
    }

    private boolean containsRole(List<String> erpRoles, String role) {
        if (erpRoles == null || role == null) {
            return false;
        }
        return erpRoles.stream().anyMatch(r -> r != null && r.equalsIgnoreCase(role));
    }
}
