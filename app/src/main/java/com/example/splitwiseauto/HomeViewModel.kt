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

/** Subtitle of the merged Slice card (payments without an account number). */
const val SLICE_BANK_LABEL = "UPI payments"

/**
 * Daily average for the month on the hero card, and the previous month's
 * daily average to compare with. Lower is better (it's spending).
 *
 * RULE: trip expenses are always EXCLUDED from this average (both months).
 * A trip is short, high spending that would distort a person's everyday
 * daily average. The card shows an "excl. trips" note for this reason.
 */
data class HeroAverage(
    val perDay: Double,
    val previousPerDay: Double,
    /** "Sep" — for the accessibility text. */
    val previousLabel: String
) {
    /** +12.0 = 12% more per day than last month. */
    val changePercent: Double get() = (perDay - previousPerDay) / previousPerDay * 100.0
}

data class AccountSpendingSummary(
    val bank: String,
    val accountIdentifier: String?,
    val total: Double,
    // Manual expenses you paid in cash (shown as one "Cash" card).
    val isCash: Boolean = false,
    // The SMS called this number a card (not a bank account).
    val isCard: Boolean = false
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
        // One-time: learn which saved numbers are cards from the SMS inbox.
        viewModelScope.launch {
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AccountKinds.backfillOnce(getApplication())
            }
            if (found) refresh()
        }
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

    // Home's donut: TODAY's spending by category (current Main/trip mode,
    // ignoring the month chip). The month view lives on Insights.
    private val _todayCategories = MutableStateFlow<List<CategorySummary>>(emptyList())
    val todayCategories: StateFlow<List<CategorySummary>> = _todayCategories
    private var todayExpenses: List<ExpenseWithParticipants> = emptyList()
    private var yesterdayExpenses: List<ExpenseWithParticipants> = emptyList()

    /** Drill-down for a slice of Home's "Today by category" donut. */
    fun todayDrill(summary: CategorySummary): DrillData {
        fun matches(e: ExpenseWithParticipants) = categoryByName(e.expense.category).name == summary.category.name
        return DrillData(
            title = summary.category.name,
            icon = summary.category.icon,
            color = summary.category.fgColor,
            periodLabel = "Today",
            percent = summary.percent,
            items = todayExpenses.filter(::matches),
            previousTotal = yesterdayExpenses.filter(::matches).sumOf { it.expense.amount },
            previousLabel = "yesterday"
        )
    }

    /** Reload with the filters last used (after a payment changed category). */
    fun refresh() = loadExpenses(currentTripId, currentDateFilter, currentParticipantFilter, currentSelectedFriendNames)

    // Month view only (not all-time / filtered): avg per day vs last month.
    private val _heroAverage = MutableStateFlow<HeroAverage?>(null)
    val heroAverage: StateFlow<HeroAverage?> = _heroAverage

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

            // Everyday spending only: trips are excluded from the daily average,
            // and no average is shown while a trip is selected.
            _heroAverage.value = if (tripId != null) null
                else computeHeroAverage(allData.filter { it.expense.tripId == null }, dateFilter, participantFilter, selectedFriendNames)

            val today = DayRange.of(now, now)
            val todayItems = tripFiltered.filter { e -> e.expense.timestampMillis?.let { today.contains(it) } == true }
            val yesterday = DayRange.of(now - 86_400_000L, now - 86_400_000L)
            todayExpenses = todayItems.sortedByDescending { it.expense.timestampMillis }
            yesterdayExpenses = tripFiltered.filter { e -> e.expense.timestampMillis?.let { yesterday.contains(it) } == true }
            val todayByCategory = todayItems
                .groupBy { categoryByName(it.expense.category) }
                .map { (category, items) -> category to items.sumOf { it.expense.amount } }
                .sortedByDescending { it.second }
            val todayPercents = InsightsMath.percents(todayByCategory.map { it.second })
            _todayCategories.value = todayByCategory.mapIndexed { i, (category, sum) ->
                CategorySummary(category, sum, todayPercents[i])
            }

            _accountSpending.value = data
                .map { it.expense }
                // Cash isn't a bank card; it gets its own card below.
                .filterNot { it.bank.equals("Cash", ignoreCase = true) && it.accountIdentifier == null }
                // Payments with no card/account number are the Slice app's
                // UPI payments (any bank) — one "Slice" card, not one per bank.
                .groupBy { expense ->
                    if (expense.accountIdentifier == null) SLICE_BANK_LABEL to null
                    else expense.bank to expense.accountIdentifier
                }
                .map { (key, expensesForAccount) ->
                    AccountSpendingSummary(
                        bank = key.first,
                        accountIdentifier = key.second,
                        total = expensesForAccount.sumOf { it.amount },
                        isCard = AccountKinds.isCard(getApplication(), key.first, key.second)
                    )
                }
                .sortedByDescending { it.total }
                .let { cards ->
                    // Cash you paid yourself (manual "Paid from: Cash") gets
                    // its own card at the end. Friends' payments aren't yours.
                    val cash = data
                        .map { it.expense }
                        .filter {
                            it.bank.equals("Cash", ignoreCase = true) && it.accountIdentifier == null &&
                                it.payer.equals("Me", ignoreCase = true)
                        }
                        .sumOf { it.amount }
                    if (cash > 0.0) cards + AccountSpendingSummary("Cash", null, cash, isCash = true) else cards
                }
        }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch {
            db.expenseDao().deleteExpenseWithParticipants(expenseId)
            loadExpenses(currentTripId, currentDateFilter, currentParticipantFilter, currentSelectedFriendNames)
        }
    }

    /**
     * This month's spend per day so far (or per day of the whole month, for a
     * past month) against the previous month's spend per day. Null when the
     * hero isn't showing a single month, a Home filter narrows the data, or
     * there's nothing last month to compare with.
     *
     * [items] must already have trip expenses removed (see [HeroAverage] RULE).
     */
    private fun computeHeroAverage(
        items: List<ExpenseWithParticipants>,
        dateFilter: DateFilter,
        participantFilter: ParticipantFilter,
        selectedFriendNames: Set<String>
    ): HeroAverage? {
        val month = dateFilter.month ?: return null
        if (participantFilter != ParticipantFilter.ALL || selectedFriendNames.isNotEmpty()) return null
        val prev = month.previous()
        fun totalOf(m: MonthKey) = items.sumOf { e ->
            val t = e.expense.timestampMillis ?: return@sumOf 0.0
            if (MonthKey.fromMillis(t) == m) e.expense.amount else 0.0
        }
        val days = if (month == MonthKey.current())
            java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
        else month.daysInMonth()
        val prevTotal = totalOf(prev)
        if (prevTotal <= 0.0 || days <= 0) return null
        return HeroAverage(
            perDay = totalOf(month) / days,
            previousPerDay = prevTotal / prev.daysInMonth(),
            previousLabel = prev.shortLabel()
        )
    }
}

/** TreemapView renders 1 primary + 2 tall + 3 compact tiles. */
private const val TREEMAP_MAX_TILES = 6
