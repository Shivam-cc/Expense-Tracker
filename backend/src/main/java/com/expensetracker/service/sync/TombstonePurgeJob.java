package com.expensetracker.service.sync;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Hard-deletes expense and category tombstones whose {@code deletedAt} is older than the retention period.
 * Live rows are never touched. Clients that last synced before the horizon are sent to a full resync by the
 * change feeds, so they never depend on a purged tombstone.
 */
@Slf4j
@Component
public class TombstonePurgeJob {

    private final MongoTemplate mongoTemplate;
    private final Duration retention;

    public TombstonePurgeJob(
            MongoTemplate mongoTemplate,
            @Value("${expenseflow.tombstone.retention-days:90}") long retentionDays) {
        this.mongoTemplate = mongoTemplate;
        this.retention = Duration.ofDays(retentionDays);
    }

    /** Oldest instant a tombstone may have and still be kept. */
    public Instant horizon() {
        return SyncTime.now().minus(retention);
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void purge() {
        Instant horizon = horizon();
        Query expired = Query.query(Criteria.where("deletedAt").lt(horizon));
        long expenses = mongoTemplate.remove(expired, Expense.class).getDeletedCount();
        long categories = mongoTemplate.remove(expired, Category.class).getDeletedCount();
        log.info("Purged {} expense and {} category tombstones deleted before {}", expenses, categories, horizon);
    }
}
