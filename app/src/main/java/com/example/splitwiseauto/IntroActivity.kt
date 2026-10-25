package com.example.splitwiseauto

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** First-run intro: shown once, before MainActivity. */
object IntroStore {
    private const val PREFS = "splitlee_intro"
    private const val KEY_DONE = "done_v1"
    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    fun setDone(context: Context) {
        // commit, not apply: after a restore the app restarts right away and
        // an async write would be lost (the intro then showed again).
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).commit()
    }
    /** Settings > Replay intro. Only this flag is reset; no app data is touched. */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, false).apply()
    }
}

/**
 * Offline UPI ID check. It can't prove the ID exists (that needs a paid
 * gateway API); it catches emails (@gmail.com), bad format and typos, and
 * gently flags handles we don't know.
 */
object UpiIdCheck {
    enum class Status { EMPTY, INCOMPLETE, EMAIL, BAD_FORMAT, UNKNOWN_HANDLE, OK }

    val KNOWN_HANDLES = setOf(
        "okaxis", "oksbi", "okhdfcbank", "okicici", "ybl", "ibl", "axl", "paytm", "ptyes", "ptaxis",
        "pthdfc", "ptsbi", "apl", "yapl", "rapl", "upi", "sbi", "icici", "hdfcbank", "axisbank",
        "kotak", "kmbl", "yesbank", "yesbankltd", "idfcbank", "idfcfirst", "indus", "federal", "fbl",
        "barodampay", "pnb", "unionbank", "cnrb", "boi", "mahb", "aubank", "rbl", "jupiteraxis",
        "slc", "superyes", "naviaxis", "freecharge", "airtel", "jio", "waaxis", "wahdfcbank",
        "wasbi", "waicici", "fam", "dbs", "citi", "hsbc", "sc", "equitas", "abfspay", "timecosmos",
        "postbank", "ikwik", "axisb", "icicibank", "kbl", "dlb", "kvb", "tjsb", "uco", "centralbank"
    )
    /** Shown as chips once "@" is typed. */
    val SUGGESTED = listOf("ybl", "okaxis", "oksbi", "okhdfcbank", "paytm", "okicici", "ibl", "axl")

    private val EMAIL_WORDS = setOf(
        "gmail", "googlemail", "yahoo", "ymail", "outlook", "hotmail", "live", "icloud", "me",
        "rediffmail", "rediff", "proton", "protonmail", "zoho", "aol", "mail"
    )
    private val NAME_PART = Regex("""^[a-z0-9._\-]{2,}$""")
    private val HANDLE_PART = Regex("""^[a-z][a-z0-9]*$""")

    fun check(raw: String): Status {
        val v = raw.trim().lowercase(Locale.ROOT)
        if (v.isEmpty()) return Status.EMPTY
        if (v.contains(' ') || v.count { it == '@' } > 1) return Status.BAD_FORMAT
        if (!v.contains('@')) return if (NAME_PART.matches(v) || v.length < 2) Status.INCOMPLETE else Status.BAD_FORMAT
        val name = v.substringBefore('@')
        val handle = v.substringAfter('@')
        if (!NAME_PART.matches(name)) return Status.BAD_FORMAT
        if (handle.isEmpty()) return Status.INCOMPLETE
        if (handle.contains('.') || handle in EMAIL_WORDS) return Status.EMAIL
        if (!HANDLE_PART.matches(handle)) return Status.BAD_FORMAT
        if (handle in KNOWN_HANDLES) return Status.OK
        // Still typing a known handle ("rohit@oka" → okaxis)?
        if (KNOWN_HANDLES.any { it.startsWith(handle) }) return Status.INCOMPLETE
        return if (handle.length >= 2) Status.UNKNOWN_HANDLE else Status.INCOMPLETE
    }

    /** Can be saved (unknown handles allowed, with a warning). */
    fun canSave(raw: String): Boolean = check(raw).let { it == Status.OK || it == Status.UNKNOWN_HANDLE }
}

