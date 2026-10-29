package com.expensetracker.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseV1Response {
    private Long id;
    private String clientId;
    private String description;
    private BigDecimal amount;
    private LocalDate date;
    private Long categoryId;
    private String categoryName;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    /** Set on tombstones (soft-deleted expenses); null for live rows. */
    private Instant deletedAt;
}
