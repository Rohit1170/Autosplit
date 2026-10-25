package com.example.splitwiseauto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** One month of history with a friend. */
private data class FriendMonth(
    val label: String,          // "Oct"
    val split: Double,          // shares in splits together (theirs when I paid, mine when they paid)
    val settled: Double         // settle-ups that month (either direction)
)

private data class FriendStats(
    val months: List<FriendMonth>,
    val splitCount: Int,
    val totalSplit: Double,
    val totalSettled: Double,
    val topCategories: List<Pair<Category, Double>>,
    val lastSplitMillis: Long?
)

/**
 * Tap a friend: how much you split together month by month (last 6
 * months), how much was settled, top categories and totals. Read-only.
 */
@Composable
fun FriendTrendsSheet(friend: Friend, balance: FriendBalance, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var stats by remember(friend.id) { mutableStateOf<FriendStats?>(null) }

    LaunchedEffect(friend.id) {
        val db = AppDatabase.getDatabase(context)
        val expenses = ExpenseRepository.allExpenses(db.expenseDao())
        val settlements = db.settlementDao().getAll().filter { it.friendId == friend.id }
        stats = computeFriendStats(friend.name, expenses, settlements)
    }

    val accent = Brand.accent()
    val warn = Color(0xFFF0A63C)
    val net = balance.netAmount

    AppSheet(
        title = friend.name,
        subtitle = when {
            net > 0.01 -> "Owes you ${formatRupeeIndian(net)}"
            net < -0.01 -> "You owe ${formatRupeeIndian(-net)}"
            else -> "All settled up"
        },
        onDismiss = onDismiss,
        glass = true
    ) {
        val s = stats
        if (s == null) {
            Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AppSheet
        }
        if (s.splitCount == 0) {
            Text(
                "No splits with ${friend.name} yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            return@AppSheet
        }

        // Totals
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Splits", s.splitCount.toString(), Modifier.weight(1f))
            StatTile("Split together", formatRupeeIndian(s.totalSplit), Modifier.weight(1f))
            StatTile("Settled", formatRupeeIndian(s.totalSettled), Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        Text("Last 6 months", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(accent); Text(" Split", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
            LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)); Text(" Settled", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        MonthBars(s.months, accent, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))

        if (s.topCategories.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("You split most on", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val max = s.topCategories.maxOf { it.second }.coerceAtLeast(0.01)
            s.topCategories.forEach { (cat, amount) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(cat.fgColor),
                        contentAlignment = Alignment.Center
                    ) { Icon(cat.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth((amount / max).toFloat().coerceIn(0.04f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(cat.fgColor)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(formatRupeeIndian(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        s.lastSplitMillis?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                "Last split: ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 16.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Paired bars per month (split, settled), labels underneath. */
@Composable
private fun MonthBars(months: List<FriendMonth>, splitColor: Color, settledColor: Color) {
    val max = months.maxOfOrNull { maxOf(it.split, it.settled) }?.coerceAtLeast(1.0) ?: 1.0
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / months.size
            val barW = (slot * 0.26f).coerceAtMost(18.dp.toPx())
            val gap = 3.dp.toPx()
            val r = CornerRadius(barW / 2f)
            months.forEachIndexed { i, m ->
                val center = slot * i + slot / 2f
                val hSplit = (m.split / max * size.height).toFloat()
                val hSettled = (m.settled / max * size.height).toFloat()
                if (hSplit > 0f) drawRoundRect(
                    splitColor,
                    topLeft = Offset(center - barW - gap / 2f, size.height - hSplit),
                    size = Size(barW, hSplit.coerceAtLeast(barW)),
                    cornerRadius = r
                )
                if (hSettled > 0f) drawRoundRect(
                    settledColor,
                    topLeft = Offset(center + gap / 2f, size.height - hSettled),
                    size = Size(barW, hSettled.coerceAtLeast(barW)),
                    cornerRadius = r
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            months.forEach { m ->
                Text(
                    m.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun computeFriendStats(
    name: String,
    expenses: List<ExpenseWithParticipants>,
    settlements: List<Settlement>
): FriendStats {
    val key = name.trim().lowercase()
    // (time, share, category, amount) of each split with this friend.
    data class Hit(val time: Long?, val share: Double, val category: String, val amount: Double)
    val hits = expenses.mapNotNull { e ->
        val ex = e.expense
        val paidByMe = ex.payer.equals("Me", ignoreCase = true)
        val share = if (paidByMe) {
            e.participants.filter { it.personName.trim().lowercase() == key }.sumOf { it.shareAmount }
        } else if (ex.payer.trim().lowercase() == key) {
            e.participants.filter { it.personName.equals("Me", ignoreCase = true) }.sumOf { it.shareAmount }
        } else 0.0
        if (share > 0.01) Hit(ex.timestampMillis, share, ex.category, ex.amount) else null
    }

    val cal = Calendar.getInstance()
    val months = (5 downTo 0).map { back ->
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -back)
        }
        val start = c.timeInMillis
        c.add(Calendar.MONTH, 1)
        val end = c.timeInMillis
        cal.timeInMillis = start
        val label = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(java.util.Date(start))
        FriendMonth(
            label = label,
            split = hits.filter { it.time != null && it.time in start until end }.sumOf { it.share },
            settled = settlements.filter { it.settledAt in start until end }.sumOf { kotlin.math.abs(it.amount) }
        )
    }

    val top = hits.groupBy { categoryByName(it.category) }
        .map { (cat, list) -> cat to list.sumOf { it.share } }
        .sortedByDescending { it.second }
        .take(3)

    return FriendStats(
        months = months,
        splitCount = hits.size,
        totalSplit = hits.sumOf { it.share },
        totalSettled = settlements.sumOf { kotlin.math.abs(it.amount) },
        topCategories = top,
        lastSplitMillis = hits.mapNotNull { it.time }.maxOrNull()
    )
}
