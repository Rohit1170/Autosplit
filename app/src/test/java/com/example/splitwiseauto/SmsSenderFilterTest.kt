package com.example.splitwiseauto

import org.junit.Assert.assertEquals
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

    @Test fun `IDBI UPI debit with payee credited is parsed`() {
        val t = parseBankSms(
            sms = "IDBI Bank Acct XX275 debited for Rs 20.00 on 05-Oct-26; Bal Rs 13657.46 SONAM HOTEL " +
                "credited. UPI:130737839605. To Block UPI send SMS UPIBLOCK <Mob. No> to 07799726511 " +
                "or call 18002094324-IDBI Bank",
            sender = "JD-IDBIBK-S"
        )
        assertTrue(t != null)
        t!!
        org.junit.Assert.assertEquals("DEBIT", t.type)
        org.junit.Assert.assertEquals("20.00", t.amount)
        org.junit.Assert.assertEquals("SONAM HOTEL", t.merchant)
        org.junit.Assert.assertEquals("130737839605", PendingPaymentMatcher.extractUpiRef12(
            "UPI:130737839605. To Block UPI send SMS"
        ))
    }

    @Test fun `unusual but genuine bank sender codes are accepted`() {
        listOf("IDBIBANKLTD", "+JD-IDBIBK-S", "JX-IDBIBK-T1", "VM-IDBIBANK-T", "jd-idbibk-s")
            .forEach { assertTrue(it, isTransactionalSender(it)) }
    }

    // ---- RCS / notification alerts ----

    @Test fun `bank brand names from notifications are accepted`() {
        listOf("IDBI Bank Limited", "JD-IDBIBK-S", "HDFC Bank", "SBI", "ICICI Bank Credit Card",
            "Kotak Mahindra Bank", "Paytm", "Amazon Pay")
            .forEach { assertTrue(it, isBankNotificationSender(it)) }
    }

    @Test fun `contacts and phone numbers from notifications are rejected`() {
        listOf("Rahul", "Mom", "Akhil Sharma", "+91 98765 43210", "9876543210", "RAHULK", "")
            .forEach { assertFalse(it, isBankNotificationSender(it)) }
    }

    @Test fun `same alert via SMS and RCS shares a cross-channel key`() {
        val sms = "IDBI Bank Acct XX275 debited for Rs 20.00 on 05-Oct-26; Bal Rs 13657.46 SONAM HOTEL credited."
        val viaNotification = sms.replace(" ", "  ") + " "
        assertEquals(
            SmsIngestion.crossChannelKeys(sms, null).first(),
            SmsIngestion.crossChannelKeys(viaNotification, null).first()
        )
    }
}
