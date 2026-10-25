package com.example.splitwiseauto

import java.util.Locale
import kotlin.math.abs

/**
 * Matches money you RECEIVED (a bank credit SMS) to a friend who owes you,
 * so Splitlee can ask "Akhil paid you ₹1,250? Mark as settled". Pure Kotlin.
 *
 * It only ever suggests; the user confirms. Rules:
 *  1. Name or UPI ID of exactly one friend appears in the SMS -> that friend.
 *  2. Else exactly one friend owes (or was reminded for) this exact amount.
 *  3. Anything else (refunds, cashback, salary, ties) -> no suggestion.
 */
object AutoSettleMatcher {

    data class Debtor(
        val friendId: Int,
        val name: String,
        /** What they owe you now (> 0). */
        val owed: Double,
        /** Their UPI ID, learned from an earlier confirmed match. */
        val vpa: String? = null,
        /** Amount in your last reminder to them (last 30 days), if any. */
        val remindedAmount: Double? = null
    )

    enum class Reason { NAME, UPI_ID, AMOUNT }

    data class Match(
        val friendId: Int,
        val name: String,
        /** Amount to record: what was received, capped at what they owe. */
        val amount: Double,
        val owed: Double,
        val reason: Reason,
        /** A UPI ID seen in the SMS (to remember for this friend). */
        val vpaInSms: String?
    )

    private val NOT_A_FRIEND = Regex(
        """\b(refund|reversal|reversed|cashback|cash\s*back|interest|salary|dividend|ach|nach|emi|loan|reward)\b""",
        RegexOption.IGNORE_CASE
    )
    private val VPA = Regex("""\b([a-z0-9._\-]{2,}@[a-z][a-z0-9]{1,})\b""", RegexOption.IGNORE_CASE)
    private val STOP_WORDS = setOf(
        "the", "and", "bank", "from", "upi", "ref", "acct", "a/c", "your", "account",
        "credited", "debited", "info", "mr", "mrs", "ms", "kumar", "singh"
    )
    private const val TOLERANCE = 0.5

    fun match(sms: String, amount: Double, debtors: List<Debtor>): Match? {
        if (amount <= 0 || NOT_A_FRIEND.containsMatchIn(sms)) return null
        val text = sms.lowercase(Locale.ROOT)
        val vpas = VPA.findAll(text).map { it.groupValues[1] }.toList()
        val owing = debtors.filter { it.owed > 0.01 }
        if (owing.isEmpty()) return null

        fun nameHit(d: Debtor): Reason? {
            val v = d.vpa?.lowercase(Locale.ROOT)
            if (v != null && v in vpas) return Reason.UPI_ID
            val tokens = d.name.lowercase(Locale.ROOT).split(Regex("[^a-z]+"))
                .filter { it.length >= 3 && it !in STOP_WORDS }
            if (tokens.isEmpty()) return null
            val inText = tokens.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(text) }
            val inVpa = vpas.any { vpa -> tokens.any { vpa.substringBefore('@').contains(it) } }
            return if (inText || inVpa) Reason.NAME else null
        }
        fun amountHit(d: Debtor) =
            abs(amount - d.owed) <= TOLERANCE ||
                (d.remindedAmount != null && abs(amount - d.remindedAmount) <= TOLERANCE)

        val byName = owing.mapNotNull { d -> nameHit(d)?.let { d to it } }
        val chosen: Pair<Debtor, Reason>? = when {
            byName.size == 1 -> byName.first()
            byName.size > 1 -> byName.filter { amountHit(it.first) }.singleOrNull()
            else -> owing.filter { amountHit(it) }.singleOrNull()?.let { it to Reason.AMOUNT }
        }
        val (d, reason) = chosen ?: return null
        return Match(
            friendId = d.friendId,
            name = d.name,
            amount = minOf(amount, d.owed),
            owed = d.owed,
            reason = reason,
            vpaInSms = vpas.firstOrNull()
        )
    }
}
