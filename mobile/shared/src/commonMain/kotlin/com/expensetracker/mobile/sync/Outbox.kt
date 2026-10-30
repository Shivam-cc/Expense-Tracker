package com.expensetracker.mobile.sync

import com.expensetracker.mobile.db.ExpenseFlowDatabase

enum class OutboxEntity { EXPENSE, CATEGORY }

enum class OutboxOperation { CREATE, UPDATE, DELETE }

/** An outbox item the server rejected. It stays queued until the user retries or edits it. */
data class FailedOutboxItem(
    val entity: OutboxEntity,
    val clientId: String,
    val operation: OutboxOperation,
    val error: String?,
)

/**
 * Records a local change in the outbox, keeping at most one row per client id.
 * Must be called inside a database transaction together with the entity write.
 *
 * A pending CREATE has never been pushed successfully (a successful push deletes its row),
 * so a DELETE on top of it drops both: the server never saw the record.
 * A new local change to a failed row requeues it, so the user's edit is retried.
 */
internal fun ExpenseFlowDatabase.enqueue(
    entity: OutboxEntity,
    clientId: String,
    operation: OutboxOperation,
) {
    val queries = expenseFlowQueries
    val existing = queries.selectOutboxForClient(entity.name, clientId).executeAsOneOrNull()
    if (existing == null) {
        queries.insertOutbox(entity.name, clientId, operation.name)
        return
    }
    val current = OutboxOperation.valueOf(existing.operation)
    val next: OutboxOperation? = when (operation) {
        OutboxOperation.CREATE, OutboxOperation.UPDATE -> current
        OutboxOperation.DELETE ->
            if (current == OutboxOperation.CREATE) null else OutboxOperation.DELETE
    }
    if (next == null) {
        queries.deleteOutbox(existing.seq)
        return
    }
    if (next != current) queries.updateOutboxOperation(next.name, existing.seq)
    if (existing.failed == 1L) queries.requeueOutbox(existing.seq)
}
