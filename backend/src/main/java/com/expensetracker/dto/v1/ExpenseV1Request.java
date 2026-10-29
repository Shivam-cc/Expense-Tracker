package com.expensetracker.dto.v1;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseV1Request {

    @NotBlank(message = "clientId is required", groups = OnCreate.class)
    @Pattern(regexp = Strings.UUID_REGEX, message = "clientId must be a UUID", groups = OnCreate.class)
    private String clientId;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description must be at most 255 characters")
    private String description;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @DecimalMax(value = "9999999999.99", message = "Amount is too large")
    @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate date;

    private Long categoryId;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    public void setClientId(String clientId) {
        this.clientId = Strings.normalizeClientId(clientId);
    }

    public void setDescription(String description) {
        this.description = Strings.trim(description);
    }

    public void setNotes(String notes) {
        this.notes = Strings.blankToNull(notes);
    }
}
