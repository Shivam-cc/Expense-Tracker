package com.expensetracker.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class UnauthorizedException extends ApiException {

    /** Returned when the refresh token belongs to an account that has been deleted. */
    public static final String ACCOUNT_DELETED = "account_deleted";

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public UnauthorizedException(String errorCode, String message) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message, Map.of());
    }
}
