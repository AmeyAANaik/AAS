package com.aas.mw.dto;

import java.util.Map;

public record NativeMappedInvoiceRow(
        String itemName,
        String itemId,
        Double qty,
        String uom,
        Double rate,
        Double mrp,
        Double gstPercent,
        Double total,
        Double rateBeforeTax,
        Double rateAfterTax,
        Map<String, String> sourceValues,
        String rawText) {
}
