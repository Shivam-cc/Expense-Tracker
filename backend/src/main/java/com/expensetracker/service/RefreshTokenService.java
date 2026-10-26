package com.expensetracker.service;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.exception.UnauthorizedException;
import com.expensetracker.model.RefreshToken;
import com.expensetracker.model.User;
import com.expensetracker.repository.RefreshTokenRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.config.Sha256BcryptPasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens. Each token is single-use: refreshing revokes it and issues a successor
 * in the same family. Presenting an already-revoked token means it was stolen or replayed, so the whole
 * family is revoked.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    static final int REFRESH_TTL_DAYS = 30;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SecureRandom random = new SecureRandom();

    /** Starts a new session (new token family) for a freshly authenticated user. */
    @Transactional
    public AuthResponse startSession(User user) {
        return issue(user, UUID.randomUUID().toString()).response();
    }

    // noRollbackFor: family revocation on reuse detection must be committed even though we throw.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse rotate(String rawToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        LocalDateTime now = LocalDateTime.now();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new UnauthorizedException("Invalid refresh token");
        }
        if (now.isAfter(current.getExpiresAt())) {
            throw new UnauthorizedException("Refresh token expired");
        }

        User user = userRepository.findById(current.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        Issued next = issue(user, current.getFamilyId());
        current.setRevokedAt(now);
        current.setReplacedBy(next.tokenId());
        refreshTokenRepository.save(current);
        return next.response();
    }

    /** Revokes the session the given token belongs to. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token ->
                refreshTokenRepository.revokeFamily(token.getFamilyId(), LocalDateTime.now()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    @Scheduled(fixedDelay = 3_600_000L)
    @Transactional
    public void evictExpired() {
        refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(7));
    }

    private record Issued(Long tokenId, AuthResponse response) {}

    private Issued issue(User user, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        LocalDateTime now = LocalDateTime.now();
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(raw))
                .familyId(familyId)
                .createdAt(now)
                .expiresAt(now.plusDays(REFRESH_TTL_DAYS))
                .build());

        AuthResponse response = AuthResponse.builder()
                .token(jwtService.generateAccessToken(user))
                .refreshToken(raw)
                .expiresIn(jwtService.getAccessTokenTtlSeconds())
                .name(user.getName())
                .email(user.getEmail())
                .build();
        return new Issued(saved.getId(), response);
    }

    // The token is 256 bits of randomness, so a plain unsalted SHA-256 is enough to protect it at rest.
    private static String hash(String rawToken) {
        return Sha256BcryptPasswordEncoder.sha256Hex(rawToken);
    }
}
