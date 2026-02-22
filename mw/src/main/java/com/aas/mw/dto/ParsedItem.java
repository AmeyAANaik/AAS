package com.aas.mw.dto;

public record ParsedItem(
        String name,
        double qty,
        double rate,
        double amount,
        String hsn) {

    public ParsedItem(String name, double qty, double rate, double amount) {
        this(name, qty, rate, amount, null);
    }
}
