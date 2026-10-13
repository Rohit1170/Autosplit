package com.example.splitwiseauto

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** Everything the drill-down sheet shows for one tapped category / location. */
data class DrillData(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    /** "October 2026", "1 – 12 Oct", "Today". */
    val periodLabel: String,
    val percent: Int,
    /** Newest first. */
    val items: List<ExpenseWithParticipants>,
    /** Same thing in the comparison period; null = no comparison. */
    val previousTotal: Double?,
    /** "September", "previous 12 days", "yesterday". */
    val previousLabel: String,
    /** Category rows allow "Change category"; location rows don't. */
    val canRecategorize: Boolean = true
)

/**
 * Tap a category (Insights "Where it went", Home's today donut) to see what
 * made it up: total, change vs the previous period, top merchants (tap one
 * to narrow the list), and every expense grouped by day. Tap an expense to
 * open it; long-press to move it to another category.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryDetailSheet(
    data: DrillData,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var merchantFilter by remember(data.title, data.periodLabel) { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<ExpenseWithParticipants?>(null) }

    val total = data.items.sumOf { it.expense.amount }
    val merchants = data.items
        .groupBy { it.expense.merchant.trim().ifEmpty { "Unknown" } }
        .map { (name, list) -> Triple(name, list.sumOf { it.expense.amount }, list.size) }
        .sortedByDescending { it.second }
    val shown = merchantFilter?.let { m -> data.items.filter { it.expense.merchant.trim().ifEmpty { "Unknown" } == m } }
        ?: data.items
    val positive = if (dark) Color(0xFF2FD39A) else Color(0xFF14A87C)
    val warn = Color(0xFFF0A63C)
    val card = if (dark) Color(0xFF1A221F) else Color(0xFFE8EFEA)

    AppSheet(
        title = data.title,
        subtitle = data.periodLabel,
        onDismiss = onDismiss,
        glass = true,
        leading = {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(data.color.copy(alpha = 0.95f), data.color.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center
            ) { Icon(data.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    ) {
        // ---- 1. Hero glass card: amount, two facts, comparison ----
        Column(
            Modifier
                .fillMaxWidth()
                .modernCard(base = card, radius = 22.dp, tint = data.color, tintAlpha = 0.16f)
                .padding(16.dp)
        ) {
            Text(
                formatRupeeIndian(round(total)),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum")
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FactPill("${data.items.size} payment${if (data.items.size == 1) "" else "s"}")
                FactPill("${data.percent}% of spending")
            }
            data.previousTotal?.let { prev ->
                val diff = total - prev
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("vs ${data.previousLabel}", style = MaterialTheme.typography.labelSmall, color = muted)
                        Text(
                            if (prev <= 0.0) "Nothing spent then" else "${formatRupeeIndian(round(prev))} then",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (prev > 0.0 && abs(diff) >= 1.0) {
                        val c = if (diff > 0) warn else positive
                        Text(
                            (if (diff > 0) "▲ " else "▼ ") + formatRupeeIndian(round(abs(diff))),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = c,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(c.copy(alpha = 0.14f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    } else if (prev > 0.0) {
                        Text("Same", color = muted)
                    }
                }
            }
        }

        // ---- 3. Where it went: top places as a ranked list with bars ----
        if (merchants.size > 1) {
            Spacer(Modifier.height(18.dp))
            SectionTitle("Where it went")
            val top = merchants.take(4)
            val max = top.first().second.coerceAtLeast(0.01)
            Column(
                Modifier
                    .fillMaxWidth()
                    .modernCard(base = card, radius = 20.dp)
                    .padding(6.dp)
            ) {
            top.forEach { (name, sum, n) ->
                val on = merchantFilter == name
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) data.color.copy(alpha = 0.16f) else Color.Transparent)
                        .combinedClickable(onClick = {
                            Haptics.tap(view)
                            merchantFilter = if (on) null else name
                        })
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            prettyName(name),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatRupeeIndian(round(sum)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((sum / max).toFloat().coerceIn(0.02f, 1f))
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Brush.horizontalGradient(listOf(data.color.copy(alpha = 0.55f), data.color)))
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$n payment${if (n == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = muted
                        )
                    }
                }
            }
            }
            if (merchants.size > 4) {
                Text(
                    "+${merchants.size - 4} more places below",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(start = 10.dp, top = 2.dp)
                )
            }
        }

        // ---- 4. Payments, grouped by day with a day total ----
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                merchantFilter?.let { "Payments at ${prettyName(it)}" } ?: "All payments",
                modifier = Modifier.weight(1f)
            )
            if (merchantFilter != null) {
                Text(
                    "Show all",
                    color = positive,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(onClick = { Haptics.tap(view); merchantFilter = null })
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        if (shown.isEmpty()) {
            Text("No payments here.", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        shown.groupBy { dayKey(it.expense.timestampMillis) }.forEach { (_, dayItems) ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    dayLabel(dayItems.first().expense.timestampMillis, dayItems.first().expense.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatRupeeIndian(round(dayItems.sumOf { it.expense.amount })),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted
                )
            }
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .modernCard(base = card, radius = 18.dp)
            ) {
                dayItems.forEachIndexed { i, item ->
                    val e = item.expense
                    val name = prettyName(e.merchant.ifBlank { "Unknown" })
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    Haptics.tap(view)
                                    context.startActivity(
                                        Intent(context, ExpenseDetailActivity::class.java).putExtra("expenseId", e.id)
                                    )
                                },
                                onLongClick = if (data.canRecategorize) {
                                    { Haptics.hold(view); moving = item }
                                } else null
                            )
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(data.color.copy(alpha = 0.30f), data.color.copy(alpha = 0.12f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(1).uppercase(),
                                color = data.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            expenseTimeLabel(e.timestampMillis)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = muted)
                            }
                        }
                        Text(
                            formatRupeeIndian(round(e.amount)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (i != dayItems.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 60.dp, end = 12.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        )
                    }
                }
            }
        }
        if (data.canRecategorize && shown.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Tip: press and hold a payment to move it to another category.",
                style = MaterialTheme.typography.labelSmall,
                color = muted.copy(alpha = 0.75f)
            )
        }
    }

    moving?.let { item ->
        MoveCategorySheet(
            current = item.expense.category,
            merchant = item.expense.merchant,
            onDismiss = { moving = null },
            onPick = { newCategory ->
                moving = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(context).expenseDao().updateCategory(item.expense.id, newCategory)
                        // Remember it, so this merchant is filed right next time.
                        MerchantCategorizer.learn(context, item.expense.merchant, newCategory)
                    }
                    onChanged()
                }
            }
        )
    }
}

@Composable
private fun MoveCategorySheet(
    current: String,
    merchant: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val view = LocalView.current
    AppSheet(title = "Move \"${merchant.ifBlank { "Unknown" }}\" to", onDismiss = onDismiss) {
        allCategories().forEach { c ->
            val selected = c.name.equals(current, ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) c.fgColor.copy(alpha = 0.14f) else Color.Transparent)
                    .combinedClickableCompat { Haptics.tap(view); if (!selected) onPick(c.name) else onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c.fgColor),
                    contentAlignment = Alignment.Center
                ) { Icon(c.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(
                    c.name,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                if (selected) Text("Current", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick))

private fun dayKey(millis: Long?): Long {
    if (millis == null) return Long.MIN_VALUE
    val c = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

/** "Today", "Yesterday", "Sat, 11 Oct". */
private fun dayLabel(millis: Long?, fallback: String): String {
    if (millis == null) return fallback
    val today = dayKey(System.currentTimeMillis())
    val day = dayKey(millis)
    val yesterday = Calendar.getInstance().apply { timeInMillis = today; add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
    return when (day) {
        today -> "Today"
        yesterday -> "Yesterday"
        else -> SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(java.util.Date(millis))
    }
}

@Composable
private fun FactPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ROOT),
        fontSize = 11.sp,
        letterSpacing = 0.9.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** "ANTHONY RODRIGU" -> "Anthony Rodrigu", "food" -> "Food"; mixed-case names are left alone. */
private fun prettyName(raw: String): String {
    val t = raw.trim()
    // Mixed case ("HungerBo") is left alone; ALL CAPS and all-lowercase
    // ("food") are turned into Title Case.
    if (t.isEmpty() || (t.any { it.isLowerCase() } && t.any { it.isUpperCase() })) return t
    return t.lowercase(Locale.ROOT).split(' ').joinToString(" ") { w ->
        if (w.length <= 3 && w.all(Char::isLetter) && w in setOf("upi", "atm", "emi")) w.uppercase(Locale.ROOT)
        else w.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
}
