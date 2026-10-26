package com.expensetracker.service;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCsvWriterTest {

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)", "\tcmd", "\rcmd"})
    void formulaTriggersAreNeutralisedWithALeadingApostrophe(String value) {
        String field = ExpenseCsvWriter.text(value);
        // the apostrophe goes before the trigger character; a field with a CR is additionally quoted
        assertThat(field).contains("'" + value);
        assertThat(field).matches(f -> f.startsWith("'") || f.startsWith("\"'"));
    }

    @Test
    void plainTextIsLeftAlone() {
        assertThat(ExpenseCsvWriter.text("Groceries")).isEqualTo("Groceries");
        assertThat(ExpenseCsvWriter.text("a=b")).isEqualTo("a=b");
    }

    @Test
    void nullAndEmptyBecomeEmptyFields() {
        assertThat(ExpenseCsvWriter.text(null)).isEmpty();
        assertThat(ExpenseCsvWriter.text("")).isEmpty();
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertThat(ExpenseCsvWriter.text("a,b")).isEqualTo("\"a,b\"");
        assertThat(ExpenseCsvWriter.text("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ExpenseCsvWriter.text("l1\nl2")).isEqualTo("\"l1\nl2\"");
    }

    @Test
    void injectionAndQuotingCombine() {
        assertThat(ExpenseCsvWriter.text("=A1,\"x\"")).isEqualTo("\"'=A1,\"\"x\"\"\"");
    }

    @Test
    void writesHeaderAndRowsWithCrlfAndUncategorizedFallback() {
        Expense withCategory = Expense.builder()
                .date(LocalDate.of(2026, 3, 1)).description("Lunch").amount(new BigDecimal("12.50"))
                .category(Category.builder().name("Food").build()).notes("tasty").build();
        Expense without = Expense.builder()
                .date(LocalDate.of(2026, 3, 2)).description("Misc").amount(new BigDecimal("3")).build();

        String csv = ExpenseCsvWriter.write(List.of(withCategory, without), "USD");

        assertThat(csv).isEqualTo("date,description,category,amount,currency,notes\r\n"
                + "2026-03-01,Lunch,Food,12.50,USD,tasty\r\n"
                + "2026-03-02,Misc,Uncategorized,3,USD,\r\n");
    }

    @Test
    void emptyExportStillHasAHeader() {
        assertThat(ExpenseCsvWriter.write(List.of(), "INR")).isEqualTo("date,description,category,amount,currency,notes\r\n");
    }
}
