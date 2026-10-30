package com.expensetracker.service.sync;

import com.expensetracker.dto.v1.ChangesV1Response;
import com.expensetracker.dto.v1.ExpenseV1Request;
import com.expensetracker.dto.v1.ExpenseV1Response;
import com.expensetracker.exception.NotFoundException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.User;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExpenseSyncService {

    private final MongoTemplate mongoTemplate;
    private final SyncChangeFeed syncChangeFeed;
    private final CategorySyncService categorySyncService;

    public ChangesV1Response<ExpenseV1Response> changes(User user, String updatedSince, String cursor, int limit) {
        return syncChangeFeed.fetch(
                Expense.class,
                Criteria.where("user").is(user.getId()),
                updatedSince,
                cursor,
                limit,
                expense -> new SyncCursorCodec.Position(expense.getUpdatedAt(), expense.getId()),
                expense -> toResponse(expense, user.getCurrency()));
    }

    /**
     * Idempotent create. When an expense with the same clientId already exists for this user (tombstones
     * included), that record is returned unchanged.
     */
    public SyncWriteResult<ExpenseV1Response> create(ExpenseV1Request request, User user) {
        Optional<Expense> existing = findByClientId(user.getId(), request.getClientId());
        if (existing.isPresent()) {
            return new SyncWriteResult<>(toResponse(existing.get(), user.getCurrency()), false);
        }

        String currency = MinorUnits.requireKnownCurrency(request.getCurrency());
        Category category = resolveCategory(request.getCategoryId(), user.getId());
        Expense expense = Expense.builder()
                .clientId(request.getClientId())
                .description(request.getDescription())
                .amount(MinorUnits.toAmount(request.getAmountMinor(), currency))
                .currency(currency)
                .date(request.getDate())
                .category(category)
                .user(user)
                .notes(request.getNotes())
                .updatedAt(SyncTime.now())
                .build();

        try {
            return new SyncWriteResult<>(toResponse(mongoTemplate.insert(expense), user.getCurrency()), true);
        } catch (DuplicateKeyException e) {
            Expense winner = findByClientId(user.getId(), request.getClientId()).orElseThrow(() -> e);
            return new SyncWriteResult<>(toResponse(winner, user.getCurrency()), false);
        }
    }

    /** Last write wins. The client's clientId is ignored, and tombstones are treated as missing. */
    @Transactional
    public ExpenseV1Response update(Long id, ExpenseV1Request request, User user) {
        Expense expense = findLive(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        String currency = MinorUnits.requireKnownCurrency(request.getCurrency());
        Category category = resolveCategory(request.getCategoryId(), user.getId());

        expense.setDescription(request.getDescription());
        expense.setAmount(MinorUnits.toAmount(request.getAmountMinor(), currency));
        expense.setCurrency(currency);
        expense.setDate(request.getDate());
        expense.setCategory(category);
        expense.setNotes(request.getNotes());
        expense.setUpdatedAt(SyncTime.now());

        return toResponse(mongoTemplate.save(expense), user.getCurrency());
    }

    /**
     * Soft delete. Deleting an expense that is already a tombstone succeeds without changes. A missing or
     * foreign expense returns 404.
     */
    @Transactional
    public void delete(Long id, User user) {
        Instant now = SyncTime.now();
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(owned(id, user.getId()).and("deletedAt").isNull()),
                new Update().set("deletedAt", now).set("updatedAt", now),
                Expense.class);

        if (result.getMatchedCount() == 0
                && !mongoTemplate.exists(Query.query(owned(id, user.getId())), Expense.class)) {
            throw new NotFoundException("Expense not found");
        }
    }

    private Category resolveCategory(Long categoryId, Long userId) {
        if (categoryId == null) {
            return null;
        }
        return categorySyncService.findUsable(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private Optional<Expense> findLive(Long id, Long userId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                Query.query(owned(id, userId).and("deletedAt").isNull()),
                Expense.class));
    }

    private Optional<Expense> findByClientId(Long userId, String clientId) {
        return Optional.ofNullable(mongoTemplate.findOne(
                Query.query(Criteria.where("user").is(userId).and("clientId").is(clientId)),
                Expense.class));
    }

    private static Criteria owned(Long id, Long userId) {
        return Criteria.where("id").is(id).and("user").is(userId);
    }

    /**
     * Rows written before v1 have no currency and are reported in the owner's currency. The changes feed
     * uses this for every row, so a deleted-user or missing-currency row can never fail the whole page.
     */
    private ExpenseV1Response toResponse(Expense expense, String ownerCurrency) {
        Category category = expense.getCategory();
        String currency = expense.getCurrency() != null ? expense.getCurrency() : ownerCurrency;
        return ExpenseV1Response.builder()
                .id(expense.getId())
                .clientId(expense.getClientId())
                .description(expense.getDescription())
                .amountMinor(MinorUnits.toMinor(expense.getAmount(), currency))
                .currency(currency)
                .date(expense.getDate())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .notes(expense.getNotes())
                .createdAt(SyncTime.fromLocal(expense.getCreatedAt()))
                .updatedAt(SyncTime.truncate(expense.getUpdatedAt()))
                .deletedAt(SyncTime.truncate(expense.getDeletedAt()))
                .build();
    }
}