private val IntroMint = Color(0xFF3DDC97)
private val IntroGrey = Color(0xFF8B8B8B)
private val IntroCard = Color(0xFF0D0D0D)
private val IntroLine = Color(0xFF222222)

class IntroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this)
        // Draw behind the status and navigation bars (no grey strip on top).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            IntroFlow(
                onFinished = {
                    IntroStore.setDone(this)
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                },
                // Restored from Drive: name, UPI ID and data came back with it.
                onRestored = {
                    IntroStore.setDone(this)
                    AppRestart.now(this)
                }
            )
        }
    }
}

private const val SLIDES = 5
private const val STEP_BACKUP = 5
private const val STEP_NAME = 6
private const val STEP_UPI = 7
private const val SLIDE_MS = 4200L

@Composable
private fun IntroFlow(onFinished: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable {
        mutableStateOf(UserProfile.name(context).takeIf { it != UserProfile.DEFAULT_NAME }.orEmpty())
    }
    var upi by rememberSaveable { mutableStateOf(UserProfile.upiId(context).orEmpty()) }

    BackHandler(enabled = step > 0) { step -= 1 }

    // Slides auto-advance; the bar on top fills with each slide's time.
    // The last slide fills its bar too, then waits for "Get started".
    val progress = remember { Animatable(0f) }
    // Which slide [progress] currently belongs to. Until the new slide's
    // timer has reset, its bar reads 0 (not the previous slide's full value).
    var progressFor by remember { mutableStateOf(-1) }
    LaunchedEffect(step) {
        if (step < SLIDES) {
            progress.snapTo(0f)
            progressFor = step
            progress.animateTo(1f, tween(SLIDE_MS.toInt(), easing = LinearEasing))
            if (step < SLIDES - 1) step += 1
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Crossfade(targetState = step, animationSpec = tween(450)) { s ->
            when {
                s < SLIDES -> IntroSlide(
                    index = s,
                    onTap = { if (s < SLIDES - 1) step = s + 1 },
                    onBack = { if (s > 0) step = s - 1 },
                    onGetStarted = { step = STEP_BACKUP }
                )
                s == STEP_BACKUP -> BackupStep(
                    onNext = { step = STEP_NAME },
                    onRestored = onRestored
                )
                s == STEP_NAME -> NameStep(
                    name = name,
                    onName = { name = it },
                    onContinue = {
                        UserProfile.setName(context, name.trim())
                        step = STEP_UPI
                    }
                )
                else -> UpiStep(
                    name = name.trim().ifEmpty { UserProfile.DEFAULT_NAME },
                    upi = upi,
                    onUpi = { upi = it },
                    onSkip = onFinished,
                    onContinue = {
                        UserProfile.setUpiId(context, upi.trim().lowercase(Locale.ROOT))
                        onFinished()
                    }
                )
            }
        }
        if (step < SLIDES) {
            // Story-style bars: one per slide, filling as the slide plays.
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(SLIDES) { i ->
                    val fill = when {
                        i < step -> 1f
                        i == step -> if (progressFor == step) progress.value else 0f
                        else -> 0f
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fill)
                                .height(3.dp)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
        if (step < SLIDES - 1) {
            Text(
                "Skip",
                color = IntroGrey,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { step = STEP_BACKUP }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared layout: visual centred in the upper third, title fixed below it.
// ---------------------------------------------------------------------------

@Composable
private fun IntroFrame(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    bottom: (@Composable () -> Unit)? = null,
    // Slides scale their visual up to fill the space; input steps don't.
    fit: Boolean = true,
    visual: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = maxHeight
        Box(
            Modifier
                .fillMaxWidth()
                .height(h * 0.5f)
                .offset(y = h * 0.07f),
            contentAlignment = Alignment.Center
        ) {
            if (fit) Box(Modifier.fillMaxSize().fitToArea()) { visual() } else visual()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = h * 0.6f)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text(subtitle, color = IntroGrey, fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
        }
        if (bottom != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { bottom() }
        }
    }
}

/**
 * Measures the visual at its design size, then scales it up (never down
 * below 1x, at most [maxScale]) so it fills the area with a small margin.
 */
private fun Modifier.fitToArea(maxScale: Float = 1.7f): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = 300.dp.roundToPx()))
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val s = minOf(w * 0.86f / p.width.coerceAtLeast(1), h * 0.88f / p.height.coerceAtLeast(1))
        .coerceIn(1f, maxScale)
    layout(w, h) {
        p.placeWithLayer((w - p.width) / 2, (h - p.height) / 2) {
            scaleX = s
            scaleY = s
        }
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(if (enabled) Color.White else Color(0xFF1A1A1A))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Color.Black else Color(0xFF555555), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Slides 1–6
// ---------------------------------------------------------------------------

@Composable
private fun IntroSlide(index: Int, onTap: () -> Unit, onBack: () -> Unit, onGetStarted: () -> Unit) {
    // Story-style taps: left third goes back, the rest goes forward.
    val tapModifier = Modifier.pointerInput(index) {
        detectTapGestures { pos ->
            if (pos.x < size.width / 3f) onBack() else onTap()
        }
    }
    when (index) {
        0 -> IntroFrame("Welcome to Splitlee", "Every account, card and cash spend,\ntracked in one place.", tapModifier) { AccountsVisual() }
        1 -> IntroFrame("Cards spotted for you", "Splitlee tells your cards from your\naccounts, straight from bank SMS.", tapModifier) { CardVisual() }
        2 -> IntroFrame("Spend, then split", "The moment your bank texts you,\nSplitlee asks if you want to split it.", tapModifier) { AlertVisual() }
        3 -> IntroFrame("Split with anyone", "Split a bill, settle up, and remind\nfriends with your pay QR.", tapModifier) { RowsVisual() }
        // Last slide: insights + the privacy line, then "Get started".
        else -> IntroFrame(
            "Know where it goes",
            "Daily average, categories and trends.\nAll of it stays on your phone.",
            tapModifier,
            bottom = { PillButton("Get started", onClick = onGetStarted) }
        ) { ChartVisual() }
    }
}

/** ₹ amount that rolls up to [target] (digits flicker as they settle). */
@Composable
private fun RollingRupees(target: Double, fontSize: Int, delayMs: Int = 0, durationMs: Int = 1100, paise: Boolean = false) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    val shown = if (p >= 1f) target else target * p + (Math.random() * target * 0.08 * (1 - p))
    val whole = formatRupeeIndianRaw(kotlin.math.floor(shown)).substringBefore('.')
    Row(verticalAlignment = Alignment.Top) {
        Text(whole, color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold)
        if (paise) {
            val ps = ((target - kotlin.math.floor(target)) * 100).toInt()
            Text(String.format(Locale.US, "%02d", ps), color = Color.White, fontSize = (fontSize * 0.45f).sp, modifier = Modifier.padding(top = 2.dp, start = 1.dp))
        }
    }
}

/** Pops a child in (scale + fade) after [delayMs]. */
@Composable
private fun PopIn(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 380f))
    }
    Box(Modifier.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        scaleX = 0.82f + 0.18f * a.value
        scaleY = 0.82f + 0.18f * a.value
    }) { content() }
}

