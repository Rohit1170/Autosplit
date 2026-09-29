package com.example.splitwiseauto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    modifier: Modifier = Modifier
) {
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val trend by viewModel.spendTrend.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        // The Main/bell row is now a real header above the pager (not a
        // floating overlay), so the old 54dp clearance just left an extra gap.
        // Same top gap as every other tab.
        contentPadding = PaddingValues(top = TAB_CONTENT_TOP_GAP, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            InsightsHeader(
                selectedMonth = selectedMonth,
                availableMonths = availableMonths,
                onMonthSelected = viewModel::selectMonth,
                onExport = onExport
            )
        }

        item {
            SpendTrendCard(trend = trend)
        }

        item {
            CategoryBreakdownCard(items = categoryBreakdown)
        }
    }
}

@Composable
private fun InsightsHeader(
    selectedMonth: MonthKey,
    availableMonths: List<MonthKey>,
    onMonthSelected: (MonthKey) -> Unit,
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
                availableMonths = availableMonths,
                onMonthSelected = onMonthSelected
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(
    selectedMonth: MonthKey,
    availableMonths: List<MonthKey>,
    onMonthSelected: (MonthKey) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

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
                    text = selectedMonth.shortLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Choose month",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableMonths.forEach { month ->
                DropdownMenuItem(
                    text = { Text(month.displayLabel()) },
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    }
                )
            }
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
                    PercentChangeBadge(percent = percent)
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
                return@Column
            }

            Spacer(modifier = Modifier.height(16.dp))

            SpendTrendChart(
                points = trend.dailyPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = trend.projectedMonthEnd?.let { projected ->
                    "On pace for ~${formatRupees(projected)} by month end"
                } ?: "Total for ${trend.monthLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            SpendStatsRow(trend = trend)
        }
    }
}

@Composable
private fun SpendStatsRow(trend: SpendTrendUiState) {
    val dailyAvg = if (trend.dailyPoints.isNotEmpty()) trend.totalSoFar / trend.dailyPoints.size else 0.0
    val biggestDay = trend.dailyPoints.maxOrNull() ?: 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SpendStat(label = "Daily avg", value = formatRupees(dailyAvg))
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
private fun PercentChangeBadge(percent: Double) {
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
            text = "${if (isIncrease) "+" else ""}${"%.0f".format(percent)}% vs last month",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SpendTrendChart(
    points: List<Double>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val maxValue = (points.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val minValue = (points.minOrNull() ?: 0.0).coerceAtMost(0.0)
        val range = (maxValue - minValue).coerceAtLeast(1.0)

        val stepX = size.width / (points.size - 1)
        val topPadding = 8f
        val bottomPadding = 8f
        val usableHeight = size.height - topPadding - bottomPadding

        fun yFor(value: Double): Float {
            val normalized = (value - minValue) / range
            return (topPadding + (1 - normalized) * usableHeight).toFloat()
        }

        val linePath = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, value ->
            val x = stepX * index
            val y = yFor(value)
            if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
        }

        // Filled area beneath the line, fading out toward the bottom —
        // the line itself is drawn separately afterward so it stays crisp.
        val lastX = stepX * (points.size - 1)
        val fillPath = androidx.compose.ui.graphics.Path().apply {
            addPath(linePath)
            lineTo(lastX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)),
                startY = 0f,
                endY = size.height
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )

        val lastY = yFor(points.last())
        drawCircle(color = lineColor, radius = 7f, center = Offset(lastX, lastY))
    }
}

@Composable
private fun CategoryBreakdownCard(items: List<CategorySummary>) {
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
                text = "Where it went",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (items.isEmpty()) {
                Text(
                    text = "No expenses yet this month — category breakdown will appear as expenses come in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))

                items.forEachIndexed { index, summary ->
                    CategoryBreakdownRow(summary = summary)
                    if (index != items.lastIndex) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(summary: CategorySummary) {
    val darkTheme = isSystemInDarkTheme()
    val badgeBackground = summary.category.fgColor.copy(alpha = if (darkTheme) 0.28f else 0.18f)

    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = "${summary.percent}% of this month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRupees(amount: Double): String =
    "₹" + String.format(Locale.US, "%,.0f", amount)
