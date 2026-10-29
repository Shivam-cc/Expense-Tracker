package com.expensetracker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

@Document("categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category implements IdentifiedDocument {

    @Id
    private Long id;

    @NotBlank
    private String name;

    private String icon;

    private String color;

    @DocumentReference
    private User user;

    /** Server-assigned UTC write time; drives sync pulls. */
    @LastModifiedDate
    private Instant updatedAt;

    /** Soft-delete tombstone: set instead of removing the document so sync clients learn about deletions. */
    private Instant deletedAt;

    /** Client-generated UUID that makes create retries idempotent per user. */
    private String clientId;
}
