package com.expensetracker.controller.v1;

import com.expensetracker.dto.v1.ChangesV1Response;
import com.expensetracker.dto.v1.ExpenseV1Request;
import com.expensetracker.dto.v1.ExpenseV1Response;
import com.expensetracker.dto.v1.OnCreate;
import com.expensetracker.model.User;
import com.expensetracker.service.sync.ExpenseSyncService;
import com.expensetracker.service.sync.SyncWriteResult;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseV1Controller {

    private final ExpenseSyncService expenseSyncService;

    @GetMapping("/changes")
    public ChangesV1Response<ExpenseV1Response> changes(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "200") int limit) {
        return expenseSyncService.changes(user, updatedSince, cursor, limit);
    }

    @PostMapping
    public ResponseEntity<ExpenseV1Response> create(
            @Validated({Default.class, OnCreate.class}) @RequestBody ExpenseV1Request request,
            @AuthenticationPrincipal User user) {
        SyncWriteResult<ExpenseV1Response> result = expenseSyncService.create(request, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseV1Response> update(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseV1Request request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(expenseSyncService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        expenseSyncService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
