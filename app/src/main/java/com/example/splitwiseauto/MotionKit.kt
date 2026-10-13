package com.example.splitwiseauto

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Splitlee motion kit.
 *
 * Smoothness rules followed everywhere here (what makes 90/120 Hz screens
 * stay at full frame rate):
 *  - Animated values are read only inside draw/layout lambdas (graphicsLayer,
 *    drawBehind/drawWithCache, offset { }), never in composition, so a
 *    running animation redraws but never recomposes.
 *  - Paths/brushes are cached with drawWithCache and reused, so frames don't
 *    allocate.
 *  - Every animation respects Android's "Remove animations" setting.
 *  - [DisplayRefresh.requestHighest] asks Android for the screen's fastest
 *    refresh mode (some phones keep apps at 60 Hz unless asked).
 */

/** "Remove animations" (Android accessibility) sets the animator scale to 0. */
fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

object DisplayRefresh {
    /**
     * Prefer the display's highest refresh rate (e.g. 120 Hz) at the current
     * resolution while this activity is on screen. Android/OEM power policy
     * still has the final say (e.g. battery saver), which is the right
     * behaviour. Safe no-op if anything is unavailable.
     */
    @Suppress("DEPRECATION")
    fun requestHighest(activity: Activity) {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display
            else activity.windowManager.defaultDisplay
            display ?: return
            val current = display.mode
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate } ?: return
            if (best.refreshRate <= current.refreshRate + 0.5f && best.modeId == current.modeId) return
            val attrs = activity.window.attributes
            attrs.preferredDisplayModeId = best.modeId
            activity.window.attributes = attrs
        } catch (_: Exception) {
        }
    }
}

// ---------------------------------------------------------------------------
// Motion tokens (Emil Kowalski's curves; the built-in Material ones are too
// soft). Use these instead of FastOutSlowInEasing for UI motion.
//   MotionEaseOut    - things entering/exiting, press feedback
//   MotionEaseInOut  - things moving/morphing on screen
//   MotionEaseDrawer - sheets and drawers (iOS-like)
// UI motion stays under 300 ms; exits are faster than entrances.
// ---------------------------------------------------------------------------

val MotionEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
val MotionEaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
val MotionEaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

// ---------------------------------------------------------------------------
// 6. Staggered entrance
// ---------------------------------------------------------------------------

private const val STAGGER_MAX_ITEMS = 8
private const val STAGGER_STEP_MS = 40L

/**
 * Fade + slide-up when an item first appears, delayed by its [index]
 * (40 ms apart, first 8 items only). [play] should be true only for the
 * screen's first appearance, so rows scrolled into view later just appear.
 */
fun Modifier.staggeredEntrance(index: Int, play: Boolean): Modifier = composed {
    val context = LocalContext.current
    val shouldAnimate = play && index < STAGGER_MAX_ITEMS && !reducedMotion(context)
    val progress = remember { Animatable(if (shouldAnimate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * STAGGER_STEP_MS)
            progress.animateTo(1f, tween(300, easing = MotionEaseOut))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 8.dp.toPx()
    }
}

// ---------------------------------------------------------------------------
// 1. Swipe to confirm
// ---------------------------------------------------------------------------

/**
 * CRED-style swipe-to-confirm. The fill follows the thumb; past 85% it
 * snaps to the end and calls [onConfirmed] once, otherwise springs back.
 * Haptic tick when the threshold is crossed and on confirm.
 */
@Composable
fun SwipeToConfirm(
    text: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Color(0xFF2FD39A),
    accentDark: Color = Color(0xFF0F5E46),
    trackColor: Color = Color(0x1FFFFFFF),
    textColor: Color = Color(0xB3FFFFFF)
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val knob = remember { Animatable(0f) }
    var trackWidth by remember { mutableStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val knobSize = 52.dp
    val pad = 5.dp
    val knobPx = with(density) { knobSize.toPx() }
    val padPx = with(density) { pad.toPx() }
    val maxOffset = (trackWidth - knobPx - 2 * padPx).coerceAtLeast(1f)
    val threshold = 0.85f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(31.dp))
            .background(trackColor)
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .drawBehind {
                val x = (knob.value + padPx + knobPx + padPx).coerceAtMost(size.width)
                val frac = (knob.value / maxOffset).coerceIn(0f, 1f)
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accentDark, accent.copy(alpha = 0.55f + 0.45f * frac)),
                        startX = 0f,
                        endX = x
                    ),
                    size = Size(x, size.height),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
            .pointerInput(enabled, maxOffset) {
                if (!enabled) return@pointerInput
                var crossed = false
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (!done && knob.value >= maxOffset * threshold) {
                                done = true
                                knob.animateTo(maxOffset, spring(stiffness = Spring.StiffnessMedium))
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirmed()
                            } else if (!done) {
                                knob.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                        crossed = false
                    },
                    onDragCancel = {
                        scope.launch { if (!done) knob.animateTo(0f, spring(dampingRatio = 0.72f)) }
                        crossed = false
                    },
                    onHorizontalDrag = { change, dx ->
                        if (done) return@detectHorizontalDragGestures
                        change.consume()
                        val next = (knob.value + dx).coerceIn(0f, maxOffset)
                        scope.launch { knob.snapTo(next) }
                        val nowCrossed = next >= maxOffset * threshold
                        if (nowCrossed && !crossed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        crossed = nowCrossed
                    }
                )
            }
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else textColor.copy(alpha = 0.35f),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = knobSize + 12.dp, end = 16.dp)
                .graphicsLayer { alpha = (1f - (knob.value / maxOffset) * 1.4f).coerceIn(0f, 1f) }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((padPx + knob.value).roundToInt(), 0) }
                .size(knobSize)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color(0x66FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = "Swipe to confirm",
                tint = Color(0xFF0E1613)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Success moment: ring (full or partial) + drawn check + glow + confetti
// ---------------------------------------------------------------------------

private data class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float, val isRect: Boolean)

