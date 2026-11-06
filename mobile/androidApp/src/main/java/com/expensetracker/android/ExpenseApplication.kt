package com.expensetracker.android

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.*
import com.expensetracker.mobile.android.AppContextHolder
import com.expensetracker.mobile.api.AccessTokenProvider
import com.expensetracker.mobile.api.KtorExpenseFlowApi
import com.expensetracker.mobile.auth.*
import com.expensetracker.mobile.data.*
import com.expensetracker.mobile.db.*
import com.expensetracker.mobile.sync.SyncEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

class ExpenseApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.init(this)
        graph = AppGraph()
        val work = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, work)
    }
}

class AppGraph {
    val db = ExpenseFlowDatabase(DatabaseDriverFactory().createDriver())
    val gate = Mutex()
    val session = MutableStateFlow<StoredSession?>(null)
    val message = MutableStateFlow<String?>(null)
    private val tokens = object : AccessTokenProvider {
        override suspend fun currentAccessToken() = auth.currentAccessToken()
        override suspend fun refreshAfterUnauthorized() = auth.refreshAfterUnauthorized()
    }
    val api = KtorExpenseFlowApi(BuildConfig.API_BASE_URL, OkHttp.create(), tokens)
    val auth: AuthRepository = AuthRepository(api, TokenStore(), wipeLocalData = {
        LocalDataWiper(db).wipe()
        session.value = null
        message.value = "Your account was deleted. Local data has been removed."
    })
    val engine = SyncEngine(api, db)
    val controller = SessionController(auth, engine, db)
    val expenses = ExpenseRepository(db)
    val categories = CategoryRepository(db)
    val syncing = MutableStateFlow(false)

    suspend fun refreshSession() {
        session.value = auth.currentSession()
    }

    suspend fun sync() = gate.withLock {
        if (auth.currentSession() == null) {
            refreshSession()
            return@withLock
        }
        syncing.value = true
        try {
            engine.sync()
        } finally {
            syncing.value = false
            refreshSession()
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as ExpenseApplication).graph
        return try {
            graph.sync()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ExpenseFlow", "Background sync failed", e)
            if (!isNetworkError(e)) graph.message.value = userMessage(e, "Background sync failed")
            if (graph.session.value == null) Result.failure() else Result.retry()
        }
    }
}
