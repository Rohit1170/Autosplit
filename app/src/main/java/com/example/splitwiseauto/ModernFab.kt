package com.example.splitwiseauto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Round floating buttons for Home / Friends.
 *  - primary: mint gradient disc with a soft green glow (Add expense).
 *  - secondary: smaller frosted disc with a hairline rim (Scan & Pay).
 * Springy press via [pressScale].
 */
@Composable
fun ModernFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean = true,
    diameter: Dp = if (primary) 60.dp else 48.dp,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val source = remember { MutableInteractionSource() }
    val material = AppThemeStore.isMaterial
    val glow = if (material) Color.Black else Color(0xFF2FD39A)
    val primaryFill = MaterialTheme.colorScheme.primaryContainer
    val primaryInk = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryFill = MaterialTheme.colorScheme.surfaceVariant
    val base = modifier
        .pressScale(source)
        .size(diameter)
        .shadow(
            elevation = if (primary) 14.dp else 8.dp,
            shape = CircleShape,
            ambientColor = if (primary) glow else Color.Black,
            spotColor = if (primary) glow else Color.Black
        )
        .clip(CircleShape)
    val styled = if (primary) {
        if (material) base.background(primaryFill)
        else base
            .background(Brush.linearGradient(listOf(Color(0xFF5BF0C0), Color(0xFF14B88A))))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
    } else if (material) {
        base.background(secondaryFill)
    } else {
        base
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color(0xF2263330), Color(0xF2141B18))
                    else listOf(Color(0xFFFFFFFF), Color(0xFFEAF2EE))
                )
            )
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
                CircleShape
            )
    }
    Box(
        modifier = styled.clickable(
            interactionSource = source,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = when {
                material && primary -> primaryInk
                material -> Brand.accent()
                primary -> Color(0xFF06231A)
                dark -> Color(0xFF2FD39A)
                else -> Color(0xFF14A87C)
            },
            modifier = Modifier.size(if (primary) 28.dp else 22.dp)
        )
    }
}
