package com.expensetracker.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface DocumentRepository<T> extends MongoRepository<T, Long> {
    Optional<T> findForUpdate(Query query);
}
