package com.expensetracker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;

import java.time.LocalDateTime;

/** Server-side record of an opaque refresh token. Only the SHA-256 of the token is stored. */
@Document("refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken implements IdentifiedDocument {

    @Id
    private Long id;

    private Long userId;

    private String tokenHash;

    /** All tokens descended from one login share a family; reuse of a revoked token revokes the family. */
    private String familyId;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;

    private Long replacedBy;

    /**
     * Set when the owning account is deleted. The row is then reduced to {@code tokenHash}, this flag and
     * {@link #deletedAt}, so refresh can report the deletion. A TTL index on {@code deletedAt} removes it.
     */
    private boolean accountDeleted;

    /** When the account was deleted. Only present on rows with {@link #accountDeleted}. */
    private LocalDateTime deletedAt;
}
