package com.aas.mw.dto;

public record AnalyticsKpi(String id, String label, double value, String valueType) {
    // valueType values: CURRENCY, PERCENT, NUMBER
}
