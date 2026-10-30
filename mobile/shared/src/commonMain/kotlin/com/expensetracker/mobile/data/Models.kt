package com.expensetracker.mobile.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * A local expense as exposed to the UI. [id] is the client UUIDv7 and stays stable across sync.
 * [serverId] is null until the first push succeeds. [amount] is formatted from [amountMinor].
 */
data class ExpenseRecord(
    val id: String,
    val serverId: Long?,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val amount: String,
    val date: LocalDate,
    val categoryClientId: String?,
    val categoryServerId: Long?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CategoryRecord(
    val id: String,
    val serverId: Long?,
    val name: String,
    val icon: String?,
    val color: String?,
    val isGlobal: Boolean,
    val updatedAt: Instant,
)

/** Outcome of [CategoryRepository.checkName]. */
sealed interface CategoryNameCheck {
    data class Valid(val name: String) : CategoryNameCheck

    data object Blank : CategoryNameCheck

    /**
     * The name matches an existing category or is reserved for a built-in one. [existing] is that category when
     * this device has it, so it can be offered for selection. It is null when the built-in has not synced yet.
     */
    data class AlreadyExists(val name: String, val existing: CategoryRecord?, val builtIn: Boolean) : CategoryNameCheck
}
