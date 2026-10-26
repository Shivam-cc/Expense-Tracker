package com.expensetracker.service;

import com.expensetracker.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/** Issues and validates short-lived access tokens. Refresh tokens are opaque and handled by {@link RefreshTokenService}. */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;
    static final String TOKEN_VERSION_CLAIM = "tv";

    @Value("${app.jwt.secret:}")
    private String secretKey;

    @Value("${app.jwt.access-expiration-ms:900000}")
    private long accessTokenTtlMs;

    private SecretKey signInKey;

    @PostConstruct
    void initKey() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. Provide a base64-encoded secret of at least 256 bits.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET must be valid base64.");
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 256 bits (32 bytes).");
        }
        signInKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenTtlMs))
                .signWith(signInKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMs / 1000;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * Valid when the signature and expiry check out (parsing throws otherwise), the subject matches,
     * and the token version still equals the user's current one.
     */
    public boolean isTokenValid(String token, User user) {
        Claims claims = extractAllClaims(token);
        Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        return user.getUsername().equals(claims.getSubject())
                && tokenVersion != null
                && tokenVersion == user.getTokenVersion();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
