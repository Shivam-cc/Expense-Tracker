package com.expensetracker.service;

import com.expensetracker.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = base64Of(32);
    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("another-secret-another-secret-12".getBytes(StandardCharsets.UTF_8));

    private static String base64Of(int bytes) {
        return Base64.getEncoder().encodeToString("x".repeat(bytes).getBytes(StandardCharsets.UTF_8));
    }

    private static JwtService service(String secret, long ttlMs) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "accessTokenTtlMs", ttlMs);
        service.initKey();
        return service;
    }

    private static User user(int tokenVersion) {
        return User.builder().email("a@example.com").name("A").password("x").tokenVersion(tokenVersion).build();
    }

    @Test
    void startupFailsWithoutASecret() {
        assertThatThrownBy(() -> service("", 1000)).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> service(null, 1000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("   ", 1000)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupFailsForShortOrNonBase64Secrets() {
        assertThatThrownBy(() -> service(base64Of(31), 1000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("256 bits");
        assertThatThrownBy(() -> service("***not base64***", 1000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
    }

    @Test
    void issuedTokenValidatesForTheSameUserAndVersion() {
        JwtService jwt = service(SECRET, 60_000);
        User user = user(3);

        String token = jwt.generateAccessToken(user);

        assertThat(jwt.extractUsername(token)).isEqualTo("a@example.com");
        assertThat(jwt.isTokenValid(token, user)).isTrue();
    }

    @Test
    void bumpingTheTokenVersionInvalidatesExistingTokens() {
        JwtService jwt = service(SECRET, 60_000);
        String token = jwt.generateAccessToken(user(1));

        assertThat(jwt.isTokenValid(token, user(2))).isFalse();
    }

    @Test
    void tokenForADifferentSubjectIsNotValid() {
        JwtService jwt = service(SECRET, 60_000);
        String token = jwt.generateAccessToken(user(0));
        User other = User.builder().email("b@example.com").name("B").password("x").tokenVersion(0).build();

        assertThat(jwt.isTokenValid(token, other)).isFalse();
    }

    @Test
    void expiredTokensAreRejected() {
        JwtService jwt = service(SECRET, -1000);
        String token = jwt.generateAccessToken(user(0));

        assertThatThrownBy(() -> jwt.isTokenValid(token, user(0))).isInstanceOf(JwtException.class);
    }

    @Test
    void tokensSignedWithAnotherKeyOrTamperedAreRejected() {
        String foreign = service(OTHER_SECRET, 60_000).generateAccessToken(user(0));
        JwtService jwt = service(SECRET, 60_000);
        assertThatThrownBy(() -> jwt.extractUsername(foreign)).isInstanceOf(JwtException.class);

        String token = jwt.generateAccessToken(user(0));
        String tampered = token.substring(0, token.length() - 6) + "AAAAAA";
        assertThatThrownBy(() -> jwt.extractUsername(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void accessTokenLifetimeIsReportedInSeconds() {
        assertThat(service(SECRET, 900_000).getAccessTokenTtlSeconds()).isEqualTo(900);
    }
}
