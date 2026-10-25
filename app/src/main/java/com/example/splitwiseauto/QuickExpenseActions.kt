package com.example.splitwiseauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One-tap outcomes for a detected bank debit, shared by the notification
 * buttons, the split screen and the bell's "To split" list:
 *  - Just me: save it as my own expense (no split screen).
 *  - Ignore:  drop it. No expense; the SMS is already fingerprinted, so it is
 *             never offered again.
 */
object QuickExpenseActions {

    private const val TAG = "QuickExpenseActions"
    private val lock = Mutex() // a double tap must never save twice

    /** Saves the pending debit as a personal expense. Returns the new expense id, or null. */
    suspend fun saveJustMe(context: Context, pendingId: Int): Int? = lock.withLock {
        val db = AppDatabase.getDatabase(context)
        // Gone already = handled elsewhere (second tap, or saved from the app).
        val pending = db.pendingExpenseNotificationDao().getById(pendingId) ?: return@withLock null
        val amount = pending.amount.replace(",", "").toDoubleOrNull() ?: return@withLock null

        val expenseId = db.expenseDao().insertExpense(
            Expense(
                amount = amount,
                merchant = pending.merchant,
                bank = pending.bank,
                date = pending.date,
                upi = pending.upi,
                payer = "Me",
                category = MerchantCategorizer.resolve(context, pending.merchant),
                accountIdentifier = pending.accountIdentifier,
                latitude = pending.latitude,
                longitude = pending.longitude,
                timestampMillis = pending.timestampMillis,
                tripId = ActiveTripStore.getRecordingTripId(context),
                paymentAttemptId = pending.paymentAttemptId,
                originalAmount = pending.originalAmount,
                originalCurrency = pending.originalCurrency,
                fxEstimated = pending.fxEstimated
            )
        ).toInt()
        db.expenseParticipantDao().insertParticipants(
            listOf(ExpenseParticipant(expenseId = expenseId, personName = "Me", shareAmount = amount))
        )
        pending.paymentAttemptId?.let { db.paymentAttemptDao().setAdoptedExpenseId(it, expenseId) }
        db.pendingExpenseNotificationDao().deleteById(pendingId)
        expenseId
    }

    /** Drops the pending debit without creating an expense. */
    suspend fun ignore(context: Context, pendingId: Int) {
        AppDatabase.getDatabase(context).pendingExpenseNotificationDao().deleteById(pendingId)
    }

    fun cancelNotification(context: Context, pendingId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationIdFor(pendingId)) }
    }

    /** Stable notification id per pending row, so its buttons can dismiss it. */
    fun notificationIdFor(pendingId: Int): Int = 40_000 + pendingId
}

/** Handles the "Just me" / "Ignore" buttons on the "₹X spent" notification. */
class ExpenseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_JUST_ME = "com.example.splitwiseauto.action.JUST_ME"
        const val ACTION_IGNORE = "com.example.splitwiseauto.action.IGNORE"
        const val EXTRA_PENDING_ID = "pendingNotificationId"
        const val ACTION_CONFIRM_SCAN_PAY = "com.example.splitwiseauto.action.CONFIRM_SCAN_PAY"
        const val EXTRA_PAYMENT_ID = "scanPayPaymentId"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingId = intent.getIntExtra(EXTRA_PENDING_ID, -1).takeIf { it >= 0 } ?: return
        val app = context.applicationContext
        // Dismiss straight away so the tap feels instant.
        QuickExpenseActions.cancelNotification(app, pendingId)

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_JUST_ME -> {
                        val saved = QuickExpenseActions.saveJustMe(app, pendingId)
                        if (saved != null) toast(app, "Saved as your expense")
                    }
                    ACTION_IGNORE -> QuickExpenseActions.ignore(app, pendingId)
                    // "Yes, it was": the user ties an unproven debit to their
                    // Scan & Pay payment (SPL-SEC-004). Re-validated inside.
                    ACTION_CONFIRM_SCAN_PAY -> {
                        val paymentId = intent.getStringExtra(EXTRA_PAYMENT_ID) ?: return@launch
                        val ok = PendingPaymentMatcher.confirmByUser(app, paymentId, pendingId)
                        toast(app, if (ok) "Marked as paid" else "Couldn't match it. It's still in To split")
                    }
                }
            } catch (e: Exception) {
                Log.e("ExpenseActionReceiver", "Action failed: ${e.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
