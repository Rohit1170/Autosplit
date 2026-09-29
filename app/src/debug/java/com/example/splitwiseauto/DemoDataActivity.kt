package com.example.splitwiseauto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEBUG ONLY (src/debug) — fills a fresh install with realistic sample data
 * for store screenshots (goldie), then opens the app.
 *
 * Safe to call repeatedly: it seeds only when there are no expenses yet, so
 * it never duplicates data or touches a database that already has real
 * entries. Not present in release builds at all.
 */
class DemoDataActivity : ComponentActivity() {

    private data class Sample(
        val daysAgo: Int,
        val amount: Double,
        val merchant: String,
        val category: String,
        val lat: Double,
        val lng: Double,
        val splitWith: List<String> = emptyList()
    )

    // Bengaluru locations, so the Map tab has clustered pins in one city.
    private val samples = listOf(
        Sample(0, 1240.0, "Truffles", "Food", 12.9352, 77.6245, listOf("Rahul", "Priya")),
        Sample(1, 349.0, "Uber", "Travel", 12.9716, 77.5946),
        Sample(2, 2899.0, "Decathlon", "Shopping", 12.9279, 77.6271),
        Sample(3, 1800.0, "BESCOM Electricity Bill", "Bills", 12.9784, 77.6408, listOf("Aman")),
        Sample(4, 460.0, "Third Wave Coffee", "Food", 12.9698, 77.7500),
        Sample(6, 3200.0, "Toit Brewpub", "Food", 12.9791, 77.6408, listOf("Rahul", "Aman", "Sneha")),
        Sample(8, 599.0, "Airtel Recharge", "Bills", 12.9716, 77.5946),
        Sample(10, 1499.0, "Myntra", "Shopping", 12.9141, 77.6101),
        Sample(12, 780.0, "Swiggy", "Food", 12.9352, 77.6245, listOf("Priya")),
        Sample(15, 4200.0, "IRCTC", "Travel", 12.9767, 77.5713, listOf("Sneha")),
        Sample(33, 2100.0, "BigBasket", "Shopping", 12.9259, 77.6229),
        Sample(38, 950.0, "Meghana Foods", "Food", 12.9345, 77.6101, listOf("Rahul")),
        Sample(45, 1650.0, "Rapido", "Travel", 12.9591, 77.6974)
    )

    private val friends = listOf("Rahul", "Priya", "Aman", "Sneha")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { seedIfEmpty() }
            startActivity(
                Intent(this@DemoDataActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
        }
    }

    private suspend fun seedIfEmpty() {
        val db = AppDatabase.getDatabase(this)
        if (db.expenseDao().getAllExpenses().isNotEmpty()) return

        friends.forEach { db.friendDao().insertFriend(Friend(name = it)) }

        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd-MMM-yy", Locale.US)

        samples.forEachIndexed { i, s ->
            // Midday-ish, staggered, so trend points spread across the day.
            val ts = now - s.daysAgo * dayMs - (i % 5) * 60 * 60 * 1000L
            val expenseId = db.expenseDao().insertExpense(
                Expense(
                    amount = s.amount,
                    merchant = s.merchant,
                    bank = "HDFC Bank",
                    date = dateFormat.format(Date(ts)),
                    upi = "DEMO${100000 + i}",
                    category = s.category,
                    accountIdentifier = "****4821",
                    latitude = s.lat,
                    longitude = s.lng,
                    timestampMillis = ts
                )
            ).toInt()

            val people = listOf("Me") + s.splitWith
            val share = Math.round(s.amount / people.size * 100) / 100.0
            db.expenseParticipantDao().insertParticipants(
                people.map { ExpenseParticipant(expenseId = expenseId, personName = it, shareAmount = share) }
            )
        }
    }
}
