package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ExpenseSpecifications {

    /** {@code categoryId} value meaning "expenses without a category". */
    public static final long UNCATEGORIZED = 0L;

    private ExpenseSpecifications() {}

    /** Always scoped to the user; every other filter is optional. */
    public static Specification<Expense> filter(
            Long userId, String query, Long categoryId, LocalDate startDate, LocalDate endDate) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("notes")), pattern, '\\')));
            }
            if (categoryId != null) {
                predicates.add(categoryId == UNCATEGORIZED
                        ? cb.isNull(root.get("category"))
                        : cb.equal(root.get("category").get("id"), categoryId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("date"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("date"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
