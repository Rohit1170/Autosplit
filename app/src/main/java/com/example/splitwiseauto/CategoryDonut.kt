package com.example.splitwiseauto

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.round

/**
 * "Expenses by category" card: a donut (share % printed on each slice big
 * enough to hold it, total in the middle) beside a legend of icon, name,
 * share and amount. The ring sweeps in on first show and when the data
 * changes (skipped when the system turns animations off).
 */
/** Process-wide: the donut's draw-in plays once per app launch. */
private var donutSweepPlayed = false

@Composable
fun CategoryDonutCard(
    items: List<CategorySummary>,
    periodLabel: String,
    modifier: Modifier = Modifier,
    title: String = "Expenses by category",
    emptyText: String? = null,
    onItemClick: ((CategorySummary) -> Unit)? = null
) {
    if (items.isEmpty()) {
        if (emptyText != null) EmptyDonutCard(title, emptyText, modifier)
        return
    }
    val dark = isSystemInDarkTheme()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = onSurface.copy(alpha = 0.6f)
    val total = items.sumOf { it.total }.coerceAtLeast(0.01)
    var expanded by remember { mutableStateOf(false) }
    val calm = reducedMotion(LocalContext.current)

    // The ring draws in once per app session (500 ms ease-out). Switching
    // month does NOT re-sweep: this is money data people read, and hiding it
    // behind a replayed animation every time would be motion for style.
    val playSweep = !calm && !donutSweepPlayed
    val sweep = remember { Animatable(if (playSweep) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (sweep.value < 1f) {
            sweep.animateTo(1f, tween(500, easing = MotionEaseOut))
        }
        donutSweepPlayed = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .modernCard(base = if (dark) Color(0xFF151C19) else Color(0xFFF4F8F6), radius = 22.dp)
            .padding(16.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onSurface
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(148.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier
                    .size(148.dp)
                    .then(if (onItemClick == null) Modifier else Modifier.pointerInput(items) {
                        detectTapGestures { p ->
                            // Which slice was tapped: angle from 12 o'clock, clockwise.
                            val dx = p.x - size.width / 2f
                            val dy = p.y - size.height / 2f
                            var deg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                            if (deg < 0) deg += 360f
                            var acc = 0f
                            val sum = items.sumOf { it.total }.coerceAtLeast(0.01)
                            items.firstOrNull { item ->
                                acc += (item.total / sum * 360.0).toFloat()
                                deg <= acc
                            }?.let(onItemClick)
                        }
                    })) {
                    // Modern ring: thin stroke, rounded ends, clear gaps
                    // between segments, no labels on the ring itself.
                    val stroke = size.minDimension * 0.12f
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    val radius = (size.minDimension - stroke) / 2
                    // Round caps stick out by half the stroke at each end, so
                    // the visible gap = cap allowance + a real gap.
                    val capDeg = Math.toDegrees((stroke / 2 / radius).toDouble()).toFloat()
                    val gapDeg = if (items.size > 1) 2 * capDeg + 4f else 0f
                    val progress = sweep.value
                    if (items.size == 1) {
                        drawArc(
                            items.first().category.fgColor, -90f, 360f * progress, false, topLeft, arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    } else {
                        // Every slice gets at least room for its own dot + gap,
                        // so a ₹100 slice never overlaps its neighbour; the
                        // rest share what's left in proportion.
                        val minSlot = gapDeg + 2f
                        val raw = items.map { (it.total / total * 360.0).toFloat() }
                        val small = raw.count { it < minSlot }
                        val bigSum = raw.filter { it >= minSlot }.sum().coerceAtLeast(0.01f)
                        val scale = (360f - small * minSlot) / bigSum
                        val slots = raw.map { if (it < minSlot) minSlot else it * scale }
                        var start = -90f
                        items.forEachIndexed { index, item ->
                            val full = slots[index] * progress
                            // Tiny slices still show as a dot, never vanish.
                            val drawn = (full - gapDeg).coerceAtLeast(0.01f)
                            drawArc(
                                color = item.category.fgColor,
                                startAngle = start + gapDeg / 2,
                                sweepAngle = drawn,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                            start += full
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatRupeeIndian(round(items.sumOf { it.total })),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = onSurface,
                        maxLines = 1
                    )
                    Text("Total spent", fontSize = 11.sp, color = muted)
                    Text(periodLabel, fontSize = 10.5.sp, color = muted.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val visible = if (expanded) items else items.take(6)
                visible.forEach { item -> LegendRow(item, onSurface, muted, onItemClick) }
                if (items.size > 6) {
                    Text(
                        if (expanded) "Show less" else "+${items.size - 6} more",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dark) Color(0xFF2FD39A) else Color(0xFF14A87C),
                        modifier = Modifier
                            .padding(top = 4.dp, start = 36.dp)
                            .clickable { expanded = !expanded }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(
    item: CategorySummary,
    onSurface: Color,
    muted: Color,
    onClick: ((CategorySummary) -> Unit)? = null
) {
    val view = androidx.compose.ui.platform.LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable { Haptics.tap(view); onClick(item) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(item.category.fgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.category.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            item.category.name,
            fontSize = 13.sp,
            color = onSurface.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("${item.percent}%", fontSize = 11.sp, color = muted, modifier = Modifier.padding(horizontal = 6.dp))
        Text(
            formatRupeeIndian(round(item.total)),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurface,
            maxLines = 1
        )
    }
}

/** Same card shell with a calm empty message ("Nothing spent today"). */
@Composable
private fun EmptyDonutCard(title: String, text: String, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .modernCard(base = if (dark) Color(0xFF151C19) else Color(0xFFF4F8F6), radius = 22.dp)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = onSurface)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(44.dp)) {
                val stroke = size.minDimension * 0.12f
                drawArc(
                    onSurface.copy(alpha = 0.10f), 0f, 360f, false,
                    Offset(stroke / 2, stroke / 2),
                    Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 13.sp, color = onSurface.copy(alpha = 0.6f))
        }
    }
}
