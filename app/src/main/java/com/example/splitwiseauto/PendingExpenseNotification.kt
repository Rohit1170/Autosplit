package com.example.splitwiseauto

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A detected-but-not-yet-actioned expense. Written the moment an SMS is parsed,
 * alongside showing the system notification — so if the user swipes the
 * notification away without tapping it, the expense is still recoverable from
 * inside the app instead of being lost. Removed once the user opens it (from the
 * notification or the in-app list) and either saves or explicitly cancels it.
 */
@Entity(tableName = "pending_expense_notifications")
data class PendingExpenseNotification(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val amount: String,

    val merchant: String,

    val bank: String,

    val date: String,

    val balance: String,

    val upi: String,

    val accountIdentifier: String? = null,

    val latitude: Double? = null,

    val longitude: Double? = null,

    val timestampMillis: Long,

    val createdAtMillis: Long = System.currentTimeMillis(),

    // "scan_pay" when this pending row was created by a successful Scan &
    // Pay attempt (drives the "Scan & Pay" badge in the queue UI); null for
    // every pending notification created the normal SMS-detection way.
    val source: String? = null,

    // Set alongside source = "scan_pay" — links back to the payment_attempts
    // row this notification was adopted from. Null otherwise.
    val paymentAttemptId: Int? = null,

    // v18: foreign-currency spend (see Expense.originalAmount).
    val originalAmount: Double? = null,
    val originalCurrency: String? = null,
    @ColumnInfo(defaultValue = "0")
    val fxEstimated: Boolean = false
)
