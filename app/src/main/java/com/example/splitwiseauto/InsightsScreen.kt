package com.example.splitwiseauto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.splitwiseauto.ui.theme.glassBorderColor
import com.example.splitwiseauto.ui.theme.glassCardColor
import com.example.splitwiseauto.ui.theme.positiveColor
import com.example.splitwiseauto.ui.theme.positiveContainerColor
import java.util.Locale

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = viewModel(),
    modifier: Modifier = Modifier,
    onDataChanged: () -> Unit = {}
) {
    // Tapped "Where it went" / "By location" row -> drill-down sheet.
    var drillTarget by remember { mutableStateOf<Pair<CategorySummary, Boolean>?>(null) }
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val customRange by viewModel.customRange.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val trend by viewModel.spendTrend.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val locationBreakdown by viewModel.locationBreakdown.collectAsState()
    val hasAnyExpenses by viewModel.hasAnyExpenses.collectAsState()

    // Fresh install / no data: never touch the trend chart or breakdown.
    when (hasAnyExpenses) {
        null -> { LoadingStateView(modifier); return }
        false -> {
            EmptyStateView(
                icon = Icons.Filled.Insights,
                title = "No insights yet",
                message = "Add your first expense and your spending trends and categories will show up here.",
                modifier = modifier
            )
            return
        }
        true -> Unit
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onExport: () -> Unit = {
        scope.launch {
            val intent = ExpenseExporter.buildShareIntent(context)
            if (intent == null) {
                Toast.makeText(context, "No expenses to export yet", Toast.LENGTH_SHORT).show()
            } else {
                context.startActivity(Intent.createChooser(intent, "Export expenses (CSV)"))
            }
        }
    }

    // Cards rise in one after another the first time Insights opens.
    var entrancePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(700)
        entrancePlayed = true
    }

    drillTarget?.let { (summary, byLocation) ->
        // Re-derived whenever the data reloads (e.g. after "move category").
        val data = remember(summary, byLocation, trend, categoryBreakdown, locationBreakdown) {
            viewModel.drill(summary, byLocation)
        }
        CategoryDetailSheet(
            data = data,
            onDismiss = { drillTarget = null },
            onChanged = {
                viewModel.refresh()
                onDataChanged()
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        // The Main/bell row is now a real header above the pager (not a
        // floating overlay), so the old 54dp clearance just left an extra gap.
        // Same top gap as every other tab.
        contentPadding = PaddingValues(top = TAB_CONTENT_TOP_GAP, bottom = 18.dp + LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InsightsHeader(
                selectedMonth = selectedMonth,
                customRange = customRange,
                availableMonths = availableMonths,
                onMonthSelected = viewModel::selectMonth,
                onRangeSelected = viewModel::selectRange,
                onExport = onExport
            )
        }

        item {
            Box(Modifier.staggeredEntrance(0, play = !entrancePlayed)) { SpendTrendCard(trend = trend) }
        }

        item {
            Box(Modifier.staggeredEntrance(1, play = !entrancePlayed)) {
                CategoryBreakdownCard(items = categoryBreakdown, onRowClick = { drillTarget = it to false })
            }
        }

        // Same month total split by location — its own list, never mixed
        // into the category rows above (that mix is what summed to 186%).
        if (locationBreakdown.isNotEmpty()) {
            item {
                Box(Modifier.staggeredEntrance(2, play = !entrancePlayed)) {
                    CategoryBreakdownCard(
                        items = locationBreakdown,
                        title = "By location",
                        emptyText = "",
                        onRowClick = { drillTarget = it to true }
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsHeader(
    selectedMonth: MonthKey,
    customRange: DayRange?,
    availableMonths: List<MonthKey>,
    onMonthSelected: (MonthKey) -> Unit,
    onRangeSelected: (DayRange) -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Filled.IosShare,
                    contentDescription = "Export expenses as CSV",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            MonthSelector(
                selectedMonth = selectedMonth,
                customRange = customRange,
                availableMonths = availableMonths,
                onMonthSelected = onMonthSelected,
                onRangeSelected = onRangeSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(
    selectedMonth: MonthKey,
    customRange: DayRange?,
    availableMonths: List<MonthKey>,
    onMonthSelected: (MonthKey) -> Unit,
    onRangeSelected: (DayRange) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showRangeDialog by remember { mutableStateOf(false) }
    if (showRangeDialog) {
        DateRangeDialog(
            initial = customRange,
            onDismiss = { showRangeDialog = false },
            onApply = { onRangeSelected(it); showRangeDialog = false }
        )
    }

    Box {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            onClick = { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = customRange?.label() ?: selectedMonth.shortLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose month",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        GlassMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            minWidth = 210.dp
        ) {
            GlassMenuHeader("Months")
            val accent = if (isSystemInDarkTheme()) Color(0xFF2FD39A) else Color(0xFF14A87C)
            availableMonths.forEach { month ->
                GlassMenuItem(
                    label = month.displayLabel(),
                    caption = if (month == MonthKey.current()) "This month" else null,
                    selected = customRange == null && month == selectedMonth,
                    accent = accent,
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    }
                )
            }
            GlassMenuDivider()
            GlassMenuItem(
                label = customRange?.let { "Custom: ${it.label()}" } ?: "Custom range…",
                selected = customRange != null,
                accent = accent,
                leadingIcon = Icons.Filled.DateRange,
                onClick = {
                    expanded = false
                    showRangeDialog = true
                }
            )
        }
    }
}

@Composable
private fun SpendTrendCard(trend: SpendTrendUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassCardColor()
        ),
        border = BorderStroke(1.dp, glassBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = if (trend.isCurrentMonth) "Spent so far" else "Total spent",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                trend.percentChangeVsPreviousMonth?.let { percent ->
                    PercentChangeBadge(percent = percent, versus = trend.comparisonLabel)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = formatRupees(trend.totalSoFar),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            if (!trend.hasAnyDatedExpenses) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No dated expenses yet — spend trends will appear as expenses come in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {

            Spacer(modifier = Modifier.height(16.dp))

            // Tap, or drag along the line, to see a day's date and amount.
            var selectedDay by remember(trend.dailyPoints, trend.monthLabel) { mutableStateOf<Int?>(null) }
            Text(
                text = selectedDay?.let { i ->
                    "${trend.pointLabels.getOrNull(i) ?: "${i + 1} ${trend.monthShortName}"} · ${formatRupees(trend.dailyPoints.getOrElse(i) { 0.0 })}"
                } ?: "Tap or drag on the chart to see a day",
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedDay != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            SpendTrendChart(
                points = trend.dailyPoints,
                selectedIndex = selectedDay,
                onTap = { selectedDay = if (selectedDay == it) null else it },
                onScrub = { selectedDay = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = when {
                    trend.projectedMonthEnd != null ->
                        "On pace for ~${formatRupees(trend.projectedMonthEnd)} by month end"
                    // No ₹0 "prediction": say why there's no projection.
                    trend.notEnoughToProject -> "Not enough regular spending to project yet"
                    else -> "Total for ${trend.monthLabel}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (trend.excludedTripSpend > 0.0 && trend.excludedTripLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    // Breakdown of the estimate: regular pace + the one-off trip.
                    text = if (trend.projectedMonthEnd != null) {
                        "Regular ${formatRupees(trend.dailyAverage)}/day + " +
                            "${formatRupees(trend.excludedTripSpend)} ${trend.excludedTripLabel}"
                    } else {
                        "Includes ${formatRupees(trend.excludedTripSpend)} ${trend.excludedTripLabel}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SpendStatsRow(trend = trend)
            }
        }
    }
}

@Composable
private fun SpendStatsRow(trend: SpendTrendUiState) {
    // Daily avg excludes trip spending (see InsightsViewModel); biggest day
    // stays a raw fact, trips included.
    val dailyAvg = trend.dailyAverage
    val biggestDay = trend.dailyPoints.maxOrNull() ?: 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SpendStat(
            label = if (trend.excludedTripSpend > 0.0) "Daily avg (excl. trip)" else "Daily avg",
            value = formatRupees(dailyAvg)
        )
        SpendStat(label = "Biggest day", value = formatRupees(biggestDay))
        SpendStat(label = "Txns", value = trend.transactionCount.toString())
    }
}

@Composable
private fun SpendStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PercentChangeBadge(percent: Double, versus: String = "last month") {
    val isIncrease = percent >= 0
    val containerColor = if (isIncrease) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        positiveContainerColor() // matches the "settled up" green used elsewhere in the app
    }
    val contentColor = if (isIncrease) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        positiveColor()
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            // Big jumps read better as a multiple: "3.2× last month", not "+220%".
            text = if (percent >= 200.0) {
                "${"%.1f".format(1 + percent / 100.0).removeSuffix(".0")}× $versus"
            } else {
                "${if (isIncrease) "+" else ""}${"%.0f".format(percent)}% vs $versus"
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Daily spend line.
 *  - Tap or drag (scrub) to pick a day: a dot + guide line glide to it with a
 *    spring, with a light haptic tick per day. Drags are consumed here so the
 *    tab pager doesn't swipe away while scrubbing.
 *  - When the data changes (e.g. switching month) the line morphs from the
 *    old shape to the new one instead of jumping.
 * All animated values are read in the draw phase; the path objects are
 * cached and reused, so frames don't allocate.
 */
@Composable
private fun SpendTrendChart(
    points: List<Double>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onTap: (Int) -> Unit = {},
    onScrub: (Int) -> Unit = {}
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.onSurface
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = LocalContext.current
    val still = remember { reducedMotion(context) }

    // ---- morph between old and new data ----
    val morph = remember { androidx.compose.animation.core.Animatable(1f) }
    var fromNorm by remember { mutableStateOf<FloatArray?>(null) }
    var lastNorm by remember { mutableStateOf<FloatArray?>(null) }
    val norm = remember(points) { normalise(points) }
    LaunchedEffect(norm) {
        val previous = lastNorm
        lastNorm = norm
        if (previous == null || still || previous.contentEquals(norm)) {
            morph.snapTo(1f)
            return@LaunchedEffect
        }
        fromNorm = previous
        morph.snapTo(0f)
        morph.animateTo(1f, androidx.compose.animation.core.tween(320, easing = MotionEaseInOut))
        fromNorm = null
    }

    // ---- selected-day marker glides between days ----
    val markerX = remember { androidx.compose.animation.core.Animatable(0f) }
    val markerAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(selectedIndex) {
        val idx = selectedIndex
        if (idx == null) {
            markerAlpha.animateTo(0f, androidx.compose.animation.core.tween(150))
        } else {
            if (markerAlpha.value == 0f || still) markerX.snapTo(idx.toFloat())
            launch { markerAlpha.animateTo(1f, androidx.compose.animation.core.tween(150)) }
            markerX.animateTo(
                idx.toFloat(),
                androidx.compose.animation.core.spring(
                    dampingRatio = 0.85f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            )
        }
    }

    val count = points.size
    var lastScrub by remember { mutableStateOf(-1) }
    fun indexAt(x: Float, width: Float): Int =
        if (count < 2) 0 else ((x / (width / (count - 1))).roundToInt()).coerceIn(0, count - 1)

    androidx.compose.foundation.layout.Spacer(
        modifier = modifier
            .pointerInput(count) {
                detectTapGestures { offset -> if (count >= 2) onTap(indexAt(offset.x, size.width.toFloat())) }
            }
            .pointerInput(count) {
                if (count < 2) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val i = indexAt(offset.x, size.width.toFloat())
                        lastScrub = i
                        onScrub(i)
                    },
                    onDragEnd = { lastScrub = -1 },
                    onDragCancel = { lastScrub = -1 },
                    onHorizontalDrag = { change, _ ->
                        change.consume() // keep the pager from taking this swipe
                        val i = indexAt(change.position.x, size.width.toFloat())
                        if (i != lastScrub) {
                            lastScrub = i
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onScrub(i)
                        }
                    }
                )
            }
            .drawWithCache {
                val linePath = androidx.compose.ui.graphics.Path()
                val fillPath = androidx.compose.ui.graphics.Path()
                val fillBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height
                )
                val top = 8f
                val usable = size.height - 16f
                onDrawBehind {
                    if (count < 2) return@onDrawBehind
                    val m = morph.value
                    val from = fromNorm
                    linePath.reset()
                    if (from != null && m < 1f) {
                        // Morph: sample both shapes across the width and blend.
                        val samples = 64
                        for (s in 0..samples) {
                            val t = s / samples.toFloat()
                            val y0 = sampleAt(from, t)
                            val y1 = sampleAt(norm, t)
                            val v = y0 + (y1 - y0) * m
                            val x = t * size.width
                            val y = top + (1f - v) * usable
                            if (s == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                        }
                    } else {
                        val step = size.width / (count - 1)
                        for (i in 0 until count) {
                            val x = step * i
                            val y = top + (1f - norm[i]) * usable
                            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                        }
                    }
                    fillPath.reset()
                    fillPath.addPath(linePath)
                    fillPath.lineTo(size.width, size.height)
                    fillPath.lineTo(0f, size.height)
                    fillPath.close()
                    drawPath(fillPath, brush = fillBrush)
                    drawPath(linePath, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))

                    val lastY = top + (1f - norm[count - 1]) * usable
                    drawCircle(color = lineColor, radius = 7f, center = Offset(size.width, lastY))

                    val a = markerAlpha.value
                    if (a > 0f) {
                        val fx = markerX.value.coerceIn(0f, (count - 1).toFloat())
                        val i0 = fx.toInt().coerceAtMost(count - 2)
                        val frac = fx - i0
                        val v = norm[i0] + (norm[i0 + 1] - norm[i0]) * frac
                        val x = fx * (size.width / (count - 1))
                        val y = top + (1f - v) * usable
                        drawLine(
                            color = markerColor.copy(alpha = 0.25f * a),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2f
                        )
                        drawCircle(color = markerColor.copy(alpha = a), radius = 9f, center = Offset(x, y))
                        drawCircle(color = lineColor.copy(alpha = a), radius = 6f, center = Offset(x, y))
                    }
                }
            }
    )
}

/** Values scaled to 0..1 by the series max (min 0), for drawing/morphing. */
private fun normalise(points: List<Double>): FloatArray {
    if (points.isEmpty()) return FloatArray(0)
    val max = (points.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val min = (points.minOrNull() ?: 0.0).coerceAtMost(0.0)
    val range = (max - min).coerceAtLeast(1.0)
    return FloatArray(points.size) { ((points[it] - min) / range).toFloat() }
}

/** Linear sample of a normalised series at horizontal position t (0..1). */
private fun sampleAt(series: FloatArray, t: Float): Float {
    if (series.isEmpty()) return 0f
    if (series.size == 1) return series[0]
    val f = t * (series.size - 1)
    val i = f.toInt().coerceAtMost(series.size - 2)
    val r = f - i
    return series[i] + (series[i + 1] - series[i]) * r
}

@Composable
private fun CategoryBreakdownCard(
    items: List<CategorySummary>,
    title: String = "Where it went",
    emptyText: String = "No expenses in this period yet — the category breakdown will appear as expenses come in.",
    onRowClick: ((CategorySummary) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassCardColor()
        ),
        border = BorderStroke(1.dp, glassBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (items.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))

                items.forEachIndexed { index, summary ->
                    CategoryBreakdownRow(summary = summary, onClick = onRowClick?.let { cb -> { cb(summary) } })
                    if (index != items.lastIndex) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(summary: CategorySummary, onClick: (() -> Unit)? = null) {
    val darkTheme = isSystemInDarkTheme()
    val badgeBackground = summary.category.fgColor.copy(alpha = if (darkTheme) 0.28f else 0.18f)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier
                    .pressScale(source)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(interactionSource = source, indication = null, onClick = onClick)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(badgeBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = summary.category.icon,
                contentDescription = summary.category.name,
                tint = summary.category.fgColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summary.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatRupees(summary.total),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (summary.percent / 100f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(summary.category.fgColor)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${summary.percent}% of this period",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRupees(amount: Double): String =
    formatRupeeIndian(kotlin.math.round(amount))

/**
 * Pick a custom period for Insights: quick presets (last 7 / 30 / 90 days)
 * or any From and To day. Order doesn't matter; at most 366 days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initial: DayRange?,
    onDismiss: () -> Unit,
    onApply: (DayRange) -> Unit
) {
    val context = LocalContext.current
    val today = System.currentTimeMillis()
    var from by remember { mutableStateOf(initial?.startMillis ?: DayRange.lastDays(7).startMillis) }
    var to by remember { mutableStateOf(initial?.endMillis ?: today) }
    val fmt = remember { java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.ENGLISH) }
    val range = DayRange.of(from, to)
    val tooLong = range.dayCount() > 366

    fun pick(current: Long, onPicked: (Long) -> Unit) {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = current }
        android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                onPicked(java.util.Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis)
            },
            c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply { datePicker.maxDate = today }.show()
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Custom range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 days", 30 to "30 days", 90 to "90 days").forEach { (n, label) ->
                        val r = DayRange.lastDays(n)
                        val on = DayRange.of(from, to) == r
                        Surface(
                            onClick = { from = r.startMillis; to = r.endMillis },
                            shape = RoundedCornerShape(50),
                            color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                ModernTextField(
                    value = fmt.format(java.util.Date(minOf(from, to))),
                    onValueChange = {},
                    label = "From",
                    readOnly = true,
                    onClick = { pick(minOf(from, to)) { from = it } }
                )
                ModernTextField(
                    value = fmt.format(java.util.Date(maxOf(from, to))),
                    onValueChange = {},
                    label = "To",
                    readOnly = true,
                    onClick = { pick(maxOf(from, to)) { to = it } }
                )
                Text(
                    if (tooLong) "Pick a range of up to a year." else "${range.dayCount()} days · ${range.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = !tooLong,
                onClick = { onApply(range) }
            ) { Text("Show insights") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
