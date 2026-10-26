package com.expensetracker.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** PATCH semantics: null fields are left unchanged. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 2, max = 50, message = "Name must be 2-50 characters")
    private String name;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code")
    private String currency;

    public void setName(String name) {
        this.name = Strings.trim(name);
    }

    public void setCurrency(String currency) {
        this.currency = currency == null ? null : currency.trim().toUpperCase();
    }
}
