package com.expensetracker.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    /** Integer minor units of {@link #currency}. */
    private Long amountMinor;
    /** ISO 4217 code. Falls back to the owner's currency for legacy rows. */
    private String currency;
    private LocalDate date;
    private Long categoryId;
    private String categoryName;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
    /** Set on tombstones (soft-deleted expenses); null for live rows. */
    private Instant deletedAt;
}
