package com.expensetracker.android

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryUtilsTest {
    private val today = LocalDate(2026, 11, 15)
    private fun e(amount: Long, date: String, currency: String = "INR", deleted: Boolean = false) =
        SpendEntry(amount, currency, LocalDate.parse(date), deleted)

    @Test
    fun includesFirstAndLastDayOfMonthOnly() {
        val entries = listOf(
            e(100, "2026-11-01"), e(200, "2026-11-30"),
            e(400, "2026-10-31"), e(800, "2026-12-01"), e(1600, "2025-11-15"),
        )
        assertEquals(300L, monthTotalMinor(entries, "INR", today))
    }

    @Test
    fun ignoresDeletedRows() {
        assertEquals(100L, monthTotalMinor(listOf(e(100, "2026-11-02"), e(500, "2026-11-03", deleted = true)), "INR", today))
    }

    @Test
    fun ignoresOtherCurrencies() {
        assertEquals(100L, monthTotalMinor(listOf(e(100, "2026-11-02"), e(900, "2026-11-02", "USD")), "INR", today))
    }

    @Test
    fun emptyIsZero() {
        assertEquals(0L, monthTotalMinor(emptyList(), "INR", today))
    }
}
