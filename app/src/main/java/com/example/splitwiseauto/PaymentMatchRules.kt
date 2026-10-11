package com.example.splitwiseauto

import java.util.Locale

/**
 * SPL-SEC-003/004: decides how strongly a bank debit proves that a specific
 * Scan & Pay payment went through. Pure Kotlin (no Android, no database), so
 * every rule is unit-tested.
 *
 * Only two kinds of evidence may confirm a payment on their own:
 *  - REFERENCE: the debit's 12-digit UPI reference equals the payment's.
 *  - AMOUNT_AND_PAYEE: same amount in paise, inside the time window, AND the
 *    debit names the payee (VPA, or a distinctive word of the payee name).
 *
 * Same amount alone is never enough: an unrelated ₹500 debit two minutes
 * later must not turn someone else's failed ₹500 into "paid". Those cases
 * are ASK_USER: the payment stays as it is and the user is asked.
 */
object PaymentMatchRules {

    enum class Evidence { REFERENCE, AMOUNT_AND_PAYEE, AMOUNT_ONLY, NONE }

    enum class Decision { CONFIRM, ASK_USER, NO_MATCH }

    /** Words too common in payee names / bank SMS to identify anyone. */
    private val GENERIC_WORDS = setOf(
        "bank", "upi", "store", "stores", "shop", "mart", "india", "private", "limited",
        "pvt", "ltd", "services", "service", "enterprises", "traders", "payment", "payments",
        "merchant", "the", "and", "account", "debited", "credited", "transfer", "paytm",
        "phonepe", "gpay", "google", "amazon", "pay", "retail", "online"
    )

    fun evidence(
        paymentAmountMinor: Long,
        paymentUpiRef: String?,
        payeeVpa: String,
        payeeName: String?,
        debitAmountMinor: Long?,
        debitRef: String?,
        debitBody: String,
        debitMerchant: String?
    ): Evidence {
        if (debitRef != null && paymentUpiRef != null && debitRef == paymentUpiRef) return Evidence.REFERENCE
        if (debitAmountMinor == null || debitAmountMinor != paymentAmountMinor) return Evidence.NONE
        return if (namesPayee(payeeVpa, payeeName, debitBody, debitMerchant)) Evidence.AMOUNT_AND_PAYEE
        else Evidence.AMOUNT_ONLY
    }

    /**
     * What to do with one candidate payment.
     * @param otherStrongCandidates how many OTHER payments also have
     *   AMOUNT_AND_PAYEE evidence for this same debit (ambiguity).
     */
    fun decide(
        evidence: Evidence,
        status: PendingPaymentStatus,
        fromNotification: Boolean,
        otherStrongCandidates: Int = 0
    ): Decision = when (evidence) {
        Evidence.NONE -> Decision.NO_MATCH
        Evidence.REFERENCE ->
            if (status == PendingPaymentStatus.CONFIRMED) Decision.NO_MATCH else Decision.CONFIRM
        Evidence.AMOUNT_AND_PAYEE -> when {
            status == PendingPaymentStatus.CONFIRMED -> Decision.NO_MATCH
            // A notification's sender name can be spoofed (SPL-SEC-003).
            fromNotification -> Decision.ASK_USER
            // The UPI app said it failed: only the user can tie this debit to it.
            status == PendingPaymentStatus.FAILED -> Decision.ASK_USER
            otherStrongCandidates > 0 -> Decision.ASK_USER
            status == PendingPaymentStatus.PENDING ||
                status == PendingPaymentStatus.VERIFIED_PENDING -> Decision.CONFIRM
            else -> Decision.ASK_USER
        }
        Evidence.AMOUNT_ONLY ->
            if (status == PendingPaymentStatus.CONFIRMED) Decision.NO_MATCH else Decision.ASK_USER
    }

    /** True when the debit text names this payee (its VPA, or a distinctive name word). */
    fun namesPayee(payeeVpa: String, payeeName: String?, body: String, merchant: String?): Boolean {
        val haystack = normalize(body) + " " + normalize(merchant.orEmpty())
        val vpa = payeeVpa.trim().lowercase(Locale.ROOT)
        if (vpa.isNotEmpty() && body.lowercase(Locale.ROOT).contains(vpa)) return true

        val handle = vpa.substringBefore('@').replace(Regex("[^a-z0-9]"), "")
        if (handle.length >= 5 && !handle.all { it.isDigit() } && Regex("\\b$handle\\b").containsMatchIn(haystack)) return true

        val words = normalize(payeeName.orEmpty()).split(' ')
            .filter { it.length >= 4 && it !in GENERIC_WORDS && !it.all(Char::isDigit) }
        return words.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(haystack) }
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
}
