package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val amount: Double,

    val merchant: String,

    val bank: String,

    val date: String,

    val upi: String,

    val payer: String = "Me",

    val category: String = "Other",

    val accountIdentifier: String? = null,

    val latitude: Double? = null,

    val longitude: Double? = null,

    val timestampMillis: Long? = null,

    // null == this expense belongs only to "Main" (no trip). When a trip is
    // active at save time, it's tagged with that trip's id so Trip-mode
    // screens can filter to it while Main keeps seeing everything, unchanged.
    val tripId: Int? = null,

    // Set only when this expense was created from a Scan & Pay payment
    // attempt that resolved to SUCCESS — links back to payment_attempts for
    // audit purposes. Null for every expense created any other way.
    val paymentAttemptId: Int? = null
)
