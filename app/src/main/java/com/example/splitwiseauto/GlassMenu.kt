package com.example.splitwiseauto

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * How much of the bottom of a tab page sits under the floating tab bar.
 * Pages scroll their content BEHIND the translucent bar and add this to
 * their bottom contentPadding so the last item can still scroll clear.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Opens just below the anchor, start-aligned, kept on screen. */
private class GlassMenuPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - popupContentSize.width - marginPx)
            .coerceAtLeast(marginPx)
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height - marginPx) below
        else (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(marginPx)
        return IntOffset(x, y)
    }
}

/**
 * Modern replacement for DropdownMenu: a rounded frosted card that springs
 * open from the chip (scale 0.9 → 1 from the top-left corner + fade) and
 * fades out on dismiss. Put it inside the same Box as the anchor chip.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    minWidth: Dp = 220.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (state.currentState || state.targetState) {
        val density = LocalDensity.current
        val provider = remember(density) {
            with(density) { GlassMenuPositionProvider(8.dp.roundToPx(), 12.dp.roundToPx()) }
        }
        val calm = reducedMotion(LocalContext.current)
        val dark = isSystemInDarkTheme()
        val shape = RoundedCornerShape(22.dp)
        val origin = TransformOrigin(0.15f, 0f)
        Popup(
            popupPositionProvider = provider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            AnimatedVisibility(
                visibleState = state,
                enter = if (calm) fadeIn(tween(90)) else
                    fadeIn(tween(140)) + scaleIn(
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
                        initialScale = 0.9f,
                        transformOrigin = origin
                    ),
                exit = if (calm) fadeOut(tween(80)) else
                    fadeOut(tween(110)) + scaleOut(
                        animationSpec = tween(110),
                        targetScale = 0.95f,
                        transformOrigin = origin
                    )
            ) {
                Column(
                    modifier = Modifier
                        .padding(6.dp) // room for the soft shadow
                        .shadow(
                            elevation = 18.dp,
                            shape = shape,
                            ambientColor = Color.Black.copy(alpha = 0.35f),
                            spotColor = Color.Black.copy(alpha = 0.35f)
                        )
                        .clip(shape)
                        .background(
                            Brush.verticalGradient(
                                if (dark) listOf(Color(0xFA222B27), Color(0xFA161D1A))
                                else listOf(Color(0xFCFFFFFF), Color(0xFCF3F7F5))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.verticalGradient(
                                if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))
                                else listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.10f))
                            ),
                            shape
                        )
                        .widthIn(min = minWidth, max = 300.dp)
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    content = content
                )
            }
        }
    }
}

/** Small uppercase caption between groups ("MONTHS"). */
@Composable
fun GlassMenuHeader(text: String) {
    val dark = isSystemInDarkTheme()
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
        fontSize = 10.5.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (dark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.45f)
    )
}

/** Hairline separator with side insets. */
@Composable
fun GlassMenuDivider() {
    val dark = isSystemInDarkTheme()
    Box(
        Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(if (dark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.07f))
    )
}

/**
 * One row: rounded 14dp pill. Selected = accent-tinted pill, bold accent
 * text and a check in a small accent circle. Optional trailing caption
 * (e.g. "This month").
 */
@Composable
fun GlassMenuItem(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    caption: String? = null
) {
    val dark = isSystemInDarkTheme()
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) accent.copy(alpha = if (dark) 0.16f else 0.13f) else Color.Transparent)
    val onSurface = if (dark) Color.White else Color(0xFF0E1613)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = source, indication = androidx.compose.foundation.LocalIndication.current, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else onSurface.copy(alpha = 0.9f),
            maxLines = 1
        )
        if (caption != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                caption,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = onSurface.copy(alpha = 0.45f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = if (dark) Color(0xFF06231A) else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