@Composable
private fun MiniTile(label: String, dot: Color, amount: Double, delayMs: Int) {
    PopIn(delayMs) {
        Column(
            Modifier
                .size(width = 128.dp, height = 80.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(IntroCard)
                .border(1.dp, IntroLine, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Color(0xFFDDDDDD), fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            // A different, random weekly change on every tile (and every launch).
            // Random, but always a believable part of the balance (5–40%).
            val week = remember(amount) {
                val pct = 0.05 + Math.random() * 0.35
                (kotlin.math.round(amount * pct / 10.0) * 10).toInt().coerceAtLeast(10)
            }
            Text("+${formatRupeeIndianRaw(week.toDouble())} this week", color = IntroMint, fontSize = 8.sp)
            RollingRupees(amount, 19, delayMs = delayMs + 150)
        }
    }
}

@Composable
private fun AccountsVisual() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniTile("HDFC", Color(0xFF5E8DF2), 42180.0, 100)
            MiniTile("Slice", Color(0xFFA37FF0), 3250.0, 450)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniTile("Cash", IntroMint, 1200.0, 800)
            PopIn(1150) {
                Box(
                    Modifier
                        .size(width = 128.dp, height = 80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(IntroCard)
                        .border(1.dp, IntroLine, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Color(0xFF666666), fontSize = 24.sp) }
            }
        }
    }
}

