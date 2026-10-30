package com.expensetracker.mobile.data

import com.expensetracker.mobile.db.ExpenseFlowDatabase

/**
 * Removes all synced rows, pending outbox entries, and sync cursors in one transaction.
 * Used when the server no longer recognises the signed-in account.
 */
class LocalDataWiper(private val db: ExpenseFlowDatabase) {
    fun wipe() {
        db.transaction {
            db.expenseFlowQueries.deleteAllOutbox()
            db.expenseFlowQueries.deleteAllExpenses()
            db.expenseFlowQueries.deleteAllCategories()
            db.expenseFlowQueries.deleteAllSyncState()
        }
    }
}
