package com.example.splitwiseauto

import android.content.Context

/**
 * Where a confirming (or failing) bank SMS gets matched against an
 * in-flight Scan & Pay attempt. Called from SmsReceiver — the SAME
 * receiver/parser the rest of the app already uses (parseBankSms), not a
 * second SMS pipeline. If [tryAdopt] claims the SMS, SmsReceiver skips its
 * normal detected-expense flow for that message; if not, the SMS falls
 * through to the normal flow untouched.
 *
 * [transaction] is passed in already parsed (or null, if parseBankSms
 * didn't recognize the message as a debit/credit at all — which a plain
 * failure notice like "your payment has failed" often won't, since it
 * carries neither keyword set parseBankSms looks for). Failure-keyword
 * matching below works directly off the raw SMS text for exactly that
 * reason, independent of [transaction].
 */
object PaymentSmsAdopter {

    private val FAILURE_KEYWORDS = Regex(
        "\\b(failed|declined|unsuccessful|insufficient\\s+balance|" +
            "transaction\\s+failed|not\\s+processed|reversed)\\b",
        RegexOption.IGNORE_CASE
    )

    private val WAITING_STATUSES = listOf(
        PaymentAttemptStatus.WAITING_FOR_VERIFICATION.name,
        PaymentAttemptStatus.TIMEOUT.name
    )

    /** Returns true if [smsBody] was consumed by a payment attempt. */
    suspend fun tryAdopt(
        context: Context,
        smsBody: String,
        transaction: Transaction?,
        receivedAtMillis: Long
    ): Boolean {
        val database = AppDatabase.getDatabase(context)
        val attemptDao = database.paymentAttemptDao()

        val waiting = attemptDao.getByStatuses(WAITING_STATUSES)
            .filter { it.adoptedExpenseId == null }
        if (waiting.isEmpty()) return false

        // Failure keyword match — checked against the raw body, since a
        // failure notice frequently isn't a parseable debit/credit at all.
        for (attempt in waiting) {
            if (!smsBody.contains(formatRupeeForMatch(attempt.expectedAmount))) continue
            if (!FAILURE_KEYWORDS.containsMatchIn(smsBody)) continue

            val claimed = attemptDao.claimStatus(
                attempt.id,
                PaymentAttemptStatus.FAILED.name,
                listOf(attempt.status)
            )
            if (claimed > 0) return true
            // claimed == 0 means something else (or a redelivery of this
            // same SMS) already resolved this attempt — keep checking the
            // rest rather than assuming this SMS is spoken for.
        }

        if (transaction == null || transaction.type != "DEBIT") return false
        val actualAmount = transaction.amount.toDoubleOrNull() ?: return false

        for (attempt in waiting) {
            if (!amountsMatch(attempt.expectedAmount, actualAmount)) continue

            val claimed = attemptDao.claimStatus(
                attempt.id,
                PaymentAttemptStatus.SUCCESS.name,
                listOf(attempt.status)
            )
            if (claimed == 0) continue

            // Same shape as the normal SMS-detected flow: a pending-queue
            // row now, a real Expense only once the person confirms/splits
            // it via ExpenseConfirmationActivity — just badged so it's
            // clear where it came from.
            AppDatabase.getDatabase(context).pendingExpenseNotificationDao().insert(
                PendingExpenseNotification(
                    amount = transaction.amount,
                    merchant = attempt.payeeName ?: attempt.upiId,
                    bank = transaction.bank,
                    date = transaction.date,
                    balance = transaction.balance,
                    upi = attempt.upiId,
                    accountIdentifier = transaction.accountIdentifier,
                    latitude = transaction.latitude,
                    longitude = transaction.longitude,
                    timestampMillis = receivedAtMillis,
                    source = "scan_pay",
                    paymentAttemptId = attempt.id
                )
            )
            return true
        }

        return false
    }

    private fun amountsMatch(expected: Double, actual: Double): Boolean =
        kotlin.math.abs(expected - actual) < 0.01

    private fun formatRupeeForMatch(amount: Double): String =
        amount.toBigDecimal().stripTrailingZeros().toPlainString()
}