@Composable
private fun CardVisual() {
    val context = LocalContext.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val sway = rememberInfiniteTransition(label = "sway")
    val wobble by sway.animateFloat(-4f, 4f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse))
    val sweep by sway.animateFloat(-1f, 2f, infiniteRepeatable(tween(2400, easing = LinearEasing)))
    Box(
        Modifier
            .size(width = 236.dp, height = 148.dp)
            .graphicsLayer {
                cameraDistance = 14f * density
                rotationY = (1f - enter.value) * 40f - 12f + wobble
                rotationX = 8f
                alpha = enter.value
                scaleX = 0.9f + 0.1f * enter.value
                scaleY = 0.9f + 0.1f * enter.value
            }
            .shadow(24.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3A3D3C), Color(0xFF1A1C1B), Color(0xFF565A58), Color(0xFF1E201F))
                )
            )
            // Light sweeping across the metal.
            .drawWithContent {
                drawContent()
                val x = size.width * sweep
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                        start = Offset(x - 90f, 0f),
                        end = Offset(x + 90f, size.height)
                    )
                )
            }
    ) {
        val holder = remember { UserProfile.name(context).uppercase(Locale.ROOT).take(18) }
        val engraved = Color(0xFFC9CDCB)
        // Bank name.
        Text(
            "ICICI Bank",
            color = engraved,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 14.dp)
        )
        // EMV chip with contact pads.
        Canvas(
            Modifier
                .offset(x = 18.dp, y = 40.dp)
                .size(width = 34.dp, height = 26.dp)
        ) {
            val r = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFFE3CF8E), Color(0xFFB59A55), Color(0xFF8A733C), Color(0xFFD2BC7A))),
                cornerRadius = r
            )
            val line = Color(0xFF6E5B2C).copy(alpha = 0.75f)
            val sw = 0.9.dp.toPx()
            val w = size.width
            val h = size.height
            // Outer pads: two vertical dividers and three rows each side.
            drawLine(line, Offset(w * 0.33f, 0f), Offset(w * 0.33f, h), sw)
            drawLine(line, Offset(w * 0.67f, 0f), Offset(w * 0.67f, h), sw)
            for (y in listOf(h / 3f, h * 2f / 3f)) {
                drawLine(line, Offset(0f, y), Offset(w * 0.33f, y), sw)
                drawLine(line, Offset(w * 0.67f, y), Offset(w, y), sw)
            }
            // Centre contact.
            drawRoundRect(
                line,
                topLeft = Offset(w * 0.4f, h * 0.24f),
                size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                style = Stroke(sw)
            )
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r, style = Stroke(sw))
        }
        // Contactless waves.
        Canvas(
            Modifier
                .offset(x = 60.dp, y = 43.dp)
                .size(20.dp)
        ) {
            val sw = 1.6.dp.toPx()
            for (i in 0..2) {
                val rad = size.minDimension * (0.22f + 0.17f * i)
                drawArc(
                    engraved.copy(alpha = 0.8f),
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.15f - rad, size.height / 2f - rad),
                    size = androidx.compose.ui.geometry.Size(rad * 2, rad * 2),
                    style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        // Card number.
        Text(
            "••••  ••••  ••••  1000",
            color = engraved,
            fontSize = 15.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.offset(x = 18.dp, y = 80.dp)
        )
        // Holder + expiry.
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 12.dp)) {
            Text("VALID THRU 08/29", color = engraved.copy(alpha = 0.7f), fontSize = 7.sp, letterSpacing = 0.5.sp)
            Text(holder, color = engraved, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        Text(
            "CARD",
            color = engraved,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp)
        )
    }
}

