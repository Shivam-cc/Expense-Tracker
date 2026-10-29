package com.expensetracker.service.sync;

import com.expensetracker.exception.BadRequestException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Time helpers for the sync API. Everything is UTC and millisecond-precise, matching what MongoDB stores. */
final class SyncTime {

    private SyncTime() {
    }

    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    static Instant truncate(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    static Instant fromLocal(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    static Instant parse(String value, String name) {
        try {
            return Instant.parse(value).truncatedTo(ChronoUnit.MILLIS);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(name + " must be an ISO-8601 UTC instant, for example 2026-10-29T10:00:00Z");
        }
    }
}