private val CONFETTI_COLORS = listOf(
    Color(0xFF2FD39A), Color(0xFFF0A63C), Color(0xFF5E8DF2), Color(0xFFE56BAE), Color(0xFFFFFFFF)
)

/**
 * [fraction] of the ring to fill (1 = fully settled → ring closes into a
 * check). [celebrate] adds a light confetti burst. Everything is drawn in
 * the draw phase from Animatables (no recomposition per frame).
 */
@Composable
fun SuccessBurst(
    fraction: Float,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 150.dp,
    accent: Color = Color(0xFF2FD39A)
) {
    val context = LocalContext.current
    val still = remember { reducedMotion(context) }
    val target = fraction.coerceIn(0f, 1f)
    val full = target >= 0.999f
    val ring = remember { Animatable(if (still) target else 0f) }
    val check = remember { Animatable(if (still && full) 1f else 0f) }
    val glow = remember { Animatable(if (still) 1f else 0f) }
    val confetti = remember { Animatable(if (still) 1f else 0f) }
    val particles = remember {
        val r = java.util.Random(7)
        List(26) {
            Particle(
                angle = (r.nextFloat() * 2f * Math.PI).toFloat(),
                speed = 0.55f + r.nextFloat() * 0.6f,
                size = 4f + r.nextFloat() * 5f,
                color = CONFETTI_COLORS[it % CONFETTI_COLORS.size],
                spin = r.nextFloat() * 720f - 360f,
                isRect = it % 3 != 0
            )
        }
    }

    LaunchedEffect(target) {
        if (still) return@LaunchedEffect
        launch { glow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        if (celebrate) launch { confetti.animateTo(1f, tween(1400, easing = LinearEasing)) }
        ring.animateTo(target, tween(650, easing = MotionEaseOut))
        if (full) check.animateTo(1f, tween(280, easing = MotionEaseOut))
    }

    Box(
        modifier = modifier
            .size(diameter)
            .drawWithCache {
                val checkPath = Path().apply {
                    moveTo(size.width * 0.33f, size.height * 0.52f)
                    lineTo(size.width * 0.45f, size.height * 0.64f)
                    lineTo(size.width * 0.68f, size.height * 0.39f)
                }
                val measure = PathMeasure().apply { setPath(checkPath, false) }
                val checkLen = measure.length
                val partial = Path()
                val c = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.30f
                val stroke = size.minDimension * 0.045f
                onDrawBehind {
                    // Glow pulse
                    val g = glow.value
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.35f * (1f - g * 0.6f)), Color.Transparent),
                            center = c,
                            radius = radius * (1.2f + 0.9f * g)
                        ),
                        radius = radius * (1.2f + 0.9f * g),
                        center = c
                    )
                    // Track + progress ring
                    drawCircle(color = accent.copy(alpha = 0.18f), radius = radius, center = c, style = Stroke(stroke))
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ring.value,
                        useCenter = false,
                        topLeft = Offset(c.x - radius, c.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    // Filled disc + drawn check when fully settled
                    val k = check.value
                    if (k > 0f) {
                        drawCircle(color = accent, radius = radius * (0.2f + 0.8f * k), center = c, alpha = k)
                        partial.reset()
                        measure.getSegment(0f, checkLen * k, partial, true)
                        drawPath(partial, color = Color(0xFF0B1210), style = Stroke(stroke * 1.25f, cap = StrokeCap.Round))
                    }
                    // Confetti
                    val t = confetti.value
                    if (celebrate && t in 0.001f..0.999f) {
                        val alpha = (1f - t).coerceIn(0f, 1f)
                        particles.forEach { p ->
                            val dist = size.minDimension * 0.75f * p.speed * t
                            val x = c.x + cos(p.angle) * dist
                            val y = c.y + sin(p.angle) * dist + size.minDimension * 0.45f * t * t
                            rotate(p.spin * t, pivot = Offset(x, y)) {
                                if (p.isRect) {
                                    drawRect(p.color, topLeft = Offset(x - p.size / 2, y - p.size), size = Size(p.size, p.size * 1.8f), alpha = alpha)
                                } else {
                                    drawCircle(p.color, radius = p.size / 2f, center = Offset(x, y), alpha = alpha)
                                }
                            }
                        }
                    }
                }
            }
    )
}

