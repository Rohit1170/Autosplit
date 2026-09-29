package com.example.splitwiseauto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/**
 * Shares ONE observed query for the full expense+participant history across
 * every screen that needs it (Home, Insights, Friends, Map) instead of each
 * one independently re-querying the entire table on every refresh.
 *
 * Backed by Room's own Flow support: the underlying query only re-runs when
 * the `expenses`/`expense_participants` tables actually change (Room's own
 * InvalidationTracker, triggered automatically by every existing insert/
 * update/delete — nothing at any of those call sites has to remember to
 * invalidate anything by hand), and every caller observes that same shared
 * result instead of re-fetching it independently.
 *
 * null is the "hasn't produced a real result yet" sentinel, kept separate
 * from a genuinely empty list, so a caller can never see a false-empty
 * flash before Room's first real emission comes in — allExpenses() waits
 * past it rather than ever handing back null or a premature empty list.
 */
object ExpenseRepository {

    // Lives for the process, same as AppDatabase's own singleton instance —
    // this cache is meant to outlive any single screen/ViewModel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var sharedFlow: StateFlow<List<ExpenseWithParticipants>?>? = null

    private fun flowFor(expenseDao: ExpenseDao): StateFlow<List<ExpenseWithParticipants>?> {
        sharedFlow?.let { return it }
        synchronized(this) {
            sharedFlow?.let { return it }
            val created = expenseDao.observeAllExpensesWithParticipants()
                .stateIn(scope, SharingStarted.Eagerly, null)
            sharedFlow = created
            return created
        }
    }

    /** The full expense+participant history — the same list every caller sees. */
    suspend fun allExpenses(expenseDao: ExpenseDao): List<ExpenseWithParticipants> =
        flowFor(expenseDao).filterNotNull().first()
}
