package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TransactionParserTest {

    @Test
    fun parsesIdbiDebitWithAccountMerchantAndUpi() {
        val transaction = parseBankSms(
            "IDBI Bank Acct XX275 debited for Rs 710.00 on 07-Sep-26; " +
                "Bal Rs 9335.81 CP CORNER FOOD credited. UPI:129217258990."
        )

        assertNotNull(transaction)
        assertEquals("710.00", transaction?.amount)
        assertEquals("****0275", transaction?.accountIdentifier)
        assertEquals("CP CORNER FOOD", transaction?.merchant)
        assertEquals("IDBI Bank", transaction?.bank)
        assertEquals("129217258990", transaction?.upi)
        assertEquals("DEBIT", transaction?.type)
    }

    @Test
    fun parsesIciciCardDebitWithoutInventingMerchant() {
        val transaction = parseBankSms(
            "Spent Rs.500 using ICICI Bank Card XX1000 on 02-01-2026 at UPI-6611555712"
        )

        assertNotNull(transaction)
        assertEquals("500.00", transaction?.amount)
        assertEquals("****1000", transaction?.accountIdentifier)
        assertEquals("ICICI Bank", transaction?.bank)
        assertEquals("6611555712", transaction?.upi)
        assertEquals("Unknown", transaction?.merchant)
    }

    @Test
    fun parsesGenericSbiDebit() {
        val transaction = parseBankSms(
            "SBI A/c XX4321 debited by INR 1,250.00 on 07/09/2026 at ABC STORE. Avl Bal Rs 3000.00",
            sender = "SBIINB"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("1250.00", transaction?.amount)
        assertEquals("****4321", transaction?.accountIdentifier)
        assertEquals("ABC STORE", transaction?.merchant)
    }
    @Test
    fun parsesHdfcUpiDebitWithVpaMerchant() {
        val transaction = parseBankSms(
            "Update! INR 500.00 debited from HDFC Bank XX1234 on 07-SEP-26. Info: UPI-merchant@okhdfcbank. Avl bal:INR 12,345.00"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("500.00", transaction?.amount)
        assertEquals("HDFC Bank", transaction?.bank)
        assertEquals("****1234", transaction?.accountIdentifier)
    }

    @Test
    fun parsesAxisUpiDebitWithSlashSeparatedMerchant() {
        val transaction = parseBankSms(
            "INR 750.00 debited from A/c no. XX5678 on 07-09-2026 04:23:11 PM. Info: UPI/DR/123456789012/MERCHANT NAME/Payment. Avl Bal: INR 5000.00 - Axis Bank"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("750.00", transaction?.amount)
        assertEquals("Axis Bank", transaction?.bank)
        assertEquals("****5678", transaction?.accountIdentifier)
        assertEquals("MERCHANT NAME", transaction?.merchant)
    }

    @Test
    fun parsesKotakSentToVpa() {
        val transaction = parseBankSms(
            "Sent Rs.300.00 from Kotak Bank AC X1234 to merchant@ybl on 07-09-26. UPI Ref 123456789012."
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("300.00", transaction?.amount)
        assertEquals("Kotak Bank", transaction?.bank)
        assertEquals("123456789012", transaction?.upi)
    }

    @Test
    fun parsesPnbDebitedForAtmWithdrawal() {
        val transaction = parseBankSms(
            "Your A/c XX9876 is debited for Rs 200.00 on 07-09-2026 towards ATM Cash Withdrawal. -PNB",
            sender = "PNBSMS"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("200.00", transaction?.amount)
        assertEquals("PNB", transaction?.bank)
        assertEquals("ATM Cash Withdrawal", transaction?.merchant)
    }

    @Test
    fun parsesBankOfBarodaAtMerchant() {
        val transaction = parseBankSms(
            "Dear Customer, Rs.150.00 debited from your A/c XX3456 on 07-09-26 at MERCHANT NAME. Avl Bal Rs.2000.00 -Bank of Baroda"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("150.00", transaction?.amount)
        assertEquals("Bank of Baroda", transaction?.bank)
        assertEquals("MERCHANT NAME", transaction?.merchant)
    }

    @Test
    fun parsesUnionBankOfIndiaWithoutBankSuffixInSenderCode() {
        val transaction = parseBankSms(
            "Rs.100.00 debited from A/c XX5566 on 07-09-26 towards ATM Withdrawal Ref No 123456789012 -Union Bank of India"
        )

        assertNotNull(transaction)
        assertEquals("Union Bank of India", transaction?.bank)
        assertEquals("123456789012", transaction?.upi)
    }

    @Test
    fun parsesYesBankCardSpend() {
        val transaction = parseBankSms(
            "Rs 450.00 spent on your Yes Bank Card XX2345 at MERCHANT on 07-09-26. Avl Lmt Rs 50000.00"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("450.00", transaction?.amount)
        assertEquals("Yes Bank", transaction?.bank)
        assertEquals("****2345", transaction?.accountIdentifier)
        assertEquals("MERCHANT", transaction?.merchant)
    }

    @Test
    fun parsesIndusIndCreditCardSpend() {
        val transaction = parseBankSms(
            "Rs.1,200.00 spent on IndusInd Bank Credit Card XX6789 at MERCHANT NAME on 07-Sep-26 16:45:00"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("1200.00", transaction?.amount)
        assertEquals("IndusInd Bank", transaction?.bank)
        assertEquals("****6789", transaction?.accountIdentifier)
    }

    @Test
    fun parsesIdfcFirstUpiDebit() {
        val transaction = parseBankSms(
            "INR 320.00 debited from A/c XX4567 on 07-09-26 via UPI to merchant@idfcfirst. UPI Ref No 123456789012 -IDFC FIRST Bank"
        )

        assertNotNull(transaction)
        assertEquals("320.00", transaction?.amount)
        assertEquals("IDFC FIRST Bank", transaction?.bank)
        assertEquals("****4567", transaction?.accountIdentifier)
    }

    @Test
    fun parsesAmericanExpressChargedTo() {
        val transaction = parseBankSms(
            "Rs.5,000.00 has been charged to your American Express Card ending 1001 at MERCHANT on 07-Sep-2026"
        )

        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("5000.00", transaction?.amount)
        assertEquals("American Express", transaction?.bank)
        assertEquals("****1001", transaction?.accountIdentifier)
        assertEquals("MERCHANT", transaction?.merchant)
    }

    @Test
    fun parsesAuSmallFinanceBankNeftDebit() {
        val transaction = parseBankSms(
            "Dear customer, Rs.999 debited from A/c no. XX1122 on 07-09-2026 towards NEFT to MERCHANT. Bal Rs.4500.00 -AU Bank",
            sender = "AUBANK"
        )

        assertNotNull(transaction)
        assertEquals("999.00", transaction?.amount)
        assertEquals("AU Bank", transaction?.bank)
    }

    @Test
    fun parsesStandardCharteredCreditCardSpend() {
        val transaction = parseBankSms(
            "You have spent INR 2,300.00 on your Standard Chartered Credit Card XX3344 at MERCHANT on 07/09/2026"
        )

        assertNotNull(transaction)
        assertEquals("2300.00", transaction?.amount)
        assertEquals("Standard Chartered", transaction?.bank)
        assertEquals("****3344", transaction?.accountIdentifier)
    }

    @Test
    fun parsesCreditAlertAsCreditType() {
        val transaction = parseBankSms(
            "Rs.10,000.00 credited to your SBI A/c XX7788 on 07-09-26. Avl Bal Rs.15,000.00",
            sender = "SBIINB"
        )

        assertNotNull(transaction)
        assertEquals("CREDIT", transaction?.type)
        assertEquals("10000.00", transaction?.amount)
        assertEquals("****7788", transaction?.accountIdentifier)
    }

    @Test
    fun ignoresOtpMessages() {
        val transaction = parseBankSms(
            "123456 is your OTP for txn of Rs.500.00 at MERCHANT. Do not share with anyone."
        )

        assertEquals(null, transaction)
    }

    @Test
    fun ignoresCardLimitReminderMessages() {
        val transaction = parseBankSms(
            "Your HDFC Bank Credit Card XX1234 payment of Rs.5000.00 is due on 15-09-26. Available limit is Rs.20000.00"
        )

        assertEquals(null, transaction)
    }

    @Test
    fun parsesIciciCreditedNameWithoutBalance() {
        // ICICI/GPay style: payee name after ';' with NO balance before it —
        // used to fall through to "Unknown".
        val transaction = parseBankSms(
            "ICICI Bank Acct XX529 debited for Rs 250.00 on 28-Sep-26; Rahul Sharma credited. UPI:445566778899."
        )
        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("250.00", transaction?.amount)
        assertEquals("Rahul Sharma", transaction?.merchant)
        assertEquals("445566778899", transaction?.upi)
    }

    @Test
    fun parsesSbiTrfToNameWithRefno() {
        // SBI UPI: "trf to NAME Refno 123" — the name must stop at "Refno".
        val transaction = parseBankSms(
            "Dear UPI user A/C X8888 debited by 480.0 on date 28Sep26 trf to PRIYA MENON Refno 556677889900. -SBI",
            sender = "SBIINB"
        )
        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("480.00", transaction?.amount)
        assertEquals("PRIYA MENON", transaction?.merchant)
    }

    @Test
    fun parsesHdfcSentToNameOnDate() {
        val transaction = parseBankSms(
            "Sent Rs.1500.00 From HDFC Bank A/C x1234 To Anil Kumar On 28-09-26 Ref 123456789012. Not You? Call 18002586161"
        )
        assertNotNull(transaction)
        assertEquals("DEBIT", transaction?.type)
        assertEquals("1500.00", transaction?.amount)
        assertEquals("Anil Kumar", transaction?.merchant)
    }
}
