package com.aas.mw.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppInvoiceAdapter implements InvoiceChannelAdapter {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String metaAccessToken;
    private final String metaPhoneNumberId;
    private final String metaGraphApiVersion;

    @Autowired
    public WhatsAppInvoiceAdapter(
            @Value("${app.invoice-delivery.whatsapp.meta.access-token:}") String metaAccessToken,
            @Value("${app.invoice-delivery.whatsapp.meta.phone-number-id:}") String metaPhoneNumberId,
            @Value("${app.invoice-delivery.whatsapp.meta.graph-api-version:v25.0}") String metaGraphApiVersion) {
        this.metaAccessToken = safe(metaAccessToken);
        this.metaPhoneNumberId = safe(metaPhoneNumberId);
        this.metaGraphApiVersion = safe(metaGraphApiVersion).isBlank() ? "v25.0" : safe(metaGraphApiVersion);
    }

    // For tests
    public WhatsAppInvoiceAdapter(String metaAccessToken, String metaPhoneNumberId) {
        this(metaAccessToken, metaPhoneNumberId, "v25.0");
    }

    @Override
    public String channel() {
        return "whatsapp";
    }

    @Override
    public boolean isConfigured() {
        return !metaAccessToken.isBlank() && !metaPhoneNumberId.isBlank();
    }

    @Override
    public String configurationHint() {
        return "Configure Meta WhatsApp Cloud API access token and phone-number ID in middleware properties before sending invoices.";
    }

    @Override
    public Map<String, Object> send(InvoiceDeliveryContext context) {
        try {
            String mediaId = uploadMetaDocument(context.invoiceId(), context.pdfBytes());
            String responseBody = sendMetaDocument(context, mediaId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "sent");
            result.put("mediaAttached", true);
            result.put("provider", "meta");
            result.put("mediaId", mediaId);
            result.put("response", responseBody);
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to send WhatsApp message through Meta.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to send WhatsApp message through Meta.", ex);
        }
    }

    private String uploadMetaDocument(String invoiceId, byte[] pdfBytes) throws IOException, InterruptedException {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Invoice PDF is empty.");
        }
        String boundary = "----AASMetaBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, invoiceId, pdfBytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create(metaEndpoint("media")))
                .header("Authorization", "Bearer " + metaAccessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(metaFailureMessage("upload invoice PDF to Meta WhatsApp", response.body()));
        }
        JsonNode json = objectMapper.readTree(response.body());
        String mediaId = json.path("id").asText("");
        if (mediaId.isBlank()) {
            throw new IllegalStateException("Meta WhatsApp media upload did not return an id: " + response.body());
        }
        return mediaId;
    }

    private String sendMetaDocument(InvoiceDeliveryContext context, String mediaId) throws IOException, InterruptedException {
        String recipient = safe(context.recipient()).replace(" ", "").replace("+", "");
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", recipient,
                "type", "document",
                "document", Map.of(
                        "id", mediaId,
                        "filename", context.invoiceId() + ".pdf",
                        "caption", context.message()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(metaEndpoint("messages")))
                .header("Authorization", "Bearer " + metaAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(metaFailureMessage("send invoice PDF through Meta WhatsApp", response.body()));
        }
        return response.body();
    }

    private String metaFailureMessage(String action, String body) {
        String detail = extractMetaErrorDetail(body);
        if (detail.toLowerCase().contains("access denied")
                || detail.toLowerCase().contains("access token")
                || detail.toLowerCase().contains("authentication error")
                || detail.contains("#131005")) {
            return "Unable to " + action
                    + ". Meta rejected the access token or permissions. Generate a fresh WhatsApp Cloud API token and try again.";
        }
        return "Unable to " + action + (detail.isBlank() ? "." : ": " + detail);
    }

    private String extractMetaErrorDetail(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText("");
                String details = error.path("error_data").path("details").asText("");
                if (!message.isBlank() && !details.isBlank()) return message + ". " + details;
                if (!message.isBlank()) return message;
                if (!details.isBlank()) return details;
            }
        } catch (JsonProcessingException ignored) {}
        return body.trim();
    }

    private byte[] buildMultipartBody(String boundary, String invoiceId, byte[] pdfBytes) {
        String filename = safe(invoiceId).isBlank() ? "invoice.pdf" : invoiceId.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"messaging_product\"\r\n\r\n"
                + "whatsapp\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headerBytes.length + pdfBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(pdfBytes, 0, body, headerBytes.length, pdfBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + pdfBytes.length, footerBytes.length);
        return body;
    }

    private String metaEndpoint(String path) {
        return "https://graph.facebook.com/" + metaGraphApiVersion + "/" + metaPhoneNumberId + "/" + path;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
