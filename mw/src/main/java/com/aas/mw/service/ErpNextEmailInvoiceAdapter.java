package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErpNextEmailInvoiceAdapter implements InvoiceChannelAdapter {

    private final ErpNextClient erpNextClient;
    private final String fallbackSender;

    public ErpNextEmailInvoiceAdapter(
            ErpNextClient erpNextClient,
            @Value("${app.invoice-delivery.email.sender:}") String fallbackSender) {
        this.erpNextClient = erpNextClient;
        this.fallbackSender = fallbackSender == null ? "" : fallbackSender.trim();
    }

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean isConfigured() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"email_id\",\"enable_outgoing\",\"default_outgoing\"]");
        params.put("limit_page_length", 50);
        List<Map<String, Object>> accounts = erpNextClient.listResources("Email Account", params);
        return accounts.stream().anyMatch(account ->
                asInt(account.get("enable_outgoing")) == 1 || asInt(account.get("default_outgoing")) == 1);
    }

    @Override
    public String configurationHint() {
        return "Configure a default outgoing ERPNext Email Account before sending invoices.";
    }

    @Override
    public Map<String, Object> send(InvoiceDeliveryContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("doctype", "Sales Invoice");
        payload.put("name", context.invoiceId());
        payload.put("recipients", context.recipient());
        payload.put("subject", "Invoice " + context.invoiceId());
        payload.put("content", context.message());
        payload.put("send_email", 1);
        payload.put("communication_medium", "Email");
        payload.put("print_format", resolvePrintFormat(context.invoice()));
        payload.put("print_letterhead", 1);
        payload.put("now", 1);
        if (!fallbackSender.isBlank()) {
            payload.put("sender", fallbackSender);
        }
        return erpNextClient.postMethod("frappe.core.doctype.communication.email.make", payload);
    }

    private String resolvePrintFormat(Map<String, Object> invoice) {
        String company = asText(invoice.get("company"));
        if (company.isBlank()) {
            return "";
        }
        Map<String, Object> companyDoc = unwrap(erpNextClient.getResource("Company", company));
        return asText(companyDoc.get("aas_sales_invoice_print_format"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }
}
