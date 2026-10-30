package com.expensetracker.mobile.api

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Amounts travel as integer minor units of [ExpenseDto.currency] (ISO 4217), for example 1250 USD cents = 12.50.
 * No float or decimal string is involved. See docs/api/mobile-sync-v1.md.
 */
@Serializable
data class ExpenseDto(
    val id: Long,
    val clientId: String? = null,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

/** Body for POST and PUT /api/v1/expenses. clientId is required on POST and ignored by PUT. */
@Serializable
data class ExpenseWriteDto(
    val clientId: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate,
    val categoryId: Long? = null,
    val notes: String? = null,
)

@Serializable
data class CategoryDto(
    val id: Long,
    val clientId: String? = null,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val global: Boolean = false,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Serializable
data class CategoryWriteDto(
    val clientId: String,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
)

/** One page of GET /api/v1/{expenses|categories}/changes. Tombstones are included. */
@Serializable
data class ChangePage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val serverTime: Instant,
)

@Serializable
data class SendOtpRequestDto(val name: String, val email: String)

@Serializable
data class RegisterRequestDto(val name: String, val email: String, val password: String, val otp: String)

@Serializable
data class AuthRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthResponseDto(
    val token: String,
    val refreshToken: String,
    val expiresIn: Long,
    val name: String,
    val email: String,
    val currency: String? = null,
)
