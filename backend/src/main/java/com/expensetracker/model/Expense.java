package com.expensetracker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Document("expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense implements IdentifiedDocument {

    @Id
    private Long id;

    @NotBlank
    private String description;

    @NotNull
    @Positive
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;

    /** ISO 4217 code. Null on rows written before v1 currency support; those use the owner's currency. */
    private String currency;

    @NotNull
    private LocalDate date;

    @DocumentReference
    private Category category;

    @DocumentReference(lazy = true)
    private User user;

    private String notes;

    @CreatedDate
    private LocalDateTime createdAt;

    /** Server-assigned UTC write time; drives sync pulls. */
    @LastModifiedDate
    private Instant updatedAt;

    /** Soft-delete tombstone: set instead of removing the document so sync clients learn about deletions. */
    private Instant deletedAt;

    /** Client-generated UUID that makes create retries idempotent per user. */
    private String clientId;

}
