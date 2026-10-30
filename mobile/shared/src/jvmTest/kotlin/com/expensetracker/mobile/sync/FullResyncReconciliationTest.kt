package com.expensetracker.mobile.sync

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.CategoryDto
import com.expensetracker.mobile.api.ExpenseDto
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** After a 410 forces a full resync, local synced rows the server did not return are deleted. */
class FullResyncReconciliationTest {
    private val date = LocalDate(2024, 1, 5)

    private fun SyncTestHarness.localDescriptions(): List<String> =
        db.expenseFlowQueries.selectActiveExpenses().executeAsList().map { it.description }.sorted()

    private fun SyncTestHarness.localId(serverId: Long): String =
        db.expenseFlowQueries.selectExpenseByServerId(serverId).executeAsOne().id

    private fun SyncTestHarness.syncState(key: String): String? =
        db.expenseFlowQueries.selectSyncState(key).executeAsOneOrNull()

    /** The server forgot a row (for example, a purged tombstone). It never sends a tombstone for it. */
    private fun SyncTestHarness.serverForgets(serverId: Long) {
        api.expenses.remove(serverId)
    }

    @Test
    fun fullResyncDeletesSyncedRowsTheServerNoLongerReturns() = runTest {
        val h = SyncTestHarness()
        val rent = h.api.serverCreateExpense("Rent", 90000L, date)
        h.api.serverCreateExpense("Taxi", 2000L, date)
        h.api.serverCreateExpense("Lunch", 500L, date)
        h.engine.sync()
        assertEquals(listOf("Lunch", "Rent", "Taxi"), h.localDescriptions())

        h.serverForgets(rent.id)
        h.api.staleCursorRejections = 1
        h.engine.sync()

        assertEquals(listOf("Lunch", "Taxi"), h.localDescriptions())
        assertNull(h.db.expenseFlowQueries.selectExpenseByServerId(rent.id).executeAsOneOrNull(), "row must be gone, not just hidden")
        assertNull(h.syncState("expense.reconcile"))
    }

    @Test
    fun rowsWithPendingOutboxEntriesAreNeverDeleted() = runTest {
        val h = SyncTestHarness()
        val a = h.api.serverCreateExpense("A", 100L, date)
        val b = h.api.serverCreateExpense("B", 200L, date)
        val c = h.api.serverCreateExpense("C", 300L, date)
        h.engine.sync()
        h.expenses.updateExpense(h.localId(a.id), "A edited", "1.00", "INR", date)
        h.expenses.updateExpense(h.localId(b.id), "B edited", "2.00", "INR", date)
        val neverSynced = h.expenses.add("D new", "4.00", "INR", date)
        h.api.expenseWriteFault = { request ->
            if (request.description == "B edited") ApiException(400, "rejected") else ApiException(500, "down")
        }
        listOf(a, b, c).forEach { h.serverForgets(it.id) }
        h.api.staleCursorRejections = 1

        h.engine.sync()

        // A is waiting to retry, B was rejected and is marked failed, D never reached the server: all still pending.
        assertEquals(listOf("A edited", "B edited", "D new"), h.localDescriptions())
        assertNotNull(h.db.expenseFlowQueries.selectExpenseById(neverSynced).executeAsOneOrNull())
        assertEquals(3L, h.outboxCount())
        assertEquals(1, h.engine.failedOutboxItems().size)
        assertNull(h.db.expenseFlowQueries.selectExpenseByServerId(c.id).executeAsOneOrNull(), "C had no pending entry")
    }

    @Test
    fun failureAfterTheFirstPageDeletesNothingAndTheNextSyncReconciles() = runTest {
        val h = SyncTestHarness()
        h.api.pageSize = 2
        val items = (1..5).map { h.api.serverCreateExpense("Item $it", 100L, date) }
        h.engine.sync()
        assertEquals(5, h.localDescriptions().size)
        h.serverForgets(items[0].id)
        h.serverForgets(items[4].id)
        h.api.staleCursorRejections = 1
        h.api.failPagesWith = IOException("network down")

        assertFailsWith<IOException> { h.engine.sync() }

        assertEquals(5, h.localDescriptions().size, "a feed that was not fully received must not delete anything")
        assertEquals("1", h.syncState("expense.reconcile"))

        h.api.failPagesWith = null
        h.engine.sync()

        assertEquals(listOf("Item 2", "Item 3", "Item 4"), h.localDescriptions())
        assertNull(h.syncState("expense.reconcile"))
        assertNull(h.syncState("expense.cursor"))
    }

    @Test
    fun aSecondGoneDuringTheFullResyncDeletesNothing() = runTest {
        val h = SyncTestHarness()
        h.api.pageSize = 2
        val items = (1..5).map { h.api.serverCreateExpense("Item $it", 100L, date) }
        h.engine.sync()
        h.serverForgets(items[0].id)
        h.api.staleCursorRejections = 2

        val error = assertFailsWith<ApiException> { h.engine.sync() }

        assertEquals(410, error.status)
        assertEquals(5, h.localDescriptions().size)
    }

    @Test
    fun anIncrementalPullNeverDeletesRowsMissingFromTheServer() = runTest {
        val h = SyncTestHarness()
        val rent = h.api.serverCreateExpense("Rent", 90000L, date)
        h.api.serverCreateExpense("Taxi", 2000L, date)
        h.engine.sync()
        h.serverForgets(rent.id)

        h.engine.sync()

        assertEquals(listOf("Rent", "Taxi"), h.localDescriptions())
    }

    @Test
    fun categoriesAreReconciledAndDanglingReferencesAreCleared() = runTest {
        val h = SyncTestHarness()
        val now = h.clock.now()
        h.api.categories[10L] = CategoryDto(id = 10L, name = "Pets", updatedAt = now)
        h.api.categories[11L] = CategoryDto(id = 11L, name = "Garden", updatedAt = now)
        h.api.categories[12L] = CategoryDto(id = 12L, name = "Boats", updatedAt = now)
        h.api.expenses[1L] = ExpenseDto(
            id = 1L, description = "Seeds", amountMinor = 300L, currency = "INR", date = date,
            categoryId = 11L, createdAt = now, updatedAt = now,
        )
        h.engine.sync()
        val queries = h.db.expenseFlowQueries
        val garden = queries.selectCategoryByServerId(11L).executeAsOne().id
        val boats = queries.selectCategoryByServerId(12L).executeAsOne().id
        assertEquals(garden, queries.selectExpenseByServerId(1L).executeAsOne().category_client_id)
        // Boats has a pending rename, so it must survive even though the server forgot it.
        h.categories.rename(boats, "Ships")
        h.api.categoryWriteError = ApiException(500, "down")
        h.api.categories.remove(10L)
        h.api.categories.remove(11L)
        h.api.categories.remove(12L)
        // One 410 per feed: expenses are pulled first and take the first, categories take the second.
        h.api.staleCursorRejections = 2

        h.engine.sync()

        assertNull(queries.selectCategoryByServerId(10L).executeAsOneOrNull())
        assertNull(queries.selectCategoryById(garden).executeAsOneOrNull())
        assertNotNull(queries.selectCategoryById(boats).executeAsOneOrNull())
        val seeds = queries.selectExpenseByServerId(1L).executeAsOne()
        assertNull(seeds.category_client_id)
        assertNull(seeds.category_server_id)
        assertNull(h.syncState("category.reconcile"))
    }
}
