package com.example.splitwiseauto

import android.net.Uri
import java.util.UUID

/**
 * UPI intent-flow helpers for Scan & Pay.
 *
 * The payment itself happens entirely inside the user's own UPI app (GPay,
 * PhonePe, Paytm, BHIM…) — including UPI PIN entry, which this app never
 * sees, collects, or logs. This file only builds the `upi://pay` URI and
 * reads back the (unreliable) result string the UPI app returns.
 */
object UpiIntentPayments {

    const val CURRENCY_INR = "INR"

    /**
     * Our own transaction reference, sent as `tr`. Alphanumeric only and
     * well under the 35-char limit some UPI apps enforce. Also the
     * PendingPayment primary key.
     */
    fun newPaymentRef(): String =
        "SWA" + UUID.randomUUID().toString().replace("-", "").take(24).uppercase()

    /**
     * Builds the `upi://pay` URI to launch, starting from the QR exactly as
     * scanned ([UpiPaymentRequest.raw]) and adding only what is missing:
     *
     * - `am` only when the QR has no amount (the amount entered on screen).
     * - `cu=INR` only when absent.
     * - `tn` only when the QR has none AND the user typed a note.
     * - NEVER our own `tr` and never an overwritten field: a payee's own
     *   `tr`/`mc`/`sign` must reach the UPI app untouched. Google Pay
     *   declines intent payments whose merchant fields were stripped or
     *   whose `tr` was replaced (it shows "exceeded the bank limit").
     *
     * [paymentRef] is kept for our own PendingPayment row only; matching the
     * bank SMS uses the amount, time and bank reference, not `tr`.
     * Signed QRs (`sign=`) are passed through without any additions.
     */
    @Suppress("UNUSED_PARAMETER")
    fun buildPayUri(
        qr: UpiPaymentRequest,
        billAmountMinor: Long,
        billNote: String?,
        paymentRef: String
    ): Uri {
        if (qr.raw.isBlank()) return buildFromFields(qr, billAmountMinor, billNote)

        val query = qr.raw.substringAfter('?', "")
        val pairs = query.split('&').filter { it.isNotBlank() }.toMutableList()
        val keys = pairs.map { it.substringBefore('=').lowercase() }.toSet()
        val signed = "sign" in keys

        if (!signed) {
            if ("am" !in keys) pairs += "am=" + minorToRupeeText(billAmountMinor)
            if ("cu" !in keys) pairs += "cu=$CURRENCY_INR"
            val note = billNote?.trim()?.takeIf { it.isNotEmpty() }
            if ("tn" !in keys && note != null) pairs += "tn=" + Uri.encode(note)
        }
        return Uri.parse("upi://pay?" + pairs.joinToString("&"))
    }

    /** Fallback for requests that didn't come from a scanned QR. */
    private fun buildFromFields(qr: UpiPaymentRequest, billAmountMinor: Long, billNote: String?): Uri {
        val amountMinor = qr.amount?.let { rupeesToMinor(it) } ?: billAmountMinor
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", qr.vpa)
            .apply { qr.payeeName?.let { appendQueryParameter("pn", it) } }
            .appendQueryParameter("am", minorToRupeeText(amountMinor))
            .apply {
                (qr.note ?: billNote?.takeIf { it.isNotBlank() })?.let { appendQueryParameter("tn", it) }
            }
            .appendQueryParameter("cu", CURRENCY_INR)
            .build()
    }

    /** The amount (paise) [buildPayUri] will actually request for this QR + bill amount. */
    fun effectiveAmountMinor(qr: UpiPaymentRequest, billAmountMinor: Long): Long =
        qr.amount?.let { rupeesToMinor(it) } ?: billAmountMinor
}

/** How the UPI app described the payment when it returned. */
enum class UpiAppOutcome {
    /** Status=SUCCESS or responseCode=00. Shown as paid; the bank SMS still has to confirm it. */
    SUCCESS,

    /** Status=FAILURE/FAILED (or declined). No money moved; no SMS expected. */
    FAILURE,

    /** No response, cancelled, SUBMITTED/PENDING, or anything unclear. */
    UNKNOWN
}

/**
 * What a UPI app handed back in the activity result's "response" extra,
 * e.g. `txnId=AXI123&responseCode=00&Status=SUCCESS&txnRef=SWA...`.
 *
 * INFORMATIONAL ONLY. Different UPI apps fill this in differently (some
 * return nothing, some report SUCCESS before the bank settles, some omit
 * it on success), so it is NEVER treated as proof of payment. Only a
 * matching bank debit SMS confirms a payment.
 */
data class UpiAppResponse(
    val txnId: String?,
    val responseCode: String?,
    val status: String?,
    val txnRef: String?,
    val approvalRefNo: String?
) {
    /**
     * The value worth storing as PendingPayment.upiRef. Prefers ApprovalRefNo
     * when it's the 12-digit bank reference (that's the number bank SMS
     * carry, so it can match exactly); otherwise the UPI app's txnId.
     */
    val bestUpiRef: String?
        get() = approvalRefNo?.takeIf { TWELVE_DIGITS.matches(it) } ?: txnId

    companion object {
        private val TWELVE_DIGITS = Regex("""\d{12}""")
    }
}

/**
 * Classifies a (possibly missing) response. An explicit failure wins over a
 * stray "00", and anything not clearly SUCCESS or FAILURE is UNKNOWN.
 */
fun UpiAppResponse?.outcome(): UpiAppOutcome {
    if (this == null) return UpiAppOutcome.UNKNOWN
    val s = status?.trim()?.uppercase()
    return when {
        s == "FAILURE" || s == "FAILED" || s == "FAIL" || s == "DECLINED" -> UpiAppOutcome.FAILURE
        s == "SUCCESS" || responseCode?.trim() == "00" -> UpiAppOutcome.SUCCESS
        else -> UpiAppOutcome.UNKNOWN
    }
}

/** Best-effort parse; returns null for a missing/blank response. Never throws. */
fun parseUpiResponse(response: String?): UpiAppResponse? {
    if (response.isNullOrBlank()) return null
    val fields = response.split('&').mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val key = pair.substring(0, idx).trim().lowercase()
        val value = runCatching { java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }
            .getOrDefault(pair.substring(idx + 1))
            .trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) null else key to value
    }.toMap()
    if (fields.isEmpty()) return null
    return UpiAppResponse(
        txnId = fields["txnid"],
        responseCode = fields["responsecode"],
        status = fields["status"],
        txnRef = fields["txnref"],
        approvalRefNo = fields["approvalrefno"]
    )
}
