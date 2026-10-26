package com.expensetracker.service;

import com.expensetracker.exception.TooManyRequestsException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates, stores, and verifies time-limited 6-digit OTPs (in-memory, single instance).
 * <p>
 * Abuse limits are tracked per email over a one-hour window and are NOT reset by requesting
 * a new code, so resending cannot be used to get unlimited guesses:
 * at most {@value #MAX_SENDS_PER_WINDOW} codes and {@value #MAX_FAILED_ATTEMPTS} wrong guesses
 * per window, and at least {@value #MIN_RESEND_SECONDS}s between sends.
 */
@Service
public class OtpService {

    static final int OTP_EXPIRY_SECONDS = 300;
    static final int MIN_RESEND_SECONDS = 30;
    static final int MAX_SENDS_PER_WINDOW = 5;
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private static final class State {
        String otp;
        Instant expiry;
        Instant lastSent;
        Instant windowStart;
        int sendCount;
        int attempts;
    }

    private final Map<String, State> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a new OTP for the email, replacing any previous one.
     * @throws TooManyRequestsException if the email is being throttled
     */
    public String generateAndStore(String email) {
        String key = normalize(email);
        Instant now = Instant.now();
        String otp = String.format("%06d", random.nextInt(1_000_000));

        // compute() makes the check-and-update atomic per email
        store.compute(key, (k, state) -> {
            if (state == null || now.isAfter(state.windowStart.plus(WINDOW))) {
                state = new State();
                state.windowStart = now;
            }
            if (state.lastSent != null && now.isBefore(state.lastSent.plusSeconds(MIN_RESEND_SECONDS))) {
                throw new TooManyRequestsException(
                        "Please wait " + MIN_RESEND_SECONDS + " seconds before requesting another code.");
            }
            if (state.sendCount >= MAX_SENDS_PER_WINDOW) {
                throw new TooManyRequestsException("Too many verification codes requested. Try again later.");
            }
            state.otp = otp;
            state.expiry = now.plusSeconds(OTP_EXPIRY_SECONDS);
            state.lastSent = now;
            state.sendCount++;
            return state;
        });
        return otp;
    }

    /**
     * Verifies the OTP. Returns {@code true} and consumes it if valid.
     * @throws TooManyRequestsException once the failed-attempt budget for the window is spent
     */
    public boolean verify(String email, String otp) {
        String key = normalize(email);
        boolean[] result = {false};

        store.computeIfPresent(key, (k, state) -> {
            Instant now = Instant.now();
            if (now.isAfter(state.windowStart.plus(WINDOW))) return null;
            if (state.attempts >= MAX_FAILED_ATTEMPTS) {
                throw new TooManyRequestsException("Too many incorrect attempts. Please try again later.");
            }
            if (now.isAfter(state.expiry)) return state;

            if (otp != null && MessageDigest.isEqual(
                    state.otp.getBytes(StandardCharsets.UTF_8), otp.getBytes(StandardCharsets.UTF_8))) {
                result[0] = true;
                return null;
            }

            state.attempts++;
            return state;
        });
        return result[0];
    }

    @Scheduled(fixedDelay = 300_000L)
    void evictExpired() {
        Instant now = Instant.now();
        store.values().removeIf(s -> now.isAfter(s.windowStart.plus(WINDOW)));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
