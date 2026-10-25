package com.example.splitwiseauto

import android.appwidget.AppWidgetManager
import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme

/**
 * Widget theme picker. Opens when you edit/customise the widget (long-press
 * on Android 12+) and from inside the app. Picking a theme applies it to all
 * Splitlee widgets right away.
 */
class WidgetThemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this)
        val widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Always OK: backing out still keeps the widget (with its current theme).
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContent {
            SplitleeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WidgetThemePicker(onDone = { finish() })
                }
            }
        }
    }
}

@Composable
private fun WidgetThemePicker(onDone: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(WidgetThemes.current(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Widget theme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Applies to every Splitlee widget on your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        WidgetTheme.values().forEach { theme ->
            ThemeOption(
                theme = theme,
                selected = theme == selected,
                onClick = {
                    if (theme.supported) {
                        selected = theme
                        WidgetThemes.set(context, theme)
                    }
                }
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryPill("Done", onDone)
    }
}

@Composable
private fun ThemeOption(theme: WidgetTheme, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val accent = Brand.accent()
    val swatch: Brush = when (theme) {
        WidgetTheme.GREEN -> Brush.linearGradient(listOf(Color(0xFF1A2B24), Color(0xFF0E1613)))
        WidgetTheme.CLEAR -> Brush.linearGradient(listOf(Color(0x991A2B24), Color(0x660E1613)))
        WidgetTheme.WALLPAPER, WidgetTheme.HYBRID -> {
            val c = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val ctx = LocalContext.current
                Color(ctx.getColor(if (theme == WidgetTheme.WALLPAPER) android.R.color.system_neutral2_800 else android.R.color.system_accent1_800))
            } else Color(0xFF1A2B24)
            Brush.linearGradient(listOf(c, Color(0xFF0E1613)))
        }
    }
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
            .clickable(interactionSource = source, indication = null, enabled = theme.supported, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 40.dp)
                .background(swatch, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (theme.supported) theme.subtitle else "Needs Android 12 or newer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
