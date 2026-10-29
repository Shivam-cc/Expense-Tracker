package com.expensetracker.mobile.auth

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.CoreFoundation.CFBridgingRelease
import platform.CoreFoundation.CFBridgingRetain
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "com.expensetracker.mobile.session"
private const val KEYCHAIN_ACCOUNT = "expenseflow_session"

@Serializable
private class SessionRecord(
    val accessToken: String,
    val accessExpiresAtMillis: Long,
    val refreshToken: String,
    val email: String,
    val name: String,
    val currency: String? = null,
)

/** Stores the session as one JSON-encoded generic-password Keychain item. */
actual class TokenStore actual constructor() {
    actual fun load(): StoredSession? {
        val json = readItem() ?: return null
        val record = runCatching { Json.decodeFromString(SessionRecord.serializer(), json) }.getOrNull() ?: return null
        return StoredSession(
            accessToken = record.accessToken,
            accessExpiresAt = Instant.fromEpochMilliseconds(record.accessExpiresAtMillis),
            refreshToken = record.refreshToken,
            email = record.email,
            name = record.name,
            currency = record.currency,
        )
    }

    actual fun save(session: StoredSession) {
        val json = Json.encodeToString(
            SessionRecord.serializer(),
            SessionRecord(
                accessToken = session.accessToken,
                accessExpiresAtMillis = session.accessExpiresAt.toEpochMilliseconds(),
                refreshToken = session.refreshToken,
                email = session.email,
                name = session.name,
                currency = session.currency,
            ),
        )
        // SecItemAdd fails with errSecDuplicateItem when the item exists, so remove it first.
        clear()
        writeItem(json)
    }

    actual fun clear() {
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to KEYCHAIN_SERVICE,
            kSecAttrAccount to KEYCHAIN_ACCOUNT,
        )
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun readItem(): String? = memScoped {
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to KEYCHAIN_SERVICE,
            kSecAttrAccount to KEYCHAIN_ACCOUNT,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<COpaquePointerVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess || result.value == null) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    private fun writeItem(json: String) {
        val attributes = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to KEYCHAIN_SERVICE,
            kSecAttrAccount to KEYCHAIN_ACCOUNT,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
            kSecValueData to NSString.create(string = json).dataUsingEncoding(NSUTF8StringEncoding),
        )
        val status = SecItemAdd(attributes, null)
        CFRelease(attributes)
        check(status == errSecSuccess) { "Keychain write failed with status $status" }
    }

    /**
     * Builds a retained CFDictionary. Constants (CF pointers) are stored as-is; Kotlin values are bridged to
     * Foundation objects. The dictionary retains what it stores, so bridged temporaries are released here.
     */
    private fun cfDictionary(vararg entries: Pair<COpaquePointer?, Any?>): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(
            null,
            entries.size.toLong(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        for ((key, value) in entries) {
            if (value is COpaquePointer) {
                CFDictionarySetValue(dict, key, value)
            } else {
                val bridged = CFBridgingRetain(value)
                CFDictionarySetValue(dict, key, bridged)
                CFRelease(bridged)
            }
        }
        return dict
    }
}
