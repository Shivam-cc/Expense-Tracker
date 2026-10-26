package com.expensetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    /** Short-lived access JWT. */
    private String token;
    private String refreshToken;
    /** Access token lifetime in seconds. */
    private long expiresIn;
    private String name;
    private String email;
}
