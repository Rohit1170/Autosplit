package com.example.splitwiseauto

import android.content.Context

/**
 * Owns every non-terminal [PaymentAttempt.status] transition driven by the
 * call itself — dialing, the call ending, the user cancelling — and this
 * object's own app-start reconciliation (stale-attempt -> TIMEOUT). Both
 * QrScanActivity and DialManager go through here rather than touching
 * PaymentAttemptDao's status column directly.
 *
 * The one deliberate exception is PaymentSmsAdopter: because a confirming
 * or failing bank SMS is the ONLY thing allowed to resolve an attempt to
 * SUCCESS/FAILED (never call state or duration alone — see the top-level
 * design rules), it claims that terminal transition directly against
 * PaymentAttemptDao.claimStatus rather than routing through this object.
 * That claim still only ever writes the exact status values
 * PaymentAttemptStateMachine would produce, and the DAO's conditional
 * claimStatus (UPDATE ... WHERE status IN (...)) is what makes both this
 * object and PaymentSmsAdopter writing concurrently safe — see
 * PaymentAttemptStateMachine for the actual transition rules.
 */
object PaymentSessionManager {

    private fun dao(context: Context) = AppDatabase.getDatabase(context).paymentAttemptDao()

    /**
     * Inserts a new attempt as INITIATING BEFORE anything is dialled, so a
     * process death between "insert" and "actually place the call" still
     * leaves a row [reconcileOnStart] can find and resolve later, instead
     * of silently losing the attempt.
     */
    suspend fun start(
        context: Context,
        upiId: String,
        payeeName: String?,
        expectedAmount: Double,
        simSlot: Int?,
        rail: PaymentRail
    ): PaymentAttempt {
        val attempt = PaymentAttempt(
            upiId = upiId,
            payeeName = payeeName,
            expectedAmount = expectedAmount,
            status = PaymentAttemptStatus.INITIATING.name,
            createdAtMillis = System.currentTimeMillis(),
            simSlot = simSlot,
            rail = rail.name
        )
        val id = dao(context).insert(attempt).toInt()
        return attempt.copy(id = id)
    }

    suspend fun onDialPlaced(context: Context, attemptId: Int) {
        transition(context, attemptId, PaymentAttemptStateMachine.Event.DialPlaced)
    }

    suspend fun onCallEnded(context: Context, attemptId: Int, durationMillis: Long) {
        transition(context, attemptId, PaymentAttemptStateMachine.Event.CallEnded(durationMillis))
    }

    suspend fun onCancelled(context: Context, attemptId: Int) {
        transition(context, attemptId, PaymentAttemptStateMachine.Event.UserCancelled)
    }

    /**
     * Called on app start (see SplitWiseAutoApp / MainActivity.onCreate).
     * Any attempt still in flight whose verification window has expired
     * becomes TIMEOUT — the row is KEPT for audit, never deleted, and a
     * late confirming SMS can still reattach to it afterwards (handled in
     * PaymentSmsAdopter, which also matches against TIMEOUT rows).
     */
    suspend fun reconcileOnStart(context: Context) {
        val now = System.currentTimeMillis()
        val inFlight = dao(context).getByStatuses(
            listOf(
                PaymentAttemptStatus.INITIATING.name,
                PaymentAttemptStatus.IN_PROGRESS.name,
                PaymentAttemptStatus.WAITING_FOR_VERIFICATION.name
            )
        )
        for (attempt in inFlight) {
            val age = now - attempt.createdAtMillis
            if (age > PaymentAttemptStateMachine.VERIFICATION_WINDOW_MILLIS) {
                dao(context).claimStatus(
                    attempt.id,
                    PaymentAttemptStatus.TIMEOUT.name,
                    listOf(attempt.status)
                )
            }
        }
    }

    private suspend fun transition(
        context: Context,
        attemptId: Int,
        event: PaymentAttemptStateMachine.Event
    ) {
        val attempt = dao(context).getById(attemptId) ?: return
        val next = PaymentAttemptStateMachine.next(attempt.statusEnum, event)
        if (next != attempt.statusEnum) {
            dao(context).setStatus(attemptId, next.name)
        }
    }
}
