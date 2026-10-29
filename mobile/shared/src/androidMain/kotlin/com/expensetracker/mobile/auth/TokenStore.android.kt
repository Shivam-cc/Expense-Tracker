package com.expensetracker.mobile.auth

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.expensetracker.mobile.android.AppContextHolder
import kotlinx.datetime.Instant

actual class TokenStore actual constructor() {
    private val prefs: SharedPreferences by lazy {
        val context = AppContextHolder.appContext
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun load(): StoredSession? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        if (!prefs.contains(KEY_ACCESS_EXPIRES_AT)) return null
        return StoredSession(
            accessToken = accessToken,
            accessExpiresAt = Instant.fromEpochMilliseconds(prefs.getLong(KEY_ACCESS_EXPIRES_AT, 0L)),
            refreshToken = refreshToken,
            email = email,
            name = name,
            currency = prefs.getString(KEY_CURRENCY, null),
        )
    }

    actual fun save(session: StoredSession) {
        // commit() rather than apply(): the rotated refresh token must be on disk before the call returns.
        val editor = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putLong(KEY_ACCESS_EXPIRES_AT, session.accessExpiresAt.toEpochMilliseconds())
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_NAME, session.name)
        val currency = session.currency
        if (currency != null) editor.putString(KEY_CURRENCY, currency) else editor.remove(KEY_CURRENCY)
        editor.commit()
    }

    actual fun clear() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val PREFS_NAME = "expenseflow_session"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_CURRENCY = "currency"
    }
}
