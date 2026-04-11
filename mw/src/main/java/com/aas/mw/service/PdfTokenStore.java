package com.aas.mw.service;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Stores short-lived tokens that map to invoice IDs so Twilio can fetch
 * the PDF from a public URL without requiring JWT authentication.
 * Tokens expire after 5 minutes.
 */
@Component
public class PdfTokenStore {

    private static final long TTL_SECONDS = 300;

    private record Entry(String invoiceId, Instant expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String createToken(String invoiceId) {
        evictExpired();
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new Entry(invoiceId, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    public String resolveInvoiceId(String token) {
        Entry entry = store.get(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            store.remove(token);
            return null;
        }
        store.remove(token); // one-time use
        return entry.invoiceId();
    }

    private void evictExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Entry>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            if (now.isAfter(it.next().getValue().expiresAt())) {
                it.remove();
            }
        }
    }
}
