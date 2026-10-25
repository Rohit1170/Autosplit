package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Test

/** Card vs account detection and masked numbers from real SMS formats. */
class AccountParsingTest {

    private val sbiUpi = "Dear UPI user A/CX0508 debited by 20.00 on date 14Oct26 trf to " +
        "VIJAY KUMAR SHAR Refno 546922177288"

    private val iciciCard = "Rs 6,478.00 spent on ICICI Bank Card XX1000 on 12-Oct-26 at " +
        "UPI-66519120788. Avl Lmt: Rs 50,457.11. To dispute, call 18002662/SMS BLOCK 1000 " +
        "to 9215676766. To convert this txn to EMI give a missed call on 9924667667."

    @Test fun sbiAccountNumberGluedToAc() {
        assertEquals("****0508", extractAccountIdentifier(sbiUpi))
        assertEquals(AccountKind.ACCOUNT, extractAccountKind(sbiUpi))
    }

    @Test fun iciciCreditCard() {
        assertEquals("****1000", extractAccountIdentifier(iciciCard))
        assertEquals(AccountKind.CARD, extractAccountKind(iciciCard))
    }
}
