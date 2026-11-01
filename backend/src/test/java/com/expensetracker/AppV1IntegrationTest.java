package com.expensetracker;

import com.expensetracker.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class AppV1IntegrationTest extends IntegrationTestBase {

    @Test
    void minVersionIsPublicAndReturnsTheConfiguredDefaults() throws Exception {
        JsonNode body = send(get("/api/v1/app/min-version"), 200);

        assertThat(body.get("android").asText()).isEqualTo("1.0.0");
        assertThat(body.get("ios").asText()).isEqualTo("1.0.0");
        assertThat(body.size()).isEqualTo(2);
    }
}
