package com.aas.mw.dto;

import java.util.List;
import java.util.Map;

public record NativeLayoutMappingProfile(
        String profileId,
        String label,
        String vendorName,
        String description,
        List<Map<String, Object>> itemMappings,
        List<Map<String, Object>> summaryMappings,
        Map<String, Object> validation) {
}
