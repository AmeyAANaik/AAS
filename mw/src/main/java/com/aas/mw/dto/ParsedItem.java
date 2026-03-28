package com.aas.mw.dto;

public record ParsedItem(
        String name,
        double qty,
        double rate,
        double amount,
        String hsn,
        Double gstPercent,
        String uom,
        Double mrp,
        Double rateBeforeTax,
        Double rateAfterTax) {

    public ParsedItem(String name, double qty, double rate, double amount) {
        this(name, qty, rate, amount, null, null, null, null, null, null);
    }

    public ParsedItem(String name, double qty, double rate, double amount, String hsn) {
        this(name, qty, rate, amount, hsn, null, null, null, null, null);
    }

    public ParsedItem(String name, double qty, double rate, double amount, String hsn, Double gstPercent) {
        this(name, qty, rate, amount, hsn, gstPercent, null, null, null, null);
    }

    public ParsedItem(String name, double qty, double rate, double amount, String hsn, Double gstPercent, Double mrp) {
        this(name, qty, rate, amount, hsn, gstPercent, null, mrp, null, null);
    }

    public ParsedItem(String name, double qty, double rate, double amount, String hsn, Double gstPercent, String uom, Double mrp) {
        this(name, qty, rate, amount, hsn, gstPercent, uom, mrp, null, null);
    }
}
