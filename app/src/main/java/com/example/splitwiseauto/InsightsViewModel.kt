package com.example.splitwiseauto

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
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

/**
 * A custom Insights period: whole days, [startMillis] = 00:00 of the first
 * day, [endMillis] = 23:59:59.999 of the last day (both inclusive).
 */
data class DayRange(val startMillis: Long, val endMillis: Long) {

    /** Number of calendar days in the range (inclusive). */
    fun dayCount(): Int {
        var n = 0
        val c = Calendar.getInstance().apply { timeInMillis = startMillis }
        while (c.timeInMillis <= endMillis) { n++; c.add(Calendar.DAY_OF_MONTH, 1) }
        return n
    }

    fun contains(millis: Long) = millis in startMillis..endMillis

    /** "12 – 18 Oct", "28 Sep – 4 Oct", "28 Dec 25 – 3 Jan 26". */
    fun label(): String {
        val s = Calendar.getInstance().apply { timeInMillis = startMillis }
        val e = Calendar.getInstance().apply { timeInMillis = endMillis }
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val sameYear = s.get(Calendar.YEAR) == e.get(Calendar.YEAR)
        val showYear = !sameYear || s.get(Calendar.YEAR) != thisYear
        val dm = SimpleDateFormat("d MMM", Locale.ENGLISH)
        val dmy = SimpleDateFormat("d MMM yy", Locale.ENGLISH)
        return when {
            sameYear && s.get(Calendar.DAY_OF_YEAR) == e.get(Calendar.DAY_OF_YEAR) ->
                (if (showYear) dmy else dm).format(s.time)
            sameYear && s.get(Calendar.MONTH) == e.get(Calendar.MONTH) ->
                "${s.get(Calendar.DAY_OF_MONTH)} – ${(if (showYear) dmy else dm).format(e.time)}"
            else -> "${(if (showYear) dmy else dm).format(s.time)} – ${(if (showYear) dmy else dm).format(e.time)}"
        }
    }

    companion object {
        /** Whole days from [fromDay] to [toDay] (any time on those days; order-insensitive). */
        fun of(fromDay: Long, toDay: Long): DayRange {
            val a = minOf(fromDay, toDay)
            val b = maxOf(fromDay, toDay)
            val start = Calendar.getInstance().apply {
                timeInMillis = a; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val end = Calendar.getInstance().apply {
                timeInMillis = b; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
            }
            return DayRange(start.timeInMillis, end.timeInMillis)
        }

        /** The last [days] days, ending today. */
        fun lastDays(days: Int): DayRange {
            val now = System.currentTimeMillis()
            val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -(days - 1)) }.timeInMillis
            return of(from, now)
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
    val transactionCount: Int = 0,
    // Regular (non-trip) spending per elapsed day. Trips are left out so one
    // weekend away doesn't inflate it. (Trip mode: the trip's own average.)
    val dailyAverage: Double = 0.0,
    // Trip spending this month that the average/projection leave out, and a
    // label for it ("Goa trip", "2 trips"). 0/null when there is none.
    val excludedTripSpend: Double = 0.0,
    val excludedTripLabel: String? = null,
    // Current month in Main mode, but no regular spending to project from.
    val notEnoughToProject: Boolean = false,
    val isTripMode: Boolean = false,
    // Regular (non-trip) part of the month-end projection, shown as the
    // breakdown under "On pace for …" when a trip is added on top.
    val regularProjection: Double? = null,
    // What the same days of last month added up to (the % badge's baseline).
    val previousComparableTotal: Double = 0.0,
    // "Oct" — for the tapped-day label on the chart.
    val monthShortName: String = "",
    // Custom range only: the date of each chart point ("12 Oct"), and what
    // the % badge compares against ("previous 7 days").
    val pointLabels: List<String> = emptyList(),
    val comparisonLabel: String = "last month"
)

/** Below this, "vs last month" is meaningless (+18693% from ₹370), so it's hidden. */
private const val MIN_COMPARISON_BASELINE = 1_000.0

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

    // Non-null while the user is looking at a custom date range instead of a
    // calendar month. Everything on the tab (trend, categories, locations)
    // then uses this range.
    private val _customRange = MutableStateFlow<DayRange?>(null)
    val customRange: StateFlow<DayRange?> = _customRange

    private val _availableMonths = MutableStateFlow<List<MonthKey>>(listOf(MonthKey.current()))
    val availableMonths: StateFlow<List<MonthKey>> = _availableMonths

