package com.expensetracker.mobile.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {
    @Test
    fun parsesWholeAndFractionalAmounts() {
        assertEquals(1250L, Money.parseMinor("12.5"))
        assertEquals(-307L, Money.parseMinor("-3.07"))
        assertEquals(1200L, Money.parseMinor("12"))
        assertEquals(5L, Money.parseMinor(" 0.05 "))
    }

    @Test
    fun formatsTwoDecimalPlaces() {
        assertEquals("12.50", Money.formatMinor(1250))
        assertEquals("-3.07", Money.formatMinor(-307))
        assertEquals("0.05", Money.formatMinor(5))
    }

    @Test
    fun rejectsInvalidInput() {
        for (input in listOf("1.234", "abc", "")) {
            assertFailsWith<IllegalArgumentException>(input) { Money.parseMinor(input) }
        }
    }
}
