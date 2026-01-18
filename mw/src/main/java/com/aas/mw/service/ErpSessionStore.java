package com.aas.mw.service;

import com.aas.mw.config.JwtProperties;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class ErpSessionStore {

    public static final String REQUEST_ATTR = "erpSessionCookie";

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final long ttlSeconds;

    public ErpSessionStore(JwtProperties properties) {
        this.ttlSeconds = properties.getExpirationSeconds();
    }

    public void put(String username, String sessionCookie) {
        if (username == null || sessionCookie == null) {
            return;
        }
        sessions.put(username, new SessionEntry(sessionCookie, Instant.now()));
    }

    public Optional<String> get(String username) {
        if (username == null) {
            return Optional.empty();
        }
        SessionEntry entry = sessions.get(username);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.createdAt().plusSeconds(ttlSeconds))) {
            sessions.remove(username);
            return Optional.empty();
        }
        return Optional.of(entry.sessionCookie());
    }

    private record SessionEntry(String sessionCookie, Instant createdAt) {}
}
