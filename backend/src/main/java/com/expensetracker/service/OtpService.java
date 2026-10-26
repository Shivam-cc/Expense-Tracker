package com.expensetracker.service;

import com.expensetracker.exception.TooManyRequestsException;
import com.expensetracker.model.OtpCode;
import com.expensetracker.model.OtpPurpose;
import com.expensetracker.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Generates, stores, and verifies time-limited 6-digit OTPs. Codes live in the {@code otp_codes} table
 * as an HMAC (keyed with the server secret), never in plaintext.
 * <p>
 * Abuse limits are tracked per (email, purpose) over a one-hour window and are NOT reset by requesting
 * a new code, so resending cannot be used to get unlimited guesses:
 * at most {@value #MAX_SENDS_PER_WINDOW} codes and {@value #MAX_FAILED_ATTEMPTS} wrong guesses
 * per window, and at least {@value #MIN_RESEND_SECONDS}s between sends.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    static final int OTP_EXPIRY_SECONDS = 300;
    static final int MIN_RESEND_SECONDS = 30;
    static final int MAX_SENDS_PER_WINDOW = 5;
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofHours(1);

    private final OtpCodeRepository otpCodeRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.jwt.secret:}")
    private String hmacSecret;

    /**
     * Generates a new OTP for the email, replacing any previous one for the same purpose.
     * @throws TooManyRequestsException if the email is being throttled
     */
    @Transactional
    public String generateAndStore(String email, OtpPurpose purpose) {
        String key = normalize(email);
        LocalDateTime now = LocalDateTime.now();
        String otp = String.format("%06d", random.nextInt(1_000_000));

        OtpCode row = otpCodeRepository.findForUpdate(key, purpose).orElse(null);
        if (row == null) {
            row = OtpCode.builder()
                    .email(key)
                    .purpose(purpose)
                    .windowStart(now)
                    .build();
        } else if (now.isAfter(row.getWindowStart().plus(WINDOW))) {
            row.setWindowStart(now);
            row.setSendCount(0);
            row.setAttempts(0);
        } else {
            if (now.isBefore(row.getLastSentAt().plusSeconds(MIN_RESEND_SECONDS))) {
                throw new TooManyRequestsException(
                        "Please wait " + MIN_RESEND_SECONDS + " seconds before requesting another code.");
            }
            if (row.getSendCount() >= MAX_SENDS_PER_WINDOW) {
                throw new TooManyRequestsException("Too many verification codes requested. Try again later.");
            }
        }

        row.setCodeHash(hash(key, purpose, otp));
        row.setExpiresAt(now.plusSeconds(OTP_EXPIRY_SECONDS));
        row.setLastSentAt(now);
        row.setSendCount(row.getSendCount() + 1);
        otpCodeRepository.save(row);
        return otp;
    }

    /**
     * Verifies the OTP. Returns {@code true} and consumes it if valid.
     * @throws TooManyRequestsException once the failed-attempt budget for the window is spent
     */
    // REQUIRES_NEW: the failed-attempt counter must persist even if the caller's transaction rolls back on the error it throws.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean verify(String email, OtpPurpose purpose, String otp) {
        String key = normalize(email);
        OtpCode row = otpCodeRepository.findForUpdate(key, purpose).orElse(null);
        if (row == null) return false;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(row.getWindowStart().plus(WINDOW))) {
            otpCodeRepository.delete(row);
            return false;
        }
        if (row.getAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new TooManyRequestsException("Too many incorrect attempts. Please try again later.");
        }
        if (now.isAfter(row.getExpiresAt())) return false;

        boolean match = otp != null && MessageDigest.isEqual(
                hash(key, purpose, otp).getBytes(StandardCharsets.UTF_8),
                row.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (match) {
            otpCodeRepository.delete(row);
            return true;
        }

        row.setAttempts(row.getAttempts() + 1);
        otpCodeRepository.save(row);
        return false;
    }

    @Transactional
    public void deleteAllForEmail(String email) {
        otpCodeRepository.deleteByEmail(normalize(email));
    }

    @Scheduled(fixedDelay = 300_000L)
    @Transactional
    public void evictExpired() {
        otpCodeRepository.deleteByWindowStartBefore(LocalDateTime.now().minus(WINDOW));
    }

    private String hash(String email, OtpPurpose purpose, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(("otp|" + purpose + "|" + email + "|" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
