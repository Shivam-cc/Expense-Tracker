package com.expensetracker.dto;

/** Input normalisation shared by request DTO setters. */
final class Strings {

    private Strings() {}

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
