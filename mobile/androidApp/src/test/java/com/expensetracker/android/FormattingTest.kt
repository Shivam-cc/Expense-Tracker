package com.expensetracker.android

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattingTest {
    private val today = LocalDate(2026, 11, 6)

    @Test fun today() = assertEquals("Today", friendlyDate(today, today))

    @Test fun yesterday() = assertEquals("Yesterday", friendlyDate(LocalDate(2026, 11, 5), today))

    @Test fun yesterdayAcrossYearBoundary() =
        assertEquals("Yesterday", friendlyDate(LocalDate(2025, 12, 31), LocalDate(2026, 1, 1)))

    @Test fun sameYear() = assertEquals("6 Jan", friendlyDate(LocalDate(2026, 1, 6), today))

    @Test fun otherYear() = assertEquals("6 Nov 2024", friendlyDate(LocalDate(2024, 11, 6), today))

    @Test fun futureDateIsNotRelative() = assertEquals("7 Nov", friendlyDate(LocalDate(2026, 11, 7), today))

    @Test fun parsesHex() {
        assertEquals(0xFF112233.toInt(), parseHexColor("#112233"))
        assertEquals(0xFFAABBCC.toInt(), parseHexColor("aabbcc"))
        assertNull(parseHexColor("#12"))
        assertNull(parseHexColor("zzzzzz"))
        assertNull(parseHexColor(null))
    }

    @Test fun derivedColorIsStableAndCaseInsensitive() {
        assertEquals(colorForName("Food"), colorForName("Food"))
        assertEquals(colorForName("Food"), colorForName(" food "))
        assertNotEquals(colorForName("Food"), colorForName("Travel"))
        assertEquals(0xFF000000.toInt(), colorForName("Food") and 0xFF000000.toInt())
    }
}
