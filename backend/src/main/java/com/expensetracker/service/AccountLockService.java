package com.expensetracker.service;

import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Password verification with per-account lockout: {@value #MAX_FAILURES} failures within 15 minutes
 * lock the account for 15 minutes. Used for login and for re-authentication on sensitive account actions.
 */
@Service
public class AccountLockService {

    static final int MAX_FAILURES = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Counter updates run in their own transaction (see below); self-invoked @Transactional methods would bypass the proxy.
    private final TransactionTemplate requiresNew;

    public AccountLockService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isLocked(User user) {
        return user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil());
    }

    /**
     * Returns true only if the account is not locked and the password matches.
     * Failures are counted; a success clears the counter.
     */
    public boolean verifyPassword(User user, String rawPassword) {
        if (isLocked(user)) return false;
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            if (user.getFailedAttempts() > 0 || user.getLockedUntil() != null) recordSuccess(user.getId());
            return true;
        }
        recordFailure(user.getId());
        return false;
    }

    // REQUIRES_NEW: counters must persist even when the caller then throws and rolls back.
    private void recordFailure(Long userId) {
        requiresNew.executeWithoutResult(status -> userRepository.findByIdForUpdate(userId).ifPresent(user -> {
            LocalDateTime now = LocalDateTime.now();
            if (user.getLastFailedAt() == null || now.isAfter(user.getLastFailedAt().plus(FAILURE_WINDOW))) {
                user.setFailedAttempts(0);
            }
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            user.setLastFailedAt(now);
            if (user.getFailedAttempts() >= MAX_FAILURES) {
                user.setLockedUntil(now.plus(LOCK_DURATION));
                user.setFailedAttempts(0);
            }
            userRepository.save(user);
        }));
    }

    private void recordSuccess(Long userId) {
        requiresNew.executeWithoutResult(status -> userRepository.findByIdForUpdate(userId).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLastFailedAt(null);
            user.setLockedUntil(null);
            userRepository.save(user);
        }));
    }
}
