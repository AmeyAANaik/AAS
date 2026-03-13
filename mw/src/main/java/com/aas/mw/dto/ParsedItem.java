package com.aas.mw.dto;

public record ParsedItem(
        String name,
        double qty,
        double rate,
        double amount,
        String hsn,
        Double gstPercent) {

    public ParsedItem(String name, double qty, double rate, double amount) {
        this(name, qty, rate, amount, null, null);
    }

    public ParsedItem(String name, double qty, double rate, double amount, String hsn) {
        this(name, qty, rate, amount, hsn, null);
    }
}
