package com.aas.mw.dto;

public record ParsedItem(
        String name,
        double qty,
        double rate,
        double amount) {
}
