package com.example.splitwiseauto

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings > App theme. Same layout as the widget theme picker: a title,
 * one card per look with a small preview swatch, light/dark below, Done.
 * Changes apply straight away (the screen restyles itself as you tap).
 */
class AppThemeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this)
        setContent {
            SplitleeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    AppThemePicker(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun AppThemePicker(onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("App theme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "How Splitlee looks inside the app. The widget has its own theme.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        AppThemeMode.values().forEach { mode ->
            AppThemeOption(
                title = mode.label,
                subtitle = if (mode == AppThemeMode.MATERIAL_YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
                    "Calm grey look (wallpaper colours need Android 12+)" else mode.detail,
                swatch = themeSwatch(mode),
                selected = AppThemeStore.mode.value == mode,
                onClick = {
                    Haptics.tap(view)
                    AppThemeStore.set(context, mode)
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "LIGHT OR DARK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        NightMode.values().forEach { option ->
            AppThemeOption(
                title = option.label,
                subtitle = when (option) {
                    NightMode.SYSTEM -> "Follow the phone's setting"
                    NightMode.LIGHT -> "Always light"
                    NightMode.DARK -> "Always dark"
                },
                swatch = when (option) {
                    NightMode.SYSTEM -> Brush.linearGradient(listOf(Color(0xFFF4F6F5), Color(0xFFF4F6F5), Color(0xFF121614), Color(0xFF121614)))
                    NightMode.LIGHT -> Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE9EEEB)))
                    NightMode.DARK -> Brush.linearGradient(listOf(Color(0xFF1A201E), Color(0xFF0B0F0D)))
                },
                selected = AppThemeStore.night.value == option,
                onClick = {
                    Haptics.tap(view)
                    AppThemeStore.setNight(context, option)
                }
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryPill("Done", onDone)
    }
}

/** Preview swatch for a look (Dynamic: the phone's real wallpaper colours). */
@Composable
private fun themeSwatch(mode: AppThemeMode): Brush = when (mode) {
    AppThemeMode.SPLITLEE -> Brush.linearGradient(listOf(Color(0xFF2FD39A), Color(0xFF14A87C), Color(0xFF0E1613)))
    AppThemeMode.MATERIAL_YOU -> {
        val c = materialYouPreviewColor()
        val ctx = LocalContext.current
        val deep = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Color(ctx.getColor(android.R.color.system_neutral2_800)) else Color(0xFF2A2C30)
        Brush.linearGradient(listOf(c, deep))
    }
}

/** Card row, same look as the widget theme picker's options. */
@Composable
private fun AppThemeOption(
    title: String,
    subtitle: String,
    swatch: Brush,
    selected: Boolean,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val accent = Brand.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source)
            .modernCard(
                base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                radius = 20.dp,
                tint = if (selected) accent else null,
                tintAlpha = 0.12f,
                borderColor = if (selected) accent else null
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(swatch)
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) accent else MaterialTheme.colorScheme.outline, CircleShape)
                .padding(5.dp)
                .background(if (selected) accent else Color.Transparent, CircleShape)
        )
    }
}
