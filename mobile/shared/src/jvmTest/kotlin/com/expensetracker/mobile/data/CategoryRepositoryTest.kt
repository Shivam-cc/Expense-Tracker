package com.expensetracker.mobile.data

import com.expensetracker.mobile.sync.SyncTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CategoryRepositoryTest {
    @Test
    fun builtInNamesAreRejectedCaseInsensitivelyOnAdd() {
        val h = SyncTestHarness()

        assertFailsWith<IllegalArgumentException> { h.categories.add("food") }
        assertFailsWith<IllegalArgumentException> { h.categories.add(" Coffee ") }
        assertEquals(0L, h.outboxCount())
    }

    @Test
    fun builtInNamesAreRejectedOnRename() {
        val h = SyncTestHarness()
        val id = h.categories.add("Sport")

        assertFailsWith<IllegalArgumentException> { h.categories.rename(id, "SUBSCRIPTIONS") }
        assertEquals("Sport", h.db.expenseFlowQueries.selectCategoryById(id).executeAsOne().name)
    }

    @Test
    fun ordinaryNamesAreTrimmedAndAccepted() {
        val h = SyncTestHarness()

        h.categories.add("  Pet Care ")

        assertEquals(listOf("Pet Care"), h.db.expenseFlowQueries.selectActiveCategories().executeAsList().map { it.name })
    }
}
