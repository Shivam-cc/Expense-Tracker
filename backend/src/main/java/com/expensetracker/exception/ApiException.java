package com.expensetracker.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for expected, client-facing errors. The message is safe to return to the caller. */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
