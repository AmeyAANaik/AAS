package com.aas.mw.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RoleResolver {

    private final String adminRole;
    private final String vendorRole;
    private final String shopRole;
    private final String helperRole;

    public RoleResolver(
            @Value("${app.role.admin:Administrator}") String adminRole,
            @Value("${app.role.vendor:Supplier}") String vendorRole,
            @Value("${app.role.shop:Customer}") String shopRole,
            @Value("${app.role.helper:Stock User}") String helperRole) {
        this.adminRole = adminRole;
        this.vendorRole = vendorRole;
        this.shopRole = shopRole;
        this.helperRole = helperRole;
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
        return AppRole.ADMIN;
    }

    private boolean containsRole(List<String> erpRoles, String role) {
        if (erpRoles == null || role == null) {
            return false;
        }
        return erpRoles.stream().anyMatch(r -> r != null && r.equalsIgnoreCase(role));
    }
}
