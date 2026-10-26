package com.expensetracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns a JSON 401 for unauthenticated requests to protected endpoints. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String REASON_ATTRIBUTE = "auth.failure.reason";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(REASON_ATTRIBUTE);
        JsonErrorWriter.write(request, response, 401, reason != null ? reason.toString() : "Authentication required");
    }
}
