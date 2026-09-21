package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class UpiQrParserTest {

    @Test
    fun parsesValidQrWithAllFields() {
        val request = parseUpiQr(
            "upi://pay?pa=friend@upi&pn=Friendly%20Store&am=250.00&cu=INR&tn=Lunch"
        )

        assertNotNull(request)
        assertEquals("friend@upi", request?.vpa)
        assertEquals("Friendly Store", request?.payeeName)
        assertEquals(250.00, request?.amount)
        assertEquals("INR", request?.currency)
        assertEquals("Lunch", request?.note)
    }

    @Test
    fun parsesQrWithNoAmount() {
        val request = parseUpiQr("upi://pay?pa=friend@upi&pn=Friendly%20Store")

        assertNotNull(request)
        assertEquals("friend@upi", request?.vpa)
        assertNull(request?.amount)
    }

    @Test
    fun rejectsNonUpiText() {
        assertNull(parseUpiQr("https://example.com/not-a-upi-qr"))
    }

    @Test
    fun rejectsBlankText() {
        assertNull(parseUpiQr(""))
    }

    @Test
    fun rejectsUpiUriMissingPayeeAddress() {
        assertNull(parseUpiQr("upi://pay?pn=Friendly%20Store&am=100"))
    }

    @Test
    fun rejectsPayeeAddressWithoutAtSign() {
        assertNull(parseUpiQr("upi://pay?pa=notavalidvpa&am=100"))
    }

    @Test
    fun rejectsMalformedAmountRatherThanTreatingAsAmountless() {
        assertNull(parseUpiQr("upi://pay?pa=friend@upi&am=not-a-number"))
    }

    @Test
    fun toleratesMissingOptionalFields() {
        val request = parseUpiQr("upi://pay?pa=friend@upi")

        assertNotNull(request)
        assertEquals("friend@upi", request?.vpa)
        assertNull(request?.payeeName)
        assertNull(request?.amount)
        assertNull(request?.currency)
        assertNull(request?.note)
    }
}
