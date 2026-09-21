package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAttemptStateMachineTest {

    private val sm = PaymentAttemptStateMachine

    @Test
    fun dialPlacedMovesInitiatingToInProgress() {
        assertEquals(
            PaymentAttemptStatus.IN_PROGRESS,
            sm.next(PaymentAttemptStatus.INITIATING, PaymentAttemptStateMachine.Event.DialPlaced)
        )
    }

    @Test
    fun userCancelledFromInitiatingIsCancelled() {
        assertEquals(
            PaymentAttemptStatus.CANCELLED,
            sm.next(PaymentAttemptStatus.INITIATING, PaymentAttemptStateMachine.Event.UserCancelled)
        )
    }

    @Test
    fun shortCallIsTreatedAsCancelled() {
        val result = sm.next(
            PaymentAttemptStatus.IN_PROGRESS,
            PaymentAttemptStateMachine.Event.CallEnded(durationMillis = 2_000)
        )
        assertEquals(PaymentAttemptStatus.CANCELLED, result)
    }

    @Test
    fun callAtExactlyTheMinimumDurationCounts() {
        val result = sm.next(
            PaymentAttemptStatus.IN_PROGRESS,
            PaymentAttemptStateMachine.Event.CallEnded(
                durationMillis = PaymentAttemptStateMachine.MIN_CALL_DURATION_MILLIS
            )
        )
        assertEquals(PaymentAttemptStatus.WAITING_FOR_VERIFICATION, result)
    }

    @Test
    fun longEnoughCallMovesToWaitingForVerification() {
        val result = sm.next(
            PaymentAttemptStatus.IN_PROGRESS,
            PaymentAttemptStateMachine.Event.CallEnded(durationMillis = 30_000)
        )
        assertEquals(PaymentAttemptStatus.WAITING_FOR_VERIFICATION, result)
    }

    @Test
    fun matchingSuccessSmsResolvesToSuccess() {
        val result = sm.next(
            PaymentAttemptStatus.WAITING_FOR_VERIFICATION,
            PaymentAttemptStateMachine.Event.MatchingSmsSuccess
        )
        assertEquals(PaymentAttemptStatus.SUCCESS, result)
    }

    @Test
    fun failureKeywordSmsResolvesToFailed() {
        val result = sm.next(
            PaymentAttemptStatus.WAITING_FOR_VERIFICATION,
            PaymentAttemptStateMachine.Event.MatchingSmsFailure
        )
        assertEquals(PaymentAttemptStatus.FAILED, result)
    }

    @Test
    fun noSmsWithinWindowTimesOut() {
        val result = sm.next(
            PaymentAttemptStatus.WAITING_FOR_VERIFICATION,
            PaymentAttemptStateMachine.Event.VerificationWindowExpired
        )
        assertEquals(PaymentAttemptStatus.TIMEOUT, result)
    }

    @Test
    fun lateSuccessSmsAfterTimeoutStillReattaches() {
        val result = sm.next(
            PaymentAttemptStatus.TIMEOUT,
            PaymentAttemptStateMachine.Event.MatchingSmsSuccess
        )
        assertEquals(PaymentAttemptStatus.SUCCESS, result)
    }

    @Test
    fun terminalStatesNeverMove() {
        val terminals = listOf(
            PaymentAttemptStatus.SUCCESS,
            PaymentAttemptStatus.FAILED,
            PaymentAttemptStatus.CANCELLED
        )
        val events = listOf(
            PaymentAttemptStateMachine.Event.DialPlaced,
            PaymentAttemptStateMachine.Event.CallEnded(30_000),
            PaymentAttemptStateMachine.Event.MatchingSmsSuccess,
            PaymentAttemptStateMachine.Event.MatchingSmsFailure,
            PaymentAttemptStateMachine.Event.VerificationWindowExpired,
            PaymentAttemptStateMachine.Event.UserCancelled
        )
        for (status in terminals) {
            for (event in events) {
                assertEquals(status, sm.next(status, event))
            }
        }
    }

    @Test
    fun isTerminalMatchesTheThreeEndStates() {
        assertTrue(sm.isTerminal(PaymentAttemptStatus.SUCCESS))
        assertTrue(sm.isTerminal(PaymentAttemptStatus.FAILED))
        assertTrue(sm.isTerminal(PaymentAttemptStatus.CANCELLED))
        assertFalse(sm.isTerminal(PaymentAttemptStatus.INITIATING))
        assertFalse(sm.isTerminal(PaymentAttemptStatus.IN_PROGRESS))
        assertFalse(sm.isTerminal(PaymentAttemptStatus.WAITING_FOR_VERIFICATION))
        assertFalse(sm.isTerminal(PaymentAttemptStatus.TIMEOUT))
    }
}
