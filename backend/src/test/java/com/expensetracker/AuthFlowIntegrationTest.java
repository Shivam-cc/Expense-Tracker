package com.expensetracker;

import com.expensetracker.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthFlowIntegrationTest extends IntegrationTestBase {

    @Test
    void sendOtpGivesTheSameResponseForRegisteredAndUnknownEmails() throws Exception {
        String existing = uniqueEmail();
        register(existing, PASSWORD);

        JsonNode forExisting = send(jsonPost("/api/auth/send-otp", Map.of("name", "Someone", "email", existing)), 200);
        JsonNode forNew = send(jsonPost("/api/auth/send-otp", Map.of("name", "Someone", "email", uniqueEmail())), 200);

        assertThat(forExisting.get("message").asText()).isEqualTo(forNew.get("message").asText());
        assertThat(forExisting.fieldNames()).toIterable().containsExactlyInAnyOrder("message", "devOtp");
    }

    @Test
    void registerWithWrongOtpIsRejected() throws Exception {
        String email = uniqueEmail();
        send(jsonPost("/api/auth/send-otp", Map.of("name", "Test User", "email", email)), 200);

        send(jsonPost("/api/auth/register", Map.of(
                "name", "Test User", "email", email, "password", PASSWORD, "otp", "000000")), 400);
    }

    @Test
    void registerEnforcesPasswordPolicy() throws Exception {
        String email = uniqueEmail();
        JsonNode otp = send(jsonPost("/api/auth/send-otp", Map.of("name", "Test User", "email", email)), 200);

        for (String weak : new String[]{"short1", "allletters", "12345678"}) {
            send(jsonPost("/api/auth/register", Map.of(
                    "name", "Test User", "email", email, "password", weak, "otp", otp.get("devOtp").asText())), 400);
        }
    }

    @Test
    void loginReturnsTokenPairAndAccessTokenWorks() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        JsonNode auth = login(email, PASSWORD, 200);
        assertThat(auth.get("refreshToken").asText()).isNotBlank();

        JsonNode me = send(get("/api/users/me").header("Authorization", bearer(auth)), 200);
        assertThat(me.get("email").asText()).isEqualTo(email);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameError() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        JsonNode wrongPassword = login(email, "Wrong-pass1", 401);
        JsonNode unknown = login(uniqueEmail(), PASSWORD, 401);

        assertThat(wrongPassword.get("message").asText()).isEqualTo(unknown.get("message").asText());
    }

    @Test
    void accountLocksAfterFiveFailuresEvenWithTheCorrectPassword() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);

        for (int i = 0; i < 5; i++) {
            login(email, "Wrong-pass1", 401);
        }

        JsonNode locked = login(email, PASSWORD, 401);
        assertThat(locked.get("message").asText()).contains("Invalid email or password");
    }

    @Test
    void protectedEndpointsReturnJson401ForMissingGarbageAndTamperedTokens() throws Exception {
        send(get("/api/expenses"), 401);
        send(get("/api/expenses").header("Authorization", "Bearer not-a-jwt"), 401);

        String email = uniqueEmail();
        JsonNode auth = register(email, PASSWORD);
        String token = auth.get("token").asText();
        String tampered = token.substring(0, token.length() - 6) + "AAAAAA";
        JsonNode body = send(get("/api/expenses").header("Authorization", "Bearer " + tampered), 401);
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("message").asText()).isNotBlank();
    }

    @Test
    void staleBearerTokenDoesNotBreakPublicEndpoints() throws Exception {
        send(jsonPost("/api/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD))
                .header("Authorization", "Bearer stale.token.value"), 401);
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheWholeFamily() throws Exception {
        String email = uniqueEmail();
        JsonNode first = register(email, PASSWORD);

        JsonNode second = send(jsonPost("/api/auth/refresh", Map.of("refreshToken", first.get("refreshToken").asText())), 200);
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first.get("refreshToken").asText());

        // Replaying the already-used token is treated as theft: it fails and kills the newer token too.
        send(jsonPost("/api/auth/refresh", Map.of("refreshToken", first.get("refreshToken").asText())), 401);
        send(jsonPost("/api/auth/refresh", Map.of("refreshToken", second.get("refreshToken").asText())), 401);
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        String refresh = auth.get("refreshToken").asText();

        send(jsonPost("/api/auth/logout", Map.of("refreshToken", refresh)), 204);
        send(jsonPost("/api/auth/refresh", Map.of("refreshToken", refresh)), 401);
    }

    @Test
    void changingPasswordInvalidatesOtherSessionsButKeepsTheCallerSignedIn() throws Exception {
        String email = uniqueEmail();
        JsonNode oldSession = register(email, PASSWORD);

        JsonNode newSession = send(jsonPost("/api/users/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "NewPassw0rd9"))
                .header("Authorization", bearer(oldSession)), 200);

        send(get("/api/users/me").header("Authorization", bearer(oldSession)), 401);
        send(jsonPost("/api/auth/refresh", Map.of("refreshToken", oldSession.get("refreshToken").asText())), 401);
        send(get("/api/users/me").header("Authorization", bearer(newSession)), 200);
        login(email, PASSWORD, 401);
        login(email, "NewPassw0rd9", 200);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        send(jsonPost("/api/users/me/password", Map.of("currentPassword", "Nope-nope1", "newPassword", "NewPassw0rd9"))
                .header("Authorization", bearer(auth)), 400);
    }

    @Test
    void logoutAllInvalidatesAccessAndRefreshTokens() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        send(post("/api/users/me/logout-all").header("Authorization", bearer(auth)), 204);

        send(get("/api/users/me").header("Authorization", bearer(auth)), 401);
        send(jsonPost("/api/auth/refresh", Map.of("refreshToken", auth.get("refreshToken").asText())), 401);
    }

    @Test
    void forgotAndResetPasswordFlow() throws Exception {
        String email = uniqueEmail();
        JsonNode session = register(email, PASSWORD);

        JsonNode forgot = send(jsonPost("/api/auth/forgot-password", Map.of("email", email)), 200);
        send(jsonPost("/api/auth/reset-password", Map.of(
                "email", email, "otp", forgot.get("devOtp").asText(), "newPassword", "Reset-pass9")), 200);

        login(email, PASSWORD, 401);
        login(email, "Reset-pass9", 200);
        send(get("/api/users/me").header("Authorization", bearer(session)), 401);
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherTheEmailExists() throws Exception {
        JsonNode unknown = send(jsonPost("/api/auth/forgot-password", Map.of("email", uniqueEmail())), 200);
        String email = uniqueEmail();
        register(email, PASSWORD);
        JsonNode known = send(jsonPost("/api/auth/forgot-password", Map.of("email", email)), 200);

        assertThat(unknown.get("message").asText()).isEqualTo(known.get("message").asText());
    }

    @Test
    void resetPasswordRejectsWrongOtp() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        send(jsonPost("/api/auth/forgot-password", Map.of("email", email)), 200);

        send(jsonPost("/api/auth/reset-password", Map.of(
                "email", email, "otp", "000000", "newPassword", "Reset-pass9")), 400);
    }

    @Test
    void profileCanBeUpdated() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode updated = send(patch("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"name\":\"New Name\"}"), 200);

        assertThat(updated.get("name").asText()).isEqualTo("New Name");
    }

    @Test
    void deleteAccountRequiresPasswordAndRemovesTheUser() throws Exception {
        String email = uniqueEmail();
        JsonNode auth = register(email, PASSWORD);

        send(delete("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"password\":\"Wrong-pass1\"}"), 400);
        send(delete("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"password\":\"" + PASSWORD + "\"}"), 204);

        login(email, PASSWORD, 401);
        send(get("/api/users/me").header("Authorization", bearer(auth)), 401);
    }

    @Test
    void legacyBcryptOfSha256HashStillVerifies() throws Exception {
        // Existing production rows are bcrypt(sha256hex(password)); the new scheme must accept them.
        var encoder = new com.expensetracker.config.Sha256BcryptPasswordEncoder();
        String sha = com.expensetracker.config.Sha256BcryptPasswordEncoder.sha256Hex("hunter2pass");
        String legacy = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(sha);

        assertThat(encoder.matches("hunter2pass", legacy)).isTrue();
        assertThat(encoder.matches("hunter3pass", legacy)).isFalse();
    }
}
