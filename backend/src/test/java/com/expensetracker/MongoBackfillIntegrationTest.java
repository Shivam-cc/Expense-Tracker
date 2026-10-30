package com.expensetracker;

import com.expensetracker.config.MongoDatabaseInitializer;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.support.IntegrationTestBase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

class MongoBackfillIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MongoTemplate template;

    @Autowired
    private MongoDatabaseInitializer initializer;

    @Autowired
    private UserRepository userRepository;

    @Test
    void backfillFillsMissingUpdatedAtOnlyAndRerunsChangeNothing() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        long base = 800_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        long legacyExpenseId = base;
        long preservedExpenseId = base + 1;
        long legacyCategoryId = base + 2;
        Date createdAt = Date.from(Instant.parse("2026-03-15T10:15:30Z"));
        Date existingUpdatedAt = Date.from(Instant.parse("2026-01-01T00:00:00Z"));

        template.getCollection("expenses").insertOne(new Document("_id", legacyExpenseId)
                .append("user", userId).append("description", "legacy").append("createdAt", createdAt));
        template.getCollection("expenses").insertOne(new Document("_id", preservedExpenseId)
                .append("user", userId).append("description", "already stamped").append("createdAt", createdAt)
                .append("updatedAt", existingUpdatedAt));
        template.getCollection("categories").insertOne(new Document("_id", legacyCategoryId)
                .append("user", userId).append("name", "Legacy"));
        try {
            initializer.run(new DefaultApplicationArguments());

            Document expense = findById("expenses", legacyExpenseId);
            assertThat(expense.get("updatedAt")).isEqualTo(createdAt);
            assertThat(findById("expenses", preservedExpenseId).get("updatedAt")).isEqualTo(existingUpdatedAt);
            Document category = findById("categories", legacyCategoryId);
            assertThat(category.get("updatedAt")).isInstanceOf(Date.class);

            Document expenseAfterFirstRun = findById("expenses", legacyExpenseId);
            Document categoryAfterFirstRun = findById("categories", legacyCategoryId);
            assertThat(template.getCollection("categories").countDocuments(new Document("updatedAt", null))).isZero();
            assertThat(template.getCollection("expenses").countDocuments(new Document("updatedAt", null))).isZero();

            initializer.run(new DefaultApplicationArguments());

            assertThat(findById("expenses", legacyExpenseId)).isEqualTo(expenseAfterFirstRun);
            assertThat(findById("categories", legacyCategoryId)).isEqualTo(categoryAfterFirstRun);
            assertThat(findById("expenses", preservedExpenseId).get("updatedAt")).isEqualTo(existingUpdatedAt);
        } finally {
            template.getCollection("expenses").deleteMany(new Document("_id",
                    new Document("$in", List.of(legacyExpenseId, preservedExpenseId))));
            template.getCollection("categories").deleteMany(new Document("_id", legacyCategoryId));
        }
    }

    @Test
    void backfillGivesLegacyOwnersAndExpensesACurrencyAndKeepsExistingOnes() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        template.getCollection("users").updateOne(new Document("_id", userId),
                new Document("$unset", new Document("currency", "")));

        long base = 800_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);
        long legacyExpenseId = base + 10;
        long keptExpenseId = base + 11;
        Date createdAt = Date.from(Instant.parse("2026-03-15T10:15:30Z"));
        template.getCollection("expenses").insertOne(new Document("_id", legacyExpenseId)
                .append("user", userId).append("description", "legacy").append("createdAt", createdAt));
        template.getCollection("expenses").insertOne(new Document("_id", keptExpenseId)
                .append("user", userId).append("description", "kept").append("currency", "EUR")
                .append("createdAt", createdAt));
        try {
            initializer.run(new DefaultApplicationArguments());

            assertThat(findById("users", userId).getString("currency")).isEqualTo("INR");
            assertThat(findById("expenses", legacyExpenseId).getString("currency")).isEqualTo("INR");
            assertThat(findById("expenses", keptExpenseId).getString("currency")).isEqualTo("EUR");
        } finally {
            template.getCollection("expenses").deleteMany(new Document("_id",
                    new Document("$in", List.of(legacyExpenseId, keptExpenseId))));
        }
    }

    private Document findById(String collection, long id) {
        return template.getCollection(collection).find(new Document("_id", id)).first();
    }
}
