package com.expensetracker.service;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    public ExpenseResponse createExpense(ExpenseRequest request, User user) {
        Category category = resolveCategory(request.getCategoryId(), user);


        Expense expense = Expense.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .date(request.getDate())
                .category(category)
                .user(user)
                .notes(request.getNotes())
                .build();

        Expense saved = expenseRepository.save(expense);
        return mapToResponse(saved);
    }

    public Page<ExpenseResponse> getUserExpenses(User user, Pageable pageable) {
        return expenseRepository.findByUserId(user.getId(), pageable)
                .map(this::mapToResponse);
    }

    public ExpenseResponse getExpenseById(Long id, User user) {
        Expense expense = findOwned(id, user);


        return mapToResponse(expense);
    }

    public ExpenseResponse updateExpense(Long id, ExpenseRequest request, User user) {
        Expense expense = findOwned(id, user);


        Category category = resolveCategory(request.getCategoryId(), user);


        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setNotes(request.getNotes());

        Expense updated = expenseRepository.save(expense);
        return mapToResponse(updated);
    }

    public void deleteExpense(Long id, User user) {
        Expense expense = findOwned(id, user);


        expenseRepository.delete(expense);
    }

    public BigDecimal getTotalExpenses(User user, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = expenseRepository.getTotalExpensesByUserAndDateRange(user.getId(), startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    public Map<String, BigDecimal> getExpensesByCategory(User user, LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = expenseRepository.getExpensesByCategory(user.getId(), startDate, endDate);
        return results.stream()
                .collect(Collectors.toMap(
                        r -> r[0] != null ? (String) r[0] : "Uncategorized",
                        r -> (BigDecimal) r[1]
                ));
    }

    private Expense findOwned(Long id, User user) {
        return expenseRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Expense not found"));
    }

    /** Only the user's own categories or global ones may be attached to an expense. */
    private Category resolveCategory(Long categoryId, User user) {
        if (categoryId == null) return null;
        return categoryRepository.findUsableById(categoryId, user.getId())
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private ExpenseResponse mapToResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .date(expense.getDate())
                .categoryId(expense.getCategory() != null ? expense.getCategory().getId() : null)
                .categoryName(expense.getCategory() != null ? expense.getCategory().getName() : null)
                .notes(expense.getNotes())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