// ---------------------------------------------------------------------------
// 4. Rolling digits (odometer)
// ---------------------------------------------------------------------------

/**
 * Shows [to]; while [progress] goes 0→1 each character that differs from
 * [from] rolls up (old slides out the top, new in from the bottom), with a
 * small left-to-right stagger. Unchanged characters never move. Strings are
 * right-aligned so the ones/tens/hundreds line up.
 */
@Composable
fun RollingText(
    from: String,
    to: String,
    progress: Animatable<Float, *>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val len = maxOf(from.length, to.length)
    val a = from.padStart(len, ' ')
    val b = to.padStart(len, ' ')
    val changed = (0 until len).filter { a[it] != b[it] }
    Row(modifier = modifier) {
        for (i in 0 until len) {
            val ca = a[i]
            val cb = b[i]
            if (ca == cb) {
                if (cb != ' ') Text(cb.toString(), style = style, color = color, maxLines = 1)
            } else {
                val order = changed.indexOf(i)
                val delayFrac = if (changed.size > 1) 0.25f * order / (changed.size - 1) else 0f
                Box(modifier = Modifier.clipToBounds()) {
                    // old char slides up and out
                    if (ca != ' ') {
                        Text(
                            ca.toString(), style = style, color = color, maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                val p = ((progress.value - delayFrac) / (1f - delayFrac)).coerceIn(0f, 1f)
                                translationY = -p * size.height
                                alpha = 1f - p
                            }
                        )
                    }
                    // new char slides up from below
                    if (cb != ' ') {
                        Text(
                            cb.toString(), style = style, color = color, maxLines = 1,
                            modifier = Modifier.graphicsLayer {
                                val p = ((progress.value - delayFrac) / (1f - delayFrac)).coerceIn(0f, 1f)
                                translationY = (1f - p) * size.height
                                alpha = p
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Small helper so callers don't need a Spacer import just for gaps. */
@Composable
internal fun VGap(h: Dp) = Spacer(Modifier.height(h))

// ---------------------------------------------------------------------------
// Modern card surface + press feedback (shared by cards, tiles, sheets)
// ---------------------------------------------------------------------------

/**
 * Soft "glass" card: a faint top-to-bottom sheen over [base], and a
 * hairline border that is brighter on top (light catching the edge).
 * [tint] (e.g. a category colour) washes the card lightly.
 */
fun Modifier.modernCard(
    base: Color,
    radius: Dp = 20.dp,
    tint: Color? = null,
    tintAlpha: Float = 0.14f,
    borderColor: Color? = null
): Modifier {
    val shape = RoundedCornerShape(radius)
    val top = tint?.let { lerpColor(base, it, tintAlpha) } ?: lerpColor(base, Color.White, 0.035f)
    val edge = borderColor ?: (tint ?: Color.White)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, base)))
        .border(
            androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        edge.copy(alpha = if (borderColor != null) 1f else 0.16f),
                        edge.copy(alpha = if (borderColor != null) 0.55f else 0.04f)
                    )
                )
            ),
            shape
        )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha
)

// ---------------------------------------------------------------------------
// Haptics: subtle tick on tap, firmer "hold" on long-press. Uses the view's
// built-in haptic feedback (no VIBRATE permission) and follows the phone's
// own "Touch feedback" setting, so users who turned it off feel nothing.
// ---------------------------------------------------------------------------

object Haptics {
    /** Light tick for a tap/click. */
    fun tap(view: android.view.View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Firmer pulse when a long-press is recognised. */
    fun hold(view: android.view.View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }
}

/**
 * Shrink to 0.97x while pressed; read in the draw phase. Press is a fast
 * ease-out (120 ms) and release settles without overshoot: buttons are
 * pressed tens of times a day, so no bounce.
 */
fun Modifier.pressScale(
    source: androidx.compose.foundation.interaction.InteractionSource,
    haptic: Boolean = true
): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    // Tick when a tap completes. A release after the long-press timeout
    // was a hold (that path plays its own "hold" haptic), so no tick then.
    if (haptic) {
        val view = androidx.compose.ui.platform.LocalView.current
        LaunchedEffect(source) {
            var pressedAt = 0L
            val holdMs = android.view.ViewConfiguration.getLongPressTimeout().toLong()
            source.interactions.collect { i ->
                when (i) {
                    is androidx.compose.foundation.interaction.PressInteraction.Press ->
                        pressedAt = android.os.SystemClock.uptimeMillis()
                    is androidx.compose.foundation.interaction.PressInteraction.Release -> {
                        if (android.os.SystemClock.uptimeMillis() - pressedAt < holdMs) Haptics.tap(view)
                    }
                    else -> Unit
                }
            }
        }
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (pressed) tween(120, easing = MotionEaseOut) else tween(200, easing = MotionEaseOut)
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Horizontal rows (e.g. the Accounts carousel) inside the tab pager: keep
 * leftover swipe/fling for themselves, so reaching the end of the row never
 * drags the whole page sideways.
 */
fun Modifier.consumeHorizontalOverscroll(): Modifier = composed {
    val connection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset = Offset(available.x, 0f)

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity = androidx.compose.ui.unit.Velocity(available.x, 0f)
        }
    }
    this.nestedScroll(connection)
}

// ---------------------------------------------------------------------------
// Spinning counter (slot-machine reels)
// ---------------------------------------------------------------------------

/**
 * Rupee amount whose digits spin like reels to the new value
 * (transitions.dev "spinning counter"): each digit is a 0–9 strip that rolls
 * through the in-between numbers — up when the amount grows, down when it
 * shrinks — left reels stopping first, the units digit taking an extra lap
 * on big changes. Commas/₹ stay put. The first value shows instantly;
 * every later change spins. Reel positions are applied at placement time
 * (no recomposition per frame).
 */
@Composable
fun SpinningRupee(
    value: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val still = remember { reducedMotion(context) }
    val digits = kotlin.math.abs(value).toString()
    val reels = remember { HashMap<Int, Animatable<Float, androidx.compose.animation.core.AnimationVector1D>>() }
    var previous by remember { mutableStateOf<Long?>(null) }
    // Make sure every place has a reel this frame (new leading places start at 0).
    digits.reversed().forEachIndexed { place, ch ->
        if (place !in reels) reels[place] = Animatable(if (previous == null) (ch - '0').toFloat() else 0f)
    }

    LaunchedEffect(value) {
        val prev = previous
        previous = value
        if (prev == null) return@LaunchedEffect
        val up = value >= prev
        val ds = digits.reversed()
        val bigChange = kotlin.math.abs(value - prev) >= 10
        kotlinx.coroutines.coroutineScope {
            ds.forEachIndexed { place, ch ->
                val reel = reels[place] ?: return@forEachIndexed
                val target = (ch - '0').toFloat()
                launch {
                    if (still) {
                        reel.snapTo(target)
                        return@launch
                    }
                    val cur = reel.value
                    val curMod = ((cur % 10f) + 10f) % 10f
                    var diff = if (up) ((target - curMod) + 10f) % 10f else -(((curMod - target) + 10f) % 10f)
                    // Reel feel: the last two digits take one extra lap on big changes.
                    if (kotlin.math.abs(diff) > 0.01f && place < 2 && bigChange) diff += if (up) 10f else -10f
                    if (kotlin.math.abs(diff) < 0.01f) {
                        reel.snapTo(target)
                        return@launch
                    }
                    val fromLeft = ds.length - 1 - place
                    delay(fromLeft * 40L)
                    reel.animateTo(cur + diff, tween(560, easing = MotionEaseInOut))
                    reel.snapTo(target)
                }
            }
        }
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        var place = digits.length
        formatRupeeIndian(value.toDouble()).forEach { c ->
            if (c.isDigit() && place > 0) {
                place--
                ReelDigit(reel = reels[place], fallback = c - '0', style = style, color = color)
            } else {
                Text(c.toString(), style = style, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReelDigit(
    reel: Animatable<Float, *>?,
    fallback: Int,
    style: TextStyle,
    color: Color
) {
    androidx.compose.ui.layout.Layout(
        content = {
            androidx.compose.foundation.layout.Column {
                for (d in 0..10) Text((d % 10).toString(), style = style, color = color, maxLines = 1)
            }
        },
        modifier = Modifier.clipToBounds()
    ) { measurables, constraints ->
        val strip = measurables.first().measure(
            constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        )
        val cell = strip.height / 11
        layout(strip.width, cell) {
            strip.placeWithLayer(0, 0) {
                val pos = reel?.value ?: fallback.toFloat()
                val m = ((pos % 10f) + 10f) % 10f
                translationY = -m * cell
            }
        }
    }
}
