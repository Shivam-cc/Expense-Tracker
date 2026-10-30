package com.expensetracker.mobile.util

import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps the mobile currency-digits table in step with the backend, which takes its exponents from
 * [java.util.Currency] (see MinorUnits.java). A mismatch here means a client would scale an amount
 * differently from the server.
 */
class MoneyContractTest {

    @Test
    fun minorDigitsMatchJavaCurrencyForEveryIsoCode() {
        val mismatches = Currency.getAvailableCurrencies()
            .filter { it.defaultFractionDigits >= 0 }
            .filter { Money.minorDigits(it.currencyCode) != it.defaultFractionDigits }
            .map { "${it.currencyCode}: mobile=${Money.minorDigits(it.currencyCode)} java=${it.defaultFractionDigits}" }
        assertTrue(mismatches.isEmpty(), "Currency digits differ from java.util.Currency: $mismatches")
    }

    @Test
    fun knownCurrenciesHaveTheExpectedExponent() {
        val expected = mapOf("USD" to 2, "INR" to 2, "EUR" to 2, "JPY" to 0, "KRW" to 0, "VND" to 0, "KWD" to 3, "BHD" to 3)
        expected.forEach { (code, digits) -> assertEquals(digits, Money.minorDigits(code), code) }
    }
}
