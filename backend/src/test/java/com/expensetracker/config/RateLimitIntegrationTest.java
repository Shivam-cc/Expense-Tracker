package com.expensetracker.config;

import com.expensetracker.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {"app.rate-limit.auth-max=5", "app.rate-limit.general-max=100000"})
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Test
    void authEndpointsAreLimitedPerClientAndRespondWithJson429() throws Exception {
        String email = uniqueEmail();
        int limited = 0;
        JsonNode lastLimited = null;
        for (int i = 0; i < 8; i++) {
            // A spoofed X-Forwarded-For must not buy a fresh bucket.
            var request = jsonPost("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                    .header("X-Forwarded-For", "203.0.113." + i);
            var result = mvc.perform(request).andReturn().getResponse();
            if (result.getStatus() == 429) {
                limited++;
                lastLimited = objectMapper.readTree(result.getContentAsString());
            }
        }

        assertThat(limited).isEqualTo(3);
        assertThat(lastLimited.get("status").asInt()).isEqualTo(429);
        assertThat(lastLimited.get("path").asText()).isEqualTo("/api/auth/login");
        assertThat(lastLimited.get("message").asText()).isNotBlank();
    }
}
