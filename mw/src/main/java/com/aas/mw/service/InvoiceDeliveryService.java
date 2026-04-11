package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.InvoiceDeliveryRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InvoiceDeliveryService {

    private final ErpNextClient erpNextClient;
    private final InvoiceService invoiceService;
    private final Map<String, InvoiceChannelAdapter> adapters;

    public InvoiceDeliveryService(
            ErpNextClient erpNextClient,
            InvoiceService invoiceService,
            List<InvoiceChannelAdapter> adapters) {
        this.erpNextClient = erpNextClient;
        this.invoiceService = invoiceService;
        this.adapters = adapters.stream().collect(Collectors.toMap(InvoiceChannelAdapter::channel, Function.identity()));
    }

    public Map<String, Object> preview(String invoiceId) {
        Map<String, Object> invoice = getInvoice(invoiceId);
        Map<String, Object> customer = getCustomer(asText(invoice.get("customer")));
        return buildPreview(invoiceId, invoice, customer);
    }

    public Map<String, Object> sendEmail(String invoiceId, InvoiceDeliveryRequest request) {
        return send(invoiceId, request, "email");
    }

    public Map<String, Object> sendWhatsApp(String invoiceId, InvoiceDeliveryRequest request) {
        return send(invoiceId, request, "whatsapp");
    }

    public byte[] downloadPublicPdf(String invoiceId) {
        return invoiceService.downloadPdf(invoiceId);
    }

    private Map<String, Object> send(String invoiceId, InvoiceDeliveryRequest request, String channel) {
        Map<String, Object> invoice = getInvoice(invoiceId);
        Map<String, Object> customer = getCustomer(asText(invoice.get("customer")));
        Map<String, Object> preview = buildPreview(invoiceId, invoice, customer);
        InvoiceChannelAdapter adapter = requireAdapter(channel);
        Map<String, Object> channelPreview = channelPreview(preview, channel);
        if (!asBoolean(channelPreview.get("configured"))) {
            throw new IllegalStateException(asText(channelPreview.get("hint")));
        }
        String recipient = firstNonBlank(
                request == null ? "" : request.getRecipient(),
                asText(channelPreview.get("recipient")));
        if (recipient.isBlank()) {
            throw new IllegalArgumentException("No " + channel + " recipient is configured for this branch.");
        }
        byte[] pdf = invoiceService.downloadPdf(invoiceId);
        String message = defaultMessage(channel, invoice, customer);
        if (request != null && request.getMessage() != null && !request.getMessage().isBlank()) {
            message = request.getMessage().trim();
        }
        Map<String, Object> result = adapter.send(new InvoiceDeliveryContext(invoiceId, invoice, customer, recipient, message, pdf));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("invoiceId", invoiceId);
        response.put("channel", channel);
        response.put("recipient", recipient);
        response.put("status", result.getOrDefault("status", "sent"));
        response.put("message", channel.equals("email") ? "Invoice email queued." : "Invoice WhatsApp message queued.");
        response.put("preview", preview);
        response.put("providerResult", result);
        response.put("sentAt", Instant.now().toString());
        return response;
    }

    private Map<String, Object> buildPreview(String invoiceId, Map<String, Object> invoice, Map<String, Object> customer) {
        Map<String, Object> email = previewChannel("email", resolveEmail(customer));
        Map<String, Object> whatsapp = previewChannel("whatsapp", resolveWhatsApp(customer));
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("invoiceId", invoiceId);
        preview.put("customer", asText(invoice.get("customer")));
        preview.put("customerDisplayName", firstNonBlank(asText(customer.get("customer_name")), asText(customer.get("name"))));
        preview.put("postingDate", asText(invoice.get("posting_date")));
        preview.put("grandTotal", invoice.getOrDefault("grand_total", 0));
        preview.put("outstandingAmount", invoice.getOrDefault("outstanding_amount", 0));
        preview.put("email", email);
        preview.put("whatsapp", whatsapp);
        preview.put("defaultEmailMessage", defaultMessage("email", invoice, customer));
        preview.put("defaultWhatsAppMessage", defaultMessage("whatsapp", invoice, customer));
        return preview;
    }

    private Map<String, Object> previewChannel(String channel, String recipient) {
        InvoiceChannelAdapter adapter = requireAdapter(channel);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("channel", channel);
        preview.put("recipient", recipient);
        preview.put("configured", adapter.isConfigured());
        preview.put("missingRecipient", recipient.isBlank());
        preview.put("hint", adapter.isConfigured()
                ? recipient.isBlank() ? "Configure a recipient on the branch to use this channel." : ""
                : adapter.configurationHint());
        return preview;
    }

    private Map<String, Object> getInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            throw new IllegalArgumentException("Invoice id is required.");
        }
        Map<String, Object> invoice = unwrap(erpNextClient.getResource("Sales Invoice", invoiceId));
        if (invoice.isEmpty()) {
            throw new IllegalArgumentException("Invoice not found.");
        }
        return invoice;
    }

    private Map<String, Object> getCustomer(String customerId) {
        if (customerId.isBlank()) {
            return Map.of();
        }
        return unwrap(erpNextClient.getResource("Customer", customerId));
    }

    private InvoiceChannelAdapter requireAdapter(String channel) {
        InvoiceChannelAdapter adapter = adapters.get(channel);
        if (adapter == null) {
            throw new IllegalStateException("Invoice delivery channel " + channel + " is not available.");
        }
        return adapter;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> channelPreview(Map<String, Object> preview, String channel) {
        Object value = preview.get(channel);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String resolveEmail(Map<String, Object> customer) {
        return firstNonBlank(
                asText(customer.get("aas_invoice_email")),
                asText(customer.get("email_id")),
                asText(customer.get("email")));
    }

    private String resolveWhatsApp(Map<String, Object> customer) {
        return firstNonBlank(
                asText(customer.get("aas_whatsapp_number")),
                asText(customer.get("whatsapp_no")),
                asText(customer.get("mobile_no")));
    }

    private String defaultMessage(String channel, Map<String, Object> invoice, Map<String, Object> customer) {
        String customerName = firstNonBlank(asText(customer.get("customer_name")), asText(invoice.get("customer")), "Customer");
        String invoiceId = asText(invoice.get("name"));
        String total = asText(invoice.get("grand_total"));
        if ("whatsapp".equals(channel)) {
            return "Hello " + customerName + ", your invoice " + invoiceId + " for INR " + total
                    + " is ready. Please find the invoice details attached or contact finance for help.";
        }
        return "Hello " + customerName + ",\n\nPlease find invoice " + invoiceId + " for INR " + total
                + " attached.\n\nRegards,\nAAS Finance";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
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
}
