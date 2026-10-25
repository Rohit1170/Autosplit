package com.example.splitwiseauto

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme

/** Light / dark, picked in Settings > Appearance. */
enum class NightMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** App look, picked in Settings > Appearance. */
enum class AppThemeMode(val label: String, val detail: String) {
    SPLITLEE("Splitlee", "Mint accent and glass cards"),
    MATERIAL_YOU("Dynamic theme", "Colours from your wallpaper, flat and minimal")
}

/**
 * Stored theme choice. Compose state, so every screen restyles the moment it
 * changes. [isMaterial] is safe to read from plain (non-@Composable) code such
 * as Modifier builders.
 */
object AppThemeStore {
    private const val PREFS = "splitlee_theme"
    private const val KEY = "mode"
    private const val KEY_NIGHT = "night"

    val mode = mutableStateOf(AppThemeMode.SPLITLEE)
    val night = mutableStateOf(NightMode.SYSTEM)

    fun setNight(context: Context, value: NightMode) {
        night.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NIGHT, value.name).apply()
    }

    val isMaterial: Boolean get() = mode.value == AppThemeMode.MATERIAL_YOU

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        mode.value = AppThemeMode.values().firstOrNull { it.name == saved } ?: AppThemeMode.SPLITLEE
        val savedNight = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NIGHT, null)
        night.value = NightMode.values().firstOrNull { it.name == savedNight } ?: NightMode.SYSTEM
    }

    fun set(context: Context, value: AppThemeMode) {
        mode.value = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.name).apply()
    }
}

/**
 * Accent colours used across the app. Splitlee: the mint. Material You: the
 * system (wallpaper) primary colour.
 */
object Brand {
    private val MintDark = Color(0xFF2FD39A)
    private val MintLight = Color(0xFF14A87C)

    @Composable
    fun accent(): Color = when {
        AppThemeStore.isMaterial -> MaterialTheme.colorScheme.primary
        isSystemInDarkTheme() -> MintDark
        else -> MintLight
    }

    /** Text/icons on a solid [accent] fill. */
    @Composable
    fun onAccent(): Color =
        if (AppThemeStore.isMaterial) MaterialTheme.colorScheme.onPrimary else Color(0xFF06231A)

    /** Calm card fill used for secondary tiles. */
    @Composable
    fun card2(default: Color): Color =
        if (AppThemeStore.isMaterial) MaterialTheme.colorScheme.surfaceVariant else default

    @Composable
    fun line(default: Color): Color =
        if (AppThemeStore.isMaterial) MaterialTheme.colorScheme.outlineVariant else default

    @Composable
    fun muted(default: Color): Color =
        if (AppThemeStore.isMaterial) MaterialTheme.colorScheme.onSurfaceVariant else default
}

/**
 * Use instead of SplitWiseAutoTheme { }. Splitlee mode: unchanged. Material
 * You: the same typography, with the phone's dynamic colours (Android 12+) or
 * a neutral grey scheme on older phones.
 */
@Composable
fun SplitleeTheme(content: @Composable () -> Unit) {
    // Light / Dark override: every isSystemInDarkTheme() in the app reads the
    // configuration's night flag, so swapping it here switches them all.
    ProvideNightMode { SplitleeThemeInner(content) }
}

/**
 * Applies Settings > Light/Dark to [content]. Popups and dialogs get a fresh
 * configuration from the phone (the system mode), so menus and sheets must
 * wrap their content in this too, or they'd show the wrong mode.
 */
@Composable
fun ProvideNightMode(content: @Composable () -> Unit) {
    val night = AppThemeStore.night.value
    if (night == NightMode.SYSTEM) {
        content()
    } else {
        val base = androidx.compose.ui.platform.LocalConfiguration.current
        val cfg = android.content.res.Configuration(base).apply {
            uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night == NightMode.DARK) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalConfiguration provides cfg,
            content = content
        )
    }
}

/** Material You primary for the current light/dark mode (Settings preview). */
@Composable
fun materialYouPreviewColor(): Color {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primaryContainer
    } else if (dark) Color(0xFF3A3D42) else Color(0xFFE0E2E6)
}

@Composable
private fun SplitleeThemeInner(content: @Composable () -> Unit) {
    SplitWiseAutoTheme {
        if (!AppThemeStore.isMaterial) {
            content()
        } else {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val scheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme(
                    primary = Color(0xFFC4C7CC), onPrimary = Color(0xFF1B1D20),
                    primaryContainer = Color(0xFF3A3D42), onPrimaryContainer = Color(0xFFE2E4E8),
                    background = Color(0xFF121314), surface = Color(0xFF121314),
                    surfaceVariant = Color(0xFF1F2124), onSurfaceVariant = Color(0xFFB4B7BC)
                )
                else -> lightColorScheme(
                    primary = Color(0xFF3B3F45), onPrimary = Color.White,
                    primaryContainer = Color(0xFFE0E2E6), onPrimaryContainer = Color(0xFF1B1D20),
                    background = Color(0xFFFAFAFB), surface = Color(0xFFFAFAFB),
                    surfaceVariant = Color(0xFFEDEEF0), onSurfaceVariant = Color(0xFF55595F)
                )
            }
            MaterialTheme(
                colorScheme = scheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes,
                content = content
            )
        }
    }
}
