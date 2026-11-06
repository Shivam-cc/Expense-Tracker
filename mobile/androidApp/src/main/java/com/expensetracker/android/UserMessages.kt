package com.expensetracker.android

import java.io.IOException
import java.nio.channels.UnresolvedAddressException

const val OFFLINE_MESSAGE = "You're offline. Your changes are saved and will sync when you're back online."

private val KTOR_TIMEOUT_NAMES = setOf("HttpRequestTimeoutException", "ConnectTimeoutException", "SocketTimeoutException")

/** True for connectivity failures: no host, refused connection, timeouts, or any other I/O error. */
fun isNetworkError(e: Throwable): Boolean {
    var current: Throwable? = e
    var depth = 0
    while (current != null && depth++ < 8) {
        if (current is IOException || current is UnresolvedAddressException) return true
        // Ktor timeouts are matched by name so androidApp does not need a direct ktor-client-core dependency.
        if (generateSequence<Class<*>>(current.javaClass) { it.superclass }.any { it.simpleName in KTOR_TIMEOUT_NAMES }) return true
        current = current.cause?.takeIf { it !== current }
    }
    return false
}

/** Text to show the user for [e]: a friendly offline message for network errors, otherwise the exception's own message. */
fun userMessage(e: Throwable, fallback: String): String =
    if (isNetworkError(e)) OFFLINE_MESSAGE else e.message ?: fallback
