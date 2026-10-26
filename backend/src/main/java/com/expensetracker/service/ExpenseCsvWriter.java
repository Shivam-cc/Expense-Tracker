package com.expensetracker.service;

import com.expensetracker.model.Expense;

import java.util.List;

/** Builds RFC 4180 CSV for expense exports, neutralising spreadsheet formula injection. */
public final class ExpenseCsvWriter {

    private static final String HEADER = "date,description,category,amount,currency,notes";
    private static final String EOL = "\r\n";

    private ExpenseCsvWriter() {}

    public static String write(List<Expense> expenses, String currency) {
        StringBuilder csv = new StringBuilder(HEADER).append(EOL);
        for (Expense e : expenses) {
            csv.append(e.getDate()).append(',')
                    .append(text(e.getDescription())).append(',')
                    .append(text(e.getCategory() != null ? e.getCategory().getName() : "Uncategorized")).append(',')
                    .append(e.getAmount().toPlainString()).append(',')
                    .append(text(currency)).append(',')
                    .append(text(e.getNotes()))
                    .append(EOL);
        }
        return csv.toString();
    }

    /** User-controlled text: defuse formulas first, then quote if the value needs it. */
    static String text(String value) {
        if (value == null || value.isEmpty()) return "";
        String safe = startsWithFormulaTrigger(value) ? "'" + value : value;
        boolean needsQuotes = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0;
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    // Tab and CR are included because Excel also treats them as a lead-in to a formula.
    private static boolean startsWithFormulaTrigger(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
