package com.expensetracker;

import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class SyncApiV1IntegrationTest extends IntegrationTestBase {

    private static final Instant T1 = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-29T10:00:01Z");
    private static final Instant T3 = Instant.parse("2026-10-29T10:00:02Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    // ---- helpers -------------------------------------------------------------------------------

    private JsonNode getJson(String url, JsonNode auth, int expected) throws Exception {
        return send(get(url).header("Authorization", bearer(auth)), expected);
    }

    private JsonNode putJson(String url, JsonNode auth, Object body, int expected) throws Exception {
        return send(put(url).header("Authorization", bearer(auth)).contentType("application/json")
                .content(objectMapper.writeValueAsString(body)), expected);
    }

    private JsonNode deleteJson(String url, JsonNode auth, int expected) throws Exception {
        return send(delete(url).header("Authorization", bearer(auth)), expected);
    }

    private JsonNode postV1(String url, JsonNode auth, Object body, int expected) throws Exception {
        return send(jsonPost(url, body).header("Authorization", bearer(auth)), expected);
    }

    private static Map<String, Object> expenseBody(String clientId, String description, Long categoryId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (clientId != null) {
            body.put("clientId", clientId);
        }
        body.put("description", description);
        body.put("amount", new BigDecimal("12.50"));
        body.put("date", "2026-03-15");
        body.put("categoryId", categoryId);
        body.put("notes", null);
        return body;
    }

    private static Map<String, Object> categoryBody(String clientId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (clientId != null) {
            body.put("clientId", clientId);
        }
        body.put("name", name);
        body.put("icon", "tag");
        body.put("color", "#aabbcc");
        return body;
    }

    private JsonNode createV1Expense(JsonNode auth, String description, Long categoryId) throws Exception {
        return postV1("/api/v1/expenses", auth, expenseBody(UUID.randomUUID().toString(), description, categoryId), 201);
    }

    private void setUpdatedAt(Class<?> type, long id, Instant updatedAt) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(id)),
                new Update().set("updatedAt", updatedAt),
                type);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static List<Long> ids(Iterable<JsonNode> nodes) {
        List<Long> result = new ArrayList<>();
        nodes.forEach(node -> result.add(node.get("id").asLong()));
        return result;
    }

    /** Follows nextCursor until hasMore is false, returning every item in feed order. */
    private List<JsonNode> collectFeed(String path, JsonNode auth, int limit) throws Exception {
        List<JsonNode> items = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < 100; page++) {
            String url = path + "?limit=" + limit + (cursor == null ? "" : "&cursor=" + enc(cursor));
            JsonNode body = getJson(url, auth, 200);
            body.get("items").forEach(items::add);
            boolean hasMore = body.get("hasMore").asBoolean();
            assertThat(hasMore).isEqualTo(!body.get("nextCursor").isNull());
            if (!hasMore) {
                return items;
            }
            cursor = body.get("nextCursor").asText();
        }
        throw new AssertionError("Feed did not terminate: " + path);
    }

    // ---- expense create ------------------------------------------------------------------------

    @Test
    void createReturns201AndReturnsTheExistingRecordWithoutChangesWhenClientIdIsReused() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        String clientId = UUID.randomUUID().toString();

        JsonNode created = postV1("/api/v1/expenses", alice, expenseBody(clientId, "Coffee", null), 201);
        JsonNode retried = postV1("/api/v1/expenses", alice, expenseBody(clientId, "Changed on retry", null), 200);

        assertThat(retried.get("id").asLong()).isEqualTo(created.get("id").asLong());
        assertThat(retried.get("description").asText()).isEqualTo("Coffee");
        assertThat(retried.get("clientId").asText()).isEqualTo(clientId);
        assertThat(created.get("amount").isNumber()).isTrue();
        assertThat(created.get("date").asText()).isEqualTo("2026-03-15");
        assertThat(created.get("updatedAt").asText()).endsWith("Z");
        assertThat(created.get("deletedAt").isNull()).isTrue();

        assertThat(ids(getJson("/api/v1/expenses/changes", alice, 200).get("items")))
                .containsExactly(created.get("id").asLong());
    }

    @Test
    void createRequiresAClientIdUuid() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);

        postV1("/api/v1/expenses", alice, expenseBody(null, "No client id", null), 400);
        postV1("/api/v1/expenses", alice, expenseBody("not-a-uuid", "Bad client id", null), 400);
    }

    @Test
    void createIsScopedPerUser() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode bob = register(uniqueEmail(), PASSWORD);
        String clientId = UUID.randomUUID().toString();

        long aliceId = postV1("/api/v1/expenses", alice, expenseBody(clientId, "Alice", null), 201).get("id").asLong();
        JsonNode bobCreate = postV1("/api/v1/expenses", bob, expenseBody(clientId, "Bob", null), 201);

        assertThat(bobCreate.get("id").asLong()).isNotEqualTo(aliceId);
    }

    @Test
    void createWithAnotherUsersCategoryReturns404() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode mallory = register(uniqueEmail(), PASSWORD);
        long aliceCategory = postV1("/api/v1/categories", alice,
                categoryBody(UUID.randomUUID().toString(), "Private"), 201).get("id").asLong();

        postV1("/api/v1/expenses", mallory,
                expenseBody(UUID.randomUUID().toString(), "x", aliceCategory), 404);
    }

    // ---- expense changes feed ------------------------------------------------------------------

    @Test
    void tombstonesAppearInChangesWithDeletedAt() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        long id = createV1Expense(alice, "To delete", null).get("id").asLong();

        deleteJson("/api/v1/expenses/" + id, alice, 204);

        JsonNode feed = getJson("/api/v1/expenses/changes", alice, 200);
        assertThat(feed.get("items")).hasSize(1);
        JsonNode tombstone = feed.get("items").get(0);
        assertThat(tombstone.get("id").asLong()).isEqualTo(id);
        assertThat(tombstone.get("deletedAt").asText()).endsWith("Z");
        assertThat(tombstone.get("updatedAt").asText()).endsWith("Z");
        assertThat(feed.get("hasMore").asBoolean()).isFalse();
        assertThat(feed.get("nextCursor").isNull()).isTrue();
        assertThat(feed.get("serverTime").asText()).endsWith("Z");
    }

    @Test
    void cursorPagesThroughEqualUpdatedAtValuesWithoutSkippingOrRepeating() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        Instant[] times = {T2, T1, T1, T3, T1, T2, T3};
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            long id = createV1Expense(alice, "item " + i, null).get("id").asLong();
            setUpdatedAt(Expense.class, id, times[i]);
            created.add(id);
        }

        List<Long> expected = List.of(
                created.get(1), created.get(2), created.get(4),
                created.get(0), created.get(5),
                created.get(3), created.get(6));

        assertThat(ids(collectFeed("/api/v1/expenses/changes", alice, 2)))
                .containsExactlyElementsOf(expected);
    }

    @Test
    void updatedSinceIsInclusiveAndCursorTakesPrecedence() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        long early = createV1Expense(alice, "early", null).get("id").asLong();
        long late = createV1Expense(alice, "late", null).get("id").asLong();
        setUpdatedAt(Expense.class, early, T1);
        setUpdatedAt(Expense.class, late, T2);

        JsonNode fromT2 = getJson("/api/v1/expenses/changes?updatedSince=" + T2, alice, 200);
        assertThat(ids(fromT2.get("items"))).containsExactly(late);

        JsonNode fromT1 = getJson("/api/v1/expenses/changes?updatedSince=" + T1, alice, 200);
        assertThat(ids(fromT1.get("items"))).containsExactly(early, late);

        JsonNode firstPage = getJson("/api/v1/expenses/changes?limit=1", alice, 200);
        assertThat(ids(firstPage.get("items"))).containsExactly(early);
        String cursor = firstPage.get("nextCursor").asText();

        JsonNode next = getJson("/api/v1/expenses/changes?limit=1&updatedSince=" + T3 + "&cursor=" + enc(cursor),
                alice, 200);
        assertThat(ids(next.get("items"))).containsExactly(late);
    }

    @Test
    void changesRejectsBadLimitInstantAndCursor() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);

        getJson("/api/v1/expenses/changes?limit=0", alice, 400);
        getJson("/api/v1/expenses/changes?limit=501", alice, 400);
        getJson("/api/v1/expenses/changes?updatedSince=yesterday", alice, 400);
        getJson("/api/v1/expenses/changes?cursor=not-a-cursor", alice, 400);
    }

    @Test
    void changesDoesNotLeakOtherUsersExpenses() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode bob = register(uniqueEmail(), PASSWORD);
        createV1Expense(alice, "alice only", null);

        assertThat(getJson("/api/v1/expenses/changes", bob, 200).get("items")).isEmpty();
    }

    // ---- expense update and delete -------------------------------------------------------------

    @Test
    void putOnATombstoneReturns404() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        long id = createV1Expense(alice, "Gone", null).get("id").asLong();
        deleteJson("/api/v1/expenses/" + id, alice, 204);

        putJson("/api/v1/expenses/" + id, alice, expenseBody(null, "Resurrect", null), 404);
    }

    @Test
    void putUpdatesTheRecordAndIgnoresClientId() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode created = createV1Expense(alice, "Original", null);
        long id = created.get("id").asLong();
        String originalClientId = created.get("clientId").asText();

        JsonNode updated = putJson("/api/v1/expenses/" + id, alice,
                expenseBody(UUID.randomUUID().toString(), "Edited", null), 200);

        assertThat(updated.get("description").asText()).isEqualTo("Edited");
        assertThat(updated.get("clientId").asText()).isEqualTo(originalClientId);
        assertThat(updated.get("updatedAt").asText()).endsWith("Z");
    }

    @Test
    void deleteIsIdempotentAndMissingOrForeignExpensesReturn404() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        JsonNode mallory = register(uniqueEmail(), PASSWORD);
        long id = createV1Expense(alice, "Delete me", null).get("id").asLong();

        deleteJson("/api/v1/expenses/" + id, alice, 204);
        deleteJson("/api/v1/expenses/" + id, alice, 204);
        deleteJson("/api/v1/expenses/" + id, mallory, 404);
        deleteJson("/api/v1/expenses/987654321", alice, 404);
    }

    // ---- categories ----------------------------------------------------------------------------

    @Test
    void categoryCreateIsIdempotentByClientId() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        String clientId = UUID.randomUUID().toString();

        JsonNode created = postV1("/api/v1/categories", alice, categoryBody(clientId, "Travel"), 201);
        JsonNode retried = postV1("/api/v1/categories", alice, categoryBody(clientId, "Other name"), 200);

        assertThat(retried.get("id").asLong()).isEqualTo(created.get("id").asLong());
        assertThat(retried.get("name").asText()).isEqualTo("Travel");
        assertThat(retried.get("global").asBoolean()).isFalse();
    }

    @Test
    void categoryNameMatchingADefaultCategoryReturns409() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        String defaultName = "Sync Default " + UUID.randomUUID();
        mongoTemplate.insert(Category.builder().name(defaultName).color("#112233").icon("g").build());

        postV1("/api/v1/categories", alice, categoryBody(UUID.randomUUID().toString(), defaultName.toLowerCase()), 409);
    }

    @Test
    void categoryFeedIncludesGlobalCategoriesAsReadOnly() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        Category global = mongoTemplate.insert(
                Category.builder().name("Sync Global " + UUID.randomUUID()).color("#112233").icon("g").build());
        long own = postV1("/api/v1/categories", alice, categoryBody(UUID.randomUUID().toString(), "Mine"), 201)
                .get("id").asLong();

        List<JsonNode> items = collectFeed("/api/v1/categories/changes", alice, 500);

        JsonNode globalItem = items.stream()
                .filter(item -> item.get("id").asLong() == global.getId())
                .findFirst()
                .orElseThrow();
        assertThat(globalItem.get("global").asBoolean()).isTrue();
        assertThat(globalItem.get("clientId").isNull()).isTrue();
        assertThat(globalItem.get("name").asText()).isEqualTo(global.getName());

        JsonNode ownItem = items.stream()
                .filter(item -> item.get("id").asLong() == own)
                .findFirst()
                .orElseThrow();
        assertThat(ownItem.get("global").asBoolean()).isFalse();
    }

    @Test
    void globalCategoriesAreReadOnlyForPutAndDelete() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        Category global = mongoTemplate.insert(
                Category.builder().name("Sync Locked " + UUID.randomUUID()).color("#112233").icon("g").build());
        String url = "/api/v1/categories/" + global.getId();

        putJson(url, alice, categoryBody(null, "Renamed"), 403);
        deleteJson(url, alice, 403);
    }

    @Test
    void deletingACategoryClearsExpenseReferencesAndTombstonesTheCategory() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        long categoryId = postV1("/api/v1/categories", alice,
                categoryBody(UUID.randomUUID().toString(), "Temporary"), 201).get("id").asLong();
        JsonNode expense = postV1("/api/v1/expenses", alice,
                expenseBody(UUID.randomUUID().toString(), "Tagged", categoryId), 201);
        long expenseId = expense.get("id").asLong();
        Instant before = Instant.parse(expense.get("updatedAt").asText());

        deleteJson("/api/v1/categories/" + categoryId, alice, 204);
        putJson("/api/v1/categories/" + categoryId, alice, categoryBody(null, "Back"), 404);

        JsonNode changedExpense = collectFeed("/api/v1/expenses/changes", alice, 500).stream()
                .filter(item -> item.get("id").asLong() == expenseId)
                .findFirst()
                .orElseThrow();
        assertThat(changedExpense.get("deletedAt").isNull()).isTrue();
        assertThat(changedExpense.get("categoryId").isNull()).isTrue();
        assertThat(changedExpense.get("categoryName").isNull()).isTrue();
        assertThat(Instant.parse(changedExpense.get("updatedAt").asText())).isAfterOrEqualTo(before);

        JsonNode tombstone = collectFeed("/api/v1/categories/changes", alice, 500).stream()
                .filter(item -> item.get("id").asLong() == categoryId)
                .findFirst()
                .orElseThrow();
        assertThat(tombstone.get("deletedAt").asText()).endsWith("Z");
    }

    @Test
    void categoryDeleteIsIdempotent() throws Exception {
        JsonNode alice = register(uniqueEmail(), PASSWORD);
        long categoryId = postV1("/api/v1/categories", alice,
                categoryBody(UUID.randomUUID().toString(), "Once"), 201).get("id").asLong();

        deleteJson("/api/v1/categories/" + categoryId, alice, 204);
        deleteJson("/api/v1/categories/" + categoryId, alice, 204);
    }
}
