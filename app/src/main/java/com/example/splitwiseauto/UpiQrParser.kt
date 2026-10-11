package com.example.splitwiseauto

/**
 * Parses a scanned UPI QR payload (a `upi://pay?...` deep-link string —
 * the same format every UPI QR in India encodes, and the same format
 * SplitWiseAuto already builds for its own `upi://pay` settle-up deep
 * link) into the fields needed to drive a Scan & Pay session.
 */
data class UpiPaymentRequest(
    val vpa: String,
    val payeeName: String?,
    val amount: Double?,
    val currency: String?,
    val note: String?,
    // The QR exactly as scanned. Payments are launched from THIS, so
    // merchant fields (mc, mode, purpose, orgid, sign, tr…) reach the UPI
    // app untouched. Rebuilding the link from just pa/pn/am is what made
    // Google Pay reject payments ("exceeded the bank limit").
    val raw: String = ""
)

/**
 * Returns null if [raw] isn't a recognizable UPI payment QR — callers
 * should treat that as "Not a valid UPI QR" and let the user try again,
 * never guess at a partial payment. [amount] is null (not defaulted to
 * anything) when the QR carries no `am` param — callers must prompt for
 * an amount before proceeding in that case, never assume ₹0 or skip it.
 */
fun parseUpiQr(raw: String): UpiPaymentRequest? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("upi://pay", ignoreCase = true)) return null

    val queryPart = trimmed.substringAfter('?', missingDelimiterValue = "")
    if (queryPart.isBlank()) return null

    val params = queryPart.split('&').mapNotNull { pair ->
        val idx = pair.indexOf('=')
        if (idx <= 0) return@mapNotNull null
        val key = pair.substring(0, idx)
        val value = pair.substring(idx + 1)
        key to decodeUriComponent(value)
    }.toMap()

    val vpa = params["pa"]?.takeIf { it.contains('@') } ?: return null

    // An `am` param that's present but not a valid number is a malformed
    // QR, not an amount-less one — treated as invalid rather than silently
    // falling back to "ask the user", which would hide a QR that's simply
    // wrong.
    val amountText = params["am"]
    val amount = when {
        amountText == null -> null
        else -> amountText.toDoubleOrNull() ?: return null
    }

    return UpiPaymentRequest(
        vpa = vpa,
        payeeName = params["pn"]?.takeIf { it.isNotBlank() },
        amount = amount,
        currency = params["cu"]?.takeIf { it.isNotBlank() },
        note = params["tn"]?.takeIf { it.isNotBlank() },
        raw = trimmed
    )
}

private fun decodeUriComponent(value: String): String =
    try {
        java.net.URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }
