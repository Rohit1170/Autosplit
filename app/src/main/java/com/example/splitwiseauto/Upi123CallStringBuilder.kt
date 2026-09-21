package com.example.splitwiseauto

/**
 * Builds the DTMF-pause dial string for UPI 123Pay's IVR rail — the
 * fallback for Jio SIMs, which don't carry GSM USSD signalling so *99#
 * never reaches the network at all. Pure string logic, no Android
 * dependency, so it's plain-JVM unit-testable (see
 * Upi123CallStringBuilderTest) independent of ever placing a real call.
 *
 * Format: tel:<serviceNumber>,,1,<recipientPhone>,,<amount>,,1
 * where "," is Android's dialer pause token (~2s each). The sequence dials
 * the service number, waits, presses 1 (send money), waits, enters the
 * recipient's phone number, waits, enters the whole-rupee amount, waits,
 * then presses 1 again (confirm) — same shape 123Pay's own published IVR
 * menu uses. NOTE: this needs the payee's registered MOBILE NUMBER, not a
 * UPI VPA — a UPI QR only ever gives us a VPA, so in practice a QR-only
 * flow can't feed this builder; see SimRailSelector for how the Jio path
 * falls back to a upi://pay deep-link instead when only a VPA is known.
 */
object Upi123CallStringBuilder {

    private const val PAUSE = ","
    const val MIN_AMOUNT_RUPEES = 1.0
    const val MAX_AMOUNT_RUPEES = 100_000.0

    sealed class BuildResult {
        data class Success(val dialString: String) : BuildResult()
        data class Failure(val reason: Reason) : BuildResult()
    }

    enum class Reason {
        INVALID_SERVICE_NUMBER,
        INVALID_RECIPIENT_NUMBER,
        AMOUNT_NOT_WHOLE_RUPEE,
        AMOUNT_TOO_LOW,
        AMOUNT_TOO_HIGH
    }

    private val SERVICE_NUMBER_PATTERN = Regex("^\\d{3,15}$")
    private val RECIPIENT_PATTERN = Regex("^[6-9]\\d{9}$")

    fun build(
        serviceNumber: String,
        recipientPhone: String,
        amountRupees: Double
    ): BuildResult {
        if (!SERVICE_NUMBER_PATTERN.matches(serviceNumber)) {
            return BuildResult.Failure(Reason.INVALID_SERVICE_NUMBER)
        }
        if (!RECIPIENT_PATTERN.matches(recipientPhone)) {
            return BuildResult.Failure(Reason.INVALID_RECIPIENT_NUMBER)
        }
        if (amountRupees != kotlin.math.floor(amountRupees)) {
            return BuildResult.Failure(Reason.AMOUNT_NOT_WHOLE_RUPEE)
        }
        if (amountRupees < MIN_AMOUNT_RUPEES) {
            return BuildResult.Failure(Reason.AMOUNT_TOO_LOW)
        }
        if (amountRupees > MAX_AMOUNT_RUPEES) {
            return BuildResult.Failure(Reason.AMOUNT_TOO_HIGH)
        }

        val wholeAmount = amountRupees.toLong()
        val dialString = buildString {
            append("tel:")
            append(serviceNumber)
            append(PAUSE).append(PAUSE)
            append("1")
            append(PAUSE)
            append(recipientPhone)
            append(PAUSE).append(PAUSE)
            append(wholeAmount)
            append(PAUSE).append(PAUSE)
            append("1")
        }
        return BuildResult.Success(dialString)
    }
}
