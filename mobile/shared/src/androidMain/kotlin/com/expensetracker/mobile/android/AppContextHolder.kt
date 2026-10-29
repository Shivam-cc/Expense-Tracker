package com.expensetracker.mobile.android

import android.content.Context

/**
 * Holds the application context for Android actuals (SQLite driver, encrypted token store).
 * The app module must call [init] once from Application.onCreate.
 */
object AppContextHolder {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
