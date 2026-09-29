package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Indices on tripId and timestampMillis: Home's trip/date filtering
// (HomeViewModel.loadExpenses) reads the whole table and narrows it in
// Kotlin rather than in SQL, but the underlying table scan Room itself does
// on this query benefits from these regardless — and the SQL-level
// tripId/timestampMillis lookups elsewhere (ScanPayLedger, notifications)
// go from a full scan to an index lookup as the table grows.
@Entity(
    tableName = "expenses",
    indices = [Index("tripId"), Index("timestampMillis")]
)
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
