package com.expensetracker.repository;

import com.expensetracker.dto.CategorySpend;
import com.expensetracker.model.Expense;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@RequiredArgsConstructor
public class ExpenseQueriesImpl implements ExpenseQueries {
    private final MongoTemplate template;

    @Override
    public Page<Expense> findAll(Criteria criteria, Pageable pageable) {
        long count = template.count(new Query(criteria), Expense.class);
        return new PageImpl<>(template.find(new Query(criteria).with(pageable), Expense.class), pageable, count);
    }

    @Override
    public BigDecimal getTotalExpensesByUserAndDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        Total result = template.aggregate(newAggregation(
                match(ExpenseSpecifications.filter(userId, null, null, startDate, endDate)),
                group().sum("amount").as("total")), Expense.class, Total.class).getUniqueMappedResult();
        return result == null ? BigDecimal.ZERO : result.total();
    }

    @Override
    public List<CategorySpend> getExpensesByCategory(Long userId, LocalDate startDate, LocalDate endDate) {
        Aggregation aggregation = newAggregation(
                match(ExpenseSpecifications.filter(userId, null, null, startDate, endDate)),
                group("category").sum("amount").as("total"),
                lookup("categories", "_id", "_id", "categoryDetails"),
                unwind("categoryDetails", true),
                project("total").and("_id").as("categoryId")
                        .and("categoryDetails.name").as("name")
                        .and("categoryDetails.color").as("color")
                        .and("categoryDetails.icon").as("icon")
                        .andExclude("_id"),
                sort(Sort.Direction.DESC, "total"));
        return template.aggregate(aggregation, Expense.class, CategorySpend.class).getMappedResults();
    }

    private record Total(BigDecimal total) {}
}
