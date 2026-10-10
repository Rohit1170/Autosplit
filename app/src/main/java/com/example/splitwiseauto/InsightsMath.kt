package com.example.splitwiseauto

import kotlin.math.floor

/** Pure maths behind Insights / Home breakdowns (unit tested in InsightsMathTest). */
object InsightsMath {

    /**
     * Whole-number percentages of [values] that always add up to exactly 100
     * (largest-remainder rounding) — so rows in one list never sum to 97% or
     * 102% from rounding. All zeros when the total is 0.
     */
    fun percents(values: List<Double>): List<Int> {
        val total = values.sum()
        if (values.isEmpty() || total <= 0.0) return values.map { 0 }
        val raw = values.map { it.coerceAtLeast(0.0) / total * 100.0 }
        val floors = raw.map { floor(it).toInt() }.toMutableList()
        var remaining = 100 - floors.sum()
        raw.indices
            .sortedByDescending { raw[it] - floors[it] }
            .forEach { i ->
                if (remaining <= 0) return@forEach
                floors[i] += 1
                remaining--
            }
        return floors
    }

    data class Pace(val dailyAverage: Double, val projectedMonthEnd: Double?)

    /**
     * Daily average and month-end projection from REGULAR (non-trip) spending
     * only: pace = nonTripSpent / daysElapsed × daysInMonth. No projection
     * when there is no regular spending (a ₹0 "prediction" would be wrong
     * too) or when the month is already over.
     */
    fun pace(nonTripSpent: Double, daysElapsed: Int, daysInMonth: Int, isCurrentMonth: Boolean): Pace {
        if (daysElapsed <= 0) return Pace(0.0, null)
        val daily = nonTripSpent / daysElapsed
        val projected = if (isCurrentMonth && nonTripSpent > 0.0) daily * daysInMonth else null
        return Pace(daily, projected)
    }
}
