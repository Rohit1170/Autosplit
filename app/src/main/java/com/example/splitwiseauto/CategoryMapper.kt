package com.example.splitwiseauto

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Category(
    val name: String,
    val icon: ImageVector,
    val bgColor: Color,
    val fgColor: Color
)

// Category colors — single source of truth (per the design spec's token
// table). fgColor is the exact spec hex; bgColor is a light pastel tint of
// the same hue, used as the tile background in light mode (dark mode uses
// fgColor at low alpha instead — see TreemapView/HomeTabContent).
val FOOD = Category("Food", Icons.Filled.Restaurant, Color(0xFFFDE4E1), Color(0xFFEF6A5B))
val TRAVEL = Category("Travel", Icons.Filled.Flight, Color(0xFFE1E9FB), Color(0xFF5E8DF2))
val SHOPPING = Category("Shopping", Icons.Filled.ShoppingBag, Color(0xFFEEE5FC), Color(0xFFA37FF0))
val BILLS = Category("Bills", Icons.Filled.Receipt, Color(0xFFDCF7EC), Color(0xFF2FD39A))
val OTHER = Category("Other", Icons.Filled.MoreHoriz, Color(0xFFFCEBD3), Color(0xFFF0A63C))
// Added for the merchant map (Blinkit/Zepto/DMart, BookMyShow/Netflix…).
// Additive: existing expenses keep whatever category they already have.
val GROCERIES = Category("Groceries", Icons.Filled.LocalGroceryStore, Color(0xFFDDF3F5), Color(0xFF3FB6C2))
val ENTERTAINMENT = Category("Entertainment", Icons.Filled.Movie, Color(0xFFFBE3F0), Color(0xFFE56BAE))

// Daily commute (cab/auto/metro) — kept apart from TRAVEL (trips: flights,
// trains, hotels) so 30 Uber rides and one Goa flight aren't one bucket.
val TRANSPORT = Category("Transport", Icons.Filled.LocalTaxi, Color(0xFFFDF5D6), Color(0xFFE0B020))
val FUEL = Category("Fuel", Icons.Filled.LocalGasStation, Color(0xFFFBE4DA), Color(0xFFE2724A))
val HEALTH = Category("Health", Icons.Filled.LocalHospital, Color(0xFFFCE1E2), Color(0xFFE5484D))
val SUBSCRIPTIONS = Category("Subscriptions", Icons.Filled.Subscriptions, Color(0xFFE3E6F8), Color(0xFF5C6BC0))

/** Built-in categories, in picker order. Other stays last. */
val expenseCategories = listOf(
    FOOD, GROCERIES, TRANSPORT, TRAVEL, SHOPPING, BILLS, FUEL, HEALTH, SUBSCRIPTIONS, ENTERTAINMENT, OTHER
)

/** Built-ins + the user's own categories (before "Other"). */
fun allCategories(): List<Category> =
    expenseCategories.dropLast(1) + CustomCategories.asCategories() + OTHER

fun categoryByName(name: String): Category =
    expenseCategories.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: CustomCategories.find(name)
        ?: OTHER

// ---------------------------------------------------------------------------
// Keyword categorizer (offline, zero cost). Last step after the learned map
// and the static merchant map in MerchantCategorizer.
//
// Matching rules:
//  * The merchant is lower-cased and every non-letter/digit becomes a space,
//    so "SHREE_GANESH-HOTEL" and "shree ganesh hotel" read the same.
//  * Short keywords (<= 5 chars) must be whole words (an optional plural "s"
//    is allowed): "ola" no longer matches "Coca Cola", "dine" no longer
//    matches "Dinesh", "cab" still matches "Cabs".
//  * Longer keywords match anywhere ("apollopharmacy", "kfcrestaurant").
//  * Banks often cut the payee name ("SHREE GANESH HO" for "...HOTEL"), so
//    if nothing matched, the LAST word is treated as a possibly-cut word and
//    checked as the start of a TRUNCATED_HINTS word ("ho" -> hotel).
// Lists are built once (top-level vals): categorize() runs per row while
// scrolling, so nothing is allocated per call except the normalised string.
// ---------------------------------------------------------------------------

private class KeywordRule(val category: Category, keywords: List<String>) {
    private val longWords = keywords.filter { it.length > 5 }
    private val wordRegex: Regex? = keywords.filter { it.length <= 5 }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("|", prefix = "\\b(?:", postfix = ")s?\\b") { Regex.escape(it) }
        ?.let { Regex(it) }

    fun matches(normalized: String): Boolean =
        longWords.any { normalized.contains(it) } ||
            (wordRegex?.containsMatchIn(normalized) == true)
}

