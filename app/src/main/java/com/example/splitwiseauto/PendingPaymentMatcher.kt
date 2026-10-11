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
 *  2. Fallback: same amount in paise within [FALLBACK_WINDOW_MILLIS] AND
 *     the SMS names the payee, from a real SMS, for a PENDING or
 *     VERIFIED_PENDING payment ([PaymentMatchRules]). Anything weaker -
 *     amount only, a notification, a FAILED payment, two possible payments -
 *     is never confirmed automatically; the user is asked instead.
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

    /** Result of offering one bank debit to the Scan & Pay payments. */
    sealed class MatchOutcome {
        /** Proven (reference, or amount + payee from an SMS): payment confirmed. */
        object Confirmed : MatchOutcome()
        /** Plausible but not proven: payment left as it was; ask the user. */
        data class AskUser(val payment: PendingPayment) : MatchOutcome()
        /** Not related to any Scan & Pay payment. */
        object NoMatch : MatchOutcome()
    }

    /**
     * Live path, called by SmsIngestion for every debit (SMS or notification).
     * Only [MatchOutcome.Confirmed] means the caller must NOT run the
     * generic split flow. See [PaymentMatchRules] for what counts as proof.
     */
    suspend fun tryConfirm(
        context: Context,
        transaction: Transaction?,
        smsBody: String,
        receivedAtMillis: Long,
        fromNotification: Boolean = false
    ): MatchOutcome {
        if (transaction == null || transaction.type != "DEBIT") return MatchOutcome.NoMatch
        val smsAmountMinor = transaction.amountMinor() ?: return MatchOutcome.NoMatch
        val dao = AppDatabase.getDatabase(context).pendingPaymentDao()
        val smsRef = extractUpiRef12(smsBody)

        // 1. Exact reference: strongest proof, any status except CONFIRMED.
        val exact = smsRef?.let { dao.findByUpiRef(it) }
        if (exact != null) {
            // Already confirmed by this same reference (SMS redelivered): still ours.
            if (exact.statusEnum == PendingPaymentStatus.CONFIRMED) return MatchOutcome.Confirmed
            return if (confirm(context, exact, transaction, smsRef, receivedAtMillis)) MatchOutcome.Confirmed
            else MatchOutcome.NoMatch
        }

        // 2. Same amount inside the window: needs the payee named too.
        val inWindow = (dao.getPending() + dao.getFailedSince(receivedAtMillis - FALLBACK_WINDOW_MILLIS))
            .distinctBy { it.id }
            .filter {
                it.initiatedAt >= receivedAtMillis - FALLBACK_WINDOW_MILLIS &&
                    it.initiatedAt <= receivedAtMillis + CLOCK_SKEW_MILLIS
            }
        val scored = inWindow.map { p ->
            p to PaymentMatchRules.evidence(
                paymentAmountMinor = p.amountMinor,
                paymentUpiRef = p.upiRef,
                payeeVpa = p.payeeVpa,
                payeeName = p.payeeName,
                debitAmountMinor = smsAmountMinor,
                debitRef = smsRef,
                debitBody = smsBody,
                debitMerchant = transaction.merchant
            )
        }.filter { it.second != PaymentMatchRules.Evidence.NONE }
        if (scored.isEmpty()) return MatchOutcome.NoMatch

        val strong = scored.filter { it.second == PaymentMatchRules.Evidence.AMOUNT_AND_PAYEE }
        val (best, evidence) = (strong.ifEmpty { scored }).minByOrNull { it.first.initiatedAt }!!
        val decision = PaymentMatchRules.decide(
            evidence = evidence,
            status = best.statusEnum,
            fromNotification = fromNotification,
            otherStrongCandidates = (strong.size - 1).coerceAtLeast(0)
        )
        return when (decision) {
            PaymentMatchRules.Decision.CONFIRM ->
                if (confirm(context, best, transaction, smsRef, receivedAtMillis)) MatchOutcome.Confirmed
                else MatchOutcome.NoMatch
            PaymentMatchRules.Decision.ASK_USER -> {
                Log.i(TAG, "Debit could be payment ${best.id} but evidence is $evidence; asking the user")
                MatchOutcome.AskUser(best)
            }
            PaymentMatchRules.Decision.NO_MATCH -> MatchOutcome.NoMatch
        }
    }

    /**
     * The user tapped "Yes, that's my payment" on a debit we could not prove
     * (AskUser). Links that debit (a "To split" row) to the payment: the
     * payment is confirmed, the expense is created once, the row is removed.
     * Returns false and changes nothing if the payment is gone, already
     * confirmed, or the amounts differ.
     */
    suspend fun confirmByUser(context: Context, paymentId: String, pendingRowId: Int): Boolean {
        val db = AppDatabase.getDatabase(context)
        val payment = db.pendingPaymentDao().getById(paymentId) ?: return false
        if (payment.statusEnum == PendingPaymentStatus.CONFIRMED) return false
        val row = db.pendingExpenseNotificationDao().getById(pendingRowId) ?: return false
        val rowMinor = row.amount.replace(",", "").toDoubleOrNull()?.let { rupeesToMinor(it) } ?: return false
        if (rowMinor != payment.amountMinor) return false
        val ref = row.upi.takeIf { it.length == 12 && it.all(Char::isDigit) }
        val tx = Transaction(
            amount = row.amount, merchant = row.merchant, bank = row.bank, date = row.date,
            balance = row.balance, upi = row.upi, type = "DEBIT",
            accountIdentifier = row.accountIdentifier,
            receivedAtMillis = row.timestampMillis
        )
        val ok = confirm(context, payment, tx, ref, row.timestampMillis)
        if (ok) db.pendingExpenseNotificationDao().deleteById(pendingRowId)
        return ok
    }

    /**
     * "Check SMS again": re-reads recent inbox SMS (in memory only) looking for
     * this one payment's debit. Needs READ_SMS; returns false without it.
     */
    enum class RescanResult { CONFIRMED, CANDIDATE_ONLY, NOT_FOUND, NO_PERMISSION }

    suspend fun rescanInboxFor(context: Context, paymentId: String): RescanResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return RescanResult.NO_PERMISSION

        val dao = AppDatabase.getDatabase(context).pendingPaymentDao()
        val payment = dao.getById(paymentId) ?: return RescanResult.NOT_FOUND
        if (payment.statusEnum == PendingPaymentStatus.CONFIRMED) return RescanResult.CONFIRMED

        val from = payment.initiatedAt - CLOCK_SKEW_MILLIS
        val until = payment.initiatedAt + RESCAN_WINDOW_MILLIS
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?",
            arrayOf(from.toString(), until.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return RescanResult.NOT_FOUND

        var sawCandidate = false
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
                val current = dao.getById(payment.id) ?: return RescanResult.NOT_FOUND
                if (current.statusEnum == PendingPaymentStatus.CONFIRMED) return RescanResult.CONFIRMED

                val evidence = PaymentMatchRules.evidence(
                    paymentAmountMinor = current.amountMinor,
                    paymentUpiRef = current.upiRef,
                    payeeVpa = current.payeeVpa,
                    payeeName = current.payeeName,
                    debitAmountMinor = transaction.amountMinor(),
                    debitRef = smsRef,
                    debitBody = body,
                    debitMerchant = transaction.merchant
                )
                when (PaymentMatchRules.decide(evidence, current.statusEnum, fromNotification = false)) {
                    PaymentMatchRules.Decision.CONFIRM ->
                        if (confirm(context, current, transaction, smsRef, date)) return RescanResult.CONFIRMED
                    // Same amount but the SMS doesn't name the payee: never
                    // confirmed automatically, even on an explicit re-check.
                    PaymentMatchRules.Decision.ASK_USER -> sawCandidate = true
                    PaymentMatchRules.Decision.NO_MATCH -> Unit
                }
            }
        }
        return if (sawCandidate) RescanResult.CANDIDATE_ONLY else RescanResult.NOT_FOUND
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
