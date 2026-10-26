package com.expensetracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.expensetracker.model.OtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails via the SendGrid Web API v3.
 * Free tier: 100 emails/day. No custom domain needed - just verify a sender email.
 *
 * Setup:
 *  1. Sign up at sendgrid.com
 *  2. Settings -> Sender Authentication -> Single Sender Verification -> add your email
 *  3. Click the verification link sent to that email
 *  4. Settings -> API Keys -> Create API Key (Full Access) -> copy it
 *  5. Set in Railway: SENDGRID_API_KEY and SENDGRID_FROM (the verified email)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private static final String SENDGRID_URL = "https://api.sendgrid.com/v3/mail/send";

    private final ObjectMapper objectMapper;

    @Value("${app.sendgrid.api-key:}")
    private String apiKey;

    @Value("${app.sendgrid.from:noreply@expenseflow.com}")
    private String fromEmail;

    @Value("${app.sendgrid.from-name:ExpenseFlow}")
    private String fromName;

    @Value("${app.otp.dev-mode:false}")
    private boolean devMode;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Sends a verification code. Returns true if delivered to SendGrid, false otherwise. */
    public boolean sendOtpEmail(String toEmail, String otp, String name, OtpPurpose purpose) {
        if (devMode) log.debug("[OTP] dev-mode code for {} => {}", toEmail, otp);
        String subject = purpose == OtpPurpose.PASSWORD_RESET
                ? "Your ExpenseFlow Password Reset Code"
                : "Your ExpenseFlow Verification Code";
        String intro = purpose == OtpPurpose.PASSWORD_RESET
                ? "Your password reset code is:"
                : "Your sign-up verification code is:";
        return send(toEmail, name, subject, buildOtpHtml(name, otp, intro));
    }

    /** Sent instead of a code when someone starts sign-up with an email that already has an account. */
    public boolean sendAccountExistsEmail(String toEmail, String name) {
        return send(toEmail, name, "You already have an ExpenseFlow account", buildAccountExistsHtml(name));
    }

    private boolean send(String toEmail, String name, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SENDGRID_API_KEY not set - email to {} was not sent.", toEmail);
            return false;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "personalizations", List.of(Map.of("to", List.of(Map.of("email", toEmail, "name", name)))),
                    "from", Map.of("email", fromEmail, "name", fromName),
                    "subject", subject,
                    "content", List.of(Map.of("type", "text/html", "value", html))
            ));
        } catch (JsonProcessingException e) {
            log.error("Could not serialize email payload: {}", e.getMessage());
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SENDGRID_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            // SendGrid returns 202 Accepted on success
            if (response.statusCode() == 202) {
                log.info("Email '{}' sent to {} via SendGrid", subject, toEmail);
                return true;
            }
            log.error("SendGrid error {} sending to {}. Body: {}", response.statusCode(), toEmail, response.body());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Email delivery interrupted for {}", toEmail);
            return false;
        } catch (Exception e) {
            log.error("Email delivery failed for {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    private String buildOtpHtml(String name, String otp, String intro) {
        return "<div style='font-family:Arial,sans-serif;max-width:500px;margin:0 auto;"
                + "background:#f9fafb;padding:32px;border-radius:12px'>"
                + "<h2 style='color:#2563eb;margin:0 0 8px'>ExpenseFlow</h2>"
                + "<p style='color:#374151'>Hi " + HtmlUtils.htmlEscape(name) + ",</p>"
                + "<p style='color:#374151'>" + HtmlUtils.htmlEscape(intro) + "</p>"
                + "<div style='font-size:38px;font-weight:700;letter-spacing:14px;color:#1e40af;"
                + "background:#dbeafe;padding:20px;border-radius:8px;text-align:center;margin:20px 0'>"
                + HtmlUtils.htmlEscape(otp) + "</div>"
                + "<p style='color:#6b7280;font-size:14px'>This code expires in "
                + "<strong>5 minutes</strong>. Never share it with anyone.</p>"
                + "<hr style='border:none;border-top:1px solid #e5e7eb;margin:24px 0'>"
                + "<p style='color:#9ca3af;font-size:12px'>"
                + "If you did not request this, you can safely ignore this email.</p></div>";
    }

    private String buildAccountExistsHtml(String name) {
        return "<div style='font-family:Arial,sans-serif;max-width:500px;margin:0 auto;"
                + "background:#f9fafb;padding:32px;border-radius:12px'>"
                + "<h2 style='color:#2563eb;margin:0 0 8px'>ExpenseFlow</h2>"
                + "<p style='color:#374151'>Hi " + HtmlUtils.htmlEscape(name) + ",</p>"
                + "<p style='color:#374151'>Someone tried to create an ExpenseFlow account with this "
                + "email address, but you already have one. You can sign in with your existing password.</p>"
                + "<hr style='border:none;border-top:1px solid #e5e7eb;margin:24px 0'>"
                + "<p style='color:#9ca3af;font-size:12px'>"
                + "If this wasn't you, you can safely ignore this email.</p></div>";
    }
}
