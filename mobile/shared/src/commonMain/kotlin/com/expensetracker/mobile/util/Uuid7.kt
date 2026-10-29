package com.expensetracker.mobile.util

import kotlinx.datetime.Clock
import kotlin.random.Random

/** RFC 9562 UUIDv7 generator: 48-bit millisecond timestamp followed by random bits. */
object Uuid7 {
    fun generate(
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
        random: Random = Random.Default,
    ): String {
        val bytes = ByteArray(16)
        for (i in 0 until 6) {
            bytes[i] = (nowMillis ushr (40 - 8 * i)).toByte()
        }
        random.nextBytes(bytes, 6, 16)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
