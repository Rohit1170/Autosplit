package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plain JVM tests for the UPI intent flow's pure helpers (no Android framework). */
class UpiIntentFlowTest {

    @Test
    fun parseUpiResponse_readsStandardFields() {
        val r = parseUpiResponse("txnId=AXI1234&responseCode=00&Status=SUCCESS&txnRef=SWAABC")!!
        assertEquals("AXI1234", r.txnId)
        assertEquals("00", r.responseCode)
        assertEquals("SUCCESS", r.status)
        assertEquals("SWAABC", r.txnRef)
        assertEquals("AXI1234", r.bestUpiRef)
    }

    @Test
    fun parseUpiResponse_prefersTwelveDigitApprovalRef() {
        val r = parseUpiResponse("txnId=XYZ&ApprovalRefNo=130027754633&Status=SUCCESS")!!
        assertEquals("130027754633", r.bestUpiRef)
    }

    @Test
    fun parseUpiResponse_isCaseInsensitiveAndIgnoresNullValues() {
        val r = parseUpiResponse("TXNID=abc&status=null&responsecode=ZD")!!
        assertEquals("abc", r.txnId)
        assertNull(r.status)
        assertEquals("ZD", r.responseCode)
    }

    @Test
    fun parseUpiResponse_blankOrGarbageIsNull() {
        assertNull(parseUpiResponse(null))
        assertNull(parseUpiResponse(""))
        assertNull(parseUpiResponse("not a response"))
    }

    @Test
    fun extractUpiRef12_findsRefAfterKeyword() {
        val sms = "IDBI Bank Acct XX275 debited for Rs 1.00 on 22-Sep-26; Bal Rs 520.14 " +
            "Siddhartha Ranj credited. UPI:130027754633. To Block UPI send SMS"
        assertEquals("130027754633", PendingPaymentMatcher.extractUpiRef12(sms))
    }

    @Test
    fun extractUpiRef12_ignoresMaskedAndShorterNumbers() {
        assertNull(PendingPaymentMatcher.extractUpiRef12("A/c XX1234 debited Rs 50.00 ref 12345"))
        // 13 digits is not a UPI ref.
        assertNull(PendingPaymentMatcher.extractUpiRef12("Rs 10 debited, ref 1300277546331"))
    }

    @Test
    fun rupeeMinorConversions_roundTrip() {
        assertEquals(49950L, rupeesToMinor(499.5))
        assertEquals(1L, rupeesToMinor(0.01))
        assertEquals(10L, rupeesToMinor(0.1))
        assertEquals("499.50", minorToRupeeText(49950))
        assertEquals("500.00", minorToRupeeText(50000))
    }

    @Test
    fun newPaymentRef_isAlphanumericAndShortEnoughForTr() {
        val ref = UpiIntentPayments.newPaymentRef()
        assert(ref.matches(Regex("[A-Z0-9]+"))) { ref }
        assert(ref.length <= 35) { ref }
    }

    @Test
    fun outcome_classifiesSuccessFailureAndUnknown() {
        assertEquals(UpiAppOutcome.SUCCESS, parseUpiResponse("Status=SUCCESS&txnId=1").outcome())
        assertEquals(UpiAppOutcome.SUCCESS, parseUpiResponse("responseCode=00&txnId=1").outcome())
        assertEquals(UpiAppOutcome.FAILURE, parseUpiResponse("Status=FAILURE&responseCode=ZM").outcome())
        // Explicit failure wins over a stray 00.
        assertEquals(UpiAppOutcome.FAILURE, parseUpiResponse("Status=FAILED&responseCode=00").outcome())
        assertEquals(UpiAppOutcome.UNKNOWN, parseUpiResponse("Status=SUBMITTED").outcome())
        assertEquals(UpiAppOutcome.UNKNOWN, parseUpiResponse(null).outcome())
    }
}
