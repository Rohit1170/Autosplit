package com.example.splitwiseauto

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
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

val expenseCategories = listOf(FOOD, TRAVEL, SHOPPING, BILLS, OTHER)

fun categoryByName(name: String): Category =
    expenseCategories.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OTHER

// Naive keyword-based categorizer. Good enough until Phase 8 (bank-specific parsers)
// or a proper category field gets added to the Expense entity.
fun categorize(merchant: String): Category {
    val m = merchant.lowercase()
    return when {
        listOf("food", "restaurant", "cafe", "corner", "kitchen", "zomato", "swiggy", "dine")
            .any { m.contains(it) } -> FOOD
        listOf("uber", "ola", "travel", "flight", "airline", "train", "irctc", "cab")
            .any { m.contains(it) } -> TRAVEL
        listOf("amazon", "flipkart", "myntra", "shop", "mall", "store")
            .any { m.contains(it) } -> SHOPPING
        listOf("electricity", "bill", "recharge", "broadband", "dth", "insurance")
            .any { m.contains(it) } -> BILLS
        else -> OTHER
    }
}
