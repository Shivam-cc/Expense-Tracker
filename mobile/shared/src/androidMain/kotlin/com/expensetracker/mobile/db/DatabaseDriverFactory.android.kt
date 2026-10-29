package com.expensetracker.mobile.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.expensetracker.mobile.android.AppContextHolder

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(ExpenseFlowDatabase.Schema, AppContextHolder.appContext, "expenseflow.db")
}
