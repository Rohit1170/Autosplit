package com.example.splitwiseauto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Confirms Scan & Pay payments from bank debit SMS. Runs FIRST in SmsReceiver,
 * before the generic "new expense → split prompt" flow. If it claims an SMS,
 * that SMS never produces a split prompt: the payment is already assigned.
 *
 * Works only from structured fields (parsed amount + 12-digit UPI reference);
 * the raw SMS body is only read in memory and never stored.
 *
 * Match order:
 *  1. Exact: the SMS's 12-digit UPI reference equals PendingPayment.upiRef
 *     (any status except CONFIRMED — a late SMS with an exact reference is
 *     unambiguous, even after a timeout).
 *  2. Fallback: same amount in paise, for a PENDING or VERIFIED_PENDING
 *     payment initiated within [FALLBACK_WINDOW_MILLIS] before the SMS
 *     (oldest such payment first).
 */
object PendingPaymentMatcher {

    private const val TAG = "PendingPaymentMatcher"
    const val FALLBACK_WINDOW_MILLIS = 15L * 60 * 1000

    /** Allowed clock skew between the phone's clock and the SMS timestamp. */
    private const val CLOCK_SKEW_MILLIS = 60L * 1000

    /** How far after initiation a "Check SMS again" rescan will look. */
    private const val RESCAN_WINDOW_MILLIS = 30L * 60 * 1000

    const val SOURCE_SCAN_PAY = "scan_pay"

    private val REF_AFTER_KEYWORD = Regex(
        """(?:upi|ref|rrn|utr|txn|transaction)[^\d]{0,20}(\d{12})(?!\d)""",
        RegexOption.IGNORE_CASE
    )
    private val ANY_TWELVE_DIGITS = Regex("""(?<![\dxX*])(\d{12})(?!\d)""")

    /** The 12-digit UPI/RRN reference in a bank SMS, or null if there isn't one. */
    fun extractUpiRef12(smsBody: String): String? =
        REF_AFTER_KEYWORD.find(smsBody)?.groupValues?.get(1)
            ?: ANY_TWELVE_DIGITS.find(smsBody)?.groupValues?.get(1)

    /**
     * Live path, called by SmsReceiver for every debit SMS.
     * Returns handledByPaymentMatcher: true means this SMS belongs to a Scan
     * & Pay payment and the caller must NOT run the generic split flow.
     */
    suspend fun tryConfirm(
        context: Context,
        transaction: Transaction?,
        smsBody: String,
        receivedAtMillis: Long
    ): Boolean {
        if (transaction == null || transaction.type != "DEBIT") return false
        val smsAmountMinor = transaction.amountMinor() ?: return false
        val dao = AppDatabase.getDatabase(context).pendingPaymentDao()
        val smsRef = extractUpiRef12(smsBody)

        val exact = smsRef?.let { dao.findByUpiRef(it) }
        if (exact != null) {
            // Already confirmed by this same reference (e.g. SMS redelivered):
            // still ours, so still skip the split prompt.
            if (exact.statusEnum == PendingPaymentStatus.CONFIRMED) return true
            return confirm(context, exact, transaction, smsRef, receivedAtMillis)
        }

        val fallback = dao.getPending()
            .filter {
                it.amountMinor == smsAmountMinor &&
                    it.initiatedAt >= receivedAtMillis - FALLBACK_WINDOW_MILLIS &&
                    it.initiatedAt <= receivedAtMillis + CLOCK_SKEW_MILLIS
            }
            .minByOrNull { it.initiatedAt }
            // The UPI app said "failed" but the bank debited the same amount
            // within the window: the bank wins. (Seen with GPay: it reports a
            // failure, yet the debit SMS arrives moments later.)
            ?: dao.getFailedSince(receivedAtMillis - FALLBACK_WINDOW_MILLIS)
                .filter {
                    it.amountMinor == smsAmountMinor &&
                        it.initiatedAt <= receivedAtMillis + CLOCK_SKEW_MILLIS
                }
                .minByOrNull { it.initiatedAt }
            ?: return false

        return confirm(context, fallback, transaction, smsRef, receivedAtMillis)
    }

