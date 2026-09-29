package com.example.splitwiseauto

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports all expenses to a CSV file and shares it (Data export / backup).
 *
 * Everything stays on the device until the user explicitly shares the file
 * through the Android share sheet — no network, no automatic upload. The CSV
 * is written to the app's cache and handed to the chosen app read-only via
 * FileProvider.
 */
object ExpenseExporter {

    private val TIMESTAMP_FMT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    /** Builds the CSV and returns a share Intent, or null if there's nothing to export. */
    suspend fun buildShareIntent(context: Context): Intent? {
        val db = AppDatabase.getDatabase(context)
        val expenses = db.expenseDao().getAllExpensesWithParticipants()
        if (expenses.isEmpty()) return null

        val csv = buildCsv(expenses)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Overwrite a single rolling file name per run; old exports don't pile up.
        val file = File(dir, "splitlee-expenses-${TIMESTAMP_FMT.format(Date())}.csv")
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Splitlee expenses export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildCsv(items: List<ExpenseWithParticipants>): String {
        val header = listOf(
            "Date", "Merchant", "Amount", "Category", "Bank", "Account",
            "Paid by", "Split with", "My share", "UPI Ref", "Latitude", "Longitude"
        )
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { csvCell(it) }).append("\n")

        // Newest first, by real transaction time when known.
        val sorted = items.sortedByDescending { it.expense.timestampMillis ?: Long.MIN_VALUE }
        for (item in sorted) {
            val e = item.expense
            val splitWith = item.participants.joinToString("; ") {
                "${it.personName}=${formatAmount(it.shareAmount)}"
            }
            val myShare = item.participants
                .filter { it.personName.equals("Me", ignoreCase = true) }
                .sumOf { it.shareAmount }
            val row = listOf(
                e.date,
                e.merchant,
                formatAmount(e.amount),
                e.category,
                e.bank,
                e.accountIdentifier ?: "",
                e.payer,
                splitWith,
                if (myShare > 0.0) formatAmount(myShare) else "",
                e.upi,
                e.latitude?.toString() ?: "",
                e.longitude?.toString() ?: ""
            )
            sb.append(row.joinToString(",") { csvCell(it) }).append("\n")
        }
        return sb.toString()
    }

    private fun formatAmount(amount: Double): String =
        String.format(Locale.US, "%.2f", amount)

    /** RFC-4180 escaping: wrap in quotes and double any embedded quotes if needed. */
    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
