package com.expensetracker.mobile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.expensetracker.mobile.db.ExpenseFlowDatabase
import com.expensetracker.mobile.sync.OutboxEntity
import com.expensetracker.mobile.sync.OutboxOperation
import com.expensetracker.mobile.sync.enqueue
import com.expensetracker.mobile.util.Money
import com.expensetracker.mobile.util.Uuid7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class ExpenseRepository(
    private val db: ExpenseFlowDatabase,
    private val clock: Clock = Clock.System,
) {
    fun observe(): Flow<List<ExpenseRecord>> =
        db.expenseFlowQueries.selectActiveExpenses()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    ExpenseRecord(
                        id = row.id,
                        serverId = row.server_id,
                        description = row.description,
                        amountMinor = row.amount_minor,
                        amount = Money.formatMinor(row.amount_minor),
                        date = LocalDate.parse(row.expense_date),
                        categoryClientId = row.category_client_id,
                        categoryServerId = row.category_server_id,
                        notes = row.notes,
                        createdAt = Instant.fromEpochMilliseconds(row.created_at),
                        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
                    )
                }
            }

    /** Creates a local expense, queues a CREATE, and returns its client id. */
    fun add(
        description: String,
        amountText: String,
        date: LocalDate,
        categoryClientId: String? = null,
        categoryServerId: Long? = null,
        notes: String? = null,
    ): String {
        val amountMinor = parsePositiveAmount(amountText)
        val id = Uuid7.generate()
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            db.expenseFlowQueries.upsertExpense(
                id,
                null,
                description,
                amountMinor,
                date.toString(),
                categoryServerId,
                categoryClientId,
                notes,
                now,
                now,
                null,
            )
            db.enqueue(OutboxEntity.EXPENSE, id, OutboxOperation.CREATE)
        }
        return id
    }

    /** Updates a local expense and queues an UPDATE (coalesced with any pending CREATE or UPDATE). */
    fun updateExpense(
        id: String,
        description: String,
        amountText: String,
        date: LocalDate,
        categoryClientId: String? = null,
        categoryServerId: Long? = null,
        notes: String? = null,
    ) {
        val amountMinor = parsePositiveAmount(amountText)
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            val existing = db.expenseFlowQueries.selectExpenseById(id).executeAsOneOrNull()
            require(existing != null && existing.deleted_at == null) { "Unknown expense: $id" }
            db.expenseFlowQueries.updateExpenseLocal(
                description = description,
                amountMinor = amountMinor,
                expenseDate = date.toString(),
                categoryServerId = categoryServerId,
                categoryClientId = categoryClientId,
                notes = notes,
                updatedAt = now,
                id = id,
            )
            db.enqueue(OutboxEntity.EXPENSE, id, OutboxOperation.UPDATE)
        }
    }

    /** Soft-deletes a local expense and queues a DELETE. Unknown or already deleted ids are ignored. */
    fun deleteExpense(id: String) {
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            val existing = db.expenseFlowQueries.selectExpenseById(id).executeAsOneOrNull()
                ?: return@transaction
            if (existing.deleted_at != null) return@transaction
            db.expenseFlowQueries.markExpenseDeleted(deletedAt = now, updatedAt = now, id = id)
            db.enqueue(OutboxEntity.EXPENSE, id, OutboxOperation.DELETE)
        }
    }

    private fun parsePositiveAmount(text: String): Long {
        val minor = Money.parseMinor(text)
        require(minor > 0) { "Amount must be greater than zero: $text" }
        return minor
    }
}
