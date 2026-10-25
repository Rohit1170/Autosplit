package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSettleMatcherTest {
    private val akhil = AutoSettleMatcher.Debtor(1, "Akhil Sharma", 1250.0)
    private val rahul = AutoSettleMatcher.Debtor(2, "Rahul", 640.0)
    private val priya = AutoSettleMatcher.Debtor(3, "Priya", 1250.0)

    @Test fun nameInSms() {
        val m = AutoSettleMatcher.match(
            "Rs.1250.00 credited to your A/c XX0508 by AKHIL SHARMA via UPI Ref 123456789012",
            1250.0, listOf(akhil, rahul)
        )!!
        assertEquals(1, m.friendId)
        assertEquals(AutoSettleMatcher.Reason.NAME, m.reason)
    }

    @Test fun nameInVpa() {
        val m = AutoSettleMatcher.match(
            "Your a/c XX0508 is credited with INR 500.00 from VPA rahul.k@okaxis",
            500.0, listOf(akhil, rahul)
        )!!
        assertEquals(2, m.friendId)
        assertEquals(500.0, m.amount, 0.001) // partial payment
        assertEquals("rahul.k@okaxis", m.vpaInSms)
    }

    @Test fun uniqueAmountWithoutName() {
        val m = AutoSettleMatcher.match("INR 640.00 credited to A/c XX0508 via UPI", 640.0, listOf(akhil, rahul))!!
        assertEquals(2, m.friendId)
        assertEquals(AutoSettleMatcher.Reason.AMOUNT, m.reason)
    }

    @Test fun tieIsNotGuessed() {
        assertNull(AutoSettleMatcher.match("INR 1,250.00 credited to A/c XX0508", 1250.0, listOf(akhil, priya)))
    }

    @Test fun refundsIgnored() {
        assertNull(AutoSettleMatcher.match("Refund of Rs 640 credited from AMAZON", 640.0, listOf(rahul)))
    }

    @Test fun cappedAtOwed() {
        val m = AutoSettleMatcher.match("Rs 2000 credited by AKHIL SHARMA", 2000.0, listOf(akhil))!!
        assertEquals(1250.0, m.amount, 0.001)
    }

    @Test fun learnedVpaWins() {
        val known = akhil.copy(name = "Bhai", vpa = "9876543210@ybl")
        val m = AutoSettleMatcher.match("Rs 300 credited from VPA 9876543210@ybl", 300.0, listOf(known, rahul))!!
        assertEquals(AutoSettleMatcher.Reason.UPI_ID, m.reason)
    }
}
