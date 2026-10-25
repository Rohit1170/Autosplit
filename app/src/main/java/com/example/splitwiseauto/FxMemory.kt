package com.example.splitwiseauto

import android.content.Context
import java.util.Locale

/**
 * Works out what a foreign-currency card spend really cost in rupees, using
 * only your own bank SMS (no internet):
 *  - exact: the drop in "Avl Limit"/"Avl Bal" since the previous SMS for the
 *    same card (includes the bank's forex markup);
 *  - else an estimate from the last rate learned that way (marked "≈");
 *  - else the parser's fallback estimate (also "≈").
 * Every SMS's limit/balance is remembered so the next one can be compared.
 */
object FxMemory {
    private const val PREFS = "fx_memory"
    private const val MAX_GAP_MS = 3L * 24 * 60 * 60 * 1000

    private fun cardKey(t: Transaction) =
        t.bank.lowercase(Locale.ROOT) + "|" + (t.accountIdentifier ?: "-")

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)

    /** Refines a parsed DEBIT and remembers its limit/balance. */
    fun refine(context: Context, t: Transaction): Transaction {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = cardKey(t)
        val bal = t.balance.replace(",", "").toDoubleOrNull()
        val prevBal = prefs.getString("b_$key", null)?.toDoubleOrNull()
        val prevAt = prefs.getLong("t_$key", 0L)
        var out = t

        val cur = t.originalCurrency
        val orig = t.originalAmount
        if (cur != null && orig != null && orig > 0) {
            val learned = prefs.getString("r_$cur", null)?.toDoubleOrNull()
            if (t.fxEstimated) {
                val reference = learned ?: ForeignCurrency.FALLBACK_INR_RATES[cur]
                val gap = t.receivedAtMillis - prevAt
                if (bal != null && prevBal != null && gap in 1..MAX_GAP_MS && reference != null) {
                    val diff = prevBal - bal
                    val rate = diff / orig
                    // Sanity: a bill payment / refund in between would make the
                    // difference wrong, so only trust a believable rate.
                    if (diff > 0 && rate in reference * 0.7..reference * 1.45) {
                        out = t.copy(amount = fmt(diff), fxEstimated = false)
                        prefs.edit().putString("r_$cur", rate.toString()).apply()
                    }
                }
                if (out.fxEstimated && learned != null) {
                    out = t.copy(amount = fmt(orig * learned))
                }
            } else {
                // The bank gave the INR amount itself: learn the rate.
                t.amount.replace(",", "").toDoubleOrNull()?.let { inr ->
                    if (inr > 0) prefs.edit().putString("r_$cur", (inr / orig).toString()).apply()
                }
            }
        }
        recordBalance(context, t)
        return out
    }

    /** Remembers this SMS's limit/balance (debits AND credits, e.g. bill payments). */
    fun recordBalance(context: Context, t: Transaction) {
        val bal = t.balance.replace(",", "").toDoubleOrNull() ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = cardKey(t)
        // Inbox catch-up can deliver older SMS later; keep the newest.
        if (t.receivedAtMillis < prefs.getLong("t_$key", 0L)) return
        prefs.edit()
            .putString("b_$key", bal.toString())
            .putLong("t_$key", t.receivedAtMillis)
            .apply()
    }
}
