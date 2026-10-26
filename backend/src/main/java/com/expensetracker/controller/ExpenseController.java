package com.expensetracker.controller;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.PageResponse;
import com.expensetracker.dto.SummaryResponse;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.model.User;
import com.expensetracker.service.ExpenseCsvWriter;
import com.expensetracker.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> SORTABLE_FIELDS = List.of("date", "amount", "description", "createdAt");
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ExpenseService expenseService;

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(
            @Valid @RequestBody ExpenseRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(expenseService.createExpense(request, user));
    }

    /**
     * Pages through the caller's expenses. Optional filters: {@code q} (case-insensitive match on
     * description/notes), {@code categoryId} ({@code 0} = uncategorized), {@code startDate}, {@code endDate}.
     */
    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> getExpenses(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("sortBy must be one of " + SORTABLE_FIELDS);
        }
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        // id as tie-breaker keeps page boundaries stable when many rows share a date
        PageRequest pageRequest = PageRequest.of(page, clampedSize, sort.and(Sort.by("id").descending()));
        return ResponseEntity.ok(expenseService.getUserExpenses(user, q, categoryId, startDate, endDate, pageRequest));
    }

    /** Downloads the filtered expenses as CSV (same filters as the list endpoint, without paging). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExpenses(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String csv = ExpenseCsvWriter.write(
                expenseService.findForExport(user, q, categoryId, startDate, endDate), user.getCurrency());
        // UTF-8 BOM so Excel detects the encoding of non-ASCII descriptions
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);

        String filename = "expenses-" + (startDate != null ? startDate : "beginning")
                + "-" + (endDate != null ? endDate : LocalDate.now()) + ".csv";
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpense(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(expenseService.getExpenseById(id, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(expenseService.updateExpense(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        expenseService.deleteExpense(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public ResponseEntity<SummaryResponse> getExpenseSummary(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(expenseService.getSummary(user, startDate, endDate));
    }
}
