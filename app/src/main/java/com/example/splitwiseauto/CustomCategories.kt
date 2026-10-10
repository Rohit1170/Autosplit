package com.example.splitwiseauto

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-created categories. Stored in SharedPreferences ("custom_categories")
 * as a small JSON array — no Room migration needed, and the prefs file is
 * included in Auto Backup. Expenses keep storing the category *name*, so a
 * custom category behaves exactly like a built-in everywhere else
 * (treemap, Insights, merchant learning).
 */
object CustomCategories {

    private const val PREFS = "custom_categories"
    private const val KEY = "items"
    const val MAX_NAME_LENGTH = 18
    const val MAX_CUSTOM = 20

    /** Preset icons the user can pick from (key is what we persist). */
    val ICONS: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "pets" to Icons.Filled.Pets,
        "fitness" to Icons.Filled.FitnessCenter,
        "cafe" to Icons.Filled.LocalCafe,
        "party" to Icons.Filled.Celebration,
        "gift" to Icons.Filled.CardGiftcard,
        "work" to Icons.Filled.Work,
        "school" to Icons.Filled.School,
        "home" to Icons.Filled.Home,
        "kids" to Icons.Filled.ChildCare,
        "gaming" to Icons.Filled.SportsEsports,
        "drinks" to Icons.Filled.LocalBar,
        "selfcare" to Icons.Filled.Spa,
        "car" to Icons.Filled.DirectionsCar,
        "savings" to Icons.Filled.Savings,
        "other" to Icons.Filled.CategoryIcon
    )

    /** Preset accent colours (fg). Background is derived as a light tint. */
    val COLORS: List<Long> = listOf(
        0xFF14A87C, 0xFF26A69A, 0xFF42A5F5, 0xFF7E57C2,
        0xFFEC407A, 0xFFFF7043, 0xFFFFB300, 0xFF8D6E63
    )

    private data class Item(val name: String, val iconKey: String, val color: Long)

    @Volatile private var items: List<Item> = emptyList()
    @Volatile private var loaded = false

    /** Cheap after the first call; safe to call from any onCreate. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            items = read(context.applicationContext)
            loaded = true
        }
    }

    fun asCategories(): List<Category> = items.map { it.toCategory() }

    fun find(name: String): Category? =
        items.firstOrNull { it.name.equals(name, ignoreCase = true) }?.toCategory()

    fun isCustom(name: String): Boolean =
        items.any { it.name.equals(name, ignoreCase = true) }

    /** Returns an error message, or null when the name is acceptable. */
    fun validate(rawName: String): String? {
        val name = rawName.trim()
        return when {
            name.isEmpty() -> "Enter a name"
            name.length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
            expenseCategories.any { it.name.equals(name, ignoreCase = true) } ||
                isCustom(name) -> "\"$name\" already exists"
            items.size >= MAX_CUSTOM -> "You can have up to $MAX_CUSTOM custom categories"
            else -> null
        }
    }

    /** Adds a category; returns the saved name, or null if invalid. */
    fun add(context: Context, rawName: String, iconKey: String, color: Long): String? {
        ensureLoaded(context)
        if (validate(rawName) != null) return null
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        synchronized(this) {
            items = items + Item(name, iconKey.takeIf { it in ICONS } ?: "other", color)
            write(context.applicationContext, items)
        }
        return name
    }

    /**
     * Removes a custom category. Expenses and learned merchants that used it
     * move to "Other" so nothing disappears from totals.
     */
    suspend fun delete(context: Context, name: String) {
        ensureLoaded(context)
        val existing = items.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return
        synchronized(this) {
            items = items.filterNot { it.name == existing.name }
            write(context.applicationContext, items)
        }
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            db.expenseDao().recategorize(existing.name, OTHER.name)
            db.merchantCategoryDao().recategorize(existing.name, OTHER.name)
        }
    }

    private fun Item.toCategory(): Category {
        val fg = Color(color)
        // Light pastel tint of the accent, matching the built-in bgColors.
        val bg = Color(
            red = fg.red + (1f - fg.red) * 0.85f,
            green = fg.green + (1f - fg.green) * 0.85f,
            blue = fg.blue + (1f - fg.blue) * 0.85f
        )
        return Category(name, ICONS[iconKey] ?: Icons.Filled.CategoryIcon, bg, fg)
    }

    private fun read(context: Context): List<Item> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            if (name.isEmpty()) null
            else Item(name, o.optString("icon", "other"), o.optLong("color", COLORS.first()))
        }
    }.getOrDefault(emptyList())

    private fun write(context: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("icon", it.iconKey).put("color", it.color))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
