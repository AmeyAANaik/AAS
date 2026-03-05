package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VendorInvoiceTemplateResolver {

    private static final String SUPPLIER = "Supplier";

    private final ErpNextClient erpNextClient;

    public VendorInvoiceTemplateResolver(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Optional<String> loadTemplateKey(String supplierName) {
        if (supplierName == null || supplierName.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> supplier = unwrapResource(erpNextClient.getResource(SUPPLIER, supplierName));
        if (supplier == null || supplier.isEmpty()) {
            return Optional.empty();
        }
        Object enabled = supplier.get("aas_invoice_template_enabled");
        if (!isEnabled(enabled)) {
            return Optional.empty();
        }
        String key = asText(supplier.get("aas_invoice_template_key"));
        return key.isBlank() ? Optional.empty() : Optional.of(key);
    }

    private boolean isEnabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }
}

