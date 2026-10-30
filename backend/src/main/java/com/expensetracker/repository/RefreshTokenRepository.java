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

    @Query("{ 'userId': ?0 }")
    @Update("{ '$set': { 'accountDeleted': true }, '$unset': { 'userId': '' } }")
    long detachAccount(Long userId);

    @Query(value = "{ 'expiresAt': { '$lt': ?0 } }", delete = true)
    long deleteExpiredBefore(LocalDateTime cutoff);
}
