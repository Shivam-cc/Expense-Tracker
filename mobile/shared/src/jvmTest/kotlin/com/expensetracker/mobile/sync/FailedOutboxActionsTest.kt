package com.expensetracker.mobile.sync

import com.expensetracker.mobile.api.ApiException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FailedOutboxActionsTest {
    private val date = LocalDate(2024, 1, 5)

    @Test
    fun discardingARejectedCreateRemovesTheLocalRowAndItsOutboxEntry() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)
        h.api.expenseWriteError = ApiException(400, "Amount is too large")
        h.engine.sync()
        assertEquals(1, h.engine.failedOutboxItems().size)

        h.engine.discardFailedItem(OutboxEntity.EXPENSE, id)

        assertNull(h.db.expenseFlowQueries.selectExpenseById(id).executeAsOneOrNull())
        assertEquals(0L, h.outboxCount())
        assertEquals(0, h.engine.failedOutboxItems().size)
    }

    @Test
    fun discardingARejectedUpdateBringsTheServerCopyBackOnTheNextSync() = runTest {
        val h = SyncTestHarness()
        val server = h.api.serverCreateExpense("Rent", 90000L, date)
        h.engine.sync()
        val id = h.db.expenseFlowQueries.selectExpenseByServerId(server.id).executeAsOne().id
        h.expenses.updateExpense(id, "Rent edited", "1.00", "INR", date)
        h.api.expenseWriteError = ApiException(400, "rejected")
        h.engine.sync()
        h.api.expenseWriteError = null

        h.engine.discardFailedItem(OutboxEntity.EXPENSE, id)
        assertNull(h.db.expenseFlowQueries.selectExpenseById(id).executeAsOneOrNull())
        h.engine.sync()

        val restored = h.db.expenseFlowQueries.selectExpenseByServerId(server.id).executeAsOne()
        assertEquals("Rent", restored.description)
        assertEquals(90000L, restored.amount_minor)
        assertEquals(0L, h.outboxCount())
    }

    @Test
    fun discardingARejectedCategoryCreateClearsExpensesThatPointAtIt() = runTest {
        val h = SyncTestHarness()
        val category = h.categories.add("Pets")
        val expense = h.expenses.add("Food", "5.00", "INR", date, categoryClientId = category)
        h.api.categoryWriteError = ApiException(400, "rejected")
        h.engine.sync()

        h.engine.discardFailedItem(OutboxEntity.CATEGORY, category)

        assertNull(h.db.expenseFlowQueries.selectCategoryById(category).executeAsOneOrNull())
        assertNull(h.db.expenseFlowQueries.selectExpenseById(expense).executeAsOne().category_client_id)
        h.api.categoryWriteError = null
        h.engine.sync()
        assertEquals(0L, h.outboxCount(), "the expense must no longer wait for a category that cannot sync")
    }

    @Test
    fun discardingACategoryRenamePreservesExpenseReferencesToTheServerCategory() = runTest {
        val h = SyncTestHarness()
        val category = h.categories.add("Pets")
        val expense = h.expenses.add("Vet", "5.00", "INR", date, categoryClientId = category)
        h.engine.sync()
        val serverId = h.db.expenseFlowQueries.selectCategoryById(category).executeAsOne().server_id
        h.categories.rename(category, "Animals")
        h.api.categoryWriteError = ApiException(400, "rejected")
        h.engine.sync()

        h.engine.discardFailedItem(OutboxEntity.CATEGORY, category)

        assertEquals(serverId, h.db.expenseFlowQueries.selectExpenseById(expense).executeAsOne().category_server_id)
        h.api.categoryWriteError = null
        h.engine.sync()
        assertEquals("Pets", h.db.expenseFlowQueries.selectCategoryById(category).executeAsOne().name)
        assertEquals(category, h.db.expenseFlowQueries.selectExpenseById(expense).executeAsOne().category_client_id)
    }

    @Test
    fun onlyRejectedItemsCanBeDiscarded() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)

        h.engine.discardFailedItem(OutboxEntity.EXPENSE, id)

        assertNotNull(h.db.expenseFlowQueries.selectExpenseById(id).executeAsOneOrNull())
        assertEquals(1L, h.outboxCount())
    }

    @Test
    fun retryingOneItemRequeuesOnlyThatItem() = runTest {
        val h = SyncTestHarness()
        val first = h.expenses.add("First", "1.00", "INR", date)
        val second = h.expenses.add("Second", "2.00", "INR", date)
        h.api.expenseWriteError = ApiException(400, "rejected")
        h.engine.sync()
        assertEquals(2, h.engine.failedOutboxItems().size)

        h.engine.retryFailedItem(OutboxEntity.EXPENSE, first)

        assertEquals(listOf(second), h.engine.failedOutboxItems().map { it.clientId })
        h.api.expenseWriteError = null
        h.engine.sync()
        assertEquals(listOf(second), h.engine.failedOutboxItems().map { it.clientId })
        assertEquals(1L, h.outboxCount())
    }

    @Test
    fun lastSyncedAtIsNullUntilASyncCompletes() = runTest {
        val h = SyncTestHarness()
        assertNull(h.engine.lastSyncedAt())

        h.engine.sync()

        assertEquals(h.clock.now(), h.engine.lastSyncedAt())
    }
}
