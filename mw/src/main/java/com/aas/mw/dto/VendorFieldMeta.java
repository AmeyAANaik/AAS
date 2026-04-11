package com.aas.mw.dto;

public record VendorFieldMeta(
        String key,
        String label,
        String fieldtype,
        String options,
        boolean required) {
}

