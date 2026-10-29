package com.expensetracker.dto.v1;

import java.time.Instant;
import java.util.List;

/** One page of a change feed. {@code nextCursor} is null when {@code hasMore} is false. */
public record ChangesV1Response<T>(List<T> items, String nextCursor, boolean hasMore, Instant serverTime) {
}
