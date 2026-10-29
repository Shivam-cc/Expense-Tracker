package com.expensetracker.mobile.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Uuid7Test {
    @Test
    fun generatesUuidV7Layout() {
        val id = Uuid7.generate()
        assertEquals(36, id.length)
        assertEquals('-', id[8])
        assertEquals('-', id[13])
        assertEquals('-', id[18])
        assertEquals('-', id[23])
        assertEquals('7', id[14])
        assertTrue(id[19] in "89ab", "variant nibble was ${id[19]}")
    }

    @Test
    fun generatesDistinctValues() {
        assertNotEquals(Uuid7.generate(), Uuid7.generate())
    }

    @Test
    fun embedsTimestampInLeadingHexDigits() {
        val millis = 1_700_000_000_123L
        val id = Uuid7.generate(nowMillis = millis)
        assertEquals(millis.toString(16).padStart(12, '0'), id.replace("-", "").substring(0, 12))
    }
}
