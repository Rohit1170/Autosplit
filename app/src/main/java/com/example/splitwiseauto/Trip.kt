package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named grouping of expenses — e.g. "Goa" — created when the user switches
 * into Trip mode. Expenses aren't moved into a trip; they're simply tagged
 * with its id (Expense.tripId) at the moment they're saved while that trip is
 * active, so nothing about the existing "main" (all-time) data changes.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    val createdAtMillis: Long = System.currentTimeMillis()
)
