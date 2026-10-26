package com.expensetracker.service;

import com.expensetracker.dto.AuthRequest;
import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.ForgotPasswordRequest;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.dto.ResetPasswordRequest;
import com.expensetracker.dto.SendOtpRequest;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ServiceUnavailableException;
import com.expensetracker.exception.UnauthorizedException;
import com.expensetracker.model.OtpPurpose;
import com.expensetracker.model.Role;
import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SEND_OTP_MESSAGE =
            "If this email can be used to sign up, a verification code has been sent to it.";
    private static final String FORGOT_PASSWORD_MESSAGE =
            "If an account exists for this email, a password reset code has been sent to it.";
    private static final String LOGIN_FAILED_MESSAGE =
            "Invalid email or password, or the account is temporarily locked.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AccountLockService accountLockService;
    private final OtpService otpService;
    private final EmailService emailService;

    @Value("${app.otp.dev-mode:false}")
    private boolean devOtpMode;

    // Verified against when the email is unknown so response time doesn't reveal whether an account exists.
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        dummyHash = passwordEncoder.encode("timing-equalisation-placeholder");
    }

    /**
     * Generates an OTP and emails it. The response is identical whether or not the email already has
     * an account (such addresses get a "you already have an account" email instead of a code).
     * The OTP is returned in the response only in dev mode.
     */
    public Map<String, Object> sendOtp(SendOtpRequest request) {
        String email = normalize(request.getEmail());
        String name = request.getName().trim();

        String otp = otpService.generateAndStore(email, OtpPurpose.SIGNUP);
        boolean emailSent = userRepository.existsByEmail(email)
                ? emailService.sendAccountExistsEmail(email, name)
                : emailService.sendOtpEmail(email, otp, name, OtpPurpose.SIGNUP);

        Map<String, Object> response = new HashMap<>();
        response.put("message", SEND_OTP_MESSAGE);
        if (devOtpMode) {
            response.put("devOtp", otp);
        } else if (!emailSent) {
            throw new ServiceUnavailableException("Could not send verification email");
        }
        return response;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.getEmail());

        // Verify OTP before creating the account
        if (!otpService.verify(email, OtpPurpose.SIGNUP, request.getOtp())) {
            throw new BadRequestException("Invalid or expired verification code");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration won the unique-email race
            throw new ConflictException("Email already registered");
        }
        return refreshTokenService.startSession(user);
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(normalize(request.getEmail())).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }
        if (!accountLockService.verifyPassword(user, request.getPassword())) {
            throw new UnauthorizedException(LOGIN_FAILED_MESSAGE);
        }
        return refreshTokenService.startSession(user);
    }

    public AuthResponse refresh(String refreshToken) {
        return refreshTokenService.rotate(refreshToken);
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Always answers with the same message. A code is generated (and throttled) for unknown emails
     * too, but only existing accounts get an email, and sending is async so latency doesn't leak either.
     */
    public Map<String, Object> forgotPassword(ForgotPasswordRequest request) {
        String email = normalize(request.getEmail());
        String otp = otpService.generateAndStore(email, OtpPurpose.PASSWORD_RESET);

        userRepository.findByEmail(email).ifPresent(user ->
                CompletableFuture.runAsync(() ->
                        emailService.sendOtpEmail(email, otp, user.getName(), OtpPurpose.PASSWORD_RESET)));

        Map<String, Object> response = new HashMap<>();
        response.put("message", FORGOT_PASSWORD_MESSAGE);
        if (devOtpMode) response.put("devOtp", otp);
        return response;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalize(request.getEmail());

        if (!otpService.verify(email, OtpPurpose.PASSWORD_RESET, request.getOtp())) {
            throw new BadRequestException("Invalid or expired verification code");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification code"));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setFailedAttempts(0);
        user.setLastFailedAt(null);
        user.setLockedUntil(null);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
