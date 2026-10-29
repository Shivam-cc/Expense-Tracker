package com.expensetracker.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

public abstract class MongoIntegrationSupport {
    private static final String DATABASE = "expense_tracker_test_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv().getOrDefault("TEST_MONGODB_URI",
                "mongodb://localhost:27017/?replicaSet=rs0&directConnection=true"));
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }
}
