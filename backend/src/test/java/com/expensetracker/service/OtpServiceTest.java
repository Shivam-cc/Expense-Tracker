package com.expensetracker.service;

import com.expensetracker.exception.TooManyRequestsException;
import com.expensetracker.model.OtpCode;
import com.expensetracker.model.OtpPurpose;
import com.expensetracker.repository.OtpCodeRepository;
import com.expensetracker.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the real database layer: the OTP rules depend on row locking and persisted counters. */
class OtpServiceTest extends IntegrationTestBase {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpCodeRepository otpCodeRepository;

    // findForUpdate takes a pessimistic lock and so needs a transaction; plain reads don't.
    private java.util.Optional<OtpCode> find(String email, OtpPurpose purpose) {
        return otpCodeRepository.findAll().stream()
                .filter(o -> o.getEmail().equals(email) && o.getPurpose() == purpose).findFirst();
    }

    private OtpCode row(String email, OtpPurpose purpose) {
        return find(email, purpose).orElseThrow();
    }

    /** Pretends the previous send happened a while ago so the resend cooldown does not interfere. */
    private void ageLastSend(String email, OtpPurpose purpose) {
        OtpCode row = row(email, purpose);
        row.setLastSentAt(LocalDateTime.now().minusMinutes(5));
        otpCodeRepository.save(row);
    }

    @Test
    void generatedCodeIsSixDigitsAndVerifiesOnce() {
        String email = uniqueEmail();

        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        assertThat(otp).matches("\\d{6}");
        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, otp)).isTrue();
        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, otp)).as("codes are single-use").isFalse();
    }

    @Test
    void codeIsStoredHashedNeverInPlaintext() {
        String email = uniqueEmail();

        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        String stored = row(email, OtpPurpose.SIGNUP).getCodeHash();
        assertThat(stored).isNotEqualTo(otp).doesNotContain(otp).hasSize(64);
    }

    @Test
    void emailMatchingIsCaseAndWhitespaceInsensitive() {
        String email = uniqueEmail();
        String otp = otpService.generateAndStore("  " + email.toUpperCase() + " ", OtpPurpose.SIGNUP);

        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, otp)).isTrue();
    }

    @Test
    void codesAreBoundToTheirPurpose() {
        String email = uniqueEmail();
        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        assertThat(otpService.verify(email, OtpPurpose.PASSWORD_RESET, otp)).isFalse();
        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, otp)).isTrue();
    }

    @Test
    void unknownEmailNeverVerifies() {
        assertThat(otpService.verify(uniqueEmail(), OtpPurpose.SIGNUP, "123456")).isFalse();
        assertThat(otpService.verify(uniqueEmail(), OtpPurpose.SIGNUP, null)).isFalse();
    }

    @Test
    void expiredCodeIsRejected() {
        String email = uniqueEmail();
        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);
        OtpCode row = row(email, OtpPurpose.SIGNUP);
        row.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        otpCodeRepository.save(row);

        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, otp)).isFalse();
    }

    @Test
    void secondSendInsideTheCooldownIsThrottled() {
        String email = uniqueEmail();
        otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        assertThatThrownBy(() -> otpService.generateAndStore(email, OtpPurpose.SIGNUP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void sendsPerWindowAreCapped() {
        String email = uniqueEmail();
        for (int i = 0; i < OtpService.MAX_SENDS_PER_WINDOW; i++) {
            otpService.generateAndStore(email, OtpPurpose.SIGNUP);
            ageLastSend(email, OtpPurpose.SIGNUP);
        }

        assertThatThrownBy(() -> otpService.generateAndStore(email, OtpPurpose.SIGNUP))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void wrongGuessesAreCountedAndEventuallyLockVerification() {
        String email = uniqueEmail();
        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);
        String wrong = otp.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < OtpService.MAX_FAILED_ATTEMPTS; i++) {
            assertThat(otpService.verify(email, OtpPurpose.SIGNUP, wrong)).isFalse();
        }

        // even the right code no longer works once the budget is spent
        assertThatThrownBy(() -> otpService.verify(email, OtpPurpose.SIGNUP, otp))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void requestingAFreshCodeDoesNotResetTheAttemptBudget() {
        String email = uniqueEmail();
        String first = otpService.generateAndStore(email, OtpPurpose.SIGNUP);
        String wrong = first.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < OtpService.MAX_FAILED_ATTEMPTS; i++) {
            otpService.verify(email, OtpPurpose.SIGNUP, wrong);
        }
        ageLastSend(email, OtpPurpose.SIGNUP);

        String second = otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        assertThat(row(email, OtpPurpose.SIGNUP).getAttempts()).isEqualTo(OtpService.MAX_FAILED_ATTEMPTS);
        assertThatThrownBy(() -> otpService.verify(email, OtpPurpose.SIGNUP, second))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void newCodeReplacesTheOldOne() {
        String email = uniqueEmail();
        String first = otpService.generateAndStore(email, OtpPurpose.SIGNUP);
        ageLastSend(email, OtpPurpose.SIGNUP);
        String second = otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        if (!first.equals(second)) {
            assertThat(otpService.verify(email, OtpPurpose.SIGNUP, first)).isFalse();
        }
        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, second)).isTrue();
    }

    @Test
    void budgetsResetAfterTheWindowPasses() {
        String email = uniqueEmail();
        otpService.generateAndStore(email, OtpPurpose.SIGNUP);
        OtpCode row = row(email, OtpPurpose.SIGNUP);
        row.setWindowStart(LocalDateTime.now().minus(OtpService.WINDOW).minusMinutes(1));
        row.setSendCount(OtpService.MAX_SENDS_PER_WINDOW);
        row.setAttempts(OtpService.MAX_FAILED_ATTEMPTS);
        otpCodeRepository.save(row);

        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);

        OtpCode fresh = row(email, OtpPurpose.SIGNUP);
        assertThat(fresh.getSendCount()).isEqualTo(1);
        assertThat(fresh.getAttempts()).isZero();
        assertThat(otpService.verify(email, OtpPurpose.SIGNUP, otp)).isTrue();
    }

    @Test
    void evictionRemovesRowsWhoseWindowHasEnded() {
        String stale = uniqueEmail();
        String live = uniqueEmail();
        otpService.generateAndStore(stale, OtpPurpose.SIGNUP);
        otpService.generateAndStore(live, OtpPurpose.SIGNUP);
        OtpCode row = row(stale, OtpPurpose.SIGNUP);
        row.setWindowStart(LocalDateTime.now().minus(OtpService.WINDOW).minusMinutes(1));
        otpCodeRepository.save(row);

        otpService.evictExpired();

        assertThat(find(stale, OtpPurpose.SIGNUP)).isEmpty();
        assertThat(find(live, OtpPurpose.SIGNUP)).isPresent();
    }
}
