package com.example.splitwiseauto

import java.util.Locale

/**
 * Foreign-currency card spends, e.g. "PHP 518.09 spent using ICICI Bank Card
 * XX1000 ... Avl Limit: INR 44,690.51". Pure Kotlin (unit-tested).
 */
object ForeignCurrency {

    /**
     * Rough INR for 1 unit of each currency. Used ONLY until a real rate is
     * learned from your own bank SMS (see [FxMemory]); estimates made with it
     * are always marked "≈" and can be edited before saving.
     */
    val FALLBACK_INR_RATES: Map<String, Double> = mapOf(
        "USD" to 85.0, "EUR" to 95.0, "GBP" to 110.0, "PHP" to 1.5, "THB" to 2.5,
        "AED" to 23.0, "SGD" to 65.0, "MYR" to 19.0, "IDR" to 0.0052, "JPY" to 0.56,
        "AUD" to 56.0, "CAD" to 61.0, "CHF" to 98.0, "HKD" to 11.0, "LKR" to 0.28,
        "NPR" to 0.625, "VND" to 0.0033, "KRW" to 0.061, "CNY" to 11.8, "SAR" to 22.6,
        "QAR" to 23.3, "OMR" to 220.0, "BHD" to 225.0, "KWD" to 277.0, "NZD" to 51.0,
        "ZAR" to 4.7, "TRY" to 2.1, "EGP" to 1.7, "MVR" to 5.5, "BDT" to 0.7
    )

    /** Typical card forex markup, added to fallback estimates only. */
    const val ESTIMATE_MARKUP = 1.035

    data class Amount(val currency: String, val amount: Double)

    private val codes = FALLBACK_INR_RATES.keys.joinToString("|")
    // Uppercase codes only ("PHP 518.09", "USD:15", "518.09 PHP").
    private val prefix = Regex("""\b($codes)\s*:?\s*([\d,]+(?:\.\d{1,2})?)""")
    private val suffix = Regex("""\b([\d,]+(?:\.\d{1,2})?)\s*($codes)\b""")

    /** The foreign amount spent, ignoring amounts in a balance/limit phrase. */
    fun find(message: String): Amount? {
        prefix.findAll(message).firstOrNull { !isBalanceContext(message, it.range.first) }?.let { m ->
            val v = m.groupValues[2].replace(",", "").toDoubleOrNull()
            if (v != null && v > 0) return Amount(m.groupValues[1], v)
        }
        suffix.findAll(message).firstOrNull { !isBalanceContext(message, it.range.first) }?.let { m ->
            val v = m.groupValues[1].replace(",", "").toDoubleOrNull()
            if (v != null && v > 0) return Amount(m.groupValues[2], v)
        }
        return null
    }

    /** INR estimate: a learned rate if we have one, else fallback x markup. */
    fun estimateInr(amount: Amount, learnedRate: Double? = null): Double? {
        val rate = learnedRate ?: FALLBACK_INR_RATES[amount.currency]?.let { it * ESTIMATE_MARKUP } ?: return null
        return amount.amount * rate
    }

    /** "PHP 518.09" */
    fun label(currency: String, amount: Double): String =
        "$currency " + String.format(Locale.US, "%,.2f", amount).removeSuffix(".00")
}

private val BALANCE_CONTEXT = Regex(
    """(?:avl|avail|available|bal|balance|limit|lmt|clr|outstanding)\b[^0-9]{0,14}$""",
    RegexOption.IGNORE_CASE
)

/** True when the amount starting at [start] belongs to "Avl Bal / Avl Limit / Balance ..." text. */
internal fun isBalanceContext(message: String, start: Int): Boolean =
    BALANCE_CONTEXT.containsMatchIn(message.substring(maxOf(0, start - 28), start))
