package com.aas.mw.service;

import java.util.Map;

public record InvoiceDeliveryContext(
        String invoiceId,
        Map<String, Object> invoice,
        Map<String, Object> customer,
        String recipient,
        String message,
        byte[] pdfBytes) {
}
