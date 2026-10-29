package com.expensetracker.repository;

import com.expensetracker.model.Category;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends DocumentRepository<Category> {
    @Query("{ 'user': ?0 }")
    List<Category> findByUserId(Long userId);
    @Query("{ '$or': [ { 'user': ?0 }, { 'user': null } ] }")
    List<Category> findByUserIdOrUserIsNull(Long userId);
    boolean existsByNameIgnoreCaseAndUserIsNull(String name);

    @Query(value = "{ 'user': ?0 }", delete = true)
    long deleteByUserId(Long userId);

    /** A category the user may attach to an expense: their own, or a global (user-less) one. */
    @Query("{ '_id': ?0, '$or': [ { 'user': null }, { 'user': ?1 } ] }")
    Optional<Category> findUsableById(Long id, Long userId);
}
