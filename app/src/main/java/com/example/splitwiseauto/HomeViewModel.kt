package com.example.splitwiseauto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
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

    /**
     * The hero total together with WHICH total it is (trip + filters), emitted
     * as ONE value so they can never disagree for a frame. null until the
     * first load finishes. The animated counter uses it so it never animates
     * from a placeholder 0 or from the previous filter's number.
     */
    private val _heroTotal = MutableStateFlow<Pair<String, Double>?>(null)
    val heroTotal: StateFlow<Pair<String, Double>?> = _heroTotal

    init {
        // Recompute Home (and so the animated total) the moment expenses
        // change — e.g. "Just me" from the bell or a background SMS — with the
        // trip/filters currently shown, instead of waiting for the next resume.
        viewModelScope.launch {
            ExpenseRepository.changes(db.expenseDao())
                .drop(1) // the first emission is the initial load, done by the screen
                .collect {
                    loadExpenses(currentTripId, currentDateFilter, currentParticipantFilter, currentSelectedFriendNames)
                }
        }
    }

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
            _heroTotal.value =
                "home_total|${tripId ?: "main"}|$dateFilter|$participantFilter|${selectedFriendNames.sorted()}" to grandTotal

            // One dimension only (category) — checked: no trip/location rows
            // are mixed in here. The treemap shows at most 6 tiles, so with
            // more categories the smallest ones are folded into "Other";
            // percentages use largest-remainder rounding so tiles total 100%.
            val byCategory = data
                .groupBy { categoryByName(it.expense.category) }
                .map { (category, shares) -> category to shares.sumOf { it.expense.amount } }
                .sortedByDescending { it.second }
            val tiles = if (byCategory.size <= TREEMAP_MAX_TILES) byCategory else {
                val keep = byCategory.filter { it.first != OTHER }.take(TREEMAP_MAX_TILES - 1)
                val folded = byCategory.filterNot { it in keep }.sumOf { it.second }
                (keep + (OTHER to folded)).sortedByDescending { it.second }
            }
            val tilePercents = InsightsMath.percents(tiles.map { it.second })
            _categorySummaries.value = tiles.mapIndexed { i, (category, sum) ->
                CategorySummary(category, sum, tilePercents[i])
            }

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

/** TreemapView renders 1 primary + 2 tall + 3 compact tiles. */
private const val TREEMAP_MAX_TILES = 6
