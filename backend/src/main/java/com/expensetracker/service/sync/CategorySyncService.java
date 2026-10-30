package com.expensetracker.service.sync;

import com.expensetracker.dto.v1.CategoryV1Request;
import com.expensetracker.dto.v1.CategoryV1Response;
import com.expensetracker.dto.v1.ChangesV1Response;
import com.expensetracker.exception.ConflictException;
import com.expensetracker.exception.ForbiddenException;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CategorySyncService {

    private final MongoTemplate mongoTemplate;
    private final SyncChangeFeed syncChangeFeed;

    public ChangesV1Response<CategoryV1Response> changes(User user, String updatedSince, String cursor, int limit) {
        return syncChangeFeed.fetch(
                Category.class,
                visibleTo(user.getId()),
                updatedSince,
                cursor,
                limit,
                category -> new SyncCursorCodec.Position(category.getUpdatedAt(), category.getId()),
                this::toResponse);
    }

    public SyncWriteResult<CategoryV1Response> create(CategoryV1Request request, User user) {
        Optional<Category> existing = findByClientId(user.getId(), request.getClientId());
        if (existing.isPresent()) {
            return new SyncWriteResult<>(toResponse(existing.get()), false);
        }

        ensureNameNotDefault(request.getName());

        Category category = Category.builder()
                .name(request.getName())
                .icon(request.getIcon())
                .color(request.getColor())
                .user(user)
                .clientId(request.getClientId())
                .updatedAt(SyncTime.now())
                .build();

        try {
            return new SyncWriteResult<>(toResponse(mongoTemplate.insert(category)), true);
        } catch (DuplicateKeyException e) {
            Category winner = findByClientId(user.getId(), request.getClientId()).orElseThrow(() -> e);
            return new SyncWriteResult<>(toResponse(winner), false);
        }
    }

    @Transactional
    public CategoryV1Response update(Long id, CategoryV1Request request, User user) {
        Category category = mongoTemplate.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("id").is(id),
                        Criteria.where("deletedAt").isNull(),
                        visibleTo(user.getId()))),
                Category.class);
        if (category == null) {
            throw new NotFoundException("Category not found");
        }
        if (category.getUser() == null) {
            throw new ForbiddenException("Default categories cannot be modified");
        }

        ensureNameNotDefault(request.getName());

        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setColor(request.getColor());
        category.setUpdatedAt(SyncTime.now());

        return toResponse(mongoTemplate.save(category));
    }

    /**
     * Soft delete. Live expenses that use the category lose the reference and get a new {@code updatedAt},
     * which matches {@code ExpenseRepository.clearCategory}.
     */
    @Transactional
    public void delete(Long id, User user) {
        Category category = mongoTemplate.findById(id, Category.class);
        if (category == null) {
            throw new NotFoundException("Category not found");
        }
        if (category.getUser() == null) {
            throw new ForbiddenException("Default categories cannot be modified");
        }
        if (!category.getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Category not found");
        }
        if (category.getDeletedAt() != null) {
            return;
        }

        Instant now = SyncTime.now();
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("category").is(id).and("deletedAt").isNull()),
                new Update().unset("category").set("updatedAt", now),
                Expense.class);
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(id)),
                new Update().set("deletedAt", now).set("updatedAt", now),
                Category.class);
    }

    /** Categories a user may attach to an expense: their own live ones, or live global ones. */
    Optional<Category> findUsable(Long id, Long userId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("id").is(id),
                        Criteria.where("deletedAt").isNull(),
                        visibleTo(userId))),
                Category.class));
    }

    /** The caller's categories plus global ones (user == null). Tombstones are not filtered here. */
    static Criteria visibleTo(Long userId) {
        return new Criteria().orOperator(
                Criteria.where("user").is(userId),
                Criteria.where("user").isNull());
    }

    private Optional<Category> findByClientId(Long userId, String clientId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                Query.query(Criteria.where("user").is(userId).and("clientId").is(clientId)),
                Category.class));
    }

    /**
     * Same rule as {@code CategoryService}: a name that matches a live default category is rejected.
     * The 409 body carries that default as {@code existingCategory} so the client can adopt it.
     */
    private void ensureNameNotDefault(String name) {
        Pattern exactName = Pattern.compile("^" + Pattern.quote(name) + "$", Pattern.CASE_INSENSITIVE);
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("user").isNull(),
                Criteria.where("deletedAt").isNull(),
                Criteria.where("name").regex(exactName)));
        Category existing = mongoTemplate.findOne(query, Category.class);
        if (existing != null) {
            throw new ConflictException(
                    "A default category named \"" + name + "\" already exists",
                    Map.of("existingCategory", toResponse(existing)));
        }
    }

    private CategoryV1Response toResponse(Category category) {
        return CategoryV1Response.builder()
                .id(category.getId())
                .clientId(category.getClientId())
                .name(category.getName())
                .icon(category.getIcon())
                .color(category.getColor())
                .global(category.getUser() == null)
                .updatedAt(SyncTime.truncate(category.getUpdatedAt()))
                .deletedAt(SyncTime.truncate(category.getDeletedAt()))
                .build();
    }
}
