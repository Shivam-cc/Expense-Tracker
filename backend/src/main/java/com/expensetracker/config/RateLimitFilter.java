package com.expensetracker.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-IP fixed-window rate limiter (in-memory, single instance).
 * <ul>
 *   <li>Auth endpoints ({@code /api/auth/**}): {@code app.rate-limit.auth-max} requests / minute</li>
 *   <li>All other endpoints: {@code app.rate-limit.general-max} requests / minute</li>
 * </ul>
 * Responds with HTTP 429 when a bucket is exhausted.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final long WINDOW_MS = 60_000L;

    private record Window(AtomicInteger count, long resetAt) {}

    private final Map<String, Window> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Window> generalBuckets = new ConcurrentHashMap<>();

    private final ClientIpResolver clientIpResolver;
    private final int authMax;
    private final int generalMax;

    public RateLimitFilter(
            ClientIpResolver clientIpResolver,
            @Value("${app.rate-limit.auth-max:15}") int authMax,
            @Value("${app.rate-limit.general-max:300}") int generalMax) {
        this.clientIpResolver = clientIpResolver;
        this.authMax = authMax;
        this.generalMax = generalMax;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String ip   = clientIpResolver.resolve(request);
        String path = request.getRequestURI();

        boolean isAuth = path.startsWith("/api/auth/");
        Map<String, Window> buckets = isAuth ? authBuckets : generalBuckets;
        int max = isAuth ? authMax : generalMax;

        if (exceeded(ip, buckets, max)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"message\":\"Too many requests. Please slow down.\",\"status\":429}");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean exceeded(String key, Map<String, Window> buckets, int max) {
        long now = Instant.now().toEpochMilli();
        Window w = buckets.compute(key, (k, existing) -> {
            if (existing == null || now > existing.resetAt()) {
                return new Window(new AtomicInteger(1), now + WINDOW_MS);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return w.count().get() > max;
    }

    /** Drops windows that have already reset so the maps cannot grow without bound. */
    @Scheduled(fixedDelay = 60_000L)
    void evictExpired() {
        long now = Instant.now().toEpochMilli();
        authBuckets.values().removeIf(w -> now > w.resetAt());
        generalBuckets.values().removeIf(w -> now > w.resetAt());
    }
}
