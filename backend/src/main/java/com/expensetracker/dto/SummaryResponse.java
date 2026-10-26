package com.expensetracker.dto;

import java.math.BigDecimal;
import java.util.List;

public record SummaryResponse(BigDecimal total, List<CategorySpend> byCategory) {}
