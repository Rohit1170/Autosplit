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
 * LEGACY — from the retired *99# USSD Scan & Pay flow. Nothing creates new
 * rows anymore (Scan & Pay now uses the UPI intent flow, see PendingPayment),
 * and nothing matches SMS against these rows. The entity and table are kept
 * only because Room migrations here are additive-only: existing rows stay
 * for audit, and ExpenseConfirmationActivity still honours the old
 * paymentAttemptId link on any pending expense queued before the change.
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
