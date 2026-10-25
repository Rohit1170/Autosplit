package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpiAndPhoneTest {

    @Test fun phoneNormalize() {
        assertEquals("919876543210", PhoneNumbers.normalize("98765 43210"))
        assertEquals("919876543210", PhoneNumbers.normalize("+91 98765-43210"))
        assertEquals("919876543210", PhoneNumbers.normalize("09876543210"))
        assertNull(PhoneNumbers.normalize("12345"))
        assertEquals("+91 98765 43210", PhoneNumbers.display("919876543210"))
    }

    private fun req(raw: String) = parseUpiQr(raw)!!

    @Test fun qrKinds() {
        assertEquals(UpiQrKind.PERSONAL, req("upi://pay?pa=akhil@okaxis&pn=Akhil%20Sharma").kind())
        assertEquals(UpiQrKind.PERSONAL, req("upi://pay?pa=akhil@ybl&pn=Akhil&mc=0000").kind())
        assertEquals(UpiQrKind.MERCHANT_UNSIGNED, req("upi://pay?pa=shop@okbizaxis&pn=Shop&mc=5411").kind())
        assertEquals(UpiQrKind.MERCHANT_SIGNED, req("upi://pay?pa=shop@pg&pn=Shop&mc=5411&sign=abc").kind())
    }
}
