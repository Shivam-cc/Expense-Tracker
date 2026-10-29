package com.expensetracker.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryV1Response {
    private Long id;
    private String clientId;
    private String name;
    private String icon;
    private String color;
    /** True for built-in categories shared by all users; these are read-only. */
    private boolean global;
    private Instant updatedAt;
    /** Set on tombstones (soft-deleted categories); null for live rows. */
    private Instant deletedAt;
}
