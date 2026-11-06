package com.expensetracker.android

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessagesTest {
    class HttpRequestTimeoutException : RuntimeException("Request timeout has expired")

    @Test
    fun networkErrorsMapToOfflineMessage() {
        val errors = listOf(
            UnknownHostException("Unable to resolve host \"api.example.com\""),
            UnresolvedAddressException(),
            ConnectException("refused"),
            SocketTimeoutException("timeout"),
            IOException("boom"),
            HttpRequestTimeoutException(),
            IllegalStateException("wrapped", UnknownHostException("host")),
        )
        errors.forEach { assertEquals(OFFLINE_MESSAGE, userMessage(it, "x")) }
    }

    @Test
    fun otherErrorsKeepTheirMessage() {
        assertEquals("Bad input", userMessage(IllegalArgumentException("Bad input"), "x"))
        assertEquals("fallback", userMessage(IllegalStateException(), "fallback"))
        assertFalse(isNetworkError(IllegalStateException("nope")))
        assertTrue(isNetworkError(IOException()))
    }
}
