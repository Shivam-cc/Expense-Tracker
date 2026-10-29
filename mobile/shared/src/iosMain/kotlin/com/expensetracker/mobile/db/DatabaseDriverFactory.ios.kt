package com.expensetracker.mobile.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(ExpenseFlowDatabase.Schema, "expenseflow.db")
}
