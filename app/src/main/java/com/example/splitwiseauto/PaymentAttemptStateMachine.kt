package com.example.splitwiseauto

/**
 * Pure state-transition logic for a [PaymentAttempt], with NO Android or
 * Room dependency, so it's plain-JVM unit-testable (see
 * PaymentAttemptStateMachineTest). [PaymentSessionManager] is the only
 * caller that actually persists the result of [next] to the DB — this
 * object just decides what the next state SHOULD be for a given event.
 */
object PaymentAttemptStateMachine {

    /** Below this, a call is treated as the person backing out, not paying. */
    const val MIN_CALL_DURATION_MILLIS = 5_000L

    /** No confirming SMS within this long after the call ends -> TIMEOUT. */
    const val VERIFICATION_WINDOW_MILLIS = 10 * 60 * 1000L

    sealed class Event {
        object DialPlaced : Event()
        data class CallEnded(val durationMillis: Long) : Event()
        object MatchingSmsSuccess : Event()
        object MatchingSmsFailure : Event()
        object VerificationWindowExpired : Event()
        object UserCancelled : Event()
    }

    fun next(current: PaymentAttemptStatus, event: Event): PaymentAttemptStatus = when (current) {
        PaymentAttemptStatus.INITIATING -> when (event) {
            is Event.DialPlaced -> PaymentAttemptStatus.IN_PROGRESS
            is Event.UserCancelled -> PaymentAttemptStatus.CANCELLED
            else -> current
        }

        PaymentAttemptStatus.IN_PROGRESS -> when (event) {
            is Event.CallEnded ->
                if (event.durationMillis < MIN_CALL_DURATION_MILLIS) {
                    PaymentAttemptStatus.CANCELLED
                } else {
                    PaymentAttemptStatus.WAITING_FOR_VERIFICATION
                }
            is Event.UserCancelled -> PaymentAttemptStatus.CANCELLED
            else -> current
        }

        PaymentAttemptStatus.WAITING_FOR_VERIFICATION -> when (event) {
            is Event.MatchingSmsSuccess -> PaymentAttemptStatus.SUCCESS
            is Event.MatchingSmsFailure -> PaymentAttemptStatus.FAILED
            is Event.VerificationWindowExpired -> PaymentAttemptStatus.TIMEOUT
            is Event.UserCancelled -> PaymentAttemptStatus.CANCELLED
            else -> current
        }

        // A late SMS still reattaches after TIMEOUT, per the locked design.
        PaymentAttemptStatus.TIMEOUT -> when (event) {
            is Event.MatchingSmsSuccess -> PaymentAttemptStatus.SUCCESS
            is Event.MatchingSmsFailure -> PaymentAttemptStatus.FAILED
            else -> current
        }

        // Terminal — nothing moves an attempt out of these.
        PaymentAttemptStatus.SUCCESS,
        PaymentAttemptStatus.FAILED,
        PaymentAttemptStatus.CANCELLED -> current
    }

    fun isTerminal(status: PaymentAttemptStatus): Boolean = when (status) {
        PaymentAttemptStatus.SUCCESS,
        PaymentAttemptStatus.FAILED,
        PaymentAttemptStatus.CANCELLED -> true
        else -> false
    }
}
