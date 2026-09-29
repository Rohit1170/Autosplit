package com.example.splitwiseauto

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Calendar-month identifier. Plain ints (not java.time) since minSdk 24 has no desugaring set up. */
data class MonthKey(val year: Int, val month: Int) { // month is 1-12

    fun displayLabel(): String =
        SimpleDateFormat("MMMM yyyy", Locale.US).format(monthStartCalendar().time)

    fun shortLabel(): String =
        SimpleDateFormat("MMM", Locale.US).format(monthStartCalendar().time)

    fun monthStartCalendar(): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, 1, 0, 0, 0)
    }

    fun daysInMonth(): Int = monthStartCalendar().getActualMaximum(Calendar.DAY_OF_MONTH)

    fun previous(): MonthKey = if (month == 1) MonthKey(year - 1, 12) else MonthKey(year, month - 1)

    companion object {
        fun current(): MonthKey {
            val now = Calendar.getInstance()
            return MonthKey(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
        }

        fun fromMillis(millis: Long): MonthKey {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return MonthKey(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }
    }
}

data class SpendTrendUiState(
    val monthLabel: String = "",
    val isCurrentMonth: Boolean = true,
    val totalSoFar: Double = 0.0,
    val percentChangeVsPreviousMonth: Double? = null,
    val dailyPoints: List<Double> = emptyList(),
    val projectedMonthEnd: Double? = null,
    val hasAnyDatedExpenses: Boolean = true,
    // Count of dated expenses that fall in the selected month — backs the
    // "txns" stat in the 3-stat row (daily avg / biggest day / txns).
    val transactionCount: Int = 0
)

class InsightsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    // Kept around (not just the current month's slice) so later Insights sections
    // — top merchants, recurring detection, split summary — can reuse this same
    // load without another DB round trip.
    private var allExpenses: List<ExpenseWithParticipants> = emptyList()

    // Set only in Main mode (tripId == null), used to add one extra
    // category-style row per trip to the breakdown.
    private var tripNamesById: Map<Int, String> = emptyMap()
    private var currentTripId: Int? = null

    private val _selectedMonth = MutableStateFlow(MonthKey.current())
    val selectedMonth: StateFlow<MonthKey> = _selectedMonth

    // True while the user is looking at "the current month" (the default).
    // Android can keep this ViewModel alive for days, so without this a
    // September selection would stay stuck on September after 1 October,
    // showing it as a finished month and hiding October's spending. When
    // this is true, the next load (e.g. reopening the app) moves the
    // selection forward to the new month. If the user deliberately picked
    // an older month, this is false and their choice is left alone.
    private var followCurrentMonth = true

    private val _availableMonths = MutableStateFlow<List<MonthKey>>(listOf(MonthKey.current()))
    val availableMonths: StateFlow<List<MonthKey>> = _availableMonths

    private val _spendTrend = MutableStateFlow(SpendTrendUiState())
    val spendTrend: StateFlow<SpendTrendUiState> = _spendTrend

    private val _categoryBreakdown = MutableStateFlow<List<CategorySummary>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategorySummary>> = _categoryBreakdown

    // null (Main mode) behaves exactly as before — every expense, with an
    // extra synthetic row per trip in the breakdown below. Passing a tripId
    // (Trip mode) narrows everything to that trip only, same sections as today.
    fun loadExpenses(tripId: Int? = null) {
        currentTripId = tripId
        viewModelScope.launch {
            val fetched = ExpenseRepository.allExpenses(db.expenseDao())
            allExpenses = if (tripId == null) fetched else fetched.filter { it.expense.tripId == tripId }

            tripNamesById = if (tripId == null) {
                db.tripDao().getAll().associate { it.id to it.name }
            } else {
                emptyMap()
            }

            val datedMonths = allExpenses
                .mapNotNull { it.expense.timestampMillis }
                .map { MonthKey.fromMillis(it) }
                .distinct()

            _availableMonths.value =
                (datedMonths + MonthKey.current())
                    .distinct()
                    .sortedWith(compareByDescending<MonthKey> { it.year }.thenByDescending { it.month })

            val nowMonth = MonthKey.current()
            if (followCurrentMonth && _selectedMonth.value != nowMonth) {
                _selectedMonth.value = nowMonth
            }

            recomputeSpendTrend()
            recomputeCategoryBreakdown()
        }
    }

    fun selectMonth(month: MonthKey) {
        followCurrentMonth = month == MonthKey.current()
        _selectedMonth.value = month
        recomputeSpendTrend()
        recomputeCategoryBreakdown()
    }

    private fun recomputeCategoryBreakdown() {
        val month = _selectedMonth.value

        val expensesInMonth = allExpenses.filter { item ->
            val millis = item.expense.timestampMillis ?: return@filter false
            MonthKey.fromMillis(millis) == month
        }

        val monthTotal = expensesInMonth.sumOf { it.expense.amount }

        val categoryRows = expensesInMonth
            .groupBy { categoryByName(it.expense.category) }
            .map { (category, items) ->
                val sum = items.sumOf { it.expense.amount }
                val percent = if (monthTotal > 0) ((sum / monthTotal) * 100).toInt() else 0
                CategorySummary(category, sum, percent)
            }

        // Main mode only: one extra row per trip, so a trip's spending shows
        // up alongside Food/Bills/Travel/etc. without touching Trip mode's
        // own (unchanged) breakdown.
        val tripRows = if (currentTripId == null) {
            expensesInMonth
                .filter { it.expense.tripId != null }
                .groupBy { it.expense.tripId }
                .mapNotNull { (tripId, items) ->
                    val tripName = tripNamesById[tripId] ?: return@mapNotNull null
                    val sum = items.sumOf { it.expense.amount }
                    val percent = if (monthTotal > 0) ((sum / monthTotal) * 100).toInt() else 0
                    CategorySummary(
                        category = Category(
                            name = tripName,
                            icon = Icons.Filled.Place,
                            bgColor = Color(0xFFDDEBFF),
                            fgColor = Color(0xFF3B82F6)
                        ),
                        total = sum,
                        percent = percent
                    )
                }
        } else {
            emptyList()
        }

        _categoryBreakdown.value = (categoryRows + tripRows).sortedByDescending { it.total }
    }

    private fun recomputeSpendTrend() {
        val month = _selectedMonth.value
        val hasAnyDated = allExpenses.any { it.expense.timestampMillis != null }

        val daysInMonth = month.daysInMonth()
        val isCurrentMonth = month == MonthKey.current()
        val daysElapsed = if (isCurrentMonth) {
            Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        } else {
            daysInMonth
        }

        val dailyTotals = DoubleArray(daysElapsed)
        allExpenses.forEach { item ->
            val millis = item.expense.timestampMillis ?: return@forEach
            if (MonthKey.fromMillis(millis) != month) return@forEach
            val dayOfMonth = Calendar.getInstance().apply { timeInMillis = millis }
                .get(Calendar.DAY_OF_MONTH)
            if (dayOfMonth in 1..daysElapsed) {
                dailyTotals[dayOfMonth - 1] += item.expense.amount
            }
        }

        val totalSoFar = dailyTotals.sum()

        val transactionCount = allExpenses.count { item ->
            val millis = item.expense.timestampMillis ?: return@count false
            MonthKey.fromMillis(millis) == month
        }

        val projectedMonthEnd = if (isCurrentMonth && daysElapsed > 0) {
            (totalSoFar / daysElapsed) * daysInMonth
        } else {
            null
        }

        // Compared over the same number of elapsed days in the previous month,
        // so a mid-month reading isn't measured against a full previous month.
        val previousMonth = month.previous()
        val previousComparableTotal = allExpenses.sumOf { item ->
            val millis = item.expense.timestampMillis ?: return@sumOf 0.0
            if (MonthKey.fromMillis(millis) != previousMonth) return@sumOf 0.0
            val dayOfMonth = Calendar.getInstance().apply { timeInMillis = millis }
                .get(Calendar.DAY_OF_MONTH)
            if (dayOfMonth in 1..daysElapsed) item.expense.amount else 0.0
        }

        val percentChange = if (previousComparableTotal > 0.0) {
            ((totalSoFar - previousComparableTotal) / previousComparableTotal) * 100.0
        } else {
            null
        }

        _spendTrend.value = SpendTrendUiState(
            monthLabel = month.displayLabel(),
            isCurrentMonth = isCurrentMonth,
            totalSoFar = totalSoFar,
            percentChangeVsPreviousMonth = percentChange,
            dailyPoints = dailyTotals.toList(),
            projectedMonthEnd = projectedMonthEnd,
            hasAnyDatedExpenses = hasAnyDated,
            transactionCount = transactionCount
        )
    }
}
