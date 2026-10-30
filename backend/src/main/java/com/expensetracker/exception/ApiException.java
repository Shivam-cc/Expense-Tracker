package com.expensetracker.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Base class for expected, client-facing errors. The message is safe to return to the caller.
 * An optional {@code errorCode} becomes the response's {@code error} field, and {@code details} are merged into the body.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final Map<String, Object> details;

    protected ApiException(HttpStatus status, String message) {
        this(status, null, message, Map.of());
    }

    protected ApiException(HttpStatus status, String errorCode, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }
}
