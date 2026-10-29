package com.expensetracker;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.RefreshTokenService;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MongoPersistenceIntegrationTest {
    @Test
    void documentsReferencesSessionsAndIdCountersSurviveApplicationRestart() {
        String uri = System.getenv().getOrDefault("TEST_MONGODB_URI",
                "mongodb://localhost:27017/?replicaSet=rs0&directConnection=true");
        String database = "expense_tracker_test_restart_" + UUID.randomUUID().toString().replace("-", "");
        Long userId;
        Long categoryId;
        Long expenseId;
        String refreshToken;
        try {
            try (ConfigurableApplicationContext first = start(uri, database)) {
                User user = first.getBean(UserRepository.class).save(User.builder()
                        .name("Persistence Test").email("restart@example.com")
                        .password(first.getBean(PasswordEncoder.class).encode("Passw0rd123")).build());
                userId = user.getId();
                Category category = first.getBean(CategoryRepository.class).save(Category.builder()
                        .name("Restart category").user(user).build());
                categoryId = category.getId();
                Expense expense = first.getBean(ExpenseRepository.class).save(Expense.builder()
                        .description("Survives restart").amount(new BigDecimal("1234.56"))
                        .date(LocalDate.of(2026, 3, 15)).category(category).user(user).notes("Saved notes").build());
                expenseId = expense.getId();
                assertThat(user.getCreatedAt()).isNotNull();
                assertThat(expense.getCreatedAt()).isNotNull();
                refreshToken = first.getBean(RefreshTokenService.class).startSession(user).getRefreshToken();
            }

            try (ConfigurableApplicationContext second = start(uri, database)) {
                User user = second.getBean(UserRepository.class).findByEmail("RESTART@example.com").orElseThrow();
                assertThat(user.getId()).isEqualTo(userId);
                CategoryRepository categories = second.getBean(CategoryRepository.class);
                assertThat(categories.findByUserId(userId)).extracting(Category::getId).containsExactly(categoryId);
                Category category = categories.findById(categoryId).orElseThrow();
                category.setName("Updated after restart");
                categories.save(category);

                ExpenseRepository expenses = second.getBean(ExpenseRepository.class);
                Expense restored = expenses.findByIdAndUserId(expenseId, userId).orElseThrow();
                assertThat(restored.getAmount()).isEqualByComparingTo("1234.56");
                assertThat(restored.getNotes()).isEqualTo("Saved notes");
                assertThat(restored.getCategory().getName()).isEqualTo("Updated after restart");
                assertThat(expenses.getTotalExpensesByUserAndDateRange(userId, null, null))
                        .isEqualByComparingTo("1234.56");
                assertThat(expenses.getExpensesByCategory(userId, null, null)).singleElement().satisfies(spend -> {
                    assertThat(spend.categoryId()).isEqualTo(categoryId);
                    assertThat(spend.name()).isEqualTo("Updated after restart");
                    assertThat(spend.total()).isEqualByComparingTo("1234.56");
                });
                AuthResponse refreshed = second.getBean(RefreshTokenService.class).rotate(refreshToken);
                assertThat(refreshed.getRefreshToken()).isNotEqualTo(refreshToken);

                Expense next = expenses.save(Expense.builder().description("New expense")
                        .amount(new BigDecimal("0.01")).date(restored.getDate()).user(user).build());
                assertThat(next.getId()).isGreaterThan(expenseId);
                assertThat(expenses.getTotalExpensesByUserAndDateRange(userId, null, null))
                        .isEqualByComparingTo("1234.57");
                assertThat(categories.findByUserIdOrUserIsNull(userId).stream()
                        .filter(c -> c.getUser() == null)).hasSize(16);
            }
        } finally {
            try (var client = MongoClients.create(uri)) {
                client.getDatabase(database).drop();
            }
        }
    }

    private ConfigurableApplicationContext start(String uri, String database) {
        return new SpringApplicationBuilder(ExpenseTrackerApplication.class).run(
                "--server.port=0", "--spring.data.mongodb.uri=" + uri, "--spring.data.mongodb.database=" + database);
    }
}
