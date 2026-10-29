package com.expensetracker.repository;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

public class LockingMongoRepository<T> extends SimpleMongoRepository<T, Long> implements DocumentRepository<T> {
    private final MongoOperations operations;
    private final Class<T> entityType;

    public LockingMongoRepository(MongoEntityInformation<T, Long> information, MongoOperations operations) {
        super(information, operations);
        this.operations = operations;
        this.entityType = information.getJavaType();
    }

    @Override
    public Optional<T> findForUpdate(Query query) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A document lock requires an active MongoDB transaction");
        }
        // A write acquires a transaction-scoped lock; a plain MongoDB read would allow token/OTP reuse races.
        return Optional.ofNullable(operations.findAndModify(query, new Update().inc("_lock", 1),
                FindAndModifyOptions.options().returnNew(true), entityType));
    }
}
