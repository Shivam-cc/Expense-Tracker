package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Optional;

public interface ExpenseRepository extends DocumentRepository<Expense>, ExpenseQueries {
    @Query("{ '_id': ?0, 'user': ?1 }")
    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    @Query(value = "{ 'user': ?0 }", delete = true)
    long deleteByUserId(Long userId);

    @Query("{ 'category': ?0 }")
    @Update("{ '$unset': { 'category': '' } }")
    long clearCategory(Long categoryId);
}
