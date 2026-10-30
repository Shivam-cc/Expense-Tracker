package com.expensetracker.mobile.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.expensetracker.mobile.android.AppContextHolder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual class TokenStore actual constructor() {

    actual fun load(): StoredSession? = runBlocking {
        val prefs = dataStore.data.first()
        try {
            decodeSession(prefs)
        } catch (e: GeneralSecurityException) {
            clearStore()
            null
        } catch (e: IllegalArgumentException) {
            clearStore()
            null
        }
    }

    actual fun save(session: StoredSession) {
        // Each value is encrypted with a fresh IV; DataStore.edit is atomic and completes before returning,
        // so a rotated refresh token is on disk when save returns (same guarantee the old commit() gave).
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_ACCESS_TOKEN] = encrypt(session.accessToken)
                prefs[KEY_ACCESS_EXPIRES_AT] = encrypt(session.accessExpiresAt.toEpochMilliseconds().toString())
                prefs[KEY_REFRESH_TOKEN] = encrypt(session.refreshToken)
                prefs[KEY_EMAIL] = encrypt(session.email)
                prefs[KEY_NAME] = encrypt(session.name)
                val currency = session.currency
                if (currency != null) {
                    prefs[KEY_CURRENCY] = encrypt(currency)
                } else {
                    prefs.remove(KEY_CURRENCY)
                }
            }
        }
    }

    actual fun clear() {
        runBlocking { clearStore() }
    }

    private suspend fun clearStore() {
        dataStore.edit { it.clear() }
    }

    private fun decodeSession(prefs: Preferences): StoredSession? {
        val accessToken = decryptField(prefs, KEY_ACCESS_TOKEN) ?: return null
        val refreshToken = decryptField(prefs, KEY_REFRESH_TOKEN) ?: return null
        val email = decryptField(prefs, KEY_EMAIL) ?: return null
        val name = decryptField(prefs, KEY_NAME) ?: return null
        val accessExpiresAtMillis = decryptField(prefs, KEY_ACCESS_EXPIRES_AT)?.toLong() ?: return null
        return StoredSession(
            accessToken = accessToken,
            accessExpiresAt = Instant.fromEpochMilliseconds(accessExpiresAtMillis),
            refreshToken = refreshToken,
            email = email,
            name = name,
            currency = prefs[KEY_CURRENCY]?.let(::decrypt),
        )
    }

    private fun decryptField(prefs: Preferences, key: Preferences.Key<String>): String? =
        prefs[key]?.let(::decrypt)

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        // The Keystore picks a random IV per encryption, so it must be stored with the ciphertext to decrypt later.
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR)
        require(parts.size == 2) { "Malformed encrypted token value" }
        val key = loadKey() ?: throw UnrecoverableKeyException("Token key is missing from the Android Keystore")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).decodeToString()
    }

    private fun encryptionKey(): SecretKey = loadKey() ?: createKey()

    private fun loadKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    }

    private fun createKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val STORE_NAME = "expenseflow_tokens"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "expenseflow_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_BITS = 128
        const val SEPARATOR = ":"

        val dataStore: DataStore<Preferences> by lazy {
            PreferenceDataStoreFactory.create(
                produceFile = { AppContextHolder.appContext.preferencesDataStoreFile(STORE_NAME) },
            )
        }

        val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        val KEY_ACCESS_EXPIRES_AT = stringPreferencesKey("access_expires_at")
        val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_CURRENCY = stringPreferencesKey("currency")
    }
}
