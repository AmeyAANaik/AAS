package com.aas.mw.config;

public enum AppRole {
    ADMIN,
    VENDOR,
    SHOP,
    HELPER;

    public String asAuthority() {
        return "ROLE_" + name();
    }

    public String asKey() {
        return name().toLowerCase();
    }
}
