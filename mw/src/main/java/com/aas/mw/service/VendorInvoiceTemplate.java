package com.aas.mw.service;

public record VendorInvoiceTemplate(
        int version,
        String itemLineRegex,
        String billDateRegex) {
}

