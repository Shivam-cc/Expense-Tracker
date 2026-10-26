package com.expensetracker;

import com.expensetracker.model.Category;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class ExpenseApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CategoryRepository categoryRepository;

    // ---- helpers -------------------------------------------------------------------------------

    private JsonNode createCategory(JsonNode auth, String name) throws Exception {
        return send(jsonPost("/api/categories", Map.of("name", name, "icon", "🍕", "color", "#aabbcc"))
                .header("Authorization", bearer(auth)), 200);
    }

    private JsonNode createExpense(JsonNode auth, String description, String amount, String date, Long categoryId, String notes)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", description);
        body.put("amount", amount);
        body.put("date", date);
        body.put("categoryId", categoryId);
        body.put("notes", notes);
        return send(jsonPost("/api/expenses", body).header("Authorization", bearer(auth)), 200);
    }

    private JsonNode createExpense(JsonNode auth, String description, String amount, Long categoryId) throws Exception {
        return createExpense(auth, description, amount, "2026-03-15", categoryId, null);
    }

    private JsonNode jsonPut(String url, JsonNode auth, Object body, int expected) throws Exception {
        return send(put(url).header("Authorization", bearer(auth)).contentType("application/json")
                .content(objectMapper.writeValueAsString(body)), expected);
    }

    private Category createGlobalCategory(String name) {
        return categoryRepository.save(Category.builder().name(name).color("#112233").icon("g").build());
    }

    private void assertErrorBody(JsonNode body, int status, String path) {
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("error").asText()).isNotBlank();
        assertThat(body.get("message").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo(path);
    }

    // ---- ownership / IDOR ----------------------------------------------------------------------

    @Test
    void usersCannotReadChangeOrDeleteEachOthersExpenses() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode mallory = register(uniqueEmail(), PASSWORD);
        long id = createExpense(alice, "Alice's lunch", "12.50", null).get("id").asLong();
        String url = "/api/expenses/" + id;

        send(get(url).header("Authorization", bearer(mallory)), 404);
        jsonPut(url, mallory, Map.of("description", "pwned", "amount", "1", "date", "2026-03-15"), 404);
        send(delete(url).header("Authorization", bearer(mallory)), 404);

        JsonNode still = send(get(url).header("Authorization", bearer(alice)), 200);
        assertThat(still.get("description").asText()).isEqualTo("Alice's lunch");
    }

    @Test
    void expenseListsOnlyContainTheCallersRows() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode bob = register(uniqueEmail(), PASSWORD);
        createExpense(alice, "alice-only", "5", null);

        JsonNode page = send(get("/api/expenses").header("Authorization", bearer(bob)), 200);
        assertThat(page.get("totalElements").asLong()).isZero();
        assertThat(page.get("content")).isEmpty();
    }

    @Test
    void expenseCannotBeFiledUnderAnotherUsersCategory() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode mallory = register(uniqueEmail(), PASSWORD);
        long aliceCategory = createCategory(alice, "Private").get("id").asLong();

        Map<String, Object> body = Map.of("description", "x", "amount", "1", "date", "2026-03-15", "categoryId", aliceCategory);
        send(jsonPost("/api/expenses", body).header("Authorization", bearer(mallory)), 404);

        long malloryExpense = createExpense(mallory, "mine", "1", null).get("id").asLong();
        jsonPut("/api/expenses/" + malloryExpense, mallory, body, 404);
    }

    @Test
    void usersCannotEditDeleteOrListEachOthersCategories() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode mallory = register(uniqueEmail(), PASSWORD);
        long id = createCategory(alice, "Alice only").get("id").asLong();

        jsonPut("/api/categories/" + id, mallory, Map.of("name", "hijacked"), 404);
        send(delete("/api/categories/" + id).header("Authorization", bearer(mallory)), 404);

        JsonNode visible = send(get("/api/categories").header("Authorization", bearer(mallory)), 200);
        for (JsonNode c : visible) {
            assertThat(c.get("id").asLong()).isNotEqualTo(id);
        }
    }

    // ---- global categories ---------------------------------------------------------------------

    @Test
    void globalCategoriesAreVisibleAndUsableButReadOnly() throws Exception {
        Category global = createGlobalCategory("Global-" + System.nanoTime());
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode list = send(get("/api/categories").header("Authorization", bearer(auth)), 200);
        boolean flagged = false;
        for (JsonNode c : list) {
            if (c.get("id").asLong() == global.getId()) flagged = c.get("global").asBoolean();
        }
        assertThat(flagged).isTrue();

        JsonNode expense = createExpense(auth, "on a global category", "3", global.getId());
        assertThat(expense.get("categoryId").asLong()).isEqualTo(global.getId());

        jsonPut("/api/categories/" + global.getId(), auth, Map.of("name", "renamed"), 403);
        send(delete("/api/categories/" + global.getId()).header("Authorization", bearer(auth)), 403);
        assertThat(categoryRepository.findById(global.getId())).isPresent();
    }

    @Test
    void deletingACategoryKeepsItsExpensesAsUncategorized() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        long categoryId = createCategory(auth, "Doomed").get("id").asLong();
        long expenseId = createExpense(auth, "keeps existing", "20", categoryId).get("id").asLong();

        send(delete("/api/categories/" + categoryId).header("Authorization", bearer(auth)), 204);

        JsonNode expense = send(get("/api/expenses/" + expenseId).header("Authorization", bearer(auth)), 200);
        assertThat(expense.get("categoryId").isNull()).isTrue();
    }

    // ---- summary -------------------------------------------------------------------------------

    @Test
    void summaryGroupsByCategoryAndReportsUncategorized() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        long food = createCategory(auth, "Food").get("id").asLong();
        createExpense(auth, "a", "10.00", food);
        createExpense(auth, "b", "5.50", food);
        createExpense(auth, "c", "4.00", null);
        createExpense(auth, "outside range", "999", "2025-01-01", null, null);

        JsonNode summary = send(get("/api/expenses/summary")
                .param("startDate", "2026-03-01").param("endDate", "2026-03-31")
                .header("Authorization", bearer(auth)), 200);

        assertThat(summary.get("total").decimalValue()).isEqualByComparingTo("19.50");
        JsonNode byCategory = summary.get("byCategory");
        assertThat(byCategory.isArray()).isTrue();
        assertThat(byCategory).hasSize(2);
        // ordered by spend, largest first
        assertThat(byCategory.get(0).get("name").asText()).isEqualTo("Food");
        assertThat(byCategory.get(0).get("categoryId").asLong()).isEqualTo(food);
        assertThat(byCategory.get(0).get("total").decimalValue()).isEqualByComparingTo("15.50");
        assertThat(byCategory.get(1).get("name").asText()).isEqualTo("Uncategorized");
        assertThat(byCategory.get(1).get("categoryId").isNull()).isTrue();
        assertThat(byCategory.get(1).get("total").decimalValue()).isEqualByComparingTo("4.00");
    }

    @Test
    void summaryRejectsAReversedDateRange() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode body = send(get("/api/expenses/summary")
                .param("startDate", "2026-04-01").param("endDate", "2026-03-01")
                .header("Authorization", bearer(auth)), 400);
        assertErrorBody(body, 400, "/api/expenses/summary");
    }

    // ---- validation and error shape ------------------------------------------------------------

    @Test
    void validationFailuresReturnFieldErrorsInTheStandardBody() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("description", "x".repeat(256));
        bad.put("amount", "12345678901.00");
        bad.put("date", "2026-03-15");
        bad.put("notes", "n".repeat(1001));
        JsonNode body = send(jsonPost("/api/expenses", bad).header("Authorization", bearer(auth)), 400);

        assertErrorBody(body, 400, "/api/expenses");
        assertThat(body.get("errors").fieldNames()).toIterable()
                .contains("description", "amount", "notes");
    }

    @Test
    void amountsWithMoreThanTwoDecimalsAreRejected() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        send(jsonPost("/api/expenses", Map.of("description", "x", "amount", "1.234", "date", "2026-03-15"))
                .header("Authorization", bearer(auth)), 400);
        send(jsonPost("/api/expenses", Map.of("description", "x", "amount", "-5", "date", "2026-03-15"))
                .header("Authorization", bearer(auth)), 400);
    }

    @Test
    void categoryFieldsAreValidated() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode badColor = send(jsonPost("/api/categories", Map.of("name", "ok", "color", "red"))
                .header("Authorization", bearer(auth)), 400);
        assertThat(badColor.get("errors").has("color")).isTrue();

        send(jsonPost("/api/categories", Map.of("name", "n".repeat(51), "color", "#ffffff"))
                .header("Authorization", bearer(auth)), 400);
        send(jsonPost("/api/categories", Map.of("name", "ok", "icon", "i".repeat(17), "color", "#ffffff"))
                .header("Authorization", bearer(auth)), 400);
    }

    @Test
    void stringsAreTrimmed() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode expense = createExpense(auth, "   padded   ", "2", "2026-03-15", null, "   ");
        assertThat(expense.get("description").asText()).isEqualTo("padded");
        assertThat(expense.get("notes").isNull()).isTrue();
    }

    @Test
    void malformedJsonAndBadPathParametersGetTheStandardBodyWithoutLeakingInternals() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode malformed = send(post("/api/expenses").header("Authorization", bearer(auth))
                .contentType("application/json").content("{not json"), 400);
        assertErrorBody(malformed, 400, "/api/expenses");
        assertThat(malformed.get("message").asText()).doesNotContain("com.fasterxml").doesNotContain("Unexpected character");

        JsonNode badId = send(get("/api/expenses/not-a-number").header("Authorization", bearer(auth)), 400);
        assertErrorBody(badId, 400, "/api/expenses/not-a-number");

        JsonNode badDate = send(get("/api/expenses/summary").param("startDate", "yesterday").param("endDate", "today")
                .header("Authorization", bearer(auth)), 400);
        assertErrorBody(badDate, 400, "/api/expenses/summary");

        JsonNode missing = send(get("/api/expenses/summary").header("Authorization", bearer(auth)), 400);
        assertErrorBody(missing, 400, "/api/expenses/summary");
    }

    @Test
    void notFoundUsesTheStandardBody() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);

        JsonNode body = send(get("/api/expenses/987654321").header("Authorization", bearer(auth)), 404);
        assertErrorBody(body, 404, "/api/expenses/987654321");
    }

    // ---- paging and sorting --------------------------------------------------------------------

    @Test
    void pageSizeIsClampedAndSortFieldsAreWhitelisted() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        createExpense(auth, "one", "1", null);

        JsonNode huge = send(get("/api/expenses").param("size", "100000").header("Authorization", bearer(auth)), 200);
        assertThat(huge.get("size").asInt()).isEqualTo(100);

        JsonNode zero = send(get("/api/expenses").param("size", "0").header("Authorization", bearer(auth)), 200);
        assertThat(zero.get("size").asInt()).isEqualTo(1);

        send(get("/api/expenses").param("page", "-1").header("Authorization", bearer(auth)), 400);
        JsonNode badSort = send(get("/api/expenses").param("sortBy", "user.password").header("Authorization", bearer(auth)), 400);
        assertErrorBody(badSort, 400, "/api/expenses");
        send(get("/api/expenses").param("sortBy", "amount").param("direction", "asc")
                .header("Authorization", bearer(auth)), 200);
    }

    @Test
    void pagingEnvelopeHasStableShape() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        for (int i = 0; i < 3; i++) createExpense(auth, "e" + i, "1", null);

        JsonNode page = send(get("/api/expenses").param("size", "2").header("Authorization", bearer(auth)), 200);
        assertThat(page.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    }

    // ---- filters -------------------------------------------------------------------------------

    @Test
    void listSupportsTextCategoryAndDateFilters() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        long food = createCategory(auth, "Food").get("id").asLong();
        createExpense(auth, "Pizza night", "20", "2026-02-10", food, "with friends");
        createExpense(auth, "Taxi", "8", "2026-03-05", null, "airport run");
        createExpense(auth, "100% organic", "6", "2026-03-20", null, null);

        assertThat(count(auth, Map.of("q", "pizza"))).isEqualTo(1);
        assertThat(count(auth, Map.of("q", "AIRPORT"))).isEqualTo(1); // notes, case-insensitive
        assertThat(count(auth, Map.of("categoryId", String.valueOf(food)))).isEqualTo(1);
        assertThat(count(auth, Map.of("categoryId", "0"))).isEqualTo(2); // 0 = uncategorized
        assertThat(count(auth, Map.of("startDate", "2026-03-01", "endDate", "2026-03-31"))).isEqualTo(2);
        assertThat(count(auth, Map.of("startDate", "2026-03-06"))).isEqualTo(1);
        // LIKE wildcards in the search text are literal
        assertThat(count(auth, Map.of("q", "%"))).isEqualTo(1);
        assertThat(count(auth, Map.of("q", "_"))).isZero();
    }

    private long count(JsonNode auth, Map<String, String> params) throws Exception {
        var request = get("/api/expenses").header("Authorization", bearer(auth));
        params.forEach(request::param);
        return send(request, 200).get("totalElements").asLong();
    }

    // ---- CSV export ----------------------------------------------------------------------------

    @Test
    void exportReturnsAnEscapedCsvAttachment() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        long food = createCategory(auth, "Food, drink").get("id").asLong();
        createExpense(auth, "=HYPERLINK(\"http://evil\")", "9.99", "2026-03-10", food, "say \"hi\"\nsecond line");
        createExpense(auth, "plain", "1.00", "2026-03-11", null, null);
        createExpense(auth, "out of range", "7", "2025-01-01", null, null);

        MvcResult result = mvc.perform(get("/api/expenses/export")
                        .param("startDate", "2026-03-01").param("endDate", "2026-03-31")
                        .header("Authorization", bearer(auth)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .contains("attachment").contains("expenses-2026-03-01-2026-03-31.csv");

        String csv = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFdate,description,category,amount,currency,notes\r\n");
        assertThat(csv).contains("2026-03-10,\"'=HYPERLINK(\"\"http://evil\"\")\",\"Food, drink\",9.99,INR,"
                + "\"say \"\"hi\"\"\nsecond line\"\r\n");
        assertThat(csv).contains("2026-03-11,plain,Uncategorized,1.00,INR,\r\n");
        assertThat(csv).doesNotContain("out of range");
    }

    @Test
    void exportOnlyContainsTheCallersRowsAndUsesTheirCurrency() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode bob = register(uniqueEmail(), PASSWORD);
        createExpense(alice, "alice secret", "5", null);
        createExpense(bob, "bob row", "6", null);
        send(patch("/api/users/me").header("Authorization", bearer(bob))
                .contentType("application/json").content("{\"currency\":\"USD\"}"), 200);

        String csv = mvc.perform(get("/api/expenses/export").header("Authorization", bearer(bob)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).contains("bob row").contains(",USD,").doesNotContain("alice secret");
    }

    @Test
    void exportRequiresAuthentication() throws Exception {
        send(get("/api/expenses/export"), 401);
    }

    // ---- currency ------------------------------------------------------------------------------

    @Test
    void currencyDefaultsToInrAndCanBeChangedToAnyIso4217Code() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        assertThat(auth.get("currency").asText()).isEqualTo("INR");

        JsonNode updated = send(patch("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"currency\":\"EUR\"}"), 200);
        assertThat(updated.get("currency").asText()).isEqualTo("EUR");

        JsonNode normalised = send(patch("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"currency\":\" gbp \"}"), 200);
        assertThat(normalised.get("currency").asText()).isEqualTo("GBP");

        send(patch("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"currency\":\"EURO\"}"), 400);
        send(patch("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"currency\":\"ZZZ\"}"), 400);
    }
}
