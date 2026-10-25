package com.example.splitwiseauto

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Danger = Color(0xFFFF6B6B)
private val Accent: Color @Composable get() = Brand.accent()

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

/**
 * Red action row that only fires after a long hold. While the finger is
 * down the row fills with red from left to right like a timer, and the
 * phone buzzes in pulses that get stronger and closer together. Letting go
 * early drains the fill back and nothing happens. A quick tap does nothing.
 */
@Composable
internal fun HoldToConfirmRow(
    icon: ImageVector,
    label: String,
    holdingLabel: String,
    hint: String,
    holdingHint: String,
    onConfirmed: () -> Unit,
    holdMillis: Int = 1500
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 16.dp)
            .clip(shape)
            .drawBehind {
                drawRect(Danger.copy(alpha = 0.38f), size = Size(size.width * progress.value, size.height))
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (done) return@detectTapGestures
                    holding = true
                    val fill = scope.launch {
                        val buzz = launch { HoldHaptics.ramp(context) { progress.value } }
                        val left = ((1f - progress.value) * holdMillis).toInt().coerceAtLeast(1)
                        progress.animateTo(1f, tween(left, easing = LinearEasing))
                        buzz.cancel()
                        done = true
                        HoldHaptics.finish(context)
                        onConfirmed()
                    }
                    tryAwaitRelease()
                    holding = false
                    if (!done) {
                        fill.cancel()
                        HoldHaptics.stop(context)
                        scope.launch { progress.animateTo(0f, tween(250)) }
                    }
                })
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Danger.copy(alpha = 0.14f + 0.5f * progress.value), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (progress.value > 0.6f) Color.White else Danger, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(if (holding) holdingLabel else label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Danger)
            Text(
                if (holding) holdingHint else hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Vibration for [HoldToConfirmRow]. Pulses get longer, stronger and closer
 * together as the hold goes on, then one firm buzz when it completes.
 * Phones without amplitude control still feel the ramp through the
 * longer, faster pulses.
 */
internal object HoldHaptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun pulse(v: Vibrator, millis: Long, strength: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val amp = if (v.hasAmplitudeControl()) strength.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(millis, amp))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(millis)
            }
        } catch (e: Exception) {
            // Missing permission or a vendor quirk: holding still works, just silently.
        }
    }

    /** Runs until cancelled. [progress] is 0..1. */
    suspend fun ramp(context: Context, progress: () -> Float) {
        val v = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        while (true) {
            val p = progress().coerceIn(0f, 1f)
            pulse(v, millis = (10 + 25 * p).toLong(), strength = (30 + 225 * p).toInt())
            delay((150 - 110 * p).toLong())
        }
    }

    fun finish(context: Context) {
        val v = vibrator(context)?.takeIf { it.hasVibrator() } ?: return
        pulse(v, millis = 70, strength = 255)
    }

    fun stop(context: Context) {
        try { vibrator(context)?.cancel() } catch (e: Exception) { }
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
                Brush.horizontalGradient(listOf(Accent.copy(alpha = 0.85f), Accent)),
                RoundedCornerShape(27.dp)
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Brand.onAccent())
    }
}

/** Expense long-press menu: header card + View / Edit / Delete (hold to delete). */
@Composable
fun ExpenseActionsSheet(
    item: ExpenseWithParticipants,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    // Shown for trip expenses: take this one out of the trip.
    onMoveToMain: (() -> Unit)? = null
) {
    val e = item.expense
    val category = categoryByName(e.category)

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
            if (onMoveToMain != null) {
                ActionRow(Icons.Filled.Home, "Move to Main", onMoveToMain, hint = "Take it out of this trip")
            }
            HoldToConfirmRow(
                icon = Icons.Filled.Delete,
                label = "Hold to delete",
                holdingLabel = "Keep holding…",
                hint = "This can't be undone",
                holdingHint = "Let go to cancel",
                onConfirmed = onDelete
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
    onDismiss: () -> Unit,
    onTrends: (() -> Unit)? = null
) {
    val net = balance.netAmount
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
            if (onTrends != null) {
                ActionRow(Icons.Filled.BarChart, "Trends", onTrends, hint = "Monthly splits, settle-ups, top categories")
            }
            ActionRow(Icons.Filled.History, "Settlement history", onHistory)
            ActionRow(
                Icons.Filled.Edit, "Edit name & number", onRename,
                hint = PhoneNumbers.display(friend.phone) ?: "Add a number for WhatsApp / SMS reminders"
            )
            HoldToConfirmRow(
                icon = Icons.Filled.PersonRemove,
                label = "Hold to remove friend",
                holdingLabel = "Keep holding…",
                hint = "Their settle-ups are deleted too",
                holdingHint = "Let go to cancel",
                onConfirmed = onRemove
            )
        }
    }
}
