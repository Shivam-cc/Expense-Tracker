package com.expensetracker.service.sync;

import com.expensetracker.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Converts between the v1 wire amount (integer minor units, e.g. cents) and the BigDecimal stored in Mongo.
 * The exponent comes from the ISO 4217 currency, so JPY has 0 digits and KWD has 3.
 */
@Slf4j
final class MinorUnits {

    private MinorUnits() {
    }

    static String requireKnownCurrency(String code) {
        try {
            return Currency.getInstance(code).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown currency code: " + code);
        }
    }

    static BigDecimal toAmount(long minor, String currencyCode) {
        return BigDecimal.valueOf(minor, exponent(currencyCode));
    }

    /**
     * Write-path amounts are exact by construction. Legacy web rows may carry more decimals than their
     * owner's currency allows, so they are rounded half-up rather than failing the whole feed. Every such
     * rounding is logged, because it is a precision loss the client cannot see.
     */
    static long toMinor(BigDecimal amount, String currencyCode) {
        int exponent = exponent(currencyCode);
        BigDecimal scaled = amount.setScale(exponent, RoundingMode.HALF_UP);
        if (amount.compareTo(scaled) != 0) {
            log.warn("Rounding amount {} {} to {} minor-unit digits; {} is not representable", amount, currencyCode,
                    exponent, amount);
        }
        return scaled.movePointRight(exponent).longValueExact();
    }

    private static int exponent(String currencyCode) {
        // XXX ("no currency") reports -1 digits; treat it as whole units.
        return Math.max(0, Currency.getInstance(requireKnownCurrency(currencyCode)).getDefaultFractionDigits());
    }
}
