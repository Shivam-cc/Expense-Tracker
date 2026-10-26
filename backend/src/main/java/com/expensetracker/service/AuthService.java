package com.expensetracker.service;

import com.expensetracker.dto.AuthRequest;
import com.expensetracker.dto.AuthResponse;
import com.expensetracker.dto.RegisterRequest;
import com.expensetracker.dto.SendOtpRequest;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.exception.ServiceUnavailableException;
import com.expensetracker.model.Role;
import com.expensetracker.model.User;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final KeyService keyService;
    private final OtpService otpService;
    private final EmailService emailService;

    @Value("${app.otp.dev-mode:false}")
    private boolean devOtpMode;

    private static final String SEND_OTP_MESSAGE =
            "If this email can be used to sign up, a verification code has been sent to it.";

    /**
     * Generates an OTP and emails it. The response is identical whether or not the email already has
     * an account (such addresses get a "you already have an account" email instead of a code).
     * The OTP is returned in the response only in dev mode.
     */
    public Map<String, Object> sendOtp(SendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        String name = request.getName().trim();

        String otp = otpService.generateAndStore(email);
        boolean emailSent = userRepository.existsByEmail(email)
                ? emailService.sendAccountExistsEmail(email, name)
                : emailService.sendOtpEmail(email, otp, name);

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
        String email = request.getEmail().trim().toLowerCase();

        // Verify OTP before creating the account
        if (!otpService.verify(email, request.getOtp())) {
            throw new BadRequestException("Invalid or expired verification code");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }

        // Decrypt RSA-OAEP envelope -> plain SHA-256 hash from browser
        String decodedPassword;
        try {
            decodedPassword = keyService.decrypt(request.getPassword());
        } catch (Exception e) {
            throw new BadRequestException("Invalid password encoding");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(decodedPassword))
                .role(Role.USER)
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Concurrent registration won the unique-email race
            throw new ConflictException("Email already registered");
        }
        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
    public AuthResponse login(AuthRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Decrypt RSA-OAEP envelope â†’ plain SHA-256 hash from browser
        String decodedPassword;
        try {
            decodedPassword = keyService.decrypt(request.getPassword());
        } catch (Exception e) {
            throw new BadRequestException("Invalid password encoding");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, decodedPassword)
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}
