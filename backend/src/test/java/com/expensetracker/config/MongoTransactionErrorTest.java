package com.expensetracker.config;

import com.mongodb.MongoException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.TransactionSystemException;

import static org.assertj.core.api.Assertions.assertThat;

class MongoTransactionErrorTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/expenses");

    @Test
    void writeConflictsAreReportedAsRetryableRatherThanDuplicateData() {
        MongoException conflict = new MongoException(112, "Write conflict");
        conflict.addLabel("TransientTransactionError");
        var response = handler.handleDataIntegrity(new DataIntegrityViolationException("Write failed", conflict), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void commitConflictsAreAlsoReportedAsRetryable() {
        MongoException conflict = new MongoException(112, "Commit conflict");
        conflict.addLabel("TransientTransactionError");
        var response = handler.handleUnexpected(new TransactionSystemException("Commit failed", conflict), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void duplicateKeysRemainConflicts() {
        var response = handler.handleDataIntegrity(new DuplicateKeyException("Duplicate email"), request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
