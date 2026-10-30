package com.expensetracker.mobile.sync

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ExpenseRepositoryTest {
    private val date = LocalDate(2024, 1, 5)

    @Test
    fun addWritesCreateAndUpdateCoalescesIntoOneOutboxRow() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Lunch", "12.50", "INR", date)

        assertEquals(1L, h.outboxCount())
        assertEquals("CREATE", h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne().operation)

        h.expenses.updateExpense(id, "Lunch with team", "15.00", "INR", date)

        assertEquals(1L, h.outboxCount())
        assertEquals("CREATE", h.db.expenseFlowQueries.selectOutboxForClient("EXPENSE", id).executeAsOne().operation)
        assertEquals(1500L, h.db.expenseFlowQueries.selectExpenseById(id).executeAsOne().amount_minor)
    }

    @Test
    fun deletingPendingCreateDropsBothOutboxEntries() = runTest {
        val h = SyncTestHarness()
        val id = h.expenses.add("Taxi", "20.00", "INR", date)

        h.expenses.deleteExpense(id)

        assertEquals(0L, h.outboxCount())
        assertNull(h.db.expenseFlowQueries.selectActiveExpenses().executeAsList().firstOrNull { it.id == id })
    }

    @Test
    fun invalidAmountThrowsAndWritesNothing() = runTest {
        val h = SyncTestHarness()

        assertFailsWith<IllegalArgumentException> { h.expenses.add("Bad", "abc", "INR", date) }
        assertFailsWith<IllegalArgumentException> { h.expenses.add("Zero", "0", "INR", date) }

        assertEquals(0L, h.outboxCount())
    }
}
