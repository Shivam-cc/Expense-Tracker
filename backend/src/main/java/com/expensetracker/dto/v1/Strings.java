package com.expensetracker.dto.v1;

import java.util.Locale;

/** Input normalisation shared by v1 request DTO setters. */
final class Strings {

    static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    private Strings() {}

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    /** Client ids are compared as strings, so they are stored in one canonical (lower-case) form. */
    static String normalizeClientId(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
