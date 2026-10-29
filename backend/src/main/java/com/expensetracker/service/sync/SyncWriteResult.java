package com.expensetracker.service.sync;

/** Result of an idempotent create: {@code created} is false when an existing record was returned. */
public record SyncWriteResult<T>(T body, boolean created) {
}
