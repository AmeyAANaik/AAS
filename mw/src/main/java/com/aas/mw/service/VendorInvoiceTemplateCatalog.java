package com.aas.mw.service;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class VendorInvoiceTemplateCatalog {

    public static final String KEY_HEURISTIC_V1 = "heuristic_v1";
    public static final String KEY_TABLE_V1 = "table_v1";

    private static final VendorInvoiceTemplate TABLE_V1 = new VendorInvoiceTemplate(
            1,
            // Greedy name group is required because item names often contain numbers (e.g. "50 kg sack").
            "^(?<name>.+)\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s+(?<rate>\\d+(?:\\.\\d+)?)\\s+(?<amount>\\d+(?:\\.\\d+)?)\\s+(?<hsn>\\d{4,10})$",
            null,
            null
    );

    public Optional<VendorInvoiceTemplate> byKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase();
        if (KEY_TABLE_V1.equals(normalized)) {
            return Optional.of(TABLE_V1);
        }
        return Optional.empty();
    }

    public Map<String, VendorInvoiceTemplate> all() {
        return Map.of(
                KEY_TABLE_V1, TABLE_V1
        );
    }
}
