package com.expensetracker.mobile.sync

import com.expensetracker.mobile.api.ApiException
import com.expensetracker.mobile.api.AuthRequestDto
import com.expensetracker.mobile.api.AuthResponseDto
import com.expensetracker.mobile.api.CategoryDto
import com.expensetracker.mobile.api.CategoryWriteDto
import com.expensetracker.mobile.api.ChangePage
import com.expensetracker.mobile.api.ExpenseDto
import com.expensetracker.mobile.api.ExpenseFlowApi
import com.expensetracker.mobile.api.ExpenseWriteDto
import com.expensetracker.mobile.api.RefreshRequestDto
import com.expensetracker.mobile.api.RegisterRequestDto
import com.expensetracker.mobile.api.SendOtpRequestDto
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * In-memory backend for sync tests. Follows the v1 semantics: tombstones in the changes feed,
 * POST returns the existing record for a known clientId, DELETE is idempotent, and deleting a
 * category clears it on expenses and bumps their updatedAt.
 */
class FakeSyncApi(private val clock: Clock) : ExpenseFlowApi {
    val expenses = linkedMapOf<Long, ExpenseDto>()
    val categories = linkedMapOf<Long, CategoryDto>()

    var pageSize: Int = 100

    /** Thrown for any changes request that carries a cursor, to simulate a network failure mid-pull. */
    var failPagesWith: Throwable? = null
    var expenseWriteError: ApiException? = null
    var categoryWriteError: ApiException? = null

    /** Runs before a create or update is applied, to simulate a local edit made while a request is in flight. */
    var beforeExpenseWrite: (() -> Unit)? = null

    private var nextId = 1L

    /** Seeds a record as if another client created it on the server. Amounts are minor units of [currency]. */
    fun serverCreateExpense(description: String, amountMinor: Long, date: LocalDate, currency: String = "INR"): ExpenseDto {
        val at = clock.now()
        val id = nextId++
        val dto = ExpenseDto(
            id = id,
            description = description,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            createdAt = at,
            updatedAt = at,
        )
        expenses[id] = dto
        return dto
    }

    fun serverChangeExpense(id: Long, description: String, amountMinor: Long, at: Instant) {
        val existing = expenses.getValue(id)
        expenses[id] = existing.copy(description = description, amountMinor = amountMinor, updatedAt = at)
    }

    fun serverDeleteExpense(id: Long, at: Instant) {
        val existing = expenses.getValue(id)
        expenses[id] = existing.copy(deletedAt = at, updatedAt = at)
    }

    override suspend fun sendOtp(request: SendOtpRequestDto) {
        notImplemented()
    }

    override suspend fun register(request: RegisterRequestDto): AuthResponseDto = notImplemented()

    override suspend fun login(request: AuthRequestDto): AuthResponseDto = notImplemented()

    override suspend fun refresh(request: RefreshRequestDto): AuthResponseDto = notImplemented()

    override suspend fun logout(request: RefreshRequestDto) {
        notImplemented()
    }

    override suspend fun getExpenseChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<ExpenseDto> {
        if (cursor != null) failPagesWith?.let { throw it }
        return changesPage(expenses.values, { it.updatedAt }, { it.id }, updatedSince, cursor, limit)
    }

    override suspend fun createExpense(request: ExpenseWriteDto): ExpenseDto {
        beforeExpenseWrite?.invoke()
        expenseWriteError?.let { throw it }
        expenses.values.firstOrNull { it.clientId == request.clientId }?.let { return it }
        val id = nextId++
        val dto = ExpenseDto(
            id = id,
            clientId = request.clientId,
            description = request.description,
            amountMinor = request.amountMinor,
            currency = request.currency,
            date = request.date,
            categoryId = request.categoryId,
            categoryName = request.categoryId?.let { categories[it]?.name },
            notes = request.notes,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )
        expenses[id] = dto
        return dto
    }

    override suspend fun updateExpense(id: Long, request: ExpenseWriteDto): ExpenseDto {
        beforeExpenseWrite?.invoke()
        expenseWriteError?.let { throw it }
        val existing = expenses[id] ?: throw ApiException(404, "Expense $id not found")
        val dto = existing.copy(
            description = request.description,
            amountMinor = request.amountMinor,
            currency = request.currency,
            date = request.date,
            categoryId = request.categoryId,
            categoryName = request.categoryId?.let { categories[it]?.name },
            notes = request.notes,
            updatedAt = clock.now(),
        )
        expenses[id] = dto
        return dto
    }

    override suspend fun deleteExpense(id: Long) {
        val existing = expenses[id] ?: return
        if (existing.deletedAt == null) {
            val at = clock.now()
            expenses[id] = existing.copy(deletedAt = at, updatedAt = at)
        }
    }

    override suspend fun getCategoryChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<CategoryDto> {
        if (cursor != null) failPagesWith?.let { throw it }
        return changesPage(categories.values, { it.updatedAt }, { it.id }, updatedSince, cursor, limit)
    }

    override suspend fun createCategory(request: CategoryWriteDto): CategoryDto {
        categoryWriteError?.let { throw it }
        categories.values.firstOrNull { it.clientId == request.clientId }?.let { return it }
        val id = nextId++
        val dto = CategoryDto(
            id = id,
            clientId = request.clientId,
            name = request.name,
            icon = request.icon,
            color = request.color,
            updatedAt = clock.now(),
        )
        categories[id] = dto
        return dto
    }

    override suspend fun updateCategory(id: Long, request: CategoryWriteDto): CategoryDto {
        categoryWriteError?.let { throw it }
        val existing = categories[id] ?: throw ApiException(404, "Category $id not found")
        val dto = existing.copy(name = request.name, icon = request.icon, color = request.color, updatedAt = clock.now())
        categories[id] = dto
        return dto
    }

    override suspend fun deleteCategory(id: Long) {
        val existing = categories[id] ?: return
        if (existing.deletedAt != null) return
        val at = clock.now()
        categories[id] = existing.copy(deletedAt = at, updatedAt = at)
        expenses.values.filter { it.categoryId == id }.forEach { expense ->
            expenses[expense.id] = expense.copy(categoryId = null, categoryName = null, updatedAt = at)
        }
    }

    private fun <T> changesPage(
        items: Collection<T>,
        updatedAtOf: (T) -> Instant,
        idOf: (T) -> Long,
        updatedSince: Instant?,
        cursor: String?,
        limit: Int,
    ): ChangePage<T> {
        val sorted = items
            .filter { updatedSince == null || updatedAtOf(it) >= updatedSince }
            .sortedWith(compareBy<T>({ updatedAtOf(it) }, { idOf(it) }))
        val offset = cursor?.toIntOrNull() ?: 0
        val page = sorted.drop(offset).take(minOf(limit, pageSize))
        val hasMore = offset + page.size < sorted.size
        return ChangePage(
            items = page,
            nextCursor = if (hasMore) (offset + page.size).toString() else null,
            hasMore = hasMore,
            serverTime = clock.now(),
        )
    }

    private fun notImplemented(): Nothing = throw NotImplementedError("Auth is not exercised by sync tests")
}
