package com.expensetracker.android

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.mobile.auth.SignInResult
import com.expensetracker.mobile.auth.SignOutResult
import com.expensetracker.mobile.data.*
import com.expensetracker.mobile.sync.FailedOutboxItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

data class Credentials(val name: String, val email: String, val password: String, val otp: String, val register: Boolean)
data class DiscardPrompt(val count: Long, val credentials: Credentials? = null)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    val graph = (application as ExpenseApplication).graph
    val expenses = graph.expenses.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = graph.categories.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unsynced = graph.engine.observeUnsyncedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val lastSynced = graph.engine.observeLastSyncedAt().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val failed = graph.engine.observeFailedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val busy = MutableStateFlow(false)
    val ready = MutableStateFlow(false)
    val prompt = MutableStateFlow<DiscardPrompt?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                graph.refreshSession()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                report(e)
            } finally {
                ready.value = true
            }
        }
    }

    private fun report(e: Exception) {
        Log.w("ExpenseFlow", "Operation failed", e)
        if (graph.message.value?.startsWith("Your account was deleted") != true) {
            graph.message.value = userMessage(e, "Operation failed")
        }
    }

    private fun run(block: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(e)
            } finally {
                try {
                    graph.refreshSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    report(e)
                } finally {
                    busy.value = false
                }
            }
        }
    }

    fun sendOtp(name: String, email: String) = run {
        require(name.isNotBlank() && email.isNotBlank()) { "Enter your name and email first" }
        graph.auth.sendOtp(name.trim(), email.trim())
        graph.message.value = "OTP sent. Check your email."
    }

    fun authenticate(credentials: Credentials, confirmed: Long? = null) = run {
        graph.gate.withLock {
            val result = if (credentials.register) {
                graph.controller.register(credentials.name.trim(), credentials.email.trim(), credentials.password, credentials.otp, confirmed)
            } else {
                graph.controller.signIn(credentials.email.trim(), credentials.password, confirmed)
            }
            prompt.value = when (result) {
                is SignInResult.UnsyncedRemaining -> DiscardPrompt(result.count, credentials)
                is SignInResult.SignedIn -> null
            }
            graph.refreshSession()
        }
        if (prompt.value == null) graph.sync()
    }

    fun signOut(confirmed: Long? = null) = run {
        graph.gate.withLock {
            prompt.value = when (val result = graph.controller.signOut(confirmed)) {
                is SignOutResult.UnsyncedRemaining -> DiscardPrompt(result.count)
                SignOutResult.SignedOut -> null
            }
        }
    }

    fun confirmDiscard() {
        val current = prompt.value ?: return
        current.credentials?.let { authenticate(it, current.count) } ?: signOut(current.count)
    }

    fun sync() = run { graph.sync() }

    fun saveExpense(
        existing: ExpenseRecord?, description: String, amount: String, currency: String,
        date: String, categoryId: String?, notes: String, saved: () -> Unit,
    ) = run {
        graph.gate.withLock {
            require(graph.auth.currentSession() != null) { "Sign in before editing" }
            require(description.trim().length in 1..255) { "Description must be 1-255 characters" }
            require(notes.length <= 1_000) { "Notes must be at most 1000 characters" }
            val day = LocalDate.parse(date)
            val code = currency.trim().uppercase()
            if (existing == null) {
                graph.expenses.add(description.trim(), amount, code, day, categoryClientId = categoryId, notes = notes.ifBlank { null })
            } else {
                graph.expenses.updateExpense(existing.id, description.trim(), amount, code, day, categoryClientId = categoryId, notes = notes.ifBlank { null })
            }
        }
        withContext(Dispatchers.Main) { saved() }
    }

    fun delete(expense: ExpenseRecord) = run {
        graph.gate.withLock { graph.expenses.deleteExpense(expense.id) }
    }

    fun saveCategory(existing: CategoryRecord?, name: String, done: (CategoryNameCheck) -> Unit) = run {
        val result = graph.gate.withLock {
            require(name.trim().length <= 50) { "Category names must be at most 50 characters" }
            val check = graph.categories.checkName(name, existing?.id)
            if (check is CategoryNameCheck.Valid) {
                if (existing == null) graph.categories.add(check.name) else graph.categories.rename(existing.id, check.name)
            }
            check
        }
        withContext(Dispatchers.Main) { done(result) }
    }

    fun retry(item: FailedOutboxItem) = run {
        graph.gate.withLock { graph.engine.retryFailedItem(item.entity, item.clientId) }
        graph.sync()
    }

    fun discard(item: FailedOutboxItem) = run {
        graph.gate.withLock { graph.engine.discardFailedItem(item.entity, item.clientId) }
        graph.sync()
    }
}
