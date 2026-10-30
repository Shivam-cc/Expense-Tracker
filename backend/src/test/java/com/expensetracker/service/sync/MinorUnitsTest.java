package com.expensetracker.service.sync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MinorUnitsTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(MinorUnits.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void exactAmountsRoundTripAtEachCurrencyExponentWithoutWarning() {
        assertThat(MinorUnits.toMinor(new BigDecimal("0.01"), "USD")).isEqualTo(1L);
        assertThat(MinorUnits.toMinor(new BigDecimal("1250.50"), "USD")).isEqualTo(125_050L);
        assertThat(MinorUnits.toMinor(new BigDecimal("12345678901234.56"), "USD")).isEqualTo(1_234_567_890_123_456L);
        assertThat(MinorUnits.toMinor(new BigDecimal("1250"), "JPY")).isEqualTo(1_250L);
        assertThat(MinorUnits.toMinor(new BigDecimal("1.235"), "KWD")).isEqualTo(1_235L);
        assertThat(MinorUnits.toAmount(1L, "USD")).isEqualByComparingTo("0.01");
        assertThat(MinorUnits.toAmount(125_050L, "USD")).isEqualByComparingTo("1250.50");
        assertThat(appender.list).isEmpty();
    }

    @Test
    void trailingZerosBeyondTheCurrencyExponentAreNotLogged() {
        assertThat(MinorUnits.toMinor(new BigDecimal("12.500"), "USD")).isEqualTo(1_250L);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void amountWithMoreDecimalsThanTheCurrencyAllowsIsRoundedAndLogged() {
        assertThat(MinorUnits.toMinor(new BigDecimal("12.345"), "USD")).isEqualTo(1_235L);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("12.345", "USD");
    }
}
