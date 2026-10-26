package com.expensetracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per (email, purpose). Holds an HMAC of the current code (never the code itself) plus the
 * abuse-throttle counters, which deliberately survive re-sends until windowStart + 1h passes.
 */
@Entity
@Table(name = "otp_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_otp_email_purpose", columnNames = {"email", "purpose"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    @Column(name = "send_count", nullable = false)
    private int sendCount;
}
