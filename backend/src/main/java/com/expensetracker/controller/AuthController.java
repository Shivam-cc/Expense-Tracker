package com.expensetracker.controller;

import com.expensetracker.dto.AuthRequest;
import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.ForgotPasswordRequest;
import com.expensetracker.dto.RefreshRequest;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.dto.ResetPasswordRequest;
import com.expensetracker.dto.SendOtpRequest;
import com.expensetracker.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Step 1 of sign-up: generates an OTP and sends it. The response is the same whether or not the
     * email is already registered. In dev mode the OTP is also returned ({@code devOtp}).
     */
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, Object>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return ResponseEntity.ok(authService.sendOtp(request));
    }

    /** Step 2 of sign-up: verifies OTP, creates account, returns an access + refresh token pair. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Rotates the refresh token and returns a new access + refresh token pair. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated. You can now sign in."));
    }
}
