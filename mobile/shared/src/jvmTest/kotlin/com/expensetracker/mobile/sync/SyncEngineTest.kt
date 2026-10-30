package com.expensetracker.mobile.sync

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.ExpenseDto
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineTest {
    private val date = LocalDate(2024, 1, 5)

    private fun SyncTestHarness.syncState(key: String): String? =
        db.expenseFlowQueries.selectSyncState(key).executeAsOneOrNull()

    @Test
    fun pushCreateStoresServerIdAndEmptiesOutbox() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)

        h.engine.sync()

        val row = h.db.expenseFlowQueries.selectExpenseById(id).executeAsOne()
        assertEquals(1L, row.server_id)
        assertEquals(0L, h.outboxCount())
        assertEquals(id, h.api.expenses.getValue(1L).clientId)
    }

    @Test
    fun pendingLocalRowIsNotOverwrittenByPull() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)
        // Push is rejected with a 4xx, so the change stays pending while the pull runs.
        h.api.expenseWriteError = ApiException(400, "rejected")
        val now = h.clock.now()
        h.api.expenses[1L] = ExpenseDto(
            id = 1L,
            clientId = id,
            description = "Server copy",
            amountMinor = 9900L,
            currency = "INR",
            date = date,
            createdAt = now,
            updatedAt = now,
        )
        h.clock.advanceHours(1)

        h.engine.sync()

        val row = h.db.expenseFlowQueries.selectExpenseById(id).executeAsOne()
        assertEquals("Lunch", row.description)
        assertEquals(1250L, row.amount_minor)
        assertEquals(1L, h.outboxCount())
        assertEquals(1L, h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne().attempts)
    }

    @Test
    fun pullAppliesNewerServerRowAndTombstone() = runTest {
        val h = SyncTestHarness()
        val rent = h.api.serverCreateExpense("Rent", 90000L, date)
        val taxi = h.api.serverCreateExpense("Taxi", 2000L, date)
        val ghost = h.api.serverCreateExpense("Ghost", 100L, date)
        h.api.serverDeleteExpense(ghost.id, h.clock.now())
        h.engine.sync()

        // Tombstone for a row we never stored is ignored.
        assertNull(h.db.expenseFlowQueries.selectExpenseByServerId(ghost.id).executeAsOneOrNull())
        assertNotNull(h.db.expenseFlowQueries.selectExpenseByServerId(rent.id).executeAsOneOrNull())

        h.clock.advanceHours(1)
        h.api.serverChangeExpense(rent.id, "Rent (edited)", 95000L, h.clock.now())
        h.api.serverDeleteExpense(taxi.id, h.clock.now())
        h.engine.sync()

        val updated = h.db.expenseFlowQueries.selectExpenseByServerId(rent.id).executeAsOne()
        assertEquals("Rent (edited)", updated.description)
        assertEquals(95000L, updated.amount_minor)
        val deleted = h.db.expenseFlowQueries.selectExpenseByServerId(taxi.id).executeAsOne()
        assertNotNull(deleted.deleted_at)
        assertEquals(1, h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().size)
    }

    @Test
    fun paginationStoresCursorAndResumesAfterFailure() = runTest {
        val h = SyncTestHarness()
        h.api.pageSize = 2
        repeat(3) { h.api.serverCreateExpense("Item $it", 100L, date) }
        h.api.failPagesWith = IOException("network down")

        assertFailsWith<IOException> { h.engine.sync() }

        assertEquals("2", h.syncState("expense.cursor"))
        assertEquals(2, h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().size)

        h.api.failPagesWith = null
        h.engine.sync()

        assertNull(h.syncState("expense.cursor"))
        assertEquals(3, h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().size)
    }

    @Test
    fun expenseWithUnsyncedCategoryStaysInOutbox() = runTest {
        val h = SyncTestHarness()
        val categoryId = h.categories.add("Food")
        h.api.categoryWriteError = ApiException(400, "rejected")
        val expenseId = h.expenses.add("Dinner", "30.00", "INR", date, categoryClientId = categoryId)

        h.engine.sync()

        assertNull(h.db.expenseFlowQueries.selectExpenseById(expenseId).executeAsOne().server_id)
        assertEquals(2L, h.outboxCount())
        assertTrue(h.api.expenses.isEmpty())

        h.api.categoryWriteError = null
        h.engine.sync()

        assertEquals(0L, h.outboxCount())
        val serverCategory = h.api.categories.values.single()
        assertEquals(serverCategory.id, h.api.expenses.values.single().categoryId)
    }

    @Test
    fun localEditDuringInFlightPushIsKeptForNextSync() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Coffee", "3.00", "INR", date)
        h.api.beforeExpenseWrite = {
            h.api.beforeExpenseWrite = null
            h.expenses.updateExpense(id, "Coffee beans", "18.00", "INR", date)
        }

        h.engine.sync()

        val row = h.db.expenseFlowQueries.selectExpenseById(id).executeAsOne()
        assertEquals(1L, row.server_id)
        assertEquals("Coffee beans", row.description)
        assertEquals("UPDATE", h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne().operation)

        h.engine.sync()

        assertEquals(0L, h.outboxCount())
        assertEquals("Coffee beans", h.api.expenses.getValue(1L).description)
    }
}
