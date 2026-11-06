package com.expensetracker.android

import kotlinx.datetime.LocalDate

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun friendlyDate(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date.toEpochDays() == today.toEpochDays() - 1 -> "Yesterday"
    else -> {
        val base = "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]}"
        if (date.year == today.year) base else "$base ${date.year}"
    }
}

/** Parses "#RRGGBB" (or "RRGGBB") into an opaque ARGB int, or null when invalid. */
fun parseHexColor(hex: String?): Int? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    val rgb = digits.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or rgb
}

/** Stable colour derived from a category name (case-insensitive), as opaque ARGB. */
fun colorForName(name: String): Int {
    var hash = 7
    for (ch in name.trim().lowercase()) hash = hash * 31 + ch.code
    val hue = ((hash % 360) + 360) % 360
    return hslToArgb(hue.toFloat(), 0.55f, 0.50f)
}

private fun hslToArgb(h: Float, s: Float, l: Float): Int {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r, g, b) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return 0xFF000000.toInt() or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
