package com.expensetracker.dto;

import java.math.BigDecimal;

/** Spend for one category; a null category is reported as "Uncategorized" with a null id. */
public record CategorySpend(Long categoryId, String name, String color, String icon, BigDecimal total) {

    public CategorySpend {
        if (name == null) name = "Uncategorized";
    }
}
