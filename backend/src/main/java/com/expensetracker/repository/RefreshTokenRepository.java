package com.expensetracker.repository;

import com.expensetracker.model.RefreshToken;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends DocumentRepository<RefreshToken> {
    default Optional<RefreshToken> findByTokenHashForUpdate(String hash) {
        return findForUpdate(new org.springframework.data.mongodb.core.query.Query(Criteria.where("tokenHash").is(hash)));
    }

    @Query("{ 'familyId': ?0, 'revokedAt': null }")
    @Update("{ '$set': { 'revokedAt': ?1 } }")
    long revokeFamily(String familyId, LocalDateTime now);

    @Query("{ 'userId': ?0, 'revokedAt': null }")
    @Update("{ '$set': { 'revokedAt': ?1 } }")
    long revokeAllForUser(Long userId, LocalDateTime now);

    /** Reduces the user's rows to token hash, accountDeleted and deletedAt. Everything else is removed. */
    @Query("{ 'userId': ?0 }")
    @Update("{ '$set': { 'accountDeleted': true, 'deletedAt': ?1 }, "
            + "'$unset': { 'userId': '', 'familyId': '', 'replacedBy': '', 'revokedAt': '', 'createdAt': '', 'expiresAt': '' } }")
    long detachAccount(Long userId, LocalDateTime deletedAt);

    // Detached rows have no expiresAt, and are left to the TTL index on deletedAt.
    @Query(value = "{ 'accountDeleted': { '$ne': true }, 'expiresAt': { '$lt': ?0 } }", delete = true)
    long deleteExpiredBefore(LocalDateTime cutoff);
}
