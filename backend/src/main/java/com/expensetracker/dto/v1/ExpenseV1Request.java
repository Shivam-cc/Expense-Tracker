package com.expensetracker.dto.v1;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Locale;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseV1Request {

    /** 9,999,999,999.99 in two-decimal currencies, the same ceiling the v1 decimal contract used. */
    static final long MAX_AMOUNT_MINOR = 999_999_999_999L;

    @NotBlank(message = "clientId is required", groups = OnCreate.class)
    @Pattern(regexp = Strings.UUID_REGEX, message = "clientId must be a UUID", groups = OnCreate.class)
    private String clientId;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    /** Integer minor units of {@link #currency}: 1250 is 12.50 USD, 1250 is 1250 JPY. */
    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Max(value = MAX_AMOUNT_MINOR, message = "Amount is too large")
    private Long amountMinor;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be an ISO 4217 code")
    private String currency;

    @NotNull(message = "Date is required")
    private LocalDate date;

    private Long categoryId;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    public void setClientId(String clientId) {
        this.clientId = Strings.normalizeClientId(clientId);
    }

    public void setCurrency(String currency) {
        this.currency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    public void setDescription(String description) {
        this.description = Strings.trim(description);
    }

    public void setNotes(String notes) {
        this.notes = Strings.blankToNull(notes);
    }
}
