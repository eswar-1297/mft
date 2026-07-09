package com.cloudfuze.mft.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies short-lived HS256 access tokens. The token asserts the caller's
 * identity, tenant, and role; the tenant claim is what lets us trust {@code TenantContext}
 * on every subsequent request without a database lookup.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final long ttlSeconds;

    public JwtService(
            @Value("${mft.jwt.secret}") String secretRef,
            @Value("${mft.jwt.issuer}") String issuer,
            @Value("${mft.jwt.access-token-ttl-minutes}") long ttlMinutes,
            com.cloudfuze.mft.crypto.SecretResolver secrets) {
        byte[] secret = Base64.getDecoder().decode(secrets.resolve(secretRef));
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "mft.jwt.secret must decode to at least 32 bytes (256 bits) for HS256. "
                            + "Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = issuer;
        this.ttlSeconds = ttlMinutes * 60;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claim("tenant", user.getTenantId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Verifies signature + expiry and returns the principal, or throws if invalid. */
    public AuthPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("tenant", String.class),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class)));
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }
}
