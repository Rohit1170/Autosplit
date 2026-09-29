package com.example.splitwiseauto

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Lists expenses that were detected from an SMS but never actioned — either the
 * system notification was swiped away without being tapped, or it hasn't been
 * yet. Tapping an item opens the same confirmation/split flow the notification
 * itself would have opened; "Dismiss" discards it without creating an expense.
 */
@Composable
fun PendingNotificationsDialog(
    pending: List<PendingExpenseNotification>,
    onDismiss: () -> Unit,
    onDismissItem: (Int) -> Unit,
    // Recent Scan & Pay payments, each with its Pending / Confirmed /
    // Unconfirmed status. Informational — tapping does nothing.
    payments: List<PendingPayment> = emptyList()
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Missed expenses")
        },
        text = {
            if (pending.isEmpty() && payments.isEmpty()) {
                Text("Nothing pending — you're all caught up.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (payments.isNotEmpty()) {
                        item(key = "payments_header") {
                            Text(
                                text = "Scan & Pay payments",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(payments, key = { "payment_" + it.id }) { payment ->
                            PendingPaymentRow(payment)
                        }
                        if (pending.isNotEmpty()) {
                            item(key = "pending_header") {
                                Text(
                                    text = "To split",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                    items(pending, key = { it.id }) { item ->
                        PendingNotificationRow(
                            item = item,
                            onReview = {
                                val intent = Intent(context, ExpenseConfirmationActivity::class.java).apply {
                                    putExtra("amount", item.amount)
                                    putExtra("merchant", item.merchant)
                                    putExtra("bank", item.bank)
                                    putExtra("date", item.date)
                                    putExtra("balance", item.balance)
                                    putExtra("upi", item.upi)
                                    item.accountIdentifier?.let { putExtra("accountIdentifier", it) }
                                    item.latitude?.let { putExtra("latitude", it) }
                                    item.longitude?.let { putExtra("longitude", it) }
                                    putExtra("timestampMillis", item.timestampMillis)
                                    putExtra("pendingNotificationId", item.id)
                                    item.source?.let { putExtra("source", it) }
                                    item.paymentAttemptId?.let { putExtra("paymentAttemptId", it) }
                                }
                                context.startActivity(intent)
                                onDismiss()
                            },
                            onDismiss = { onDismissItem(item.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PendingNotificationRow(
    item: PendingExpenseNotification,
    onReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReview),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.merchant,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.source == "scan_pay") {
                        Spacer(modifier = Modifier.size(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Scan & Pay",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    text = "₹${item.amount} • ${item.bank}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
