package com.example.splitwiseauto

import android.content.Context
import android.os.Build
import android.widget.RemoteViews

/**
 * Home-screen widget themes, chosen by the user (long-press the widget →
 * edit/customise on Android 12+, or from the app). One choice for all
 * Splitlee widgets.
 *
 *  - GREEN: Splitlee's dark green glass (default).
 *  - WALLPAPER: Material You — glass and accents in the wallpaper's colours
 *    (Android 12+; older phones show GREEN).
 *  - HYBRID: Splitlee layout + green money colours, glass tinted with the
 *    wallpaper's main colour (Android 12+; older phones show GREEN).
 *  - CLEAR: Splitlee green but more see-through (~70%).
 * Owed (green) / you owe (amber) / vs-yesterday colours never change, so
 * money always reads the same.
 */
enum class WidgetTheme(val title: String, val subtitle: String) {
    GREEN("Splitlee green", "Dark green glass (default)"),
    WALLPAPER("Match wallpaper", "Material You colours from your wallpaper"),
    HYBRID("Hybrid", "Splitlee look, glass tinted by your wallpaper"),
    CLEAR("Clear glass", "Green, more see-through");

    /** Wallpaper-based themes need Android 12+. */
    val supported: Boolean
        get() = when (this) {
            WALLPAPER, HYBRID -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            else -> true
        }
}

object WidgetThemes {
    private const val PREFS = "widget_theme"
    private const val KEY = "theme"

    fun current(context: Context): WidgetTheme =
        runCatching {
            WidgetTheme.valueOf(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: WidgetTheme.GREEN.name
            )
        }.getOrDefault(WidgetTheme.GREEN)

    fun set(context: Context, theme: WidgetTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.name).apply()
        SplitleeWidget.refreshAll(context)
    }

    /** Applies the chosen theme to one widget layout (any size). */
    fun applyTo(context: Context, views: RemoteViews) {
        val theme = current(context)
        val background = when (theme) {
            WidgetTheme.GREEN -> R.drawable.widget_bg_large
            WidgetTheme.WALLPAPER -> R.drawable.widget_bg_wallpaper
            WidgetTheme.HYBRID -> R.drawable.widget_bg_hybrid
            WidgetTheme.CLEAR -> R.drawable.widget_bg_clear
        }
        views.setInt(R.id.widget_root, "setBackgroundResource", background)

        // Material You. Colours are passed as RESOURCE ids (setColor, API 31),
        // so the launcher resolves them itself and re-resolves them when the
        // wallpaper changes. The old code resolved them here with getColor(),
        // which froze the colour of the moment into the widget: it never
        // followed a new wallpaper until Splitlee happened to update it.
        // (A view a smaller layout doesn't have, e.g. the date line, is
        // simply skipped by RemoteViews.)
        if (theme == WidgetTheme.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(R.id.widget_total, "setTextColor", android.R.color.system_accent1_100)
            views.setColor(R.id.widget_total_label, "setTextColor", android.R.color.system_neutral2_200)
            views.setColor(R.id.widget_date, "setTextColor", android.R.color.system_neutral2_200)
            views.setInt(R.id.widget_scan, "setBackgroundResource", R.drawable.widget_button_bg_you)
            views.setInt(R.id.widget_add, "setBackgroundResource", R.drawable.widget_button_bg_you)
        }
    }
}
