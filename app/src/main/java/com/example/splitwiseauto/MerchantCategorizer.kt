package com.example.splitwiseauto

import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * Picks a category for a newly parsed merchant string, in priority order:
 *   1. what the user taught us for this merchant (merchant_category_map),
 *   2. a built-in map of common Indian merchants,
 *   3. the old keyword heuristic ([categorize]), which falls back to "Other".
 *
 * Only used when a NEW expense is created. Existing expenses are never
 * re-categorised. Nothing leaves the device.
 */
object MerchantCategorizer {

    private const val TAG = "MerchantCategorizer"

    // Corporate noise dropped before matching, so "Zepto Pvt Ltd",
    // "ZEPTO*" and "zepto" all share the key "ZEPTO".
    private val NOISE_WORDS = setOf(
        "PVT", "PRIVATE", "LTD", "LIMITED", "LLP", "INC", "CO", "COMPANY", "CORP",
        "INDIA", "IND", "TECHNOLOGIES", "TECHNOLOGY", "TECH", "SERVICES", "SERVICE",
        "SOLUTIONS", "RETAIL", "ONLINE", "THE"
    )

    /**
     * Uppercase, then keep only A-Z0-9. Corporate suffix words are removed
     * first (unless that would leave nothing), so legal names and display
     * names collapse to the same key.
     */
    fun normalizeKey(merchant: String): String {
        val words = merchant.uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
        val meaningful = words.filterNot { it in NOISE_WORDS }
        return (meaningful.ifEmpty { words }).joinToString("")
    }

    /**
     * Built-in seed (lowest priority). Keys are normalised. A merchant
     * matches a seed key exactly, or by prefix ("SWIGGYINSTAMART12" ->
     * "SWIGGYINSTAMART"); the longest matching key wins, so JIOMART beats JIO.
     * "Utilities" from the spec maps to this app's existing "Bills" category.
     */
    internal val STATIC_MAP: Map<String, String> = mapOf(
        // Transport (daily commute)
        "UBER" to "Transport", "OLA" to "Transport", "OLACABS" to "Transport", "RAPIDO" to "Transport",
        "NAMMAMETRO" to "Transport", "DMRC" to "Transport", "BMRCL" to "Transport", "FASTAG" to "Transport",
        // Travel (trips)
        "IRCTC" to "Travel", "REDBUS" to "Travel", "MAKEMYTRIP" to "Travel",
        "GOIBIBO" to "Travel", "INDIGO" to "Travel", "AIRINDIA" to "Travel", "CLEARTRIP" to "Travel",
        // Fuel
        "INDIANOIL" to "Fuel", "IOCL" to "Fuel", "HPCL" to "Fuel", "BPCL" to "Fuel",
        "HINDUSTANPETROLEUM" to "Fuel", "BHARATPETROLEUM" to "Fuel", "NAYARA" to "Fuel", "SHELL" to "Fuel",
        // Health
        "APOLLO" to "Health", "PHARMEASY" to "Health", "NETMEDS" to "Health", "TATA1MG" to "Health",
        "PRACTO" to "Health", "MEDPLUS" to "Health",
        // Subscriptions
        "NETFLIX" to "Subscriptions", "SPOTIFY" to "Subscriptions", "HOTSTAR" to "Subscriptions",
        "PRIMEVIDEO" to "Subscriptions", "YOUTUBEPREMIUM" to "Subscriptions", "APPLEMEDIA" to "Subscriptions",
        // Food
        "ZOMATO" to "Food", "SWIGGY" to "Food", "DOMINOS" to "Food", "MCDONALDS" to "Food",
        "KFC" to "Food", "STARBUCKS" to "Food", "PIZZAHUT" to "Food", "BURGERKING" to "Food",
        // Groceries
        "SWIGGYINSTAMART" to "Groceries", "INSTAMART" to "Groceries", "BLINKIT" to "Groceries",
        "ZEPTO" to "Groceries", "BIGBASKET" to "Groceries", "DMART" to "Groceries",
        "AVENUESUPERMARTS" to "Groceries", "JIOMART" to "Groceries",
        // Shopping
        "AMAZON" to "Shopping", "FLIPKART" to "Shopping", "MYNTRA" to "Shopping",
        "AJIO" to "Shopping", "MEESHO" to "Shopping", "NYKAA" to "Shopping",
        "CROMA" to "Shopping", "DECATHLON" to "Shopping",
        // Bills (utilities)
        "JIO" to "Bills", "AIRTEL" to "Bills", "BSNL" to "Bills", "VODAFONE" to "Bills",
        "TATAPLAY" to "Bills", "BESCOM" to "Bills",
        // Entertainment (one-off)
        "BOOKMYSHOW" to "Entertainment", "PVR" to "Entertainment", "INOX" to "Entertainment"
    )

