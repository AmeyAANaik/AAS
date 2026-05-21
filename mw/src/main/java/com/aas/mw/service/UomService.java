package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UomService {

    private static final String UOM = "UOM";

    private final ErpNextClient erpNextClient;
    private final long cacheTtlMs;

    private volatile CacheEntry cache = null;

    public UomService(
            ErpNextClient erpNextClient,
            @Value("${app.uoms.cache-ttl-seconds:600}") long cacheTtlSeconds) {
        this.erpNextClient = erpNextClient;
        this.cacheTtlMs = Duration.ofSeconds(Math.max(cacheTtlSeconds, 0)).toMillis();
    }

    public List<Map<String, Object>> listUoms(boolean refresh) {
        if (!refresh && cacheTtlMs > 0) {
            CacheEntry entry = cache;
            if (entry != null && entry.isFresh(cacheTtlMs)) {
                return entry.uoms();
            }
        }
        synchronized (this) {
            if (!refresh && cacheTtlMs > 0) {
                CacheEntry entry = cache;
                if (entry != null && entry.isFresh(cacheTtlMs)) {
                    return entry.uoms();
                }
            }
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"uom_name\",\"must_be_whole_number\"]");
            params.put("limit_page_length", 1000);
            List<Map<String, Object>> uoms = erpNextClient.listResources(UOM, params);
            if (cacheTtlMs > 0) {
                cache = new CacheEntry(System.currentTimeMillis(), uoms);
            }
            return uoms;
        }
    }

    public String normalizeUom(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return "";
        }
        return switch (normalized) {
            case "KG", "KGS", "KILOGRAM", "KILOGRAMS" -> "Kg";
            case "GM", "GMS", "GRAM", "GRAMS" -> "Gram";
            case "LTR", "LITRE", "LITRES", "LITER", "LITERS" -> "Litre";
            case "PCS", "PC", "PIECE", "PIECES", "NOS", "NO", "NUMBER", "NUMBERS", "UNIT", "UNITS" -> "Nos";
            case "TIN", "TINS" -> "Tin";
            case "PACK", "PACKS", "PKT", "PKTS", "PACKET", "PACKETS" -> "Pack";
            default -> normalized.substring(0, 1) + normalized.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    public void ensureUomExists(String rawUom) {
        String uomName = normalizeUom(rawUom);
        if (uomName.isBlank()) {
            return;
        }
        if (resourceExists(UOM, uomName)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("uom_name", uomName);
        payload.put("must_be_whole_number", "Nos".equalsIgnoreCase(uomName) ? 1 : 0);
        erpNextClient.createResource(UOM, payload);
        // Invalidate cache so newly created UOM appears on the next list call.
        cache = null;
    }

    private boolean resourceExists(String doctype, String name) {
        if (doctype == null || doctype.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> resource = unwrapResource(erpNextClient.getResource(doctype, name));
            return !resource.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> resource) {
        if (resource == null) {
            return Map.of();
        }
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return resource;
    }

    private record CacheEntry(long createdAtMs, List<Map<String, Object>> uoms) {
        boolean isFresh(long ttlMs) {
            return ttlMs <= 0 || (System.currentTimeMillis() - createdAtMs) <= ttlMs;
        }
    }
}

