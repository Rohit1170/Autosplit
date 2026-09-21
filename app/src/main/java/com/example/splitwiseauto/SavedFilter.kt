package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named, reusable custom date range for the Home expense filter (e.g.
 * "Diwali week"). Saved once from the filter dialog, then quickly reapplied
 * later — this never moves or deletes any expense, it's purely a saved
 * shortcut for a date range.
 */
@Entity(tableName = "saved_filters")
data class SavedFilter(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    val startMillis: Long,

    val endMillis: Long,

    val createdAtMillis: Long = System.currentTimeMillis()
)
