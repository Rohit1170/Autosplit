package com.example.splitwiseauto

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named grouping of expenses — e.g. "Goa" — created when the user switches
 * into Trip mode. Expenses aren't moved into a trip; they're simply tagged
 * with its id (Expense.tripId) at the moment they're saved while that trip is
 * active, so nothing about the existing "main" (all-time) data changes.
 *
 * v15 (additive): optional start/end dates, a closed time and a hidden flag.
 * A closed trip can still be viewed, but new expenses go to Main.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    val createdAtMillis: Long = System.currentTimeMillis(),

    // First day of the trip. null = the day it was created.
    val startMillis: Long? = null,

    // Last day of the trip. null = still going (or the day it was closed).
    val endMillis: Long? = null,

    // When the trip was closed. null = open (new expenses can join it).
    val closedAtMillis: Long? = null,

    // Hidden trips don't appear in the Home trip menu (Manage trips only).
    @ColumnInfo(defaultValue = "0")
    val hidden: Boolean = false
) {
    val isClosed: Boolean get() = closedAtMillis != null
    val rangeStart: Long get() = startMillis ?: createdAtMillis
    val rangeEnd: Long? get() = endMillis ?: closedAtMillis
}

/** "3 – 9 Oct", "28 Sep – 4 Oct", "3 Oct – now", "3 Oct 2025 – 9 Oct 2025". */
fun Trip.dateRangeLabel(now: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance()
    fun parts(ms: Long): Triple<Int, Int, Int> {
        cal.timeInMillis = ms
        return Triple(cal.get(java.util.Calendar.DAY_OF_MONTH), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.YEAR))
    }
    val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val (sd, sm, sy) = parts(rangeStart)
    val (_, _, nowYear) = parts(now)
    val end = rangeEnd ?: return "$sd ${months[sm]}${if (sy != nowYear) " $sy" else ""} – now"
    val (ed, em, ey) = parts(end)
    val showYear = sy != ey || ey != nowYear
    return when {
        !showYear && sm == em && sd == ed -> "$sd ${months[sm]}"
        !showYear && sm == em -> "$sd – $ed ${months[em]}"
        !showYear -> "$sd ${months[sm]} – $ed ${months[em]}"
        else -> "$sd ${months[sm]} $sy – $ed ${months[em]} $ey"
    }
}
