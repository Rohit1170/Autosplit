package com.example.splitwiseauto

import com.example.splitwiseauto.PaymentMatchRules.Decision
import com.example.splitwiseauto.PaymentMatchRules.Evidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPL-SEC-003 / SPL-SEC-004 regression: what may confirm a Scan & Pay payment.
 * The exploit tests replay the audit scenarios and must never CONFIRM.
 */
class PaymentMatchRulesTest {

    private val vpa = "shreeganesh.hotel@okicici"
    private val name = "Shree Ganesh Hotel"

    private fun ev(body: String, merchant: String = "Unknown", amount: Long? = 50_000, ref: String? = null, payRef: String? = null) =
        PaymentMatchRules.evidence(
            paymentAmountMinor = 50_000, paymentUpiRef = payRef, payeeVpa = vpa, payeeName = name,
            debitAmountMinor = amount, debitRef = ref, debitBody = body, debitMerchant = merchant
        )

    // ---- Exploit replays: must NOT confirm ----

    @Test fun `failed payment plus unrelated same-amount debit is not confirmed`() {
        val e = ev("Rs 500.00 debited from A/c XX1234 to SWIGGY on 11-Oct-26. UPI 612345678901", "SWIGGY")
        assertEquals(Evidence.AMOUNT_ONLY, e)
        assertEquals(Decision.ASK_USER, PaymentMatchRules.decide(e, PendingPaymentStatus.FAILED, false))
    }

    @Test fun `pending payment plus unrelated same-amount debit is not confirmed`() {
        val e = ev("Rs 500.00 debited from A/c XX1234 to SWIGGY. UPI 612345678901", "SWIGGY")
        assertEquals(Decision.ASK_USER, PaymentMatchRules.decide(e, PendingPaymentStatus.PENDING, false))
    }

    @Test fun `notification-only match is never confirmed even when it names the payee`() {
        // A spoofed "HDFC Bank" chat can copy the payee name (SPL-SEC-003).
        val e = ev("Rs 500 debited to SHREE GANESH HOTEL", "SHREE GANESH HOTEL")
        assertEquals(Evidence.AMOUNT_AND_PAYEE, e)
        assertEquals(Decision.ASK_USER, PaymentMatchRules.decide(e, PendingPaymentStatus.PENDING, fromNotification = true))
    }

    @Test fun `failed payment is not auto-confirmed even with payee named`() {
        val e = ev("Rs 500 debited to SHREE GANESH HOTEL", "SHREE GANESH HOTEL")
        assertEquals(Decision.ASK_USER, PaymentMatchRules.decide(e, PendingPaymentStatus.FAILED, false))
    }

    @Test fun `two payments that both fit make it ambiguous`() {
        val e = ev("Rs 500 debited to SHREE GANESH HOTEL", "SHREE GANESH HOTEL")
        assertEquals(Decision.ASK_USER, PaymentMatchRules.decide(e, PendingPaymentStatus.PENDING, false, otherStrongCandidates = 1))
    }

    @Test fun `generic words do not count as naming the payee`() {
        assertFalse(PaymentMatchRules.namesPayee("abc1@ybl", "Fresh Store Bank", "Rs 500 paid at BIG STORE via BANK UPI", "BIG STORE"))
    }

    @Test fun `digit-only VPA handle does not count`() {
        assertFalse(PaymentMatchRules.namesPayee("9876543210@ybl", null, "Rs 500 debited, ref 9876543210 info", "Unknown"))
    }

    @Test fun `different amount is no match at all`() {
        val e = ev("Rs 499.00 debited to SHREE GANESH HOTEL", "SHREE GANESH HOTEL", amount = 49_900)
        assertEquals(Evidence.NONE, e)
        assertEquals(Decision.NO_MATCH, PaymentMatchRules.decide(e, PendingPaymentStatus.PENDING, false))
    }

    @Test fun `already confirmed payment is never re-confirmed`() {
        assertEquals(Decision.NO_MATCH, PaymentMatchRules.decide(Evidence.REFERENCE, PendingPaymentStatus.CONFIRMED, false))
        assertEquals(Decision.NO_MATCH, PaymentMatchRules.decide(Evidence.AMOUNT_ONLY, PendingPaymentStatus.CONFIRMED, false))
    }

    // ---- Legitimate matches still confirm ----

    @Test fun `matching 12-digit reference confirms, any open status`() {
        val e = ev("Rs 500 debited. UPI Ref 612345678901", ref = "612345678901", payRef = "612345678901")
        assertEquals(Evidence.REFERENCE, e)
        assertEquals(Decision.CONFIRM, PaymentMatchRules.decide(e, PendingPaymentStatus.FAILED, false))
        assertEquals(Decision.CONFIRM, PaymentMatchRules.decide(e, PendingPaymentStatus.UNCONFIRMED, true))
    }

    @Test fun `SMS naming the payee by VPA confirms a pending payment`() {
        val e = ev("Rs.500.00 debited A/c XX1234 to VPA shreeganesh.hotel@okicici UPI 612345678901")
        assertEquals(Evidence.AMOUNT_AND_PAYEE, e)
        assertEquals(Decision.CONFIRM, PaymentMatchRules.decide(e, PendingPaymentStatus.PENDING, false))
        assertEquals(Decision.CONFIRM, PaymentMatchRules.decide(e, PendingPaymentStatus.VERIFIED_PENDING, false))
    }

    @Test fun `SMS naming a distinctive payee word confirms`() {
        assertTrue(PaymentMatchRules.namesPayee(vpa, name, "Rs 500 debited to SHREE GANESH HO on 11-Oct", "SHREE GANESH HO"))
    }
}
