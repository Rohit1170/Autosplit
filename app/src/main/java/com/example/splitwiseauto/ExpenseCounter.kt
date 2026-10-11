package com.example.splitwiseauto

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
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
 * shown, the digits that changed roll like an odometer (550–850 ms,
 * ease-out; see RollingText), then a quiet "+₹170" shows for a moment.
 * Unchanged = instant. The roll animates in the draw phase only.
 *
 * - Remembers the last shown value per [storageKey] (SharedPreferences), so
 *   it survives navigation, process death and restarts.
 * - First ever show / no stored value / reduced motion → final value at once.
 * - Always lands on the exact [target] (rounded to whole rupees, like every
 *   other total in the app).
 * - Indian grouping via [formatRupeeIndian]; tabular figures so digits that
 *   don't change stay perfectly still, and the ₹ never moves.
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

    // What's on screen. NOT keyed by storageKey: switching month/filter spins
    // from the number you were looking at to the new one.
    var displayed by remember { mutableStateOf<Long?>(null) }
    var lastKey by remember { mutableStateOf<String?>(null) }
    // While the real total is still loading (cold start), keep showing the
    // last total the user saw instead of a placeholder ₹0.
    val loadingValue = remember { store.lastShownAnywhere() }
    var delta by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(storageKey, targetRupees, ready) {
        if (!ready) return@LaunchedEffect
        val viewChanged = lastKey != null && lastKey != storageKey
        val from = displayed ?: store.lastShown(storageKey) ?: loadingValue
        lastKey = storageKey
        store.save(storageKey, targetRupees)
        displayed = targetRupees
        // "+₹170" only for real changes in the same view, not month switches.
        if (from != null && from != targetRupees && !viewChanged && !reducedMotion(context)) {
            delta = targetRupees - from
            delay(2_400)
            delta = null
        } else {
            delta = null
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        // "tnum": every digit the same width, so the reels line up.
        val numberStyle = style.copy(fontFeatureSettings = "tnum")
        val onScreen = displayed ?: if (!ready) loadingValue ?: targetRupees else store.lastShown(storageKey) ?: targetRupees
        SpinningRupee(value = onScreen, style = numberStyle, color = color)
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
