package com.expensetracker.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Adds OWASP-recommended security response headers to every HTTP response.
 */
@Component
@Order(2)
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse response = (HttpServletResponse) res;

        // Prevent MIME-type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");

        // Legacy XSS filter (IE/older browsers)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Browsers ignore this over plain HTTP, so it is safe to send unconditionally behind a TLS-terminating proxy
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // Control referrer information
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Restrict browser features
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        // Do not cache API responses
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");

        chain.doFilter(req, res);
    }
}