// Order matters: first matching rule wins. Strong, specific signals first
// (subscriptions, fuel, medical), lodging before food so "XYZ Resort" isn't
// Food, groceries before shopping so "General Store" isn't Shopping, and
// the vaguest words (shop/store/bill) last.
private val KEYWORD_RULES = listOf(
    KeywordRule(SUBSCRIPTIONS, listOf(
        "netflix", "spotify", "hotstar", "prime video", "primevideo", "youtube premium",
        "icloud", "jiocinema", "sonyliv", "zee5", "gaana", "wynk", "audible"
    )),
    KeywordRule(FUEL, listOf(
        "petrol", "petroleum", "fuel", "fuels", "diesel", "filling station", "filling stn",
        "petrol pump", "indian oil", "indianoil", "iocl", "hpcl", "bpcl", "shell", "nayara",
        "cng", "service station"
    )),
    KeywordRule(HEALTH, listOf(
        "pharmacy", "pharma", "medical", "medicals", "medicos", "medicose", "chemist",
        "chemists", "druggist", "hospital", "clinic", "nursing home", "diagnostic",
        "diagnostics", "pathology", "pathlab", "dental", "dentist", "apollo", "1mg",
        "pharmeasy", "netmeds", "medplus", "optical", "opticals", "ayurved"
    )),
    KeywordRule(TRAVEL, listOf(
        "travel", "travels", "tours", "flight", "airline", "airlines", "airways", "airport",
        "train", "irctc", "makemytrip", "goibibo", "redbus", "yatra", "cleartrip", "ixigo",
        "resort", "resorts", "lodge", "lodging", "oyo", "residency", "guest house",
        "guesthouse", "homestay"
    )),
    KeywordRule(FOOD, listOf(
        "food", "foods", "restaurant", "restro", "resto", "cafe", "corner", "kitchen",
        "zomato", "swiggy", "dine", "hotel", "dhaba", "bhavan", "bhawan", "bhojanalaya",
        "sweets", "sweet", "mithai", "halwai", "bakery", "bakers", "cake", "canteen",
        "caterers", "mess", "tiffin", "juice", "chai", "tea", "coffee", "pizza", "biryani",
        "dosa", "idli", "burger", "eatery", "eats", "kfc", "mcdonald", "dominos", "domino",
        "subway", "starbucks", "chaayos", "haldiram", "bikaner", "kulfi", "ice cream",
        "icecream", "paan", "snacks", "fast food", "chinese", "momos", "rolls"
    )),
    KeywordRule(GROCERIES, listOf(
        "kirana", "general store", "general stores", "provision", "provisions",
        "supermarket", "super market", "departmental", "department store", "grocery",
        "groceries", "dairy", "milk", "vegetable", "vegetables", "sabzi", "fruits",
        "fruit", "mart", "bigbasket", "blinkit", "zepto", "dmart", "kirana store"
    )),
    KeywordRule(ENTERTAINMENT, listOf(
        "cinema", "cinemas", "movies", "multiplex", "pvr", "inox", "bookmyshow",
        "gaming", "bowling", "amusement", "water park", "waterpark", "theatre", "theater"
    )),
    KeywordRule(TRANSPORT, listOf(
        "uber", "ola", "cab", "taxi", "rapido", "metro", "parking", "fastag", "toll",
        "auto rickshaw", "autorickshaw", "bus stand"
    )),
    KeywordRule(SHOPPING, listOf(
        "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "shop", "mall",
        "store", "garments", "garment", "fashion", "textiles", "textile", "cloth",
        "footwear", "shoes", "electronics", "mobiles", "jewellers", "jewellery",
        "boutique", "collection", "emporium", "novelty", "gifts", "stationery",
        "hardware", "furniture", "salon", "parlour", "parlor"
    )),
    KeywordRule(BILLS, listOf(
        "electricity", "bill", "recharge", "broadband", "dth", "insurance", "postpaid",
        "prepaid", "gas agency", "water supply", "municipal", "rent", "maintenance",
        "society", "school fee", "fees", "tuition"
    ))
)

/**
 * Words banks commonly cut off at the end of a payee name, in priority order.
 * Only the LAST word of the merchant is checked, and only if it is a strict
 * prefix of one of these (at least 3 letters, or one of TWO_LETTER_HINTS).
 */
private val TRUNCATED_HINTS: List<Pair<String, Category>> = listOf(
    "hotel" to FOOD, "restaurant" to FOOD, "bhavan" to FOOD, "bhawan" to FOOD,
    "dhaba" to FOOD, "sweets" to FOOD, "bakery" to FOOD, "bakers" to FOOD,
    "kitchen" to FOOD, "canteen" to FOOD, "caterers" to FOOD, "bhojanalaya" to FOOD,
    "medical" to HEALTH, "medicals" to HEALTH, "medicose" to HEALTH, "chemist" to HEALTH,
    "pharmacy" to HEALTH, "hospital" to HEALTH, "clinic" to HEALTH, "diagnostics" to HEALTH,
    "petroleum" to FUEL, "filling" to FUEL,
    "kirana" to GROCERIES, "provisions" to GROCERIES, "supermarket" to GROCERIES,
    "departmental" to GROCERIES, "vegetables" to GROCERIES,
    "garments" to SHOPPING, "textiles" to SHOPPING, "electronics" to SHOPPING,
    "jewellers" to SHOPPING, "footwear" to SHOPPING,
    "travels" to TRAVEL, "resort" to TRAVEL
)

/** 2-letter cut-offs that are unambiguous enough in Indian payee names. */
private val TWO_LETTER_HINTS = mapOf("ho" to FOOD) // "SHREE GANESH HO" -> hotel

private val NON_ALNUM = Regex("[^a-z0-9]+")

internal fun normalizeForKeywords(merchant: String): String =
    merchant.lowercase().replace(NON_ALNUM, " ").trim()

/** Category from a cut-off last word, e.g. "shree ganesh ho" -> FOOD. */
internal fun truncatedWordCategory(normalized: String): Category? {
    val words = normalized.split(' ').filter { it.isNotEmpty() }
    if (words.size < 2) return null // a lone short word isn't a cut-off name
    val last = words.last()
    if (last.any { it.isDigit() }) return null
    if (last.length == 2) return TWO_LETTER_HINTS[last]
    if (last.length < 3) return null
    return TRUNCATED_HINTS.firstOrNull { (word, _) ->
        word.length > last.length && word.startsWith(last)
    }?.second
}

// Keyword-based categorizer: offline fallback when neither the learned map
// nor the static merchant map knows the payee.
fun categorize(merchant: String): Category {
    val m = normalizeForKeywords(merchant)
    if (m.isEmpty()) return OTHER
    KEYWORD_RULES.firstOrNull { it.matches(m) }?.let { return it.category }
    return truncatedWordCategory(m) ?: OTHER
}
