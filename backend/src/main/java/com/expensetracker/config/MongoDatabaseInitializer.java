package com.expensetracker.config;

import com.mongodb.client.MongoCollection;
import com.expensetracker.model.*;
import com.expensetracker.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Collation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MongoDatabaseInitializer implements ApplicationRunner {
    public static final String DELETED_REFRESH_TTL_INDEX = "refresh_tokens_deletedAt_ttl";
    private static final String DEFAULT_CURRENCY = new User().getCurrency();

    private final MongoTemplate template;
    private final MongoDocumentIds ids;

    @Override
    public void run(ApplicationArguments args) {
        Document hello = template.executeCommand(new Document("hello", 1));
        if (!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) {
            throw new IllegalStateException("MongoDB must be a replica set or sharded cluster to support transactions");
        }
        for (Class<?> type : new Class<?>[]{User.class, Category.class, Expense.class, OtpCode.class, RefreshToken.class}) {
            if (!template.collectionExists(type)) template.createCollection(type);
        }
        template.indexOps(User.class).ensureIndex(new Index().on("email", Sort.Direction.ASC).unique()
                .collation(Collation.of("en").strength(Collation.ComparisonLevel.secondary())));
        template.indexOps(OtpCode.class).ensureIndex(new Index().on("email", Sort.Direction.ASC)
                .on("purpose", Sort.Direction.ASC).unique());
        template.indexOps(OtpCode.class).ensureIndex(new Index().on("windowStart", Sort.Direction.ASC));
        template.indexOps(RefreshToken.class).ensureIndex(new Index().on("tokenHash", Sort.Direction.ASC).unique());
        for (String field : new String[]{"userId", "familyId", "expiresAt"}) {
            template.indexOps(RefreshToken.class).ensureIndex(new Index().on(field, Sort.Direction.ASC));
        }
        // Rows of deleted accounts carry only tokenHash, accountDeleted and deletedAt, and expire from deletedAt.
        template.indexOps(RefreshToken.class).ensureIndex(new Index().named(DELETED_REFRESH_TTL_INDEX)
                .on("deletedAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(RefreshTokenService.DELETED_ACCOUNT_RETENTION_DAYS)));
        template.indexOps(Category.class).ensureIndex(new Index().on("user", Sort.Direction.ASC));
        template.indexOps(Category.class).ensureIndex(new Index().on("name", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("user").is(null))));
        template.indexOps(Expense.class).ensureIndex(new Index().on("user", Sort.Direction.ASC)
                .on("date", Sort.Direction.DESC).on("_id", Sort.Direction.DESC));
        template.indexOps(Expense.class).ensureIndex(new Index().on("category", Sort.Direction.ASC));
        ensureSyncIndexes();
        seedCategories();
        backfillUpdatedAt();
        backfillCurrencies();
        backfillDeletedRefreshRows();
    }

    /**
     * Rows detached by an earlier version kept more fields and have no deletedAt, so the TTL index would never
     * remove them. This stamps them with the current time and strips them down to the hash and flags.
     */
    private void backfillDeletedRefreshRows() {
        List<Document> pipeline = List.of(
                new Document("$set", new Document("deletedAt", "$$NOW")),
                new Document("$unset", List.of("userId", "familyId", "replacedBy", "revokedAt", "createdAt", "expiresAt", "_lock")));
        template.getCollection(template.getCollectionName(RefreshToken.class))
                .updateMany(new Document("accountDeleted", true).append("deletedAt", null), pipeline);
    }

    /** Sync pulls scan by owner and updatedAt; clientId is unique per owner only when present, so create retries are idempotent. */
    private void ensureSyncIndexes() {
        template.indexOps(Expense.class).ensureIndex(new Index().named("expenses_user_updatedAt")
                .on("user", Sort.Direction.ASC).on("updatedAt", Sort.Direction.ASC));
        template.indexOps(Category.class).ensureIndex(new Index().named("categories_user_updatedAt")
                .on("user", Sort.Direction.ASC).on("updatedAt", Sort.Direction.ASC));
        template.indexOps(Expense.class).ensureIndex(new Index().named("expenses_user_clientId_unique")
                .on("user", Sort.Direction.ASC).on("clientId", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("clientId").exists(true))));
        template.indexOps(Category.class).ensureIndex(new Index().named("categories_user_clientId_unique")
                .on("user", Sort.Direction.ASC).on("clientId", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("clientId").exists(true))));
    }

    /**
     * Sets updatedAt on documents that predate it, copying the stored createdAt instant (already UTC in BSON),
     * or the current time when there is none. Documents that already have updatedAt are never matched, so reruns are no-ops.
     */
    private void backfillUpdatedAt() {
        Document missingUpdatedAt = new Document("updatedAt", null);
        List<Document> pipeline = List.of(new Document("$set", new Document("updatedAt",
                new Document("$ifNull", List.of("$createdAt", "$$NOW")))));
        for (Class<?> type : new Class<?>[]{Expense.class, Category.class}) {
            template.getCollection(template.getCollectionName(type)).updateMany(missingUpdatedAt, pipeline);
        }
    }

    /**
     * Gives every owner and every legacy expense a currency, so /api/v1 never returns null. Legacy expenses take
     * their owner's currency, which matches the read-side fallback. Documents that already have a currency are
     * never matched, so reruns are no-ops.
     */
    private void backfillCurrencies() {
        MongoCollection<Document> users = template.getCollection(template.getCollectionName(User.class));
        MongoCollection<Document> expenses = template.getCollection(template.getCollectionName(Expense.class));
        users.updateMany(new Document("currency", null),
                new Document("$set", new Document("currency", DEFAULT_CURRENCY)));
        Set<Object> ownerIds = new LinkedHashSet<>();
        for (Document legacy : expenses.find(new Document("currency", null)).projection(new Document("user", 1))) {
            ownerIds.add(legacy.get("user"));
        }
        for (Object userId : ownerIds) {
            Document owner = users.find(new Document("_id", userId)).first();
            String currency = owner == null ? DEFAULT_CURRENCY : owner.getString("currency");
            expenses.updateMany(new Document("user", userId).append("currency", null),
                    new Document("$set", new Document("currency", currency)));
        }
    }

    private void seedCategories() {
        String[][] defaults = {
                {"Food", "🍔", "#3b82f6"}, {"Groceries", "🛒", "#22c55e"},
                {"Coffee", "☕", "#f97316"}, {"Train", "🚆", "#a855f7"},
                {"Car", "🚗", "#ec4899"}, {"Flights", "✈️", "#14b8a6"},
                {"Shopping", "🛍️", "#eab308"}, {"Home", "🏠", "#64748b"},
                {"Bills", "💡", "#3b82f6"}, {"Health", "💊", "#22c55e"},
                {"Fitness", "🏋️", "#f97316"}, {"Entertainment", "🎬", "#a855f7"},
                {"Education", "📚", "#ec4899"}, {"Gifts", "🎁", "#14b8a6"},
                {"Subscriptions", "📱", "#eab308"}, {"Other", "📦", "#64748b"}
        };
        for (String[] category : defaults) {
            template.upsert(new Query(Criteria.where("name").is(category[0]).and("user").is(null)),
                    new Update().setOnInsert("_id", ids.nextId("categories"))
                            .setOnInsert("name", category[0]).setOnInsert("icon", category[1])
                            .setOnInsert("color", category[2]), Category.class);
        }
    }
}
