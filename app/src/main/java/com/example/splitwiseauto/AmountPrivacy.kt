package com.example.splitwiseauto

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * "Hide amounts" (Settings). When on, every amount shown through
 * [formatRupeeIndian] reads "₹••••" — Home, cards, lists, charts, friends and
 * the widget. Typing in amount fields still shows what you type, and Scan &
 * Pay / notifications keep real amounts ([formatRupeeIndianRaw]).
 *
 * Backed by Compose state, so screens update the moment it's toggled.
 */
object AmountPrivacy {
    private const val PREFS = "splitlee_privacy"
    private const val KEY_HIDE = "hide_amounts"
    const val MASK = "₹••••"

    val hidden = mutableStateOf(false)

    fun load(context: Context) {
        hidden.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE, false)
    }

    fun set(context: Context, hide: Boolean) {
        hidden.value = hide
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE, hide).apply()
        // The widget shows amounts too; redraw it.
        runCatching { SplitleeWidget.refreshAll(context) }
    }
}

/** Loads app-wide settings once per process (before any screen or widget). */
class SplitleeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AmountPrivacy.load(this)
        AppThemeStore.load(this)
    }
}
