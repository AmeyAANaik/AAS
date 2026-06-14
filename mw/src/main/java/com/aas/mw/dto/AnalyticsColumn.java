package com.aas.mw.dto;

public record AnalyticsColumn(String id, String label, String colType) {
    // colType values: DIMENSION, CURRENCY, PERCENT, NUMBER
}
