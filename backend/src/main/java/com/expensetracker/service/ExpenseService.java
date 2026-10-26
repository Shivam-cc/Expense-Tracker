package com.expensetracker.service;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.PageResponse;
import com.expensetracker.dto.SummaryResponse;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.ExpenseSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    /** Hard cap on exported rows so one request can't pull an unbounded result set into memory. */
    static final int MAX_EXPORT_ROWS = 50_000;
    private static final int MAX_QUERY_LENGTH = 100;

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
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

        return mapToResponse(expenseRepository.save(expense));
    }

    public PageResponse<ExpenseResponse> getUserExpenses(
            User user, String query, Long categoryId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        requireValidRange(startDate, endDate);
        return PageResponse.from(expenseRepository
                .findAll(ExpenseSpecifications.filter(user.getId(), clip(query), categoryId, startDate, endDate), pageable)
                .map(this::mapToResponse));
    }

    /** Expenses matching the same filters as the list endpoint, newest first, capped at {@link #MAX_EXPORT_ROWS}. */
    public List<Expense> findForExport(
            User user, String query, Long categoryId, LocalDate startDate, LocalDate endDate) {
        requireValidRange(startDate, endDate);
        Pageable pageable = PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("date").descending().and(Sort.by("id").descending()));
        return expenseRepository
                .findAll(ExpenseSpecifications.filter(user.getId(), clip(query), categoryId, startDate, endDate), pageable)
                .getContent();
    }

    public ExpenseResponse getExpenseById(Long id, User user) {
        return mapToResponse(findOwned(id, user));
    }

    @Transactional
    public ExpenseResponse updateExpense(Long id, ExpenseRequest request, User user) {
        Expense expense = findOwned(id, user);
        Category category = resolveCategory(request.getCategoryId(), user);

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setNotes(request.getNotes());

        return mapToResponse(expenseRepository.save(expense));
    }

    @Transactional
    public void deleteExpense(Long id, User user) {
        expenseRepository.delete(findOwned(id, user));
    }

    public SummaryResponse getSummary(User user, LocalDate startDate, LocalDate endDate) {
        requireValidRange(startDate, endDate);
        BigDecimal total = expenseRepository.getTotalExpensesByUserAndDateRange(user.getId(), startDate, endDate);
        return new SummaryResponse(
                total != null ? total : BigDecimal.ZERO,
                expenseRepository.getExpensesByCategory(user.getId(), startDate, endDate));
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

    private static void requireValidRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must not be after endDate");
        }
    }

    private static String clip(String query) {
        return query != null && query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
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
