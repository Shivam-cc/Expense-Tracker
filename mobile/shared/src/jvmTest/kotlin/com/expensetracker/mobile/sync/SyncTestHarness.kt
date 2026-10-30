package com.expensetracker.mobile.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.expensetracker.mobile.data.CategoryRepository
import com.expensetracker.mobile.data.ExpenseRepository
import com.expensetracker.mobile.db.ExpenseFlowDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TestClock(var current: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000)) : Clock {
    override fun now(): Instant = current

    fun advanceHours(hours: Long) = advanceMillis(hours * 3_600_000L)

    fun advanceMillis(millis: Long) {
        current = Instant.fromEpochMilliseconds(current.toEpochMilliseconds() + millis)
    }
}

class SyncTestHarness {
    val clock = TestClock()
    val db: ExpenseFlowDatabase = ExpenseFlowDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpenseFlowDatabase.Schema.create(it) },
    )
    val api = FakeSyncApi(clock)
    val expenses = ExpenseRepository(db, clock)
    val categories = CategoryRepository(db, clock)
    val engine = SyncEngine(api, db, clock)

    fun outboxCount(): Long = db.expenseFlowQueries.countOutbox().executeAsOne()
}
