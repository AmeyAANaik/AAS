package com.aas.mw.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TwilioWhatsAppInvoiceAdapter implements InvoiceChannelAdapter {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;
    private final String mediaUrlBase;

    public TwilioWhatsAppInvoiceAdapter(
            @Value("${app.invoice-delivery.whatsapp.account-sid:}") String accountSid,
            @Value("${app.invoice-delivery.whatsapp.auth-token:}") String authToken,
            @Value("${app.invoice-delivery.whatsapp.from-number:}") String fromNumber,
            @Value("${app.invoice-delivery.whatsapp.media-url-base:}") String mediaUrlBase) {
        this.accountSid = safe(accountSid);
        this.authToken = safe(authToken);
        this.fromNumber = safe(fromNumber);
        this.mediaUrlBase = safe(mediaUrlBase);
    }

    @Override
    public String channel() {
        return "whatsapp";
    }

    @Override
    public boolean isConfigured() {
        return !accountSid.isBlank() && !authToken.isBlank() && !fromNumber.isBlank();
    }

    @Override
    public String configurationHint() {
        return "Configure Twilio WhatsApp sandbox or production credentials in middleware properties before sending invoices.";
    }

    @Override
    public Map<String, Object> send(InvoiceDeliveryContext context) {
        try {
            String endpoint = "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json";
            StringBuilder body = new StringBuilder();
            append(body, "To", "whatsapp:" + normalizeRecipient(context.recipient()));
            append(body, "From", "whatsapp:" + fromNumber);
            append(body, "Body", context.message());
            String mediaUrl = resolveMediaUrl(context.invoiceId());
            if (!mediaUrl.isBlank()) {
                append(body, "MediaUrl", mediaUrl);
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Authorization", "Basic " + basicAuth())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Twilio WhatsApp send failed: " + response.body());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "queued");
            result.put("mediaAttached", !mediaUrl.isBlank());
            result.put("provider", "twilio");
            result.put("response", response.body());
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Unable to send WhatsApp message through Twilio.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to send WhatsApp message through Twilio.", ex);
        }
    }

    private String resolveMediaUrl(String invoiceId) {
        if (mediaUrlBase.isBlank()) {
            return "";
        }
        String base = mediaUrlBase.endsWith("/") ? mediaUrlBase.substring(0, mediaUrlBase.length() - 1) : mediaUrlBase;
        return base + "/api/public/invoices/" + urlEncode(invoiceId) + "/pdf";
    }

    private void append(StringBuilder builder, String key, String value) {
        if (builder.length() > 0) {
            builder.append("&");
        }
        builder.append(urlEncode(key)).append("=").append(urlEncode(value));
    }

    private String normalizeRecipient(String value) {
        String trimmed = safe(value).replace(" ", "");
        return trimmed.startsWith("+") ? trimmed : "+" + trimmed;
    }

    private String basicAuth() {
        return Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
