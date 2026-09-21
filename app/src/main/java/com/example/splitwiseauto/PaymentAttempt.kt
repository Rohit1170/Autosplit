package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Every state a Scan & Pay attempt can be in. Stored as [PaymentAttempt.status]'s raw string. */
enum class PaymentAttemptStatus {
    INITIATING,
    IN_PROGRESS,
    WAITING_FOR_VERIFICATION,
    SUCCESS,
    FAILED,
    TIMEOUT,
    CANCELLED
}

/** Which rail a given attempt actually dialled. */
enum class PaymentRail {
    USSD_99,
    IVR_123PAY,
    UPI_DEEPLINK
}

/**
 * One row per "Pay" tap from Scan & Pay — the single source of truth for an
 * in-flight (or finished) payment. [PaymentSessionManager] is the only
 * writer of [status]; nothing else in the app updates this table directly.
 * Rows are never deleted, including FAILED/TIMEOUT/CANCELLED ones — they
 * stay for audit, per the locked design (no destructive cleanup).
 *
 * Inserted with status=INITIATING BEFORE the call is placed, so a process
 * death mid-dial still leaves a row [PaymentSessionManager.reconcileOnStart]
 * can find and resolve on next launch.
 */
@Entity(tableName = "payment_attempts")
data class PaymentAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val upiId: String,

    val payeeName: String?,

    val expectedAmount: Double,

    val status: String,

    val createdAtMillis: Long,

    /** Which SIM slot this was (or should be) dialled from; null if undetermined. */
    val simSlot: Int?,

    /** [PaymentRail] as a raw string. */
    val rail: String,

    /** Set once the bank-SMS confirmation adopts this attempt into a real Expense. */
    val adoptedExpenseId: Int? = null
)

val PaymentAttempt.statusEnum: PaymentAttemptStatus
    get() = runCatching { PaymentAttemptStatus.valueOf(status) }.getOrDefault(PaymentAttemptStatus.FAILED)

val PaymentAttempt.railEnum: PaymentRail
    get() = runCatching { PaymentRail.valueOf(rail) }.getOrDefault(PaymentRail.USSD_99)
