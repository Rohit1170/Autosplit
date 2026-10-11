package com.example.splitwiseauto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val Danger = Color(0xFFFF6B6B)
private val Accent = Color(0xFF2FD39A)

/**
 * One tappable row in an action sheet: icon chip, label, optional hint,
 * chevron. Springy press feedback. [danger] rows are red.
 */
@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    hint: String? = null,
    danger: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val tint = if (danger) Danger else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 16.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background((if (danger) Danger else Accent).copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) Danger else Accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tint)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!danger) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/** Big filled primary action (e.g. "Settle up") with a soft gradient. */
@Composable
internal fun PrimaryPill(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(source)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF14A87C), Accent)),
                RoundedCornerShape(27.dp)
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF06231A))
    }
}

/** Expense long-press menu: header card + View / Edit / Delete (tap twice). */
@Composable
fun ExpenseActionsSheet(
    item: ExpenseWithParticipants,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val e = item.expense
    val category = categoryByName(e.category)
    var confirmDelete by remember(e.id) { mutableStateOf(false) }

    AppSheet(title = "Expense", onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), radius = 20.dp, tint = category.fgColor, tintAlpha = 0.12f)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(category.fgColor.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(category.icon, contentDescription = null, tint = category.fgColor) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${expenseDateTimeLabel(e.date, e.timestampMillis)} · ${category.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(formatRupeeIndian(e.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionRow(Icons.Filled.Visibility, "View details", onView)
            ActionRow(Icons.Filled.Edit, "Edit", onEdit, hint = "Amount, category, split")
            ActionRow(
                Icons.Filled.Delete,
                if (confirmDelete) "Tap again to delete" else "Delete expense",
                onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                hint = if (confirmDelete) "This can't be undone" else null,
                danger = true
            )
        }
    }
}

/** Friend long-press menu: balance header, Settle up, History, Rename, Remove. */
@Composable
fun FriendActionsSheet(
    friend: Friend,
    balance: FriendBalance,
    onSettle: () -> Unit,
    onHistory: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val net = balance.netAmount
    var confirmRemove by remember(friend.id) { mutableStateOf(false) }
    AppSheet(title = friend.name, onDismiss = onDismiss) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .modernCard(
                    base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    radius = 20.dp,
                    tint = if (net >= 0) Accent else Color(0xFFF0A63C),
                    tintAlpha = 0.12f
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(Accent.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(friend.name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Accent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        abs(net) <= 0.01 -> "All settled up"
                        net > 0 -> "${friend.name} owes you"
                        else -> "You owe ${friend.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (abs(net) > 0.01) {
                    Text(
                        formatRupeeIndian(abs(net)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (net > 0) Accent else Color(0xFFF0A63C)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (abs(net) > 0.01) {
            PrimaryPill("Settle up", onSettle)
            Spacer(Modifier.height(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionRow(Icons.Filled.History, "Settlement history", onHistory)
            ActionRow(Icons.Filled.Edit, "Rename", onRename)
            ActionRow(
                Icons.Filled.PersonRemove,
                if (confirmRemove) "Tap again to remove" else "Remove friend",
                onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
                hint = if (confirmRemove) "Their settle-ups are deleted too" else null,
                danger = true
            )
        }
    }
}
