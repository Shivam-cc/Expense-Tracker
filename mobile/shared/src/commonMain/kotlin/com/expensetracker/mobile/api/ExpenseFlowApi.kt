package com.expensetracker.mobile.api

import kotlinx.datetime.Instant

/**
 * Contract for the ExpenseFlow backend as seen by the mobile core.
 * Implemented by [KtorExpenseFlowApi]. Errors surface as [ApiException].
 */
interface ExpenseFlowApi {
    suspend fun sendOtp(request: SendOtpRequestDto)
    suspend fun register(request: RegisterRequestDto): AuthResponseDto
    suspend fun login(request: AuthRequestDto): AuthResponseDto
    suspend fun refresh(request: RefreshRequestDto): AuthResponseDto
    suspend fun logout(request: RefreshRequestDto)

    suspend fun getExpenseChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<ExpenseDto>
    suspend fun createExpense(request: ExpenseWriteDto): ExpenseDto
    suspend fun updateExpense(id: Long, request: ExpenseWriteDto): ExpenseDto
    suspend fun deleteExpense(id: Long)

    suspend fun getCategoryChanges(updatedSince: Instant?, cursor: String?, limit: Int): ChangePage<CategoryDto>
    suspend fun createCategory(request: CategoryWriteDto): CategoryDto
    suspend fun updateCategory(id: Long, request: CategoryWriteDto): CategoryDto
    suspend fun deleteCategory(id: Long)
}

/** Thrown for any non-2xx response. [status] is the HTTP code, [message] the backend error message if present. */
class ApiException(val status: Int, override val message: String) : Exception(message)
