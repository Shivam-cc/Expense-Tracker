package com.expensetracker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One document per (email, purpose). Holds an HMAC of the current code (never the code itself) plus the
 * abuse-throttle counters, which deliberately survive re-sends until windowStart + 1h passes.
 */
@Document("otp_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpCode implements IdentifiedDocument {

    @Id
    private Long id;

    private String email;

    private OtpPurpose purpose;

    private String codeHash;

    private LocalDateTime expiresAt;

    private int attempts;

    private LocalDateTime lastSentAt;

    private LocalDateTime windowStart;

    private int sendCount;
}
