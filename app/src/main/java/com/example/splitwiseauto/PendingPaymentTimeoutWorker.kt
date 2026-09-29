package com.example.splitwiseauto

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Every 15 minutes, for payments with no matching bank SMS after
 * [TIMEOUT_MILLIS]:
 *  - VERIFIED_PENDING (UPI app said success) -> UNVERIFIED: still shown as
 *    paid, with a calm "couldn't verify with your bank SMS" notice.
 *  - PENDING (UPI app gave no clear answer) -> UNCONFIRMED.
 * Purely local (no network); only touches pending_payments. A late SMS with
 * an exact reference match can still move either to CONFIRMED.
 */
class PendingPaymentTimeoutWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        markTimedOut(applicationContext)
        return Result.success()
    }

    companion object {
        const val TIMEOUT_MILLIS = 30L * 60 * 1000
        private const val UNIQUE_NAME = "pending_payment_timeout"

        /** Applies the 30-minute timeouts above. Safe to call any time. */
        suspend fun markTimedOut(context: Context) {
            val dao = AppDatabase.getDatabase(context).pendingPaymentDao()
            val cutoff = System.currentTimeMillis() - TIMEOUT_MILLIS
            val transitions = listOf(
                PendingPaymentStatus.VERIFIED_PENDING to PendingPaymentStatus.UNVERIFIED,
                PendingPaymentStatus.PENDING to PendingPaymentStatus.UNCONFIRMED
            )
            for ((from, to) in transitions) {
                dao.getWithStatusOlderThan(from.name, cutoff).forEach { payment ->
                    // Conditional: if an SMS confirmed it a moment ago, leave it.
                    dao.compareAndSetStatus(payment.id, from.name, to.name)
                }
            }
        }

        /** Idempotent: KEEP means repeated app launches don't reset the schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PendingPaymentTimeoutWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
