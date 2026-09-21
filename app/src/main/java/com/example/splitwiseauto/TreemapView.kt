package com.example.splitwiseauto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Dynamic category-card mosaic. Compact tiles show icons instead of broken labels. */
@Composable
fun Treemap(
    items: List<CategorySummary>,
    modifier: Modifier = Modifier,
    horizontalSplit: Boolean = true
) {
    if (items.isEmpty()) return

    val primary = items.first()
    val secondary = items.drop(1)
    val tallTiles = secondary.take(2)
    val compactTiles = secondary.drop(2).take(3)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (secondary.isEmpty()) {
            CategoryTile(primary, Modifier.fillMaxWidth().height(130.dp), showDetails = true)
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                CategoryTile(
                    summary = primary,
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    showDetails = true
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                    tallTiles.forEachIndexed { index, summary ->
                        CategoryTile(
                            summary = summary,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            showDetails = true
                        )
                        if (index != tallTiles.lastIndex) Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (tallTiles.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (compactTiles.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().height(78.dp)) {
                compactTiles.forEachIndexed { index, summary ->
                    CategoryTile(
                        summary = summary,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showDetails = false
                    )
                    if (index != compactTiles.lastIndex) Spacer(modifier = Modifier.width(10.dp))
                }
                repeat(3 - compactTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    summary: CategorySummary,
    modifier: Modifier = Modifier,
    showDetails: Boolean
) {
    val darkTheme = isSystemInDarkTheme()
    val tileBackground = if (darkTheme) {
        summary.category.fgColor.copy(alpha = 0.22f)
    } else {
        summary.category.bgColor
    }
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val detailTextColor = if (darkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    } else {
        summary.category.fgColor
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = tileBackground,
            contentColor = primaryTextColor
        ),
        border = BorderStroke(
            width = 1.dp,
            color = summary.category.fgColor.copy(alpha = if (darkTheme) 0.32f else 0.18f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints {
            val compactTile = maxWidth < 118.dp || maxHeight < 96.dp
            val showFullDetails = showDetails && !compactTile

            Column(
                modifier = Modifier.padding(if (compactTile) 10.dp else 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(if (compactTile) 28.dp else 34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(summary.category.fgColor.copy(alpha = if (darkTheme) 0.28f else 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = summary.category.icon,
                        contentDescription = summary.category.name,
                        tint = summary.category.fgColor,
                        modifier = Modifier.size(if (compactTile) 17.dp else 20.dp)
                    )
                }
                // Every tile — full-detail or compact — shows the same
                // "amount · percent" combo, Indian-grouped, so a tile that
                // measures too narrow to also fit the category name (the
                // tall/compact tiles below the primary one) never drops down
                // to a bare, ungrouped number or a percent-only label.
                if (showFullDetails) {
                    Text(
                        text = summary.category.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        color = primaryTextColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "${formatRupeeIndian(summary.total)} · ${summary.percent}%",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = detailTextColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
