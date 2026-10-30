package com.expensetracker;

import com.expensetracker.config.MongoDatabaseInitializer;
import com.expensetracker.config.Sha256BcryptPasswordEncoder;
import com.expensetracker.model.RefreshToken;
import com.expensetracker.service.RefreshTokenService;
import com.expensetracker.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

class DeletedAccountRefreshRetentionTest extends IntegrationTestBase {

    private static final Set<String> ALLOWED_FIELDS = Set.of("_id", "_class", "tokenHash", "accountDeleted", "deletedAt");

    @Autowired
    private MongoTemplate template;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private MongoDatabaseInitializer initializer;

    @Test
    void deletingAnAccountKeepsOnlyTokenHashFlagAndDeletionTime() throws Exception {
        JsonNode first = register(uniqueEmail(), PASSWORD);
        String firstToken = first.get("refreshToken").asText();
        // Rotation leaves a revoked row with replacedBy next to the live one.
        String secondToken = send(jsonPost("/api/auth/refresh", Map.of("refreshToken", firstToken)), 200)
                .get("refreshToken").asText();
        Date before = new Date();

        deleteAccount(first);

        for (String raw : new String[]{firstToken, secondToken}) {
            Document stored = storedRow(raw);
            assertThat(stored.keySet()).isSubsetOf(ALLOWED_FIELDS);
            assertThat(stored.getBoolean("accountDeleted")).isTrue();
            assertThat(stored.getDate("deletedAt")).isAfterOrEqualTo(new Date(before.getTime() - 1_000));
            assertThat(stored.getString("tokenHash")).isEqualTo(Sha256BcryptPasswordEncoder.sha256Hex(raw));
        }
    }

    @Test
    void presentedTokensOfADeletedAccountStillReportAccountDeleted() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        String revoked = auth.get("refreshToken").asText();
        String live = send(jsonPost("/api/auth/refresh", Map.of("refreshToken", revoked)), 200)
                .get("refreshToken").asText();

        deleteAccount(auth);

        for (String raw : new String[]{revoked, live}) {
            JsonNode refreshed = send(jsonPost("/api/auth/refresh", Map.of("refreshToken", raw)), 401);
            assertThat(refreshed.get("error").asText()).isEqualTo("account_deleted");
            assertThat(storedRow(raw).keySet()).isSubsetOf(ALLOWED_FIELDS);
        }
    }

    @Test
    void logoutOfADeletedAccountDoesNotModifyDetachedRows() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        String raw = auth.get("refreshToken").asText();
        deleteAccount(auth);
        Document before = storedRow(raw);

        send(jsonPost("/api/auth/logout", Map.of("refreshToken", raw)), 204);

        assertThat(storedRow(raw)).isEqualTo(before);
        assertThat(storedRow(raw).keySet()).isSubsetOf(ALLOWED_FIELDS);
    }

    @Test
    void expiredTokenEvictionLeavesDeletedAccountRowsAlone() throws Exception {
        JsonNode auth = register(uniqueEmail(), PASSWORD);
        String raw = auth.get("refreshToken").asText();
        deleteAccount(auth);

        refreshTokenService.evictExpired();

        assertThat(storedRow(raw)).isNotNull();
        JsonNode refreshed = send(jsonPost("/api/auth/refresh", Map.of("refreshToken", raw)), 401);
        assertThat(refreshed.get("error").asText()).isEqualTo("account_deleted");
    }

    @Test
    void expiredTokenEvictionStillRemovesOrdinaryExpiredRows() throws Exception {
        String raw = register(uniqueEmail(), PASSWORD).get("refreshToken").asText();
        Document filter = new Document("tokenHash", Sha256BcryptPasswordEncoder.sha256Hex(raw));
        Date longAgo = new Date(System.currentTimeMillis() - Duration.ofDays(60).toMillis());
        refreshTokens().updateOne(filter, new Document("$set", new Document("expiresAt", longAgo)));

        refreshTokenService.evictExpired();

        assertThat(refreshTokens().countDocuments(filter)).isZero();
    }

    @Test
    void ttlIndexOnDeletedAtExpiresRowsAfterRefreshLifetimePlusSevenDays() {
        IndexInfo ttl = template.indexOps(RefreshToken.class).getIndexInfo().stream()
                .filter(index -> MongoDatabaseInitializer.DELETED_REFRESH_TTL_INDEX.equals(index.getName()))
                .findFirst().orElseThrow();

        assertThat(ttl.getIndexFields()).extracting(field -> field.getKey()).containsExactly("deletedAt");
        assertThat(ttl.getExpireAfter()).contains(Duration.ofDays(RefreshTokenService.REFRESH_TTL_DAYS + 7));
        assertThat(RefreshTokenService.DELETED_ACCOUNT_RETENTION_DAYS).isEqualTo(37);
    }

    @Test
    void startupBackfillReducesRowsLeftByAnEarlierVersion() {
        String hash = Sha256BcryptPasswordEncoder.sha256Hex("legacy-" + System.nanoTime());
        refreshTokens().insertOne(new Document("_id", System.nanoTime()).append("tokenHash", hash)
                .append("accountDeleted", true).append("familyId", "family").append("revokedAt", new Date())
                .append("replacedBy", 5L).append("createdAt", new Date()).append("expiresAt", new Date()));

        initializer.run(new DefaultApplicationArguments());

        Document stored = refreshTokens().find(new Document("tokenHash", hash)).first();
        assertThat(stored.keySet()).isSubsetOf(ALLOWED_FIELDS);
        assertThat(stored.getDate("deletedAt")).isNotNull();
    }

    private void deleteAccount(JsonNode auth) throws Exception {
        send(delete("/api/users/me").header("Authorization", bearer(auth))
                .contentType("application/json").content("{\"password\":\"" + PASSWORD + "\"}"), 204);
    }

    private Document storedRow(String rawToken) {
        return refreshTokens().find(new Document("tokenHash", Sha256BcryptPasswordEncoder.sha256Hex(rawToken))).first();
    }

    private com.mongodb.client.MongoCollection<Document> refreshTokens() {
        return template.getCollection(template.getCollectionName(RefreshToken.class));
    }
}
