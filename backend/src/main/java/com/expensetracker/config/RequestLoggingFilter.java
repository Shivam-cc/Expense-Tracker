package com.expensetracker.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Logs every inbound request: method, path, client IP, and response status/time.
 */
@Component
@Order(3)
@Slf4j
@RequiredArgsConstructor
public class RequestLoggingFilter implements Filter {

    private final ClientIpResolver clientIpResolver;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        long start = System.currentTimeMillis();
        String ip  = clientIpResolver.resolve(request);

        try {
            chain.doFilter(req, res);
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("{} {} | IP: {} | {} | {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    ip,
                    response.getStatus(),
                    elapsed);
        }
    }
}
