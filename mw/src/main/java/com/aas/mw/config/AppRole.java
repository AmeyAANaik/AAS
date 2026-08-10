package com.aas.mw.config;

public enum AppRole {
    ADMIN,
    VENDOR,
    SHOP,
    HELPER;

    /**
     * Custom field on the ERPNext User doc holding the app role verbatim.
     *
     * <p>Login reads the User doc with the caller's own ERP session, and a Website User
     * cannot read their own {@code roles} child table — so role cannot be derived from ERP
     * roles at login time. This field is the authoritative record instead.
     */
    public static final String FIELD = "aas_app_role";

    /** Select options for {@link #FIELD}, leading blank so the field is optional. */
    public static final String FIELD_OPTIONS = "\nadmin\nvendor\nshop\nhelper";

    public String asAuthority() {
        return "ROLE_" + name();
    }

    public String asKey() {
        return name().toLowerCase();
    }
}
