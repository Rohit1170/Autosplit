package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lifecycle of a Scan & Pay payment. Stored as [PendingPayment.status]'s raw string.
 *
 * Only a matching bank debit SMS ever produces [CONFIRMED]. The UPI app's own
 * result can move a payment to [VERIFIED_PENDING] or [FAILED], never further.
 */
enum class PendingPaymentStatus {
    /** Handed off to a UPI app, which gave no clear answer. Waiting for the bank SMS. */
    PENDING,

    /** The UPI app reported success; the bank SMS hasn't been seen yet. */
    VERIFIED_PENDING,

    /** A matching bank debit SMS arrived — the only real proof of payment. */
    CONFIRMED,

    /** The UPI app reported the payment failed. No SMS is expected. */
    FAILED,

    /** Shown as paid (UPI app said success, or the user marked it paid) but no SMS within 30 min. */
    UNVERIFIED,

    /** No clear answer from the UPI app and no SMS within 30 min. */
    UNCONFIRMED;

    /** Still waiting on the bank SMS inside the normal 30-minute window. */
    val isAwaitingSms: Boolean get() = this == PENDING || this == VERIFIED_PENDING
}

/**
 * One row per "Pay" tap in Scan & Pay. Inserted BEFORE the UPI intent is
 * fired, so even if the app is killed while the UPI app is open, the row is
 * still there for the SMS matcher and the timeout worker to resolve.
 *
 * Only structured payment fields live here — never the raw SMS body, and
 * never anything PIN-related (the PIN is entered in the user's UPI app,
 * which this app never sees).
 */
@Entity(tableName = "pending_payments")
data class PendingPayment(
    /** Our own reference. Also sent to the UPI app as the `tr` parameter. */
    @PrimaryKey
    val id: String,

    /** The bill/expense this payment belongs to, once known (expense id as text). */
    val billId: String? = null,

    /** Amount in paise, so matching never depends on floating-point equality. */
    val amountMinor: Long,

    val payeeVpa: String,

    val payeeName: String? = null,

    /** Epoch millis, set just before the UPI intent is launched. */
    val initiatedAt: Long,

    /** [PendingPaymentStatus] as a raw string. */
    val status: String = PendingPaymentStatus.PENDING.name,

    /**
     * Best-effort UPI reference. First filled from the UPI app's response
     * (unreliable, informational only), then overwritten with the 12-digit
     * reference from the confirming bank SMS once matched.
     */
    val upiRef: String? = null,

    /** When the matching bank SMS was received (epoch ms). Set only on CONFIRMED. */
    val confirmedAt: Long? = null
)

val PendingPayment.statusEnum: PendingPaymentStatus
    get() = runCatching { PendingPaymentStatus.valueOf(status) }
        .getOrDefault(PendingPaymentStatus.UNCONFIRMED)

/** Rupees (e.g. 499.5) -> paise (49950), rounded to the nearest paisa. */
fun rupeesToMinor(rupees: Double): Long = Math.round(rupees * 100.0)

/** Paise -> plain rupee text for a UPI URI / display, e.g. 49950 -> "499.50", 50000 -> "500.00". */
fun minorToRupeeText(minor: Long): String =
    java.math.BigDecimal.valueOf(minor, 2).toPlainString()
