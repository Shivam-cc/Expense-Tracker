package com.expensetracker.service.sync;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the real database: the job is a pair of MongoDB deletes. Retention is the default 90 days. */
class TombstonePurgeJobTest extends IntegrationTestBase {

    @Autowired
    private TombstonePurgeJob purgeJob;

    @Autowired
    private MongoTemplate mongoTemplate;

    private Expense expense(Instant deletedAt) {
        return mongoTemplate.insert(Expense.builder()
                .description("Purge " + UUID.randomUUID())
                .amount(new BigDecimal("1.00"))
                .currency("USD")
                .date(LocalDate.parse("2026-01-01"))
                .deletedAt(deletedAt)
                .build());
    }

    private Category category(Instant deletedAt) {
        return mongoTemplate.insert(Category.builder()
                .name("Purge " + UUID.randomUUID())
                .deletedAt(deletedAt)
                .build());
    }

    private static Instant daysAgo(long days) {
        return Instant.now().minus(Duration.ofDays(days));
    }

    @Test
    void purgesOldTombstonesAndKeepsRecentTombstonesAndLiveRows() {
        Expense oldExpense = expense(daysAgo(91));
        Expense recentExpense = expense(daysAgo(89));
        Expense liveExpense = expense(null);
        Category oldCategory = category(daysAgo(91));
        Category recentCategory = category(daysAgo(89));
        Category liveCategory = category(null);

        purgeJob.purge();

        assertThat(mongoTemplate.findById(oldExpense.getId(), Expense.class)).isNull();
        assertThat(mongoTemplate.findById(oldCategory.getId(), Category.class)).isNull();
        assertThat(mongoTemplate.findById(recentExpense.getId(), Expense.class).getDeletedAt()).isNotNull();
        assertThat(mongoTemplate.findById(recentCategory.getId(), Category.class).getDeletedAt()).isNotNull();
        assertThat(mongoTemplate.findById(liveExpense.getId(), Expense.class).getDeletedAt()).isNull();
        assertThat(mongoTemplate.findById(liveCategory.getId(), Category.class).getDeletedAt()).isNull();
    }
}
