package com.example.splitwiseauto

import android.content.Context
import java.util.Calendar

/**
 * The glanceable spending numbers, computed in ONE place from the same
 * sources the app uses (ExpenseRepository, FriendBalances, InsightsMath), so
 * the widget and the app can never disagree.
 */
data class SpendingSummary(
    val monthTotal: Double,      // this calendar month, full bill amounts (= Insights "Spent so far")
    val monthCount: Int,         // expenses this month
    val todayTotal: Double,
    val yesterdayTotal: Double,
    val dailyAverage: Double,    // this month's regular (non-trip) spend per day (= Insights "Daily avg")
    val owedToYou: Double,
    val youOwe: Double
) {
    companion object {
        suspend fun load(context: Context, now: Calendar = Calendar.getInstance()): SpendingSummary {
            val db = AppDatabase.getDatabase(context)
            val expenses = ExpenseRepository.allExpenses(db.expenseDao()).map { it.expense }

            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH)
            val todayStart = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
            val probe = Calendar.getInstance()
            fun inThisMonth(ts: Long): Boolean {
                probe.timeInMillis = ts
                return probe.get(Calendar.YEAR) == year && probe.get(Calendar.MONTH) == month
            }

            var monthTotal = 0.0
            var monthCount = 0
            var nonTripMonth = 0.0
            var today = 0.0
            var yesterday = 0.0
            expenses.forEach { e ->
                val ts = e.timestampMillis ?: return@forEach
                if (inThisMonth(ts)) {
                    monthTotal += e.amount
                    monthCount++
                    if (e.tripId == null) nonTripMonth += e.amount
                }
                if (ts >= todayStart) today += e.amount
                else if (ts >= yesterdayStart) yesterday += e.amount
            }

            val dailyAverage = InsightsMath.pace(
                nonTripSpent = nonTripMonth,
                daysElapsed = now.get(Calendar.DAY_OF_MONTH),
                daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH),
                isCurrentMonth = true
            ).dailyAverage

            val (_, balances) = FriendBalances.compute(db.friendDao(), db.expenseDao(), db.settlementDao())
            return SpendingSummary(
                monthTotal = monthTotal,
                monthCount = monthCount,
                todayTotal = today,
                yesterdayTotal = yesterday,
                dailyAverage = dailyAverage,
                owedToYou = balances.values.sumOf { it.netAmount.coerceAtLeast(0.0) },
                youOwe = balances.values.sumOf { (-it.netAmount).coerceAtLeast(0.0) }
            )
        }
    }
}