    private const val MIN_PREFIX_KEY = 3

    /** Seed-map lookup only (pure — unit tested). */
    fun staticCategoryFor(merchantKey: String): String? {
        if (merchantKey.isEmpty()) return null
        STATIC_MAP[merchantKey]?.let { return it }
        return STATIC_MAP.entries
            .filter { it.key.length >= MIN_PREFIX_KEY && merchantKey.startsWith(it.key) }
            .maxByOrNull { it.key.length }
            ?.value
    }

    /** Category name for a NEW expense with this merchant. Never throws. */
    suspend fun resolve(context: Context, merchant: String): String {
        CustomCategories.ensureLoaded(context) // a learned custom category must not read as "Other"
        val key = normalizeKey(merchant)
        if (key.isNotEmpty()) {
            try {
                AppDatabase.getDatabase(context).merchantCategoryDao().categoryFor(key)
                    ?.let { learned -> return categoryByName(learned).name }
            } catch (e: Exception) {
                Log.w(TAG, "Learned-map lookup failed: ${e.javaClass.simpleName}")
            }
            staticCategoryFor(key)?.let { return categoryByName(it).name }
        }
        return categorize(merchant).name
    }

    /** The user picked [category] for [merchant] by hand: remember it. */
    suspend fun learn(context: Context, merchant: String, category: String) {
        CustomCategories.ensureLoaded(context)
        val key = normalizeKey(merchant)
        if (key.isEmpty()) return
        try {
            AppDatabase.getDatabase(context).merchantCategoryDao().upsert(
                MerchantCategory(key, categoryByName(category).name, System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not save learned category: ${e.javaClass.simpleName}")
        }
    }

    private const val RECHECK_PREFS = "category_recheck"
    private const val RECHECK_VERSION = 1

    /**
     * One-time pass over expenses still sitting in "Other": re-runs today's
     * rules (learned merchants, merchant list, keywords incl. cut-off names)
     * and moves the ones it now recognises. Expenses in any other category
     * are never touched. Runs once per rules version; returns how many moved.
     */
    suspend fun recheckOtherExpensesOnce(context: Context): Int {
        val prefs = context.getSharedPreferences(RECHECK_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt("done_version", 0) >= RECHECK_VERSION) return 0
        var moved = 0
        try {
            val dao = AppDatabase.getDatabase(context).expenseDao()
            dao.getByCategory(OTHER.name).forEach { e ->
                if (e.merchant.isBlank() || e.merchant.equals("Unknown", ignoreCase = true)) return@forEach
                val better = resolve(context, e.merchant)
                if (!better.equals(OTHER.name, ignoreCase = true)) {
                    dao.updateCategory(e.id, better)
                    moved++
                }
            }
            prefs.edit().putInt("done_version", RECHECK_VERSION).apply()
            Log.i(TAG, "Re-checked 'Other' expenses: $moved moved to a category")
        } catch (e: Exception) {
            Log.w(TAG, "Re-check of Other failed: ${e.javaClass.simpleName}")
        }
        return moved
    }
}
