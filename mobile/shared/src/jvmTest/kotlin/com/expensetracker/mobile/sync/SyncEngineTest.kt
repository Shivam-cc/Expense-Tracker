package com.expensetracker.mobile.sync

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.CategoryDto
import com.expensetracker.mobile.api.ERROR_ACCOUNT_DELETED
import com.expensetracker.mobile.api.ExpenseDto
import com.expensetracker.mobile.util.Uuid7
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

    /** Writes a category without the reserved-name check, as a device that predates the check would have. */
    private fun SyncTestHarness.addUnvalidatedCategory(name: String): String {
        val id = Uuid7.generate()
        db.expenseFlowQueries.upsertCategory(id, null, name, null, null, 0L, clock.now().toEpochMilliseconds(), null)
        db.enqueue(OutboxEntity.CATEGORY, id, OutboxOperation.CREATE)
        return id
    }

    @Test
    fun createThatDuplicatesADefaultCategoryAdoptsTheDefault() = runTest {
        val h = SyncTestHarness()
        val localFood = h.addUnvalidatedCategory("Food")
        val expenseId = h.expenses.add("Dinner", "30.00", "INR", date, categoryClientId = localFood)
        val serverFood = CategoryDto(id = 42L, name = "Food", global = true, updatedAt = h.clock.now())
        h.api.categories[42L] = serverFood
        h.api.categoryWriteError = ApiException(
            409,
            "A default category named \"Food\" already exists",
            existingCategory = serverFood,
        )

        h.engine.sync()

        assertNull(h.db.expenseFlowQueries.selectCategoryById(localFood).executeAsOneOrNull())
        val builtIn = h.db.expenseFlowQueries.selectCategoryByServerId(42L).executeAsOne()
        assertTrue(builtIn.is_global != 0L)
        assertEquals(1, h.db.expenseFlowQueries.selectActiveCategories().executeAsList().count { it.name == "Food" })
        val expense = h.db.expenseFlowQueries.selectExpenseById(expenseId).executeAsOne()
        assertEquals(builtIn.id, expense.category_client_id)
        assertEquals(42L, expense.category_server_id)
        assertEquals(0L, h.outboxCount())
        assertEquals(42L, h.api.expenses.getValue(expense.server_id!!).categoryId)
    }

    @Test
    fun conflictOnUpdateIsNotAdoptedAndIsMarkedFailed() = runTest {
        val h = SyncTestHarness()
        val id = h.categories.add("Sport")
        h.engine.sync()
        h.categories.rename(id, "Gym")
        h.api.categoryWriteError = ApiException(
            409,
            "A default category named \"Food\" already exists",
            existingCategory = CategoryDto(id = 42L, name = "Food", global = true, updatedAt = h.clock.now()),
        )

        h.engine.sync()

        assertEquals("Gym", h.db.expenseFlowQueries.selectCategoryById(id).executeAsOne().name)
        val op = h.db.expenseFlowQueries.selectOutboxForClient(OutboxEntity.CATEGORY.name, id).executeAsOne()
        assertEquals(OutboxOperation.UPDATE.name, op.operation)
        assertEquals(1L, op.failed)
        assertEquals(1L, op.attempts)
        assertEquals(
            listOf(FailedOutboxItem(OutboxEntity.CATEGORY, id, OutboxOperation.UPDATE, op.last_error)),
            h.engine.failedOutboxItems(),
        )
    }

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
    fun staleCursorRestartsTheFeedFromTheBeginning() = runTest {
        val h = SyncTestHarness()
        h.api.pageSize = 2
        repeat(3) { h.api.serverCreateExpense("Item $it", 100L, date) }
        h.api.failPagesWith = IOException("network down")
        assertFailsWith<IOException> { h.engine.sync() }
        assertEquals("2", h.syncState("expense.cursor"))

        h.api.failPagesWith = null
        h.api.staleCursorRejections = 1
        h.engine.sync()

        assertEquals(0, h.api.staleCursorRejections, "the saved cursor should have been rejected")
        assertNull(h.syncState("expense.cursor"))
        assertNotNull(h.syncState("expense.watermark"))
        assertEquals(3, h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().size)
    }

    @Test
    fun staleCursorRejectedAgainAfterRestartSurfaces() = runTest {
        val h = SyncTestHarness()
        h.api.pageSize = 2
        repeat(3) { h.api.serverCreateExpense("Item $it", 100L, date) }
        h.api.failPagesWith = IOException("network down")
        assertFailsWith<IOException> { h.engine.sync() }

        h.api.failPagesWith = null
        h.api.staleCursorRejections = 2
        val error = assertFailsWith<ApiException> { h.engine.sync() }
        assertEquals(410, error.status)
    }

    @Test
    fun expenseWithUnsyncedCategoryStaysInOutbox() = runTest {
        val h = SyncTestHarness()
        val categoryId = h.categories.add("Takeout")
        h.api.categoryWriteError = ApiException(503, "unavailable")
        val expenseId = h.expenses.add("Dinner", "30.00", "INR", date, categoryClientId = categoryId)

        h.engine.sync()

        assertNull(h.db.expenseFlowQueries.selectExpenseById(expenseId).executeAsOne().server_id)
        assertEquals(2L, h.outboxCount())
        assertTrue(h.api.expenses.isEmpty())

        h.api.categoryWriteError = null
        h.clock.advanceHours(1)
        h.engine.sync()

        assertEquals(0L, h.outboxCount())
        val serverCategory = h.api.categories.values.single()
        assertEquals(serverCategory.id, h.api.expenses.values.single().categoryId)
    }

    @Test
    fun transientServerErrorBacksOffBeforeRetrying() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)
        h.api.expenseWriteError = ApiException(503, "unavailable")
        val failedAt = h.clock.now().toEpochMilliseconds()

        h.engine.sync()

        val op = h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne()
        assertEquals(0L, op.failed)
        assertEquals(1L, op.attempts)
        assertEquals("unavailable", op.last_error)
        assertEquals(failedAt + 30_000L, op.next_attempt_at)
        assertTrue(h.engine.failedOutboxItems().isEmpty())

        h.api.expenseWriteError = null
        h.clock.advanceMillis(29_000L)
        h.engine.sync()
        assertTrue(h.api.expenses.isEmpty(), "row must not be retried before its backoff expires")

        h.clock.advanceMillis(1_000L)
        h.engine.sync()
        assertEquals(0L, h.outboxCount())
        assertEquals(1, h.api.expenses.size)
    }

    @Test
    fun rejectedItemIsMarkedFailedWithoutBlockingOthers() = runTest {
        val h = SyncTestHarness()
        val bad = h.expenses.add("Bad", "1.00", "INR", date)
        val good = h.expenses.add("Good", "2.00", "INR", date)
        h.api.expenseWriteFault = { request ->
            if (request.description == "Bad") ApiException(400, "amount rejected") else null
        }

        h.engine.sync()

        assertEquals(
            listOf(FailedOutboxItem(OutboxEntity.EXPENSE, bad, OutboxOperation.CREATE, "amount rejected")),
            h.engine.failedOutboxItems(),
        )
        assertNotNull(h.db.expenseFlowQueries.selectExpenseById(good).executeAsOne().server_id)
        assertNull(h.db.expenseFlowQueries.selectExpenseById(bad).executeAsOne().server_id)
        assertEquals(1L, h.outboxCount())

        h.api.expenseWriteFault = null
        h.engine.sync()
        assertEquals(1L, h.outboxCount(), "a failed row is not retried until the user asks")

        h.engine.retryFailed()
        h.engine.sync()
        assertEquals(0L, h.outboxCount())
        assertTrue(h.engine.failedOutboxItems().isEmpty())
    }

    @Test
    fun localEditRequeuesAFailedRow() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Bad", "1.00", "INR", date)
        h.api.expenseWriteFault = { ApiException(400, "amount rejected") }
        h.engine.sync()
        assertEquals(1, h.engine.failedOutboxItems().size)

        h.api.expenseWriteFault = null
        h.expenses.updateExpense(id, "Fixed", "5.00", "INR", date)

        assertTrue(h.engine.failedOutboxItems().isEmpty())
        h.engine.sync()
        assertEquals(0L, h.outboxCount())
        assertEquals("Fixed", h.api.expenses.getValue(1L).description)
    }

    @Test
    fun unauthorizedOrAccountDeletedAbortsRunAndLeavesOutboxUntouched() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)
        h.api.expenseWriteError = ApiException(401, "expired")
        assertFailsWith<ApiException> { h.engine.sync() }
        val op = h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne()
        assertEquals(0L, op.attempts)
        assertEquals(0L, op.failed)
        assertEquals(0L, op.next_attempt_at)

        h.api.expenseWriteError = ApiException(401, "gone", error = ERROR_ACCOUNT_DELETED)
        assertFailsWith<ApiException> { h.engine.sync() }
        assertEquals(0L, h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne().failed)
        assertTrue(h.engine.failedOutboxItems().isEmpty())
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
