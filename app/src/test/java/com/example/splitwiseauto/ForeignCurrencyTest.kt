package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignCurrencyTest {
    private val icici1 = "PHP 518.09 spent using ICICI Bank Card XX1000 on 19-Oct-26 on Grab. Avl Limit: INR 44,690.51. If not you, call 1800 2662/SMS BLOCK 1000 to 9215676766."
    private val icici2 = "PHP 156.56 spent using ICICI Bank Card XX1000 on 19-Oct-26 on Grab. Avl Limit: INR 44,454.74. If not you, call 1800 2662/SMS BLOCK 1000 to 9215676766."

    @Test fun foreignSpendIsNotTheLimit() {
        val t = parseBankSms(icici1, "JD-ICICIT-S")!!
        assertEquals("PHP", t.originalCurrency)
        assertEquals(518.09, t.originalAmount!!, 0.001)
        assertTrue(t.fxEstimated)
        val inr = t.amount.toDouble()
        assertTrue("estimate $inr", inr in 700.0..900.0)
        assertEquals("44,690.51", t.balance)
        assertEquals("Grab", t.merchant)
    }

    @Test fun inrSmsUnchanged() {
        val t = parseBankSms("INR 500.00 spent using ICICI Bank Card XX1000 on 19-Oct-26 on Swiggy. Avl Limit: INR 44,000.00.", "JD-ICICIT-S")!!
        assertEquals("500.00", t.amount)
        assertNull(t.originalCurrency)
        assertFalse(t.fxEstimated)
    }

    @Test fun balanceNeverTakenAsAmount() {
        // Amount only after the limit text must not become the spend.
        assertNull(ForeignCurrency.find("Avl Limit: USD 2,000.00"))
    }

    @Test fun bankGivenInrIsExact() {
        val t = parseBankSms("USD 15.00 spent on HDFC Bank Card XX1234 at AMAZON. INR 1,290.40 debited. Avl bal INR 20,000.", "AX-HDFCBK")!!
        assertEquals("1290.40", t.amount)
        assertEquals("USD", t.originalCurrency)
        assertFalse(t.fxEstimated)
    }

    @Test fun secondSmsDiff() {
        val t2 = parseBankSms(icici2, "JD-ICICIT-S")!!
        assertEquals("44,454.74", t2.balance)
        assertEquals(156.56, t2.originalAmount!!, 0.001)
    }
}
