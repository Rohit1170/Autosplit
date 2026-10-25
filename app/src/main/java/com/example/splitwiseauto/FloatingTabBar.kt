package com.example.splitwiseauto

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class TabSpec(val label: String, val icon: ImageVector)

/**
 * Floating glass tab bar (iOS-style): a rounded pill floating above the
 * bottom edge, with a lighter capsule behind the selected tab. The capsule
 * follows the pager's live position, so it glides with your finger while
 * you swipe between tabs and slides across when you tap one. Position is
 * read at placement time only — no recomposition per frame.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingTabBar(
    tabs: List<TabSpec>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit
) {
    val shape = RoundedCornerShape(34.dp)
    val dark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background
    val material = AppThemeStore.isMaterial
    val flatBar = MaterialTheme.colorScheme.surfaceVariant
    val capsule = MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Pages scroll BEHIND the bar now, so fade them out softly under
            // it instead of drawing a solid block.
            .background(
                Brush.verticalGradient(
                    0f to bg.copy(alpha = 0f),
                    0.55f to bg.copy(alpha = 0.55f),
                    1f to bg.copy(alpha = 0.85f)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.18f)
                )
                .clip(shape)
                // Translucent frosted tint: content shows through.
                .background(
                    Brush.verticalGradient(
                        when {
                            material -> listOf(flatBar, flatBar)
                            dark -> listOf(Color(0x8C2A3530), Color(0xA6121915))
                            else -> listOf(Color(0xB3FFFFFF), Color(0xC7F1F5F3))
                        }
                    )
                )
                // Glass sheen on the upper half.
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (material) 0f else if (dark) 0.07f else 0.35f),
                        0.5f to Color.Transparent
                    )
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        when {
                            material -> listOf(Color.Transparent, Color.Transparent)
                            dark -> listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))
                            else -> listOf(Color.White.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.06f))
                        }
                    ),
                    shape
                )
        ) {
            val itemWidth = maxWidth / tabs.size
            // Sliding selection capsule.
            Box(
                modifier = Modifier
                    .offset {
                        val pos = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, (tabs.size - 1).toFloat())
                        IntOffset((itemWidth.toPx() * pos).roundToInt(), 0)
                    }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(
                        Brush.verticalGradient(
                            when {
                                material -> listOf(capsule, capsule)
                                dark -> listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f))
                                else -> listOf(Color(0xFF14A87C).copy(alpha = 0.16f), Color(0xFF14A87C).copy(alpha = 0.10f))
                            }
                        )
                    )
                    .border(
                        1.dp,
                        when {
                            material -> Color.Transparent
                            dark -> Color.White.copy(alpha = 0.12f)
                            else -> Color(0xFF14A87C).copy(alpha = 0.18f)
                        },
                        RoundedCornerShape(29.dp)
                    )
            )
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                tabs.forEachIndexed { index, tab ->
                    TabBarItem(
                        tab = tab,
                        selected = pagerState.targetPage == index,
                        onClick = { onSelect(index) },
                        modifier = Modifier.width(itemWidth)
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBarItem(
    tab: TabSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val dark = isSystemInDarkTheme()
    val material = AppThemeStore.isMaterial
    val on = if (material) MaterialTheme.colorScheme.onSurface else if (dark) Color.White else Color(0xFF0E1613)
    val selectedMaterial = MaterialTheme.colorScheme.onSecondaryContainer
    val tint by animateColorAsState(
        when {
            selected && material -> selectedMaterial
            selected && !dark -> Color(0xFF0F7F5E)
            selected -> on
            else -> on.copy(alpha = if (dark) 0.6f else 0.55f)
        }
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .pressScale(source)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
