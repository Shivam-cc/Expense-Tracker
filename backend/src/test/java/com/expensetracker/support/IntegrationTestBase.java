package com.expensetracker.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against the real application context on in-memory H2 (local profile). The local profile
 * enables OTP dev mode, so {@code send-otp} returns the code in {@code devOtp}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Passw0rd123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected MockHttpServletRequestBuilder jsonPost(String url, Object body) throws Exception {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected JsonNode send(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    /** Runs the full send-otp + register flow and returns the auth response (token, refreshToken, ...). */
    protected JsonNode register(String email, String password) throws Exception {
        JsonNode otp = send(jsonPost("/api/auth/send-otp", Map.of("name", "Test User", "email", email)), 200);
        return send(jsonPost("/api/auth/register", Map.of(
                "name", "Test User", "email", email, "password", password,
                "otp", otp.get("devOtp").asText())), 200);
    }

    protected JsonNode login(String email, String password, int expectedStatus) throws Exception {
        return send(jsonPost("/api/auth/login", Map.of("email", email, "password", password)), expectedStatus);
    }

    protected static String bearer(JsonNode auth) {
        return "Bearer " + auth.get("token").asText();
    }
}
