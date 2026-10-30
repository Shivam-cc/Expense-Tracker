package com.expensetracker.mobile.util

/**
 * Money helpers. Amounts are Long minor units of a currency, never floats. The number of minor digits per
 * currency (ISO 4217 exponent) must match the backend's `MinorUnits`, which uses `java.util.Currency`.
 */
object Money {
    private val ZERO_DECIMAL = setOf(
        "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
    )
    private val THREE_DECIMAL = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")

    /** Minor digits for [currency]: 0 for JPY-style, 3 for KWD-style, otherwise 2. */
    fun minorDigits(currency: String): Int = when (currency) {
        in ZERO_DECIMAL -> 0
        in THREE_DECIMAL -> 3
        else -> 2
    }

    /** Parses e.g. "12", "12.5", or "-3.07" into minor units. Rejects more fraction digits than the currency has. */
    fun parseMinor(value: String, digits: Int = 2): Long {
        val trimmed = value.trim()
        val negative = trimmed.startsWith("-")
        val body = if (negative) trimmed.substring(1) else trimmed
        val parts = body.split(".")
        require(parts.size <= 2 && parts[0].isNotEmpty() && parts[0].all { it.isDigit() }) { "Invalid amount: $value" }
        val fraction = if (parts.size == 2) parts[1] else ""
        require(fraction.length <= digits && fraction.all { it.isDigit() }) { "Invalid amount: $value" }
        val scale = pow10(digits)
        val total = parts[0].toLong() * scale + (fraction.padEnd(digits, '0').ifEmpty { "0" }).toLong()
        return if (negative) -total else total
    }

    /** Formats minor units with [digits] decimals, e.g. 1250 with 2 digits -> "12.50", 1250 with 0 -> "1250". */
    fun formatMinor(minor: Long, digits: Int = 2): String {
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor < 0) -minor else minor
        if (digits == 0) return "$sign$abs"
        val scale = pow10(digits)
        return "$sign${abs / scale}.${(abs % scale).toString().padStart(digits, '0')}"
    }

    private fun pow10(digits: Int): Long {
        var result = 1L
        repeat(digits) { result *= 10 }
        return result
    }
}
