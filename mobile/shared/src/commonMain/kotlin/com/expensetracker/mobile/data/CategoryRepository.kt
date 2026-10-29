package com.expensetracker.mobile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.expensetracker.mobile.db.ExpenseFlowDatabase
import com.expensetracker.mobile.sync.OutboxEntity
import com.expensetracker.mobile.sync.OutboxOperation
import com.expensetracker.mobile.sync.enqueue
import com.expensetracker.mobile.util.Uuid7
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class CategoryRepository(
    private val db: ExpenseFlowDatabase,
    private val clock: Clock = Clock.System,
) {
    fun observe(): Flow<List<CategoryRecord>> =
        db.expenseFlowQueries.selectActiveCategories()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    CategoryRecord(
                        id = row.id,
                        serverId = row.server_id,
                        name = row.name,
                        icon = row.icon,
                        color = row.color,
                        isGlobal = row.is_global != 0L,
                        updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
                    )
                }
            }

    /** Creates a local category, queues a CREATE, and returns its client id. */
    fun add(name: String, icon: String? = null, color: String? = null): String {
        val trimmed = requireName(name)
        val id = Uuid7.generate()
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            db.expenseFlowQueries.upsertCategory(id, null, trimmed, icon, color, 0L, now, null)
            db.enqueue(OutboxEntity.CATEGORY, id, OutboxOperation.CREATE)
        }
        return id
    }

    fun rename(id: String, name: String) {
        val trimmed = requireName(name)
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            val existing = db.expenseFlowQueries.selectCategoryById(id).executeAsOneOrNull()
            require(existing != null && existing.deleted_at == null) { "Unknown category: $id" }
            db.expenseFlowQueries.renameCategory(name = trimmed, updatedAt = now, id = id)
            db.enqueue(OutboxEntity.CATEGORY, id, OutboxOperation.UPDATE)
        }
    }

    /**
     * Soft-deletes a local category and queues a DELETE. Expenses that reference it keep the
     * reference locally until the server clears it on the next pull.
     */
    fun delete(id: String) {
        val now = clock.now().toEpochMilliseconds()
        db.transaction {
            val existing = db.expenseFlowQueries.selectCategoryById(id).executeAsOneOrNull()
                ?: return@transaction
            if (existing.deleted_at != null) return@transaction
            db.expenseFlowQueries.markCategoryDeleted(deletedAt = now, updatedAt = now, id = id)
            db.enqueue(OutboxEntity.CATEGORY, id, OutboxOperation.DELETE)
        }
    }

    private fun requireName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Category name must not be blank" }
        return trimmed
    }
}
