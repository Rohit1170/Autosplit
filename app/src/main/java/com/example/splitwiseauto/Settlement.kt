package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A signed amount that clears the balance between Me and one friend.
 * Positive means the friend paid Me; negative means Me paid the friend.
 */
@Entity(tableName = "settlements")
data class Settlement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val friendId: Int,
    val amount: Double,
    val settledAt: Long = System.currentTimeMillis()
)
