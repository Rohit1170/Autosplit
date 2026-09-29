package com.example.splitwiseauto

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The single place a Scan & Pay payment becomes an expense.
 *
 * Both the UPI-app result (QrScanActivity) and the bank SMS
 * (PendingPaymentMatcher, in SmsReceiver) can race to do this. Everything
 * here runs under one process-wide lock and re-reads the payment first, so a
 * payment is linked to AT MOST one expense and never produces a split prompt:
 * the payer is already known (you), so it is saved as paid right away. It can
 * still be split later by editing it from Home.
 */
object ScanPayLedger {

    private val lock = Mutex()

    /**
     * Returns the id of the expense this payment is linked to, creating it
     * if the payment isn't linked yet. Idempotent.
     *
     * [bank]/[accountIdentifier]/[timestampMillis] come from the bank SMS when
     * that's what triggered this; otherwise defaults are used and the SMS
     * fills them in later via [attachBankDetails].
     */
    suspend fun ensureExpense(
        context: Context,
        paymentId: String,
        bank: String? = null,
        accountIdentifier: String? = null,
        timestampMillis: Long? = null
    ): Int? = lock.withLock {
        val database = AppDatabase.getDatabase(context)
        val paymentDao = database.pendingPaymentDao()
        val payment = paymentDao.getById(paymentId) ?: return@withLock null

        // Already linked: either started from a bill, or created earlier.
        payment.billId?.toIntOrNull()?.let { return@withLock it }

        // The same debit may already be an expense (e.g. the user saved it by
        // hand) — link to it instead of creating a duplicate.
        payment.upiRef?.let { ref ->
            database.expenseDao().findByUpi(ref)?.let { existing ->
                paymentDao.linkBill(payment.id, existing.id.toString())
                database.pendingExpenseNotificationDao().deleteByUpi(ref)
                return@withLock existing.id
            }
        }

        val merchant = payment.payeeName ?: payment.payeeVpa
        val whenMillis = timestampMillis ?: System.currentTimeMillis()
        val amount = payment.amountMinor / 100.0
        val location = LocationHelper.getCurrentLocation(context)

        val expenseId = database.expenseDao().insertExpense(
            Expense(
                amount = amount,
                merchant = merchant,
                bank = bank ?: "UPI",
                date = SimpleDateFormat("dd-MMM-yy", Locale.US).format(Date(whenMillis)),
                // Until the bank SMS supplies the real UPI ref, our own payment
                // reference identifies this expense (see attachBankDetails).
                upi = payment.upiRef?.takeIf { it.length == 12 } ?: payment.id,
                payer = "Me",
                category = categorize(merchant).name,
                accountIdentifier = accountIdentifier,
                latitude = location?.latitude,
                longitude = location?.longitude,
                timestampMillis = whenMillis,
                tripId = ActiveTripStore.getActiveTripId(context)
            )
        ).toInt()

        database.expenseParticipantDao().insertParticipants(
            listOf(ExpenseParticipant(expenseId = expenseId, personName = "Me", shareAmount = amount))
        )
        paymentDao.linkBill(payment.id, expenseId.toString())
        payment.upiRef?.let { database.pendingExpenseNotificationDao().deleteByUpi(it) }
        expenseId
    }

    /**
     * After the SMS confirms a payment: put the bank's UPI ref (and bank /
     * account) on the expense this flow created. Bills the payment was merely
     * linked to are left untouched.
     */
    suspend fun attachBankDetails(
        context: Context,
        paymentId: String,
        upiRef: String?,
        bank: String,
        accountIdentifier: String?
    ) = lock.withLock {
        val database = AppDatabase.getDatabase(context)
        val payment = database.pendingPaymentDao().getById(paymentId) ?: return@withLock
        val expenseId = payment.billId?.toIntOrNull() ?: return@withLock
        val expense = database.expenseDao().getExpenseById(expenseId) ?: return@withLock
        val createdByThisFlow = expense.upi == payment.id || expense.upi == upiRef
        if (!createdByThisFlow || upiRef == null) return@withLock
        database.expenseDao().attachBankReference(expenseId, upiRef, bank, accountIdentifier)
        database.pendingExpenseNotificationDao().deleteByUpi(upiRef)
    }
}
