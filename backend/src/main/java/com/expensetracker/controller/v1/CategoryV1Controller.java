package com.expensetracker.controller.v1;

import com.expensetracker.dto.v1.CategoryV1Request;
import com.expensetracker.dto.v1.CategoryV1Response;
import com.expensetracker.dto.v1.ChangesV1Response;
import com.expensetracker.dto.v1.OnCreate;
import com.expensetracker.model.User;
import com.expensetracker.service.sync.CategorySyncService;
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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryV1Controller {

    private final CategorySyncService categorySyncService;

    /** Includes global categories, which clients receive read-only. */
    @GetMapping("/changes")
    public ChangesV1Response<CategoryV1Response> changes(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "200") int limit) {
        return categorySyncService.changes(user, updatedSince, cursor, limit);
    }

    @PostMapping
    public ResponseEntity<CategoryV1Response> create(
            @Validated({Default.class, OnCreate.class}) @RequestBody CategoryV1Request request,
            @AuthenticationPrincipal User user) {
        SyncWriteResult<CategoryV1Response> result = categorySyncService.create(request, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.body());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryV1Response> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryV1Request request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(categorySyncService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        categorySyncService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
