package com.expensetracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 50, message = "Category name must be at most 50 characters")
    private String name;

    @Size(max = 16, message = "Icon must be at most 16 characters")
    private String icon;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must be a hex value like #3b82f6")
    private String color;

    public void setName(String name) {
        this.name = Strings.trim(name);
    }

    public void setIcon(String icon) {
        this.icon = Strings.blankToNull(icon);
    }

    public void setColor(String color) {
        this.color = Strings.blankToNull(color);
    }
}
