package com.expensetracker.mobile.data

import com.expensetracker.mobile.db.ExpenseFlowDatabase

/**
 * Removes all synced rows, pending outbox entries, and sync cursors in one transaction.
 * Used when the server no longer recognises the signed-in account, and on sign-out.
 */
class LocalDataWiper(private val db: ExpenseFlowDatabase) {
    fun wipe() {
        db.transaction { deleteEverything() }
    }

    /**
     * Wipes unless more than [maxUnsynced] changes are waiting in the outbox. The count and the delete run in
     * one transaction, so a write cannot land between them and be discarded unseen. Returns null after a wipe,
     * or the current pending count when nothing was changed.
     */
    fun wipeUnlessUnsyncedExceeds(maxUnsynced: Long): Long? = db.transactionWithResult {
        val pending = db.expenseFlowQueries.countOutbox().executeAsOne()
        if (pending > maxUnsynced) {
            pending
        } else {
            deleteEverything()
            null
        }
    }

    private fun deleteEverything() {
        db.expenseFlowQueries.deleteAllOutbox()
        db.expenseFlowQueries.deleteAllExpenses()
        db.expenseFlowQueries.deleteAllCategories()
        db.expenseFlowQueries.deleteAllSyncState()
    }
}
