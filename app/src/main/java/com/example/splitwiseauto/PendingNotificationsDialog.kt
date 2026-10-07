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
    // Scan & Pay payments that still need attention. Tap opens the payment's
    // status screen (check SMS again / mark as paid); × removes it.
    payments: List<PendingPayment> = emptyList(),
    onDismissPayment: (String) -> Unit = {}
) {
    val context = LocalContext.current

    AppSheet(
        title = "Missed expenses",
        onDismiss = onDismiss,
        trailing = { TextButton(onClick = onDismiss) { Text("Close") } }
    ) {
        if (pending.isEmpty() && payments.isEmpty()) {
            Text(
                text = "Nothing pending — you're all caught up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
            return@AppSheet
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pending.isNotEmpty()) {
                SectionLabel("To split")
                pending.forEach { item ->
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

            if (payments.isNotEmpty()) {
                SectionLabel("Scan & Pay — not confirmed by bank", topPadding = if (pending.isNotEmpty()) 8 else 0)
                payments.forEach { payment ->
                    PaymentAttentionRow(
                        payment = payment,
                        onOpen = {
                            context.startActivity(
                                Intent(context, QrScanActivity::class.java)
                                    .putExtra(QrScanActivity.EXTRA_PAYMENT_ID, payment.id)
                            )
                            onDismiss()
                        },
                        onRemove = { onDismissPayment(payment.id) }
                    )
                }
                Text(
                    text = "Tap one to check your SMS again or mark it as paid. × removes it if no money left your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: Int = 0) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding.dp)
    )
}

@Composable
private fun PaymentAttentionRow(payment: PendingPayment, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Name gets the full width; the status chip sits on the line
                // below instead of squeezing the name ("MASAL GANESH NA…").
                Text(
                    text = payment.payeeName ?: payment.payeeVpa,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatRupeeIndian(payment.amountMinor / 100.0)} · " +
                            java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(payment.initiatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    PaymentStatusChip(payment.statusEnum)
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
                    text = "${formatRupeeIndian(item.amount)} • ${item.bank}",
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
