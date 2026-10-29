package com.expensetracker.repository;

import com.expensetracker.dto.CategorySpend;
import com.expensetracker.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseQueries {
    Page<Expense> findAll(Criteria criteria, Pageable pageable);
    BigDecimal getTotalExpensesByUserAndDateRange(Long userId, LocalDate startDate, LocalDate endDate);
    List<CategorySpend> getExpensesByCategory(Long userId, LocalDate startDate, LocalDate endDate);
}
