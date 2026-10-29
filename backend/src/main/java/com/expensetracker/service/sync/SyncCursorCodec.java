package com.expensetracker.service.sync;

import com.expensetracker.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

/** Opaque pagination cursor: base64url of "&lt;updatedAt epoch millis&gt;|&lt;id&gt;". */
public final class SyncCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private SyncCursorCodec() {
    }

    /** Sort position of the last row returned on a page. */
    public record Position(Instant updatedAt, long id) {
    }

    public static String encode(Position position) {
        String raw = position.updatedAt().toEpochMilli() + "|" + position.id();
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Position decode(String cursor) {
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new IllegalArgumentException("Missing separator");
            }
            long millis = Long.parseLong(raw.substring(0, separator));
            long id = Long.parseLong(raw.substring(separator + 1));
            return new Position(Instant.ofEpochMilli(millis), id);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new BadRequestException("Invalid cursor");
        }
    }
}
