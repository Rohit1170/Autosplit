package com.example.splitwiseauto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CategorySummary(
    val category: Category,
    val total: Double,
    val percent: Int
)

data class AccountSpendingSummary(
    val bank: String,
    val accountIdentifier: String?,
    val total: Double
) {
    val displayName: String
        get() = if (accountIdentifier == null) {
            "$bank · Unknown account"
        } else {
            "$bank · $accountIdentifier"
        }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    private val _expenses = MutableStateFlow<List<ExpenseWithParticipants>>(emptyList())
    val expenses: StateFlow<List<ExpenseWithParticipants>> = _expenses

    private val _totalSpent = MutableStateFlow(0.0)
    val totalSpent: StateFlow<Double> = _totalSpent

    private val _mySpent = MutableStateFlow(0.0)
    val mySpent: StateFlow<Double> = _mySpent

    private val _categorySummaries = MutableStateFlow<List<CategorySummary>>(emptyList())
    val categorySummaries: StateFlow<List<CategorySummary>> = _categorySummaries

    private val _accountSpending = MutableStateFlow<List<AccountSpendingSummary>>(emptyList())
    val accountSpending: StateFlow<List<AccountSpendingSummary>> = _accountSpending

    // Remembers whichever mode/filters were last requested so a
    // delete-triggered refresh doesn't accidentally snap back to "Main" or
    // clear the active filter out from under the user.
    private var currentTripId: Int? = null
    private var currentDateFilter: DateFilter = DateFilter()
    private var currentParticipantFilter: ParticipantFilter = ParticipantFilter.ALL
    private var currentSelectedFriendNames: Set<String> = emptySet()

    // null tripId (Main mode) + default filters keeps the exact same
    // unfiltered, all-time query as before this feature existed. Every
    // filter here narrows the same underlying data — nothing is ever moved,
    // duplicated, or deleted by filtering.
    fun loadExpenses(
        tripId: Int? = null,
        dateFilter: DateFilter = DateFilter(),
        participantFilter: ParticipantFilter = ParticipantFilter.ALL,
        // Only used when participantFilter == ANY_FRIEND. Empty means "any
        // friend at all" (unchanged behavior); non-empty narrows to expenses
        // split with at least one of these specific friends (matched by
        // name, e.g. just Akhil, or Akhil + Abin together).
        selectedFriendNames: Set<String> = emptySet()
    ) {
        currentTripId = tripId
        currentDateFilter = dateFilter
        currentParticipantFilter = participantFilter
        currentSelectedFriendNames = selectedFriendNames
        viewModelScope.launch {
            val allData = ExpenseRepository.allExpenses(db.expenseDao())
            val tripFiltered = if (tripId == null) allData else allData.filter { it.expense.tripId == tripId }

            val now = System.currentTimeMillis()
            val dateFiltered = when (dateFilter.type) {
                DateFilterType.ALL -> tripFiltered
                DateFilterType.LAST_WEEK -> {
                    val cutoff = now - 7L * 24 * 60 * 60 * 1000
                    tripFiltered.filter { (it.expense.timestampMillis ?: 0L) >= cutoff }
                }
                DateFilterType.LAST_MONTH -> {
                    val cutoff = now - 30L * 24 * 60 * 60 * 1000
                    tripFiltered.filter { (it.expense.timestampMillis ?: 0L) >= cutoff }
                }
                DateFilterType.CUSTOM -> {
                    val start = dateFilter.startMillis ?: Long.MIN_VALUE
                    val end = dateFilter.endMillis ?: Long.MAX_VALUE
                    tripFiltered.filter { val t = it.expense.timestampMillis; t != null && t in start..end }
                }
            }

            val data = when (participantFilter) {
                ParticipantFilter.ALL -> dateFiltered
                // "With me" is group expenses split between Me and at least
                // one other person — a solo "Me"-only expense belongs to
                // "Only me", not here.
                ParticipantFilter.WITH_ME -> dateFiltered.filter { ewp ->
                    ewp.participants.any { it.personName.equals("Me", ignoreCase = true) } &&
                            ewp.participants.size > 1
                }
                ParticipantFilter.WITHOUT_ME -> dateFiltered.filter { ewp ->
                    ewp.participants.none { it.personName.equals("Me", ignoreCase = true) }
                }
                ParticipantFilter.ONLY_ME -> dateFiltered.filter { ewp ->
                    ewp.participants.size == 1 &&
                            ewp.participants[0].personName.equals("Me", ignoreCase = true)
                }
                ParticipantFilter.ANY_FRIEND -> if (selectedFriendNames.isEmpty()) {
                    dateFiltered.filter { ewp ->
                        ewp.participants.any { !it.personName.equals("Me", ignoreCase = true) }
                    }
                } else {
                    dateFiltered.filter { ewp ->
                        ewp.participants.any { p ->
                            selectedFriendNames.any { name -> p.personName.equals(name, ignoreCase = true) }
                        }
                    }
                }
            }
            _expenses.value = data

            val personalExpenseShares = data.mapNotNull { expenseWithParticipants ->
                val myShare = expenseWithParticipants.participants
                    .filter { it.personName.equals("Me", ignoreCase = true) }
                    .sumOf { it.shareAmount }

                if (myShare > 0.0) {
                    categorize(expenseWithParticipants.expense.merchant) to myShare
                } else {
                    null
                }
            }
            val personalTotal = personalExpenseShares.sumOf { it.second }
            _mySpent.value = personalTotal

            // Home shows the full value of all bills. Personal shares remain
            // available separately for the fixed "Me" card in Friends.
            val grandTotal = data.sumOf { it.expense.amount }
            _totalSpent.value = grandTotal

            _categorySummaries.value = data
                .groupBy { categoryByName(it.expense.category) }
                .map { (category, shares) ->
                    val sum = shares.sumOf { it.expense.amount }
                    val percent = if (grandTotal > 0) ((sum / grandTotal) * 100).toInt() else 0
                    CategorySummary(category, sum, percent)
                }
                .sortedByDescending { it.total }

            _accountSpending.value = data
                .map { it.expense }
                // Manual cash is intentionally not presented as a bank/card account.
                .filterNot { it.bank.equals("Cash", ignoreCase = true) && it.accountIdentifier == null }
                .groupBy { expense -> expense.bank to expense.accountIdentifier }
                .map { (key, expensesForAccount) ->
                    AccountSpendingSummary(
                        bank = key.first,
                        accountIdentifier = key.second,
                        total = expensesForAccount.sumOf { it.amount }
                    )
                }
                .sortedByDescending { it.total }
        }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch {
            db.expenseDao().deleteExpenseWithParticipants(expenseId)
            loadExpenses(currentTripId, currentDateFilter, currentParticipantFilter, currentSelectedFriendNames)
        }
    }
}