    private val _spendTrend = MutableStateFlow(SpendTrendUiState())
    val spendTrend: StateFlow<SpendTrendUiState> = _spendTrend

    private val _categoryBreakdown = MutableStateFlow<List<CategorySummary>>(emptyList())
    val categoryBreakdown: StateFlow<List<CategorySummary>> = _categoryBreakdown

    // Main mode only, and only when the month has trip spending: the same
    // month total split by location (Main vs each trip). A separate list from
    // the category breakdown so no list ever mixes two dimensions.
    private val _locationBreakdown = MutableStateFlow<List<CategorySummary>>(emptyList())
    val locationBreakdown: StateFlow<List<CategorySummary>> = _locationBreakdown

    /** null = still loading; false = no expenses at all yet (fresh install). */
    private val _hasAnyExpenses = MutableStateFlow<Boolean?>(null)
    val hasAnyExpenses: StateFlow<Boolean?> = _hasAnyExpenses

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
            if (followCurrentMonth && _customRange.value == null && _selectedMonth.value != nowMonth) {
                _selectedMonth.value = nowMonth
            }

            recomputeSpendTrend()
            recomputeCategoryBreakdown()
            _hasAnyExpenses.value = allExpenses.isNotEmpty()
        }
    }

    fun selectMonth(month: MonthKey) {
        followCurrentMonth = month == MonthKey.current()
        _customRange.value = null
        _selectedMonth.value = month
        recomputeSpendTrend()
        recomputeCategoryBreakdown()
    }

    /** Show Insights for a custom range of whole days instead of a month. */
    fun selectRange(range: DayRange) {
        followCurrentMonth = false
        _customRange.value = range
        recomputeSpendTrend()
        recomputeCategoryBreakdown()
    }

    private fun inSelectedPeriod(millis: Long): Boolean =
        _customRange.value?.contains(millis) ?: (MonthKey.fromMillis(millis) == _selectedMonth.value)

    private fun recomputeCategoryBreakdown() {
        val expensesInMonth = allExpenses.filter { item ->
            val millis = item.expense.timestampMillis ?: return@filter false
            inSelectedPeriod(millis)
        }

        // ONE dimension per list. "Where it went" = categories across every
        // location (trips included), so its rows add up to the month total
        // and its percentages to 100%. It used to append one row per trip
        // ("Goa 87%") on top of the category rows, double-counting the trip
        // spending (rows summed to 186%).
        val byCategory = expensesInMonth
            .groupBy { categoryByName(it.expense.category) }
            .map { (category, items) -> category to items.sumOf { it.expense.amount } }
            .sortedByDescending { it.second }
        val categoryPercents = InsightsMath.percents(byCategory.map { it.second })
        _categoryBreakdown.value = byCategory.mapIndexed { i, (category, sum) ->
            CategorySummary(category, sum, categoryPercents[i])
        }

        // "By location": Main (no trip) vs each trip — Main mode only, and
        // only if this month actually has trip spending.
        _locationBreakdown.value = if (currentTripId == null &&
            expensesInMonth.any { it.expense.tripId != null }
        ) {
            val byLocation = expensesInMonth
                .groupBy { it.expense.tripId }
                .map { (tripId, items) ->
                    val location = if (tripId == null) {
                        Category("Main", Icons.Filled.Home, Color(0xFFDCF7EC), Color(0xFF14A87C))
                    } else {
                        Category(
                            name = tripNamesById[tripId] ?: "Trip",
                            icon = Icons.Filled.Place,
                            bgColor = Color(0xFFDDEBFF),
                            fgColor = Color(0xFF3B82F6)
                        )
                    }
                    location to items.sumOf { it.expense.amount }
                }
                .sortedByDescending { it.second }
            val locationPercents = InsightsMath.percents(byLocation.map { it.second })
            byLocation.mapIndexed { i, (location, sum) ->
                CategorySummary(location, sum, locationPercents[i])
            }
        } else {
            emptyList()
        }
    }

    private fun recomputeSpendTrend() {
        _customRange.value?.let { recomputeRangeTrend(it); return }
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

        // Pace + daily average use REGULAR spending only (Main mode): a trip
        // is a one-off, so including it predicted absurd months (one Goa
        // weekend -> "on pace for ₹2,00,987"). "Biggest day" and the chart
        // above still show the raw days, trips included.
        val isTripMode = currentTripId != null
        val tripSpendInMonth = if (isTripMode) 0.0 else allExpenses.sumOf { item ->
            val millis = item.expense.timestampMillis ?: return@sumOf 0.0
            if (item.expense.tripId == null || MonthKey.fromMillis(millis) != month) return@sumOf 0.0
            val dayOfMonth = Calendar.getInstance().apply { timeInMillis = millis }
                .get(Calendar.DAY_OF_MONTH)
            if (dayOfMonth in 1..daysElapsed) item.expense.amount else 0.0
        }
        val tripIdsInMonth = if (isTripMode) emptySet() else allExpenses.mapNotNull { item ->
            val millis = item.expense.timestampMillis ?: return@mapNotNull null
            item.expense.tripId?.takeIf { MonthKey.fromMillis(millis) == month }
        }.toSet()
        val excludedTripLabel = when {
            tripSpendInMonth <= 0.0 -> null
            tripIdsInMonth.size == 1 -> tripNamesById[tripIdsInMonth.first()]?.let { "$it trip" } ?: "a trip"
            else -> "${tripIdsInMonth.size} trips"
        }

        val pace = if (isTripMode) {
            // Viewing a trip: its own average, and no month-end prediction.
            InsightsMath.Pace(if (daysElapsed > 0) totalSoFar / daysElapsed else 0.0, null)
        } else {
            InsightsMath.pace(totalSoFar - tripSpendInMonth, daysElapsed, daysInMonth, isCurrentMonth)
        }
        // Month-end estimate = regular pace for the whole month + the trip
        // money already spent (a trip is a one-off: it isn't extrapolated,
        // but it IS part of what the month will cost). Shown next to a
        // "spent so far" that includes the trip, so the two now agree.
        val regularProjection = pace.projectedMonthEnd
        val projectedMonthEnd = regularProjection?.let { it + tripSpendInMonth }

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

        // A tiny baseline (e.g. the app was only installed late last month)
        // gives meaningless numbers like "+18693%", so no badge below ₹1,000.
        val percentChange = if (previousComparableTotal >= MIN_COMPARISON_BASELINE) {
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
            transactionCount = transactionCount,
            dailyAverage = pace.dailyAverage,
            excludedTripSpend = tripSpendInMonth,
            excludedTripLabel = excludedTripLabel,
            notEnoughToProject = !isTripMode && isCurrentMonth && projectedMonthEnd == null,
            regularProjection = regularProjection,
            previousComparableTotal = previousComparableTotal,
            monthShortName = java.text.SimpleDateFormat("MMM", java.util.Locale.ENGLISH)
                .format(month.monthStartCalendar().time),
            isTripMode = isTripMode
        )
    }

    /**
     * Trend for a custom range: one point per day from the first day to the
     * last (or to today, if the range runs into the future). No month-end
     * projection; the % badge compares with the same number of days just
     * before the range.
     */
    private fun recomputeRangeTrend(range: DayRange) {
        val hasAnyDated = allExpenses.any { it.expense.timestampMillis != null }
        val now = System.currentTimeMillis()
        val lastMillis = minOf(range.endMillis, DayRange.of(now, now).endMillis)

        val dayStarts = mutableListOf<Long>()
        val c = Calendar.getInstance().apply { timeInMillis = range.startMillis }
        while (c.timeInMillis <= lastMillis) {
            dayStarts += c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        val days = dayStarts.size.coerceAtLeast(1)
        val daily = DoubleArray(dayStarts.size)
        fun dayIndex(millis: Long): Int {
            // Calendar math, not millis / 86_400_000, so DST-free zones and
            // odd offsets still land on the right day.
            val i = dayStarts.indexOfLast { it <= millis }
            return if (i >= 0 && millis <= lastMillis) i else -1
        }

        val isTripMode = currentTripId != null
        var count = 0
        var tripSpend = 0.0
        val tripIds = mutableSetOf<Int>()
        allExpenses.forEach { item ->
            val millis = item.expense.timestampMillis ?: return@forEach
            val i = dayIndex(millis)
            if (i < 0) return@forEach
            daily[i] += item.expense.amount
            count++
            if (!isTripMode) item.expense.tripId?.let { tripSpend += item.expense.amount; tripIds += it }
        }
        val total = daily.sum()
        val average = if (isTripMode) total / days else (total - tripSpend) / days

        // Same number of days immediately before the range.
        val prevEnd = range.startMillis - 1
        val prevStart = Calendar.getInstance().apply {
            timeInMillis = range.startMillis; add(Calendar.DAY_OF_MONTH, -days)
        }.timeInMillis
        val previous = allExpenses.sumOf { item ->
            val m = item.expense.timestampMillis ?: return@sumOf 0.0
            if (m in prevStart..prevEnd) item.expense.amount else 0.0
        }
        val percent = if (previous >= MIN_COMPARISON_BASELINE) ((total - previous) / previous) * 100.0 else null

        val labelFmt = SimpleDateFormat("d MMM", Locale.ENGLISH)
        _spendTrend.value = SpendTrendUiState(
            monthLabel = range.label(),
            isCurrentMonth = range.contains(now),
            totalSoFar = total,
            percentChangeVsPreviousMonth = percent,
            dailyPoints = daily.toList(),
            projectedMonthEnd = null,
            hasAnyDatedExpenses = hasAnyDated,
            transactionCount = count,
            dailyAverage = average,
            excludedTripSpend = tripSpend,
            excludedTripLabel = when {
                tripSpend <= 0.0 -> null
                tripIds.size == 1 -> tripNamesById[tripIds.first()]?.let { "$it trip" } ?: "a trip"
                else -> "${tripIds.size} trips"
            },
            notEnoughToProject = false,
            isTripMode = isTripMode,
            regularProjection = null,
            previousComparableTotal = previous,
            monthShortName = "",
            pointLabels = dayStarts.map { labelFmt.format(java.util.Date(it)) },
            comparisonLabel = "previous ${if (days == 1) "day" else "$days days"}"
        )
    }

    /** Re-read everything (e.g. after a payment was moved to another category). */
    fun refresh() = loadExpenses(currentTripId)

    /**
     * The selected period and the one it's compared with:
     *  - custom range: the same number of days just before it
     *  - this month: the same days of last month (1st..today's date)
     *  - an older month: the whole previous month
     */
    private fun periodWindows(): Triple<LongRange, LongRange, String> {
        _customRange.value?.let { r ->
            val n = r.dayCount()
            val prevStart = Calendar.getInstance().apply {
                timeInMillis = r.startMillis; add(Calendar.DAY_OF_MONTH, -n)
            }.timeInMillis
            return Triple(r.startMillis..r.endMillis, prevStart until r.startMillis,
                DayRange(prevStart, r.startMillis - 1).label())
        }
        val m = _selectedMonth.value
        val start = m.monthStartCalendar().timeInMillis
        val nextStart = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
        val prev = m.previous()
        val prevStart = prev.monthStartCalendar().timeInMillis
        val prevName = SimpleDateFormat("MMMM", Locale.ENGLISH).format(prev.monthStartCalendar().time)
        return if (m == MonthKey.current()) {
            val now = System.currentTimeMillis()
            val prevEnd = minOf(prevStart + (now - start), start - 1)
            Triple(start..now, prevStart..prevEnd, DayRange.of(prevStart, prevEnd).label())
        } else {
            Triple(start until nextStart, prevStart until start, prevName)
        }
    }

    private fun locationName(e: ExpenseWithParticipants): String =
        e.expense.tripId?.let { tripNamesById[it] ?: "Trip" } ?: "Main"

    /** Data for the drill-down sheet of one "Where it went" / "By location" row. */
    fun drill(summary: CategorySummary, byLocation: Boolean): DrillData {
        val (cur, prev, prevLabel) = periodWindows()
        fun matches(e: ExpenseWithParticipants) =
            if (byLocation) locationName(e) == summary.category.name
            else categoryByName(e.expense.category).name == summary.category.name
        val items = allExpenses
            .filter { e -> e.expense.timestampMillis?.let { it in cur } == true && matches(e) }
            .sortedByDescending { it.expense.timestampMillis }
        val previous = allExpenses
            .filter { e -> e.expense.timestampMillis?.let { it in prev } == true && matches(e) }
            .sumOf { it.expense.amount }
        return DrillData(
            title = summary.category.name,
            icon = summary.category.icon,
            color = summary.category.fgColor,
            periodLabel = _customRange.value?.label()
                ?: (if (_selectedMonth.value == MonthKey.current())
                    DayRange.of(_selectedMonth.value.monthStartCalendar().timeInMillis, System.currentTimeMillis()).label()
                else _selectedMonth.value.displayLabel()),
            percent = summary.percent,
            items = items,
            previousTotal = previous,
            previousLabel = prevLabel,
            canRecategorize = !byLocation
        )
    }
}
