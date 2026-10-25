package com.example.splitwiseauto

import android.content.Context

/**
 * Who the app greets. Hardcoded for now; the future intro pages will ask for
 * the name and save it with [UserProfile.setName], and everything that reads
 * [UserProfile.name] picks it up without other changes.
 */
object UserProfile {
    private const val PREFS = "splitlee_profile"
    private const val KEY_NAME = "user_name"
    const val DEFAULT_NAME = "Rohit"

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NAME

    fun setName(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NAME, name.trim()).apply()
    }

    private const val KEY_UPI = "user_upi_id"

    /** Your own UPI ID (for the pay QR in reminders). Null until set. */
    fun upiId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_UPI, null)?.takeIf { it.isNotBlank() }

    fun setUpiId(context: Context, vpa: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_UPI, vpa?.trim()?.takeIf { it.isNotEmpty() }).apply()
    }
}

data class MoneyQuote(val text: String, val author: String)

/**
 * One-line money quotes for the Home greeting. A different one each time
 * Home opens (never the same as last time). Several are traditional
 * attributions (Franklin, Barnum, Cicero) rather than verified sources.
 */
object MoneyQuotes {
    private const val PREFS = "splitlee_profile"
    private const val KEY_LAST = "last_quote"

    val all = listOf(
        MoneyQuote("Beware of little expenses; a small leak will sink a great ship.", "Benjamin Franklin"),
        MoneyQuote("An investment in knowledge pays the best interest.", "Benjamin Franklin"),
        MoneyQuote("Price is what you pay. Value is what you get.", "Warren Buffett"),
        MoneyQuote("Rule No. 1: Never lose money. Rule No. 2: Never forget rule No. 1.", "Warren Buffett"),
        MoneyQuote("Saving is the gap between your ego and your income.", "Morgan Housel"),
        MoneyQuote("Wealth is what you don't see.", "Morgan Housel"),
        MoneyQuote("It's not how much money you make, but how much money you keep.", "Robert Kiyosaki"),
        MoneyQuote("Money is a terrible master but an excellent servant.", "P. T. Barnum"),
        MoneyQuote("Never spend your money before you have it.", "Thomas Jefferson"),
        MoneyQuote("Money often costs too much.", "Ralph Waldo Emerson"),
        MoneyQuote("It is not the man who has too little, but the man who craves more, that is poor.", "Seneca"),
        MoneyQuote("Wealth consists not in having great possessions, but in having few wants.", "Epictetus"),
        MoneyQuote("Frugality includes all the other virtues.", "Cicero"),
        MoneyQuote("Know what you own, and know why you own it.", "Peter Lynch"),
        MoneyQuote("Formal education will make you a living; self-education will make you a fortune.", "Jim Rohn"),
        MoneyQuote("He who buys what he does not need steals from himself.", "Swedish proverb")
    )

    /** A random quote that differs from the one shown last time. */
    fun next(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_LAST, -1)
        var i = all.indices.random()
        if (all.size > 1 && i == last) i = (i + 1 + all.indices.random().coerceAtMost(all.size - 2)) % all.size
        prefs.edit().putInt(KEY_LAST, i).apply()
        return i
    }
}
