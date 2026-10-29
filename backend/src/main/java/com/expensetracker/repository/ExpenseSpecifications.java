package com.expensetracker.repository;

import org.springframework.data.mongodb.core.query.Criteria;

import java.time.LocalDate;
import java.util.regex.Pattern;

public final class ExpenseSpecifications {

    /** {@code categoryId} value meaning "expenses without a category". */
    public static final long UNCATEGORIZED = 0L;

    private ExpenseSpecifications() {}

    /** Always scoped to the user and excludes soft-deleted tombstones; every other filter is optional. */
    public static Criteria filter(
            Long userId, String query, Long categoryId, LocalDate startDate, LocalDate endDate) {
        Criteria criteria = Criteria.where("user").is(userId).and("deletedAt").isNull();
        if (query != null && !query.isBlank()) {
            Pattern pattern = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            criteria.orOperator(Criteria.where("description").regex(pattern), Criteria.where("notes").regex(pattern));
        }
        if (categoryId != null) {
            criteria.and("category").is(categoryId == UNCATEGORIZED ? null : categoryId);
        }
        if (startDate != null || endDate != null) {
            Criteria date = criteria.and("date");
            if (startDate != null) date.gte(startDate);
            if (endDate != null) date.lte(endDate);
        }
        return criteria;
    }
}
