package com.expensetracker.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP without trusting spoofable headers.
 * Each trusted proxy appends the address it saw to X-Forwarded-For, so with N trusted hops
 * the real client is the Nth entry from the right; anything further left is client-supplied.
 */
@Component
public class ClientIpResolver {

    private final int trustedHops;

    public ClientIpResolver(@Value("${app.proxy.trusted-hops:1}") int trustedHops) {
        this.trustedHops = trustedHops;
    }

    public String resolve(HttpServletRequest request) {
        if (trustedHops > 0) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                int index = parts.length - trustedHops;
                String candidate = parts[Math.max(index, 0)].trim();
                if (!candidate.isEmpty()) return candidate;
            }
        }
        return request.getRemoteAddr();
    }
}
