package com.expensetracker.service.sync;

import com.expensetracker.dto.v1.ChangesV1Response;
import com.expensetracker.exception.BadRequestException;
import com.expensetracker.exception.GoneException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Shared paging for the expense and category change feeds. Rows are ordered by (updatedAt, id) and tombstones
 * are included. The caller supplies the owner scope.
 */
@Component
@RequiredArgsConstructor
public class SyncChangeFeed {

    static final int MIN_LIMIT = 1;
    static final int MAX_LIMIT = 500;

    private final MongoTemplate mongoTemplate;
    private final TombstonePurgeJob tombstonePurgeJob;

    public <E, T> ChangesV1Response<T> fetch(
            Class<E> type,
            Criteria scope,
            String updatedSince,
            String cursor,
            int limit,
            Function<E, SyncCursorCodec.Position> positionOf,
            Function<E, T> toResponse) {
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and 500");
        }

        Instant serverTime = SyncTime.now();
        Query query = new Query(criteria(scope, updatedSince, cursor, tombstonePurgeJob.horizon()))
                .with(Sort.by(Sort.Direction.ASC, "updatedAt", "id"))
                .limit(limit + 1);

        List<E> rows = mongoTemplate.find(query, type);
        boolean hasMore = rows.size() > limit;
        List<E> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = hasMore
                ? SyncCursorCodec.encode(positionOf.apply(page.get(page.size() - 1)))
                : null;

        return new ChangesV1Response<>(
                page.stream().map(toResponse).toList(),
                nextCursor,
                hasMore,
                serverTime);
    }

    /** An updatedSince before {@code horizon} may skip purged tombstones, so it is rejected with 410. */
    private static Criteria criteria(Criteria scope, String updatedSince, String cursor, Instant horizon) {
        if (cursor != null && !cursor.isBlank()) {
            SyncCursorCodec.Position position = SyncCursorCodec.decode(cursor);
            Criteria after = new Criteria().orOperator(
                    Criteria.where("updatedAt").gt(position.updatedAt()),
                    new Criteria().andOperator(
                            Criteria.where("updatedAt").is(position.updatedAt()),
                            Criteria.where("id").gt(position.id())));
            return new Criteria().andOperator(scope, after);
        }
        if (updatedSince != null && !updatedSince.isBlank()) {
            Instant since = SyncTime.parse(updatedSince, "updatedSince");
            if (since.isBefore(horizon)) {
                throw new GoneException("updatedSince is older than the tombstone retention period; do a full resync");
            }
            return new Criteria().andOperator(scope, Criteria.where("updatedAt").gte(since));
        }
        return scope;
    }
}
