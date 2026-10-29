package com.expensetracker.repository;

import com.expensetracker.model.Expense;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.Instant;
import java.util.Optional;

public interface ExpenseRepository extends DocumentRepository<Expense>, ExpenseQueries {
    @Query("{ '_id': ?0, 'user': ?1, 'deletedAt': null }")
    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    @Query(value = "{ 'user': ?0 }", delete = true)
    long deleteByUserId(Long userId);

    /** Detaches live expenses from a category being deleted; {@code updatedAt} lets sync clients see the change. */
    @Query("{ 'category': ?0, 'deletedAt': null }")
    @Update("{ '$unset': { 'category': '' }, '$set': { 'updatedAt': ?1 } }")
    long clearCategory(Long categoryId, Instant updatedAt);
}
