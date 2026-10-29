package com.expensetracker.repository;

import com.expensetracker.model.User;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.Optional;

public interface UserRepository extends DocumentRepository<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    default Optional<User> findByEmail(String email) {
        return findByEmailIgnoreCase(email);
    }

    default Optional<User> findByIdForUpdate(Long id) {
        return findForUpdate(new org.springframework.data.mongodb.core.query.Query(Criteria.where("_id").is(id)));
    }

    boolean existsByEmailIgnoreCase(String email);

    default boolean existsByEmail(String email) {
        return existsByEmailIgnoreCase(email);
    }
}
