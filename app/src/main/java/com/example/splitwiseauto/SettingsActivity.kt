package com.example.splitwiseauto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme

/**
 * Full-screen Settings (opened from the gear on Home): greeting, Personalise
 * (app + widget theme), Backup, Payments, Trips, Data & privacy, About.
 */
class SettingsActivity : ComponentActivity() {
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
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

private val SettingsAccent: Color @Composable get() = Brand.accent()

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val name = remember { UserProfile.name(context) }
    var showSmsCheck by remember { mutableStateOf(false) }
    if (showSmsCheck) SmsCheckSheet(onDismiss = { showSmsCheck = false })
    var showMyUpi by remember { mutableStateOf(false) }
    var upiId by remember { mutableStateOf(UserProfile.upiId(context)) }
    if (showMyUpi) MyUpiSheet(onDismiss = { showMyUpi = false; upiId = UserProfile.upiId(context) })

    // Refresh status lines (backup) when coming back from another screen.
    var resumeTick by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val backupSummary = remember(resumeTick) { BackupStore.summary(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Back
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(18.dp))

        // Greeting card: "Hi Rohit" and a
        // one-line money quote. New quote each time Settings opens; tap the
        // quote for another.
        SettingsGreeting(name = name)

        SettingsSection("Personalise")
        SettingsItem(
            Icons.Filled.Palette,
            "App theme",
            "${AppThemeStore.mode.value.label} · ${AppThemeStore.night.value.label}"
        ) {
            context.startActivity(Intent(context, AppThemeActivity::class.java))
        }
        SettingsItem(Icons.Filled.Widgets, "Widget theme", "Colours for the home-screen widget") {
            context.startActivity(Intent(context, WidgetThemeActivity::class.java))
        }

        SettingsSection("Backup")
        SettingsItem(Icons.Filled.CloudUpload, "Google Drive backup", backupSummary) {
            context.startActivity(Intent(context, BackupActivity::class.java))
        }

        SettingsSection("Payments")
        SettingsItem(
            Icons.Filled.QrCode2,
            "Your UPI ID",
            upiId ?: "Add it so reminders include a pay QR"
        ) { showMyUpi = true }

        SettingsSection("Trips")
        SettingsItem(Icons.Filled.Place, "Manage trips", "Close, hide, edit dates, search or delete trips") {
            context.startActivity(Intent(context, ManageTripsActivity::class.java))
        }

        SettingsSection("Data & privacy")
        HideAmountsRow()
        SettingsItem(Icons.Filled.Sms, "Check recent bank SMS", "Find bank messages the app missed") {
            showSmsCheck = true
        }
        SettingsItem(Icons.Filled.Security, "App permissions", "SMS, notifications, camera, location") {
            context.startActivity(Intent(context, PermissionsActivity::class.java))
        }

        SettingsSection("About")
        AppDetailsCard()
        Spacer(Modifier.height(8.dp))
        SettingsItem(Icons.Filled.NewReleases, "What's new", "Changes in each version") {
            InfoActivity.open(context, InfoActivity.PAGE_CHANGELOG)
        }
        SettingsItem(Icons.Filled.Policy, "Privacy policy", "What Splitlee does with your data") {
            InfoActivity.open(context, InfoActivity.PAGE_PRIVACY)
        }
        SettingsItem(Icons.Filled.PlayCircle, "Replay intro", "See the welcome screens again. Your data stays.") {
            IntroStore.reset(context)
            context.startActivity(
                Intent(context, IntroActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
}

/** Logo, name, one-line about, then the app version. */
@Composable
private fun AppDetailsCard() {
    val context = LocalContext.current
    val d = remember { appDetails(context) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 22.dp)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E1A16)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Splitlee", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Track spending from bank SMS and split it with friends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        d.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun appDetails(context: Context): List<Pair<String, String>> = runCatching {
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode
    else @Suppress("DEPRECATION") info.versionCode.toLong()
    listOf("Version" to "${info.versionName ?: "?"} (build $code)")
}.getOrDefault(emptyList())

@Composable
private fun SettingsGreeting(name: String) {
    val context = LocalContext.current
    val view = LocalView.current
    var quoteIndex by rememberSaveable { mutableStateOf(MoneyQuotes.next(context)) }
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 22.dp)
            .padding(16.dp)
    ) {
        Text(
            "Hi $name",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Crossfade(
            targetState = quoteIndex,
            animationSpec = tween(260, easing = MotionEaseOut)
        ) { i ->
            val q = MoneyQuotes.all[i]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = source, indication = null) {
                        Haptics.tap(view)
                        quoteIndex = MoneyQuotes.next(context)
                    }
            ) {
                Text(
                    "\u201C${q.text}\u201D",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\u2014 ${q.author}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SettingsAccent
                )
            }
        }
    }
}

/** Switch row: masks every ₹ amount as "₹••••" across the app and widget. */
@Composable
private fun HideAmountsRow() {
    val context = LocalContext.current
    val view = LocalView.current
    val hidden = AmountPrivacy.hidden.value
    val toggle: (Boolean) -> Unit = {
        Haptics.tap(view)
        AmountPrivacy.set(context, it)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .modernCard(
                base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                radius = 18.dp,
                tint = if (hidden) SettingsAccent else null,
                tintAlpha = 0.10f
            )
            .clickable { toggle(!hidden) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SettingsAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = null,
                tint = SettingsAccent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Hide amounts", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                if (hidden) "Showing \u20B9\u2022\u2022\u2022\u2022 everywhere" else "For when you show your screen to someone",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(
            checked = hidden,
            onCheckedChange = toggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF06231A),
                checkedTrackColor = SettingsAccent
            )
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** One rounded row card, like the reference: icon · title/subtitle · chevron. */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .then(if (onClick != null) Modifier.pressScale(source) else Modifier)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 18.dp)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SettingsAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
