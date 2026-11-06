package com.expensetracker.android

import kotlinx.datetime.LocalDate

data class SpendEntry(val amountMinor: Long, val currency: String, val date: LocalDate, val deleted: Boolean = false)

/** Sums non-deleted entries in [currency] dated in the same year and month as [today], in minor units. */
fun monthTotalMinor(entries: List<SpendEntry>, currency: String, today: LocalDate): Long =
    entries.asSequence()
        .filter { !it.deleted && it.currency.equals(currency, ignoreCase = true) }
        .filter { it.date.year == today.year && it.date.monthNumber == today.monthNumber }
        .sumOf { it.amountMinor }
