package com.expensetracker.config;

import com.expensetracker.model.*;
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

@Component
@RequiredArgsConstructor
public class MongoDatabaseInitializer implements ApplicationRunner {
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
        template.indexOps(Category.class).ensureIndex(new Index().on("user", Sort.Direction.ASC));
        template.indexOps(Category.class).ensureIndex(new Index().on("name", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("user").is(null))));
        template.indexOps(Expense.class).ensureIndex(new Index().on("user", Sort.Direction.ASC)
                .on("date", Sort.Direction.DESC).on("_id", Sort.Direction.DESC));
        template.indexOps(Expense.class).ensureIndex(new Index().on("category", Sort.Direction.ASC));
        seedCategories();
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
