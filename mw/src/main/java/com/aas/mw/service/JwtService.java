package com.aas.mw.service;

import com.aas.mw.config.JwtProperties;
import com.aas.mw.config.AppRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String subject, AppRole role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationSeconds());
        SecretKey key = signingKey();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role == null ? AppRole.ADMIN.asKey() : role.asKey())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String parseSubject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String parseRole(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object role = claims.get("role");
        return role == null ? AppRole.ADMIN.asKey() : role.toString();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
