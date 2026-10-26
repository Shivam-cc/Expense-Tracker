package com.expensetracker.controller;

import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.ChangePasswordRequest;
import com.expensetracker.dto.DeleteAccountRequest;
import com.expensetracker.dto.UpdateProfileRequest;
import com.expensetracker.dto.UserProfileResponse;
import com.expensetracker.model.User;
import com.expensetracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    @PatchMapping
    public ResponseEntity<UserProfileResponse> updateMe(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    /** Returns a fresh token pair; every other session is invalidated. */
    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.changePassword(user, request));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal User user) {
        userService.logoutAll(user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMe(
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal User user) {
        userService.deleteAccount(user, request.getPassword());
        return ResponseEntity.noContent().build();
    }
}
