package com.expensetracker.service;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.ChangePasswordRequest;
import com.expensetracker.dto.UpdateProfileRequest;
import com.expensetracker.dto.UserProfileResponse;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.model.User;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String WRONG_PASSWORD = "Current password is incorrect";

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccountLockService accountLockService;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(User principal) {
        return toProfile(load(principal));
    }

    @Transactional
    public UserProfileResponse updateProfile(User principal, UpdateProfileRequest request) {
        User user = load(principal);
        if (request.getName() != null) {
            user.setName(request.getName().trim());
        }
        return toProfile(userRepository.save(user));
    }

    /**
     * Changes the password, invalidates every other session (token version bump + refresh revocation),
     * and returns a fresh session for the caller so this device stays signed in.
     */
    @Transactional
    public AuthResponse changePassword(User principal, ChangePasswordRequest request) {
        User user = load(principal);
        requirePassword(user, request.getCurrentPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        return refreshTokenService.startSession(user);
    }

    /** "Log out all devices": bumps the token version so access tokens die immediately, and revokes all refresh tokens. */
    @Transactional
    public void logoutAll(User principal) {
        User user = load(principal);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
    }

    @Transactional
    public void deleteAccount(User principal, String password) {
        User user = load(principal);
        requirePassword(user, password);

        expenseRepository.deleteByUserId(user.getId());
        categoryRepository.deleteByUserId(user.getId());
        refreshTokenService.deleteAllForUser(user.getId());
        otpService.deleteAllForEmail(user.getEmail());
        userRepository.delete(user);
    }

    private void requirePassword(User user, String rawPassword) {
        if (!accountLockService.verifyPassword(user, rawPassword)) {
            throw new BadRequestException(WRONG_PASSWORD);
        }
    }

    private User load(User principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