private data class IntroRow(val initial: String, val color: Color, val title: String, val sub: String, val amount: String, val amountColor: Color)

/**
 * Slide 3: a bank SMS notification drops in, then Splitlee's own alert
 * lands on top of it ("₹789 at Dominos · Split it?") and "Split" gets tapped.
 */
@Composable
private fun AlertVisual() {
    val bankIn = remember { Animatable(0f) }
    val appIn = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(200)
        bankIn.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 320f))
        delay(900)
        appIn.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 300f))
        delay(1000)
        press.animateTo(0.9f, tween(120))
        press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
    }
    val a = appIn.value
    Box(Modifier.size(width = 292.dp, height = 236.dp)) {
        // Bank SMS (slides down; pushed back once Splitlee's alert arrives).
        Box(
            Modifier
                .graphicsLayer {
                    translationY = (-36f * (1f - bankIn.value)).dp.toPx() + (118f * a).dp.toPx()
                    alpha = bankIn.value.coerceIn(0f, 1f) * (1f - 0.45f * a)
                    scaleX = 1f - 0.05f * a
                    scaleY = 1f - 0.05f * a
                }
        ) {
            NotifCard(
                icon = {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF34C759)), contentAlignment = Alignment.Center) {
                        Text("✉", color = Color.White, fontSize = 13.sp)
                    }
                },
                app = "Messages",
                title = "HDFC Bank",
                body = "Rs.789.00 debited from A/c XX0508 to DOMINOS."
            )
        }
        // Splitlee's alert on top.
        Box(
            Modifier
                .graphicsLayer {
                    translationY = (-40f * (1f - a)).dp.toPx()
                    alpha = a.coerceIn(0f, 1f)
                }
        ) {
            NotifCard(
                icon = {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF0E1613)), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                app = "Splitlee",
                title = "₹789 at Dominos",
                body = "Food · HDFC ****0508. Split it with friends?",
                highlight = true
            ) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .graphicsLayer { scaleX = press.value; scaleY = press.value }
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(IntroMint),
                        contentAlignment = Alignment.Center
                    ) { Text("Split", color = Color(0xFF06130D), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2A2A2C)),
                        contentAlignment = Alignment.Center
                    ) { Text("Just me", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

/** A phone-style notification card. */
@Composable
private fun NotifCard(
    icon: @Composable () -> Unit,
    app: String,
    title: String,
    body: String,
    highlight: Boolean = false,
    extra: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier
            .width(292.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1C1C1E))
            .border(1.dp, if (highlight) IntroMint.copy(alpha = 0.45f) else Color(0xFF2A2A2C), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(app, color = Color(0xFF9A9A9F), fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("now", color = Color(0xFF9A9A9F), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            body,
            color = Color(0xFFCFCFD4),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        extra?.invoke()
    }
}

@Composable
private fun RowsVisual() {
    val rows = listOf(
        IntroRow("D", Color(0xFFEF6A5B), "Dominos", "Split 3 ways", "₹789", Color.White),
        IntroRow("H", Color(0xFF5E8DF2), "HDFC ****0508", "Settled with Rahul", "₹640", Color.White),
        IntroRow("A", Color(0xFFFFB86B), "Akhil", "“Goa dinner”", "+₹500", IntroMint)
    )
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        repeat(rows.size) { delay(if (it == 0) 200 else 1100); shown = it + 1 }
    }
    Box(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 28.dp), contentAlignment = Alignment.TopCenter) {
        rows.take(shown).forEachIndexed { i, r ->
          androidx.compose.runtime.key(r.title) {
            val depth = (shown - 1 - i).toFloat()
            val enter = remember { Animatable(0f) }
            LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
            val d by animateFloatAsState(depth, spring(dampingRatio = 0.85f, stiffness = 300f))
            Row(
                Modifier
                    .padding(top = 30.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (-d * 12f).dp.toPx() + (1f - enter.value) * 40.dp.toPx()
                        scaleX = 1f - 0.06f * d
                        scaleY = 1f - 0.06f * d
                        alpha = (1f - 0.4f * d).coerceAtLeast(0f) * enter.value
                    }
                    .clip(RoundedCornerShape(15.dp))
                    .background(IntroCard)
                    .border(1.dp, IntroLine, RoundedCornerShape(15.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(r.color), contentAlignment = Alignment.Center) {
                    Text(r.initial, color = Color(0xFF06130D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(r.sub, color = Color(0xFF888888), fontSize = 11.sp)
                }
                Text(r.amount, color = r.amountColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
    }
}

@Composable
private fun ChartVisual() {
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(500); draw.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(0.6f, 1.4f, infiniteRepeatable(tween(1100), RepeatMode.Reverse))
    PopIn(0) {
        Box(
            Modifier
                .size(width = 214.dp, height = 148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(IntroCard)
                .border(1.dp, IntroLine, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(IntroMint))
                    Spacer(Modifier.width(6.dp))
                    Text("October", color = Color(0xFFDDDDDD), fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                RollingRupees(28869.0, 24, delayMs = 150)
                Text("Spent ₹412 today", color = IntroMint, fontSize = 9.sp)
            }
            Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(54.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.88f)
                    cubicTo(size.width * 0.2f, size.height * 0.85f, size.width * 0.32f, size.height * 0.68f, size.width * 0.48f, size.height * 0.64f)
                    cubicTo(size.width * 0.66f, size.height * 0.6f, size.width * 0.8f, size.height * 0.4f, size.width * 0.97f, size.height * 0.12f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val seg = Path()
                measure.getSegment(0f, measure.length * draw.value, seg, true)
                drawPath(seg, IntroMint, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                if (draw.value > 0.98f) {
                    val end = Offset(size.width * 0.97f, size.height * 0.12f)
                    drawCircle(IntroMint.copy(alpha = 0.18f), radius = 9.dp.toPx() * glow, center = end)
                    drawCircle(IntroMint, radius = 3.5.dp.toPx(), center = end)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Setup: name, UPI ID
// ---------------------------------------------------------------------------

@Composable
private fun IntroField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    borderColor: Color,
    keyboardType: KeyboardType,
    capitalization: KeyboardCapitalization,
    onDone: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .padding(horizontal = 28.dp)
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (borderColor == IntroLine) Color(0xFF888888) else borderColor, fontSize = 11.sp)
            Box {
                if (value.isEmpty()) Text(placeholder, color = Color(0xFF555555), fontSize = 18.sp)
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
                        androidx.compose.foundation.text.selection.TextSelectionColors(
                            handleColor = IntroMint,
                            backgroundColor = IntroMint.copy(alpha = 0.3f)
                        )
                ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(IntroMint),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        capitalization = capitalization,
                        imeAction = ImeAction.Done,
                        autoCorrect = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                    modifier = Modifier.fillMaxWidth()
                )
                }
            }
        }
        trailing?.invoke()
    }
}

/**
 * Layout for the input steps: title, subtitle and the inputs sit in the
 * space ABOVE the keyboard (scrolling if they don't fit), and the buttons
 * stay pinned right above the keyboard, so nothing hides behind it.
 */
@Composable
private fun SetupFrame(
    title: String,
    subtitle: String,
    bottom: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 56.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                color = IntroGrey,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp)
            )
            Spacer(Modifier.height(28.dp))
            content()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { bottom() }
    }
}

/**
 * Optional Google Drive backup. If the account already has a Splitlee
 * backup (new phone, reinstall), offer to restore it right here.
 */
@Composable
private fun BackupStep(onNext: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Pair<String, DriveBackupFile>?>(null) }

    val signIn = rememberDriveSignIn(
        onToken = { token ->
            scope.launch {
                busy = "Checking your Drive…"
                BackupStore.enable(context, DriveBackup.accountEmail(token))
                // Newest backup that actually has data (skips empty ones).
                val latest = runCatching {
                    DriveBackup.list(token).let { all -> all.firstOrNull { !it.looksEmpty } }
                }.getOrNull()
                busy = null
                if (latest != null) found = token to latest else onNext()
            }
        },
        onError = { busy = null; error = it }
    )

    Box(Modifier.fillMaxSize()) {
        val f = found
        if (f != null) {
            SetupFrame(
                title = "Welcome back",
                subtitle = "We found a Splitlee backup\nin your Google Drive.",
                bottom = {
                    Text(
                        "Start fresh instead",
                        color = Color(0xFFCFCFCF),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = busy == null) { onNext() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    PillButton("Restore", enabled = busy == null) {
                        error = null
                        scope.launch {
                            busy = "Restoring your data…"
                            try {
                                DriveBackup.restore(context, f.first, f.second)
                                onRestored()
                            } catch (e: Exception) {
                                busy = null
                                error = DriveBackup.friendlyError(e)
                            }
                        }
                    }
                }
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(IntroCard)
                        .border(1.dp, IntroLine, RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Text("Latest backup", color = IntroGrey, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        BackupStore.whenLabel(f.second.modifiedMillis).replaceFirstChar { it.uppercase() },
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    f.second.device?.let { Text(it, color = IntroGrey, fontSize = 13.sp) }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Brings back your expenses, friends, groups, trips and settings.",
                        color = Color(0xFFBDBDBD),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                error?.let { IntroError(it) }
            }
        } else {
            SetupFrame(
                title = "Restore or back up",
                subtitle = "Used Splitlee before? We'll bring\nyour data back. New here? We'll keep\na backup in your Google Drive as you go.",
                bottom = {
                    Text("You can do this later in Settings → Backup", color = Color(0xFF6F6F6F), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Not now",
                        color = Color(0xFFCFCFCF),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = busy == null) { onNext() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    PillButton("Continue with Google", enabled = busy == null) {
                        error = null
                        signIn()
                    }
                }
            ) {
                Column(Modifier.padding(horizontal = 28.dp).fillMaxWidth()) {
                    IntroPromise("A private Splitlee folder in your Drive", "Hidden. We can't see your other files, mail or contacts.")
                    IntroPromise("We don't have a server", "Your data never reaches us.")
                    IntroPromise("Your SMS stay on your phone", "Only the expenses made from them are backed up.")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Google will ask to let Splitlee \u201Csee, create and delete its own configuration data\u201D. That's only this private folder.",
                        color = Color(0xFF7A7A7A),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                error?.let { IntroError(it) }
            }
        }

        busy?.let { msg ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = true, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(color = IntroMint)
                    Spacer(Modifier.height(14.dp))
                    Text(msg, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun IntroPromise(title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(IntroMint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text("✓", color = IntroMint, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = IntroGrey, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun IntroError(message: String) {
    Text(
        message,
        color = Color(0xFFFF6B6B),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 14.dp).fillMaxWidth()
    )
}

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onContinue: () -> Unit) {
    val focus = LocalFocusManager.current
    val clean = name.trim()
    SetupFrame(
        title = if (clean.isEmpty()) "What should we call you?" else "Hi ${clean.substringBefore(' ')} 👋",
        subtitle = "Just your first name. Only shown\ninside the app.",
        bottom = { PillButton("Continue", enabled = clean.isNotEmpty()) { focus.clearFocus(); onContinue() } }
    ) {
        IntroField(
            label = "Your name",
            value = name,
            onValue = { if (it.length <= 24) onName(it) },
            placeholder = "e.g. Rohit",
            borderColor = if (name.isNotEmpty()) IntroMint else IntroLine,
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
            onDone = { if (clean.isNotEmpty()) { focus.clearFocus(); onContinue() } }
        )
    }
}

@Composable
private fun UpiStep(name: String, upi: String, onUpi: (String) -> Unit, onSkip: () -> Unit, onContinue: () -> Unit) {
    val focus = LocalFocusManager.current
    val status = UpiIdCheck.check(upi)
    val ok = status == UpiIdCheck.Status.OK
    val canSave = UpiIdCheck.canSave(upi)
    val warn = Color(0xFFF0A63C)
    val error = Color(0xFFFF6B6B)
    val vpa = upi.trim().lowercase(Locale.ROOT)

    // Live QR for whatever looks like an ID; sharp once it can be saved.
    val showQr = vpa.contains('@') && status != UpiIdCheck.Status.EMAIL && status != UpiIdCheck.Status.BAD_FORMAT
    val qr = remember(vpa, name, showQr) {
        if (!showQr) null else runCatching { MyUpiQr.bitmap(MyUpiQr.link(vpa, name, null), 480) }.getOrNull()
    }
    val sharp by animateFloatAsState(if (canSave) 1f else 0f, tween(450))

    val message: Pair<String, Color>? = when (status) {
        UpiIdCheck.Status.EMAIL -> "That's an email address, not a UPI ID. UPI IDs look like name@okaxis." to error
        UpiIdCheck.Status.BAD_FORMAT -> "UPI IDs look like name@bank, with no spaces." to error
        UpiIdCheck.Status.UNKNOWN_HANDLE -> "We don't recognise @${vpa.substringAfter('@')}. Double-check it." to warn
        else -> null
    }
    val border = when {
        message?.second == error -> error
        message?.second == warn -> warn
        upi.isNotEmpty() -> IntroMint
        else -> IntroLine
    }

    SetupFrame(
        title = "Your UPI ID",
        subtitle = if (canSave) "Your QR is ready. Friends scan it\nto pay you back."
        else "Optional. Friends scan your QR\nto pay you back.",
        bottom = {
            // Skip is always there (even with an ID filled in from Settings).
            Text("You can add or change it anytime in Settings", color = Color(0xFF6F6F6F), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Skip for now",
                color = Color(0xFFCFCFCF),
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { focus.clearFocus(); onSkip() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(6.dp))
            PillButton("Continue", enabled = canSave) { focus.clearFocus(); onContinue() }
        }
    ) {
        IntroField(
            label = "UPI ID",
            value = upi,
            onValue = { onUpi(it.replace(" ", "")) },
            placeholder = "name@bank",
            borderColor = border,
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            onDone = { focus.clearFocus() },
            trailing = { if (ok) Text("✓", color = IntroMint, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        )
        // Handle chips once "@" is typed.
        if (vpa.contains('@') && !ok) {
            val typed = vpa.substringAfter('@')
            val chips = UpiIdCheck.SUGGESTED.filter { it.startsWith(typed) && it != typed }.ifEmpty {
                if (typed.isEmpty()) UpiIdCheck.SUGGESTED else emptyList()
            }
            if (chips.isNotEmpty()) {
                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chips.forEach { hnd ->
                        Text(
                            "@$hnd",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF161616))
                                .clickable { onUpi(vpa.substringBefore('@') + "@" + hnd) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        if (message != null) {
            Text(
                message.first,
                color = message.second,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp).fillMaxWidth()
            )
        }
        if (qr != null) {
            Spacer(Modifier.height(20.dp))
            val blurDp: Dp = (6f * (1f - sharp)).dp
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * sharp
                        scaleX = 0.94f + 0.06f * sharp
                        scaleY = 0.94f + 0.06f * sharp
                    }
                    .then(
                        if (canSave) Modifier.shadow(30.dp, RoundedCornerShape(18.dp), ambientColor = IntroMint, spotColor = IntroMint)
                        else Modifier
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(8.dp)
            ) {
                Image(
                    qr.asImageBitmap(),
                    contentDescription = "Your UPI QR",
                    modifier = Modifier
                        .size(150.dp)
                        .then(if (Build.VERSION.SDK_INT >= 31 && blurDp > 0.dp) Modifier.blur(blurDp) else Modifier)
                )
            }
        }
    }
}
