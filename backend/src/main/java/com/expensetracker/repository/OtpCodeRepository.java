package com.expensetracker.repository;

import com.expensetracker.model.OtpCode;
import com.expensetracker.model.OtpPurpose;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpCodeRepository extends DocumentRepository<OtpCode> {
    default Optional<OtpCode> findForUpdate(String email, OtpPurpose purpose) {
        return findForUpdate(new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("email").is(email).and("purpose").is(purpose)));
    }

    @Query(value = "{ 'windowStart': { '$lt': ?0 } }", delete = true)
    long deleteByWindowStartBefore(LocalDateTime cutoff);

    @Query(value = "{ 'email': ?0 }", delete = true)
    long deleteByEmail(String email);
}
