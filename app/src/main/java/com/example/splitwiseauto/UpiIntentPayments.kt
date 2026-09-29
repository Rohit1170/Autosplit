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
    private const val DEFAULT_NOTE = "Splitlee payment"

    /**
     * Our own transaction reference, sent as `tr`. Alphanumeric only and
     * well under the 35-char limit some UPI apps enforce. Also the
     * PendingPayment primary key.
     */
    fun newPaymentRef(): String =
        "SWA" + UUID.randomUUID().toString().replace("-", "").take(24).uppercase()

    /**
     * Builds the `upi://pay` URI to launch.
     *
     * - `pa` / `pn` come from the scanned QR, unchanged.
     * - `am`: the QR's own amount if it had one, otherwise [billAmountMinor]
     *   (the amount of the bill being paid / entered on screen).
     * - `tn`: the QR's own note if it had one, otherwise [billNote], otherwise a
     *   generic note.
     * - `tr`: ALWAYS our [paymentRef], overriding anything in the QR, so the
     *   payment can be tied back to its PendingPayment row.
     * - `cu`: always INR.
     */
    fun buildPayUri(
        qr: UpiPaymentRequest,
        billAmountMinor: Long,
        billNote: String?,
        paymentRef: String
    ): Uri {
        val amountMinor = qr.amount?.let { rupeesToMinor(it) } ?: billAmountMinor
        val note = qr.note ?: billNote?.takeIf { it.isNotBlank() } ?: DEFAULT_NOTE

        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", qr.vpa)
            .apply { qr.payeeName?.let { appendQueryParameter("pn", it) } }
            .appendQueryParameter("am", minorToRupeeText(amountMinor))
            .appendQueryParameter("tn", note)
            .appendQueryParameter("tr", paymentRef)
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
