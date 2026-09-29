package com.example.splitwiseauto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSenderFilterTest {

    @Test fun `bank and fintech DLT headers are accepted`() {
        listOf(
            "AX-HDFCBK", "JD-SBIINB-S", "VM-ICICIT-T", "HDFCBK", "AD-CREDIN",
            "JM-SLICEIT", "BP-ONECRD-S", "VK-PAYTMB", "AUBANK"
        ).forEach { assertTrue(it, isTransactionalSender(it)) }
    }

    @Test fun `personal phone numbers are rejected`() {
        listOf("+919876543210", "9876543210", "09876543210", "+91 98765 43210", "98765-43210")
            .forEach { assertFalse(it, isTransactionalSender(it)) }
    }

    @Test fun `promotional headers are rejected`() {
        assertFalse(isTransactionalSender("AX-HDFCBK-P"))
    }

    @Test fun `friend forwarding a fake debit message creates nothing`() {
        val t = parseBankSms(
            sms = "money debited from ***1234 card amount is Rs 50,000",
            sender = "+919812345678"
        )
        assertNull(t)
    }

    @Test fun `same text from a real bank sender still parses`() {
        val t = parseBankSms(
            sms = "Rs 50,000.00 debited from card ***1234 on 29-09-26 at AMAZON. Not you? Call 1800",
            sender = "AX-HDFCBK-S"
        )
        assertTrue(t != null && t.type == "DEBIT")
    }
}
