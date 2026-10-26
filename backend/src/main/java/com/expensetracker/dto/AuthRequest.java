package com.expensetracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    // No password policy here: accounts created before the policy existed must still be able to sign in.
    @NotBlank(message = "Password is required")
    @Size(max = 1024, message = "Password is too long")
    private String password;
}
