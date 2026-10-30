package com.expensetracker.mobile.sync

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.CategoryDto
import com.expensetracker.mobile.api.CategoryWriteDto
import com.expensetracker.mobile.api.ChangePage
import com.expensetracker.mobile.api.ExpenseDto
import com.expensetracker.mobile.api.ExpenseFlowApi
import com.expensetracker.mobile.api.ExpenseWriteDto
import com.expensetracker.mobile.db.Category
import com.expensetracker.mobile.db.Expense
import com.expensetracker.mobile.db.ExpenseFlowDatabase
import com.expensetracker.mobile.db.Outbox
import com.expensetracker.mobile.util.Money
import com.expensetracker.mobile.util.Uuid7
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * Offline-first sync. Pushes the outbox (categories first, then expenses, in seq order), then pulls
 * changes (expenses, then categories). Local rows with a pending outbox entry are never overwritten by a pull.
 *
 * Transport failures (and 5xx or 401/408/429) are rethrown; the outbox and cursors stay consistent,
 * so the next [sync] resumes where this one stopped.
 */
class SyncEngine(
    private val api: ExpenseFlowApi,
    private val db: ExpenseFlowDatabase,
    private val clock: Clock = Clock.System,
) {
    private val queries = db.expenseFlowQueries
    private val mutex = Mutex()

    suspend fun sync() {
        mutex.withLock {
            pushCategories()
            pushExpenses()
            pullExpenses()
            pullCategories()
            setState(LAST_SYNC_KEY, clock.now().toEpochMilliseconds().toString())
        }
    }

    private suspend fun pushCategories() {
        for (op in queries.selectOutboxByEntity(OutboxEntity.CATEGORY.name).executeAsList()) {
            try {
                pushCategory(op)
            } catch (e: ApiException) {
                val existing = e.existingCategory
                if (e.status == 409 && existing != null && op.operation == OutboxOperation.CREATE.name) {
                    adoptDefaultCategory(op.client_id, existing)
                    continue
                }
                if (!e.isClientRejection()) throw e
                queries.incrementOutboxAttempts(op.seq)
            }
        }
    }

    /**
     * The server already has a default category with this name. The local duplicate is replaced by that
     * default: its expenses move to the default's row and its outbox entry and row are removed.
     */
    private fun adoptDefaultCategory(duplicateClientId: String, existing: CategoryDto) {
        db.transaction {
            applyCategory(existing)
            val builtIn = queries.selectCategoryByServerId(existing.id).executeAsOneOrNull()
                ?: return@transaction
            queries.remapExpenseCategory(
                fromClientId = duplicateClientId,
                toClientId = builtIn.id,
                toServerId = existing.id,
            )
            queries.deleteOutboxForClient(OutboxEntity.CATEGORY.name, duplicateClientId)
            queries.deleteCategoryById(duplicateClientId)
        }
    }

    private suspend fun pushExpenses() {
        for (op in queries.selectOutboxByEntity(OutboxEntity.EXPENSE.name).executeAsList()) {
            try {
                pushExpense(op)
            } catch (e: ApiException) {
                if (!e.isClientRejection()) throw e
                queries.incrementOutboxAttempts(op.seq)
            }
        }
    }

    private suspend fun pushCategory(op: Outbox) {
        val clientId = op.client_id
        val local = queries.selectCategoryById(clientId).executeAsOneOrNull()
        if (op.operation == OutboxOperation.DELETE.name) {
            val serverId = local?.server_id
            if (serverId != null) ignoreNotFound { api.deleteCategory(serverId) }
            queries.deleteOutbox(op.seq)
            return
        }
        if (local == null) {
            queries.deleteOutbox(op.seq)
            return
        }
        val request = CategoryWriteDto(clientId = clientId, name = local.name, icon = local.icon, color = local.color)
        val serverId = local.server_id
        val dto = if (serverId == null) api.createCategory(request) else api.updateCategory(serverId, request)
        completeCategoryPush(clientId, local, dto)
    }

    private suspend fun pushExpense(op: Outbox) {
        val clientId = op.client_id
        val local = queries.selectExpenseById(clientId).executeAsOneOrNull()
        if (op.operation == OutboxOperation.DELETE.name) {
            val serverId = local?.server_id
            if (serverId != null) ignoreNotFound { api.deleteExpense(serverId) }
            queries.deleteOutbox(op.seq)
            return
        }
        if (local == null) {
            queries.deleteOutbox(op.seq)
            return
        }
        // The expense stays queued until its category has a server id.
        if (isCategoryUnsynced(local)) return
        val request = ExpenseWriteDto(
            clientId = clientId,
            description = local.description,
            amountMinor = local.amount_minor,
            currency = local.currency,
            date = LocalDate.parse(local.expense_date),
            categoryId = wireCategoryId(local),
            notes = local.notes,
        )
        val serverId = local.server_id
        val dto = if (serverId == null) api.createExpense(request) else api.updateExpense(serverId, request)
        completeExpensePush(clientId, local, dto)
    }

    /**
     * Stores the server id after a successful push and resolves the outbox row. If the local row changed
     * while the request was in flight, the outbox entry is kept so the new state is pushed next time.
     */
    private fun completeCategoryPush(clientId: String, sent: Category, dto: CategoryDto) {
        db.transaction {
            val current = queries.selectCategoryById(clientId).executeAsOneOrNull() ?: return@transaction
            val editedInFlight = current != sent
            if (editedInFlight) {
                queries.setCategoryServerId(serverId = dto.id, id = clientId)
            } else {
                queries.markCategorySynced(
                    serverId = dto.id,
                    updatedAt = dto.updatedAt.toEpochMilliseconds(),
                    id = clientId,
                )
            }
            resolveOutboxAfterPush(OutboxEntity.CATEGORY, clientId, editedInFlight, current.deleted_at != null)
        }
    }

    private fun completeExpensePush(clientId: String, sent: Expense, dto: ExpenseDto) {
        db.transaction {
            val current = queries.selectExpenseById(clientId).executeAsOneOrNull() ?: return@transaction
            val editedInFlight = current != sent
            if (editedInFlight) {
                queries.setExpenseServerId(serverId = dto.id, id = clientId)
            } else {
                queries.markExpenseSynced(
                    serverId = dto.id,
                    updatedAt = dto.updatedAt.toEpochMilliseconds(),
                    id = clientId,
                )
            }
            resolveOutboxAfterPush(OutboxEntity.EXPENSE, clientId, editedInFlight, current.deleted_at != null)
        }
    }

    private fun resolveOutboxAfterPush(entity: OutboxEntity, clientId: String, editedInFlight: Boolean, deleted: Boolean) {
        val pending = queries.selectOutboxForClient(entity.name, clientId).executeAsOneOrNull()
        if (deleted) {
            // Deleted while the create was in flight: the server now has a record that must be removed.
            if (pending == null) {
                queries.insertOutbox(entity.name, clientId, OutboxOperation.DELETE.name)
            } else if (pending.operation != OutboxOperation.DELETE.name) {
                queries.updateOutboxOperation(OutboxOperation.DELETE.name, pending.seq)
            }
        } else if (!editedInFlight) {
            if (pending != null) queries.deleteOutbox(pending.seq)
        } else if (pending != null && pending.operation == OutboxOperation.CREATE.name) {
            // The server id is known now, so further pushes must be updates.
            queries.updateOutboxOperation(OutboxOperation.UPDATE.name, pending.seq)
        }
    }

    private fun isCategoryUnsynced(expense: Expense): Boolean {
        val categoryClientId = expense.category_client_id ?: return false
        val category = queries.selectCategoryById(categoryClientId).executeAsOneOrNull() ?: return true
        return category.deleted_at == null && category.server_id == null
    }

    /**
     * Server category id to send. A locally deleted category is sent as null; the local reference
     * is kept until the server clears it on pull.
     */
    private fun wireCategoryId(expense: Expense): Long? {
        val categoryClientId = expense.category_client_id ?: return expense.category_server_id
        val category = queries.selectCategoryById(categoryClientId).executeAsOneOrNull() ?: return null
        return if (category.deleted_at == null) category.server_id else null
    }

    private suspend fun pullExpenses() {
        pullEntity(
            name = "expense",
            fetch = { since, cursor -> api.getExpenseChanges(since, cursor, PAGE_LIMIT) },
            updatedAtOf = { it.updatedAt },
            applyItem = ::applyExpense,
        )
    }

    private suspend fun pullCategories() {
        pullEntity(
            name = "category",
            fetch = { since, cursor -> api.getCategoryChanges(since, cursor, PAGE_LIMIT) },
            updatedAtOf = { it.updatedAt },
            applyItem = ::applyCategory,
        )
    }

    /**
     * Pages through one changes feed. Each page is applied in one transaction together with its cursor
     * (or with the cleared cursor on the last page) and the updatedAt watermark. The watermark is the
     * updatedSince used for the next incremental pull, which the server treats as inclusive.
     */
    private suspend fun <T> pullEntity(
        name: String,
        fetch: suspend (updatedSince: Instant?, cursor: String?) -> ChangePage<T>,
        updatedAtOf: (T) -> Instant,
        applyItem: (T) -> Unit,
    ) {
        val cursorKey = "$name.cursor"
        val watermarkKey = "$name.watermark"
        var cursor = getState(cursorKey)
        var since = if (cursor == null) {
            getState(watermarkKey)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
        } else {
            null
        }
        while (true) {
            val page = fetch(since, cursor)
            val nextCursor = if (page.hasMore) page.nextCursor else null
            val pageMax = page.items.maxOfOrNull { updatedAtOf(it).toEpochMilliseconds() }
            db.transaction {
                page.items.forEach(applyItem)
                if (pageMax != null) {
                    val previous = getState(watermarkKey)?.toLongOrNull() ?: pageMax
                    setState(watermarkKey, maxOf(previous, pageMax).toString())
                }
                if (nextCursor != null) setState(cursorKey, nextCursor) else deleteState(cursorKey)
            }
            if (nextCursor == null) break
            cursor = nextCursor
            since = null
        }
    }

    private fun applyExpense(dto: ExpenseDto) {
        val existing = dto.clientId?.let { queries.selectExpenseById(it).executeAsOneOrNull() }
            ?: queries.selectExpenseByServerId(dto.id).executeAsList().firstOrNull()
        if (existing == null && dto.deletedAt != null) return
        if (existing != null) {
            if (hasPending(OutboxEntity.EXPENSE, existing.id)) return
            if (dto.updatedAt.toEpochMilliseconds() < existing.updated_at) return
        }
        val categoryClientId = dto.categoryId?.let { serverId ->
            queries.selectCategoryByServerId(serverId).executeAsList().firstOrNull()?.id
        }
        queries.upsertExpense(
            existing?.id ?: dto.clientId ?: Uuid7.generate(),
            dto.id,
            dto.description,
            dto.amountMinor,
            dto.currency,
            dto.date.toString(),
            dto.categoryId,
            categoryClientId,
            dto.notes,
            existing?.created_at ?: dto.createdAt.toEpochMilliseconds(),
            dto.updatedAt.toEpochMilliseconds(),
            dto.deletedAt?.toEpochMilliseconds(),
        )
    }

    private fun applyCategory(dto: CategoryDto) {
        val existing = dto.clientId?.let { queries.selectCategoryById(it).executeAsOneOrNull() }
            ?: queries.selectCategoryByServerId(dto.id).executeAsList().firstOrNull()
        if (existing == null && dto.deletedAt != null) return
        if (existing != null) {
            if (hasPending(OutboxEntity.CATEGORY, existing.id)) return
            if (dto.updatedAt.toEpochMilliseconds() < existing.updated_at) return
        }
        val localId = existing?.id ?: dto.clientId ?: Uuid7.generate()
        queries.upsertCategory(
            localId,
            dto.id,
            dto.name,
            dto.icon,
            dto.color,
            if (dto.global) 1L else 0L,
            dto.updatedAt.toEpochMilliseconds(),
            dto.deletedAt?.toEpochMilliseconds(),
        )
        // Expenses pulled before their category carry only the server id; link them to the local id now.
        queries.relinkExpensesToCategory(categoryClientId = localId, serverId = dto.id)
    }

    private fun hasPending(entity: OutboxEntity, clientId: String): Boolean =
        queries.selectOutboxForClient(entity.name, clientId).executeAsOneOrNull() != null

    private fun getState(key: String): String? = queries.selectSyncState(key).executeAsOneOrNull()

    private fun setState(key: String, value: String) {
        queries.upsertSyncState(key, value)
    }

    private fun deleteState(key: String) {
        queries.deleteSyncState(key)
    }

    private fun ApiException.isClientRejection(): Boolean =
        status in 400..499 && status != 401 && status != 408 && status != 429

    /** A DELETE that already finished on the server (404) counts as success. */
    private suspend fun ignoreNotFound(call: suspend () -> Unit) {
        try {
            call()
        } catch (e: ApiException) {
            if (e.status != 404) throw e
        }
    }

    private companion object {
        const val PAGE_LIMIT = 100
        const val LAST_SYNC_KEY = "last_sync_at"
    }
}
