package com.expensetracker.mobile.util

/** Money helpers. Amounts are stored as Long minor units (cents). Floats are never used. */
object Money {
    /** Parses "12", "12.5", or "-3.07" into minor units. Rejects more than two fraction digits. */
    fun parseMinor(value: String): Long {
        val trimmed = value.trim()
        val negative = trimmed.startsWith("-")
        val body = if (negative) trimmed.substring(1) else trimmed
        val parts = body.split(".")
        require(parts.size <= 2 && parts[0].isNotEmpty() && parts[0].all { it.isDigit() }) { "Invalid amount: $value" }
        val fraction = if (parts.size == 2) parts[1] else ""
        require(fraction.length <= 2 && fraction.all { it.isDigit() }) { "Invalid amount: $value" }
        val total = parts[0].toLong() * 100 + fraction.padEnd(2, '0').toLong()
        return if (negative) -total else total
    }

    /** Formats minor units as a two-decimal string, e.g. 1250 -> "12.50". */
    fun formatMinor(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor < 0) -minor else minor
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }
}