    /**
     * "Check SMS again": re-reads recent inbox SMS (in memory only) looking for
     * this one payment's debit. Needs READ_SMS; returns false without it.
     */
    suspend fun rescanInboxFor(context: Context, paymentId: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return false

        val dao = AppDatabase.getDatabase(context).pendingPaymentDao()
        val payment = dao.getById(paymentId) ?: return false
        if (payment.statusEnum == PendingPaymentStatus.CONFIRMED) return true

        val from = payment.initiatedAt - CLOCK_SKEW_MILLIS
        val until = payment.initiatedAt + RESCAN_WINDOW_MILLIS
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(from.toString(), until.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return false

        cursor.use {
            while (it.moveToNext()) {
                val body = it.getString(0) ?: continue
                val sender = it.getString(1)
                val date = it.getLong(2)
                val transaction = parseBankSms(body, sender, date) ?: continue
                if (transaction.type != "DEBIT") continue

                val smsRef = extractUpiRef12(body)
                // Skip an SMS that already confirmed a different payment.
                if (smsRef != null) {
                    val owner = dao.findByUpiRef(smsRef)
                    if (owner != null && owner.id != payment.id &&
                        owner.statusEnum == PendingPaymentStatus.CONFIRMED
                    ) continue
                }
                val refMatches = smsRef != null && smsRef == payment.upiRef
                val amountMatches = transaction.amountMinor() == payment.amountMinor
                if (!refMatches && !amountMatches) continue

                val current = dao.getById(payment.id) ?: return false
                if (current.statusEnum == PendingPaymentStatus.CONFIRMED) return true
                if (confirm(context, current, transaction, smsRef, date)) return true
            }
        }
        return false
    }

    /** Moves [payment] to CONFIRMED and links it to exactly one expense (no split prompt). */
    private suspend fun confirm(
        context: Context,
        payment: PendingPayment,
        transaction: Transaction,
        smsRef: String?,
        smsTimeMillis: Long
    ): Boolean {
        val dao = AppDatabase.getDatabase(context).pendingPaymentDao()

        // Create (or find) the linked expense FIRST, before the status can
        // ever become CONFIRMED. Previously this ran the other way around:
        // status flipped to CONFIRMED (so the UI immediately said "Bank
        // confirmed"), then the expense was created as a separate step. If
        // that second step threw or never ran to completion — a GPS lookup
        // in LocationHelper stalling, the process getting killed mid-flight,
        // any exception — the payment was left stuck CONFIRMED with no
        // expense, and nothing would ever retry it: every later code path
        // that sees statusEnum == CONFIRMED treats it as already handled and
        // returns early. ensureExpense() is idempotent (mutex-guarded, and
        // it re-checks the payment's billId/upiRef for an existing link), so
        // it's safe to call here even if another caller is racing to confirm
        // the same payment.
        val expenseId = ScanPayLedger.ensureExpense(
            context,
            payment.id,
            bank = transaction.bank,
            accountIdentifier = transaction.accountIdentifier,
            timestampMillis = smsTimeMillis
        )
        if (expenseId == null) {
            // Payment row vanished from under us — don't mark it CONFIRMED
            // with nothing for it to point to.
            Log.e(TAG, "Payment ${payment.id} could not be linked to an expense; leaving status unchanged")
            return false
        }

        val claimed = dao.confirm(payment.id, payment.status, smsRef, smsTimeMillis)
        if (claimed == 0) {
            // Lost the status race (another copy of this SMS, or the UPI-app
            // result, moved it first) — but the expense above is already
            // guaranteed to exist either way, so there's nothing left to do.
            val current = dao.getById(payment.id) ?: return false
            if (current.statusEnum != PendingPaymentStatus.CONFIRMED) {
                dao.confirm(current.id, current.status, smsRef, smsTimeMillis)
            }
        }
        Log.d(TAG, "Payment ${payment.id} confirmed by bank SMS")

        // Never silent: tell the user the bank confirmed it — and loudly if
        // the UPI app had reported this payment as failed.
        showPaymentConfirmedNotification(
            context,
            payee = payment.payeeName ?: payment.payeeVpa,
            amountMinor = payment.amountMinor,
            appSaidFailed = payment.statusEnum == PendingPaymentStatus.FAILED
        )

        ScanPayLedger.attachBankDetails(
            context,
            payment.id,
            upiRef = smsRef,
            bank = transaction.bank,
            accountIdentifier = transaction.accountIdentifier
        )
        return true
    }

    private fun Transaction.amountMinor(): Long? =
        amount.replace(",", "").toDoubleOrNull()?.let { rupeesToMinor(it) }
}
