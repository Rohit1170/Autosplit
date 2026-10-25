package com.example.splitwiseauto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

/** What the masked number in a bank SMS belongs to. */
enum class AccountKind { CARD, ACCOUNT }

/**
 * Card or bank account? Bank SMS say "Card ending 0508" / "Credit Card XX0508"
 * for cards and "A/c XX9683" / "Acct" for accounts. Null when the SMS doesn't
 * say (then Home keeps the plain account look).
 */
fun extractAccountKind(sms: String): AccountKind? {
    // Mask glued to the label: "A/CX0508" (account), "CardXX1000" (card).
    if (Regex("""\b(?:a/c|acct)\s*[x*]+\d{3,4}\b""", RegexOption.IGNORE_CASE).containsMatchIn(sms)) return AccountKind.ACCOUNT
    if (Regex("""\bcard\s*(?:no\.?\s*)?[x*]+\d{3,4}\b""", RegexOption.IGNORE_CASE).containsMatchIn(sms)) return AccountKind.CARD
    val labelled = Regex(
        """\b(a/?c(?:count)?|acct|card)(?:\s+(?:no\.?|number))?\s+(?:ending(?:\s+(?:in|with))?\s+)?(?:no\.?\s*)?(?:x+|\*+)?\s*\d{3,4}\b""",
        RegexOption.IGNORE_CASE
    ).find(sms)?.groupValues?.getOrNull(1)?.lowercase()
    if (labelled != null) return if (labelled.startsWith("card")) AccountKind.CARD else AccountKind.ACCOUNT
    val lower = sms.lowercase()
    val mentionsAccount = Regex("""\ba/?c\b|\bacct\b|\baccount\b""").containsMatchIn(lower)
    return when {
        "credit card" in lower || "debit card" in lower -> AccountKind.CARD
        Regex("""\bcard\b""").containsMatchIn(lower) && !mentionsAccount -> AccountKind.CARD
        mentionsAccount -> AccountKind.ACCOUNT
        else -> null
    }
}

/**
 * Remembers which bank + masked numbers are cards (seen in SMS), so Home can
 * draw those as cards (chip + contactless) and the rest as accounts. Kept in
 * prefs: no database change. Filled as SMS arrive, plus a one-time scan of
 * the SMS inbox for expenses that were already saved.
 */
object AccountKinds {
    private const val PREFS = "splitlee_account_kinds"
    private const val KEY_CARDS = "cards"
    private const val KEY_BACKFILLED = "backfilled_v1"
    private const val BACKFILL_DAYS = 400L

    private fun key(bank: String, accountIdentifier: String) =
        "${bank.trim().lowercase()}|$accountIdentifier"

    fun isCard(context: Context, bank: String, accountIdentifier: String?): Boolean {
        if (accountIdentifier == null) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_CARDS, emptySet())!!
            .contains(key(bank, accountIdentifier))
    }

    /** Records one parsed SMS. Returns true when a new card was learned. */
    fun record(context: Context, bank: String, accountIdentifier: String?, sms: String): Boolean {
        if (accountIdentifier == null) return false
        if (extractAccountKind(sms) != AccountKind.CARD) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cards = prefs.getStringSet(KEY_CARDS, emptySet())!!
        val k = key(bank, accountIdentifier)
        if (k in cards) return false
        prefs.edit().putStringSet(KEY_CARDS, cards + k).apply()
        return true
    }

    /**
     * One-time pass over recent inbox SMS so cards already on Home get the
     * card look. Reads only; no expenses are created. Returns true when
     * any card was found (caller refreshes Home).
     */
    fun backfillOnce(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BACKFILLED, false)) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        var found = false
        val from = System.currentTimeMillis() - BACKFILL_DAYS * 86_400_000L
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(from.toString()),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val sender = c.getString(0) ?: continue
                    val body = c.getString(1) ?: continue
                    val t = runCatching { parseBankSms(body, sender, c.getLong(2)) }.getOrNull() ?: continue
                    if (record(context, t.bank, t.accountIdentifier, body)) found = true
                }
            }
        } catch (e: SecurityException) {
            return false
        }
        prefs.edit().putBoolean(KEY_BACKFILLED, true).apply()
        return found
    }
}
