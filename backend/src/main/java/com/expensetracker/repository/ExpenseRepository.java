package com.expensetracker.repository;

import com.expensetracker.dto.CategorySpend;
import com.expensetracker.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /** Category is fetched in the same query so mapping a page of expenses doesn't issue one query per row. */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Expense> findAll(Specification<Expense> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM Expense e WHERE e.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

    // Not scoped by user: rows pointing at someone else's category (possible before ownership checks existed) would break the FK too.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Expense e SET e.category = null WHERE e.category.id = :categoryId")
    int clearCategory(@Param("categoryId") Long categoryId);

    @Query("SELECT SUM(e.amount) FROM Expense e WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpensesByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** LEFT JOIN keeps uncategorized expenses (null category); grouping by id keeps same-named categories apart. */
    @Query("""
            SELECT new com.expensetracker.dto.CategorySpend(c.id, c.name, c.color, c.icon, SUM(e.amount))
            FROM Expense e LEFT JOIN e.category c
            WHERE e.user.id = :userId AND e.date BETWEEN :startDate AND :endDate
            GROUP BY c.id, c.name, c.color, c.icon
            ORDER BY SUM(e.amount) DESC
            """)
    List<CategorySpend> getExpensesByCategory(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
