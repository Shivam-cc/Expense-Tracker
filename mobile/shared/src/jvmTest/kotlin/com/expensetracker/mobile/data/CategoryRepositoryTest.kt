package com.expensetracker.mobile.data

import com.expensetracker.mobile.sync.SyncTestHarness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun checkNameOffersTheExistingCategoryForADuplicate() {
        val h = SyncTestHarness()
        val id = h.categories.add("Sport")

        val check = assertIs<CategoryNameCheck.AlreadyExists>(h.categories.checkName(" sport "))

        assertEquals(id, check.existing?.id)
        assertEquals("Sport", check.name)
        assertFalse(check.builtIn)
    }

    @Test
    fun checkNameReportsAReservedNameEvenBeforeTheBuiltInHasSynced() {
        val h = SyncTestHarness()

        val check = assertIs<CategoryNameCheck.AlreadyExists>(h.categories.checkName("food"))

        assertNull(check.existing)
        assertEquals("Food", check.name)
        assertTrue(check.builtIn)
    }

    @Test
    fun checkNameOffersTheSyncedBuiltInForAReservedName() {
        val h = SyncTestHarness()
        h.db.expenseFlowQueries.upsertCategory("builtin-food", 42L, "Food", null, null, 1L, 0L, null)

        val check = assertIs<CategoryNameCheck.AlreadyExists>(h.categories.checkName("FOOD"))

        assertEquals("builtin-food", check.existing?.id)
        assertTrue(check.builtIn)
    }

    @Test
    fun checkNameAllowsKeepingOrRecasingTheCategorysOwnName() {
        val h = SyncTestHarness()
        val id = h.categories.add("Sport")
        h.categories.add("Gym")

        assertIs<CategoryNameCheck.Valid>(h.categories.checkName("SPORT", excludingId = id))
        assertIs<CategoryNameCheck.AlreadyExists>(h.categories.checkName("gym", excludingId = id))
        assertIs<CategoryNameCheck.Blank>(h.categories.checkName("   "))
        assertEquals(CategoryNameCheck.Valid("Pets"), h.categories.checkName(" Pets "))
    }
}
