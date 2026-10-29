package com.expensetracker.mobile.auth

import kotlinx.datetime.Instant

data class StoredSession(
    val accessToken: String,
    val accessExpiresAt: Instant,
    val refreshToken: String,
    val email: String,
    val name: String,
    val currency: String?,
)
