package com.example.splitwiseauto

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * Splitlee's signature total: when the number has changed since it was last
 * shown, it counts smoothly from the old value to the new one (500–900 ms,
 * ease-out), then shows a quiet "+₹170" for a moment. Unchanged = instant.
 *
 * - Remembers the last shown value per [storageKey] (SharedPreferences), so
 *   it survives navigation, process death and restarts.
 * - First ever show / no stored value / reduced motion → final value at once.
 * - Always lands on the exact [target] (rounded to whole rupees, like every
 *   other total in the app).
 * - Indian grouping via [formatRupeeIndian]; tabular figures so digits that
 *   don't change stay perfectly still, and the ₹ never moves.
 * - Only this composable recomposes per frame (~40 frames, then idle).
 *
 * [ready] must be false until [target] is the real value for [storageKey]
 * (avoids animating from a placeholder 0).
 */
@Composable
fun AnimatedRupeeCounter(
    target: Double,
    storageKey: String,
    ready: Boolean,
    style: TextStyle,
    color: Color,
    deltaColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = remember { CounterStore(context) }
    val targetRupees = target.roundToLong()

    // What is on screen right now. Starts at the last value the user saw for
    // this key (or null = never shown → snap to the real value).
    var shown by remember(storageKey) { mutableStateOf(store.lastShown(storageKey)) }
    // While the real total is still loading (cold start), keep showing the
    // last total the user saw instead of a placeholder ₹0.
    val loadingValue = remember { store.lastShownAnywhere() }
    var delta by remember(storageKey) { mutableStateOf<Long?>(null) }

    LaunchedEffect(storageKey, targetRupees, ready) {
        if (!ready) return@LaunchedEffect
        val from = shown
        if (from == null || from == targetRupees || reducedMotion(context)) {
            shown = targetRupees
            store.save(storageKey, targetRupees)
            return@LaunchedEffect
        }
        val change = targetRupees - from
        delta = change
        // Bigger jumps get a little longer, within 500–900 ms.
        val duration = (500 + 90 * log10(abs(change).toDouble() + 1)).toInt().coerceIn(500, 900)
        animate(0f, 1f, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { p, _ ->
            shown = from + (change * p.toDouble()).roundToLong()
        }
        shown = targetRupees // exact landing, whatever the float maths did
        store.save(storageKey, targetRupees)
        delay(2_500)
        delta = null
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        val onScreen = if (!ready) loadingValue ?: targetRupees else shown ?: targetRupees
        Text(
            text = formatRupeeIndian(onScreen.toDouble()),
            // "tnum": every digit the same width, so the number doesn't jiggle
            // while counting and untouched digits stay exactly where they are.
            style = style.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1
        )
        AnimatedVisibility(visible = delta != null, enter = fadeIn(), exit = fadeOut()) {
            val d = delta ?: 0L
            Text(
                text = (if (d >= 0) "+" else "−") + formatRupeeIndian(abs(d).toDouble()),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = deltaColor,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )
        }
    }
}

/** "Remove animations" (Android accessibility) sets the animator scale to 0. */
private fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Last total the user actually saw, per screen/filter context. */
private class CounterStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("expense_counter", Context.MODE_PRIVATE)
    fun lastShown(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    fun lastShownAnywhere(): Long? = lastShown(LAST_ANY)
    fun save(key: String, value: Long) = prefs.edit().putLong(key, value).putLong(LAST_ANY, value).apply()

    private companion object {
        const val LAST_ANY = "__last_shown__"
    }
}
