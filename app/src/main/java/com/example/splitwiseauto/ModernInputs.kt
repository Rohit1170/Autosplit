package com.example.splitwiseauto

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * Splitlee's modern inputs, used everywhere instead of the old rectangular
 * OutlinedTextFields:
 *  - WiseAmountField: Wise-style animated money input (digits spring in,
 *    Indian commas regroup, font shrinks as the number grows).
 *  - ModernTextField: soft rounded glass field with a floating label and a
 *    glowing edge while focused (text, or read-only "tap to pick" fields
 *    like date/time).
 */

private val Accent = Color(0xFF2FD39A)
private val Danger = Color(0xFFFF6B6B)

@Composable
private fun fieldEdge(isError: Boolean, focused: Boolean): Color {
    val c by animateColorAsState(
        when {
            isError -> Danger
            focused -> Accent
            else -> Color.White.copy(alpha = 0.10f)
        }
    )
    return c
}

@Composable
private fun fieldBase(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

// ---------------------------------------------------------------------------
// Wise-style amount input
// ---------------------------------------------------------------------------

/**
 * Animated amount input. [value] is the raw typed amount ("100000.5", no
 * commas); what's shown is grouped Indian-style and animated per digit.
 * [compact] = smaller version for list rows (split shares).
 */
@Composable
fun WiseAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val edge = fieldEdge(isError, focused)
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val display = remember(value) { groupIndian(value) }
    val maxSp = if (compact) 24f else 40f
    val minSp = if (compact) 15f else 24f
    val step = if (compact) 1.4f else 2.2f
    val targetSp = (maxSp - (display.length - 4).coerceAtLeast(0) * step).coerceIn(minSp, maxSp)
    val fontSp by animateFloatAsState(targetSp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    val numberStyle = MaterialTheme.typography.headlineLarge.copy(
        fontSize = fontSp.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum"
    )
    val numberColor = when {
        isError -> Danger
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Accent
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 58.dp else 80.dp)
                .modernCard(base = fieldBase(), radius = if (compact) 18.dp else 24.dp, borderColor = edge)
        ) {
            // Hidden input owns focus + keyboard; caret is kept at the end.
            BasicTextField(
                value = TextFieldValue(text = value, selection = TextRange(value.length)),
                onValueChange = { onValueChange(sanitizeAmount(it.text)) },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .matchParentSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = if (compact) 14.dp else 20.dp)
            ) {
                if (label != null && !compact) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (focused) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "₹",
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Accent
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(start = if (compact) 36.dp else 96.dp, end = if (compact) 12.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (display.isEmpty()) {
                    Text(
                        "0",
                        style = numberStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        maxLines = 1
                    )
                } else {
                    AnimatedAmountText(display = display, style = numberStyle, color = numberColor)
                }
                BlinkingCaret(visible = focused, heightDp = fontSp * 1.05f, color = Accent)
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * [WiseAmountField] bound to a Double (e.g. a split share that can also be
 * changed by "split equally" or the +/- buttons). Keeps what the user is
 * typing ("12." stays "12.") and only reformats when the amount changes
 * from outside.
 */
@Composable
fun WiseAmountFieldFor(
    amount: Double,
    onAmountChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    hint: String? = null,
    isError: Boolean = false,
    compact: Boolean = true
) {
    var text by remember { mutableStateOf(amountToText(amount)) }
    val typed = text.toDoubleOrNull() ?: 0.0
    if (kotlin.math.abs(typed - amount) > 0.004) {
        // Changed elsewhere (split equally, stepper…): follow it.
        text = amountToText(amount)
    }
    WiseAmountField(
        value = text,
        onValueChange = {
            text = it
            onAmountChange(it.toDoubleOrNull() ?: 0.0)
        },
        modifier = modifier,
        label = label,
        hint = hint,
        isError = isError,
        compact = compact
    )
}

private fun amountToText(amount: Double): String = when {
    amount <= 0.0 -> ""
    amount == kotlin.math.floor(amount) -> amount.toLong().toString()
    else -> String.format(Locale.US, "%.2f", amount).trimEnd('0').trimEnd('.')
}

/** Digits + one dot, max 2 decimals, max 9 whole digits, no leading zeros. */
internal fun sanitizeAmount(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    val dot = cleaned.indexOf('.')
    var whole = if (dot >= 0) cleaned.substring(0, dot) else cleaned
    val frac = if (dot >= 0) cleaned.substring(dot + 1).filter { it.isDigit() }.take(2) else null
    whole = whole.take(9).trimStart('0').ifEmpty { if (frac != null || cleaned.startsWith("0")) "0" else "" }
    return if (frac != null) "$whole.$frac" else whole
}

/** "100000.5" -> "1,00,000.5" (Indian grouping, typed decimals kept as-is). */
internal fun groupIndian(value: String): String {
    if (value.isEmpty()) return ""
    val dot = value.indexOf('.')
    val whole = if (dot >= 0) value.substring(0, dot) else value
    val rest = if (dot >= 0) value.substring(dot) else ""
    if (whole.length <= 3) return whole + rest
    val last3 = whole.takeLast(3)
    val head = whole.dropLast(3)
    val grouped = head.reversed().chunked(2).joinToString(",").reversed()
    return "$grouped,$last3$rest"
}

private class ShownGlyph(
    val key: String,
    var ch: Char,
    val anim: Animatable<Float, AnimationVector1D>,
    var leaving: Boolean = false
)

/**
 * Per-character animation. Digits keep a stable identity (their typing
 * order), so typing only animates the new digit; commas are keyed by where
 * they sit, so regrouping springs the new comma in.
 */
@Composable
private fun AnimatedAmountText(display: String, style: TextStyle, color: Color) {
    val context = LocalContext.current
    val still = remember { reducedMotion(context) }
    val scope = rememberCoroutineScope()
    val shown = remember {
        mutableStateListOf<ShownGlyph>().apply {
            glyphKeys(display).forEach { (k, c) -> add(ShownGlyph(k, c, Animatable(1f))) }
        }
    }
    LaunchedEffect(display) {
        val wanted = glyphKeys(display)
        val wantedKeys = wanted.map { it.first }.toSet()
        shown.filter { !it.leaving && it.key !in wantedKeys }.forEach { g ->
            g.leaving = true
            if (still || g.ch == ',') {
                shown.remove(g)
            } else {
                scope.launch {
                    g.anim.animateTo(0f, tween(140))
                    shown.remove(g)
                }
            }
        }
        val next = mutableListOf<ShownGlyph>()
        wanted.forEach { (k, c) ->
            val existing = shown.firstOrNull { it.key == k && !it.leaving }
            if (existing != null) {
                existing.ch = c
                next += existing
            } else {
                val a = Animatable(if (still) 1f else 0f)
                next += ShownGlyph(k, c, a)
                if (!still) scope.launch {
                    a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
        val stillLeaving = shown.filter { it.leaving }
        shown.clear()
        shown.addAll(next + stillLeaving)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { g ->
            key(g.key + if (g.leaving) "~" else "") {
                Text(
                    text = g.ch.toString(),
                    style = style,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val p = g.anim.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * size.height * (if (g.leaving) 0.35f else 0.5f)
                        val sc = 0.8f + 0.2f * p
                        scaleX = sc
                        scaleY = sc
                    }
                )
            }
        }
    }
}

/** (stable key, char) for each character of the grouped amount. */
private fun glyphKeys(display: String): List<Pair<String, Char>> {
    var digit = 0
    return display.map { c ->
        when {
            c.isDigit() -> ("d" + digit++) to c
            c == ',' -> ("c" + digit) to c
            else -> "dot" to c
        }
    }
}

@Composable
private fun BlinkingCaret(visible: Boolean, heightDp: Float, color: Color) {
    if (!visible) return
    val blink = rememberInfiniteTransition()
    val a by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530, easing = LinearEasing), RepeatMode.Reverse)
    )
    Box(
        modifier = Modifier
            .padding(start = 3.dp)
            .width(2.5.dp)
            .height(heightDp.dp)
            .graphicsLayer { alpha = if (a > 0.5f) 1f else 0f }
            .background(color, RoundedCornerShape(2.dp))
    )
}

// ---------------------------------------------------------------------------
// Modern text field
// ---------------------------------------------------------------------------

/**
 * Rounded glass text field with a floating label: the label sits in the
 * middle when empty and glides up (and shrinks) when focused or filled.
 * Glowing green edge while focused, red when [isError].
 * [onClick] + [readOnly] = a "tap to pick" field (date, time…).
 */
@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leading: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    autoFocus: Boolean = false,
    height: Dp = 64.dp
) {
    var focused by remember { mutableStateOf(false) }
    val edge = fieldEdge(isError, focused)
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val raised = focused || value.isNotEmpty() || readOnly
    val lift by animateFloatAsState(if (raised) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
    val tapSource = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (onClick != null) Modifier.pressScale(tapSource) else Modifier)
                .modernCard(base = fieldBase(), radius = 18.dp, borderColor = edge)
                // Tap anywhere: pick (read-only fields) or focus the text.
                .clickable(
                    interactionSource = tapSource,
                    indication = null,
                    enabled = enabled
                ) {
                    if (onClick != null) onClick() else runCatching { focusRequester.requestFocus() }
                }
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    Text(
                        leading,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    // Floating label: centred when empty, small + raised otherwise.
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (focused) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer {
                            val s = 1f - 0.25f * lift
                            scaleX = s
                            scaleY = s
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            // Centred in the field when empty, at the top when raised.
                            translationY = (1f - lift) * 9.dp.toPx()
                        }
                    )
                    val textModifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .graphicsLayer { alpha = lift }
                    if (readOnly) {
                        Text(
                            text = value.ifEmpty { placeholder.orEmpty() },
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = textModifier
                        )
                    } else {
                        Box(modifier = textModifier) {
                            if (value.isEmpty() && placeholder != null && focused) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                enabled = enabled,
                                singleLine = singleLine,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(Accent),
                                keyboardOptions = keyboardOptions,
                                keyboardActions = keyboardActions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focused = it.isFocused }
                            )
                        }
                    }
                }
                trailing?.invoke()
            }
        }
        if (supportingText != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
