package com.aas.mw.meta;

public record VendorFieldSpec(
        String key,
        String fieldname,
        String label,
        String fieldtype,
        String options,
        String insertAfter,
        boolean inListView,
        boolean required) {
}

