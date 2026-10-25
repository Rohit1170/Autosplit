package com.example.splitwiseauto

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Settings > App permissions. One row per permission with its status.
 * "Allow" shows Android's popup first; when Android won't show it any more
 * (denied before, or a page-only permission), it opens the exact settings
 * page for that permission instead of the general app page.
 */
class PermissionsActivity : ComponentActivity() {
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
                    PermissionsScreen(this, onBack = { finish() })
                }
            }
        }
    }
}

/** Opens the most specific settings page Android offers. */
object PermissionPages {
    /** App info. Android has no public link to one runtime permission's page. */
    fun appDetails(context: Context, hint: String?) {
        hint?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Splitlee's own notification settings page (Android 8+). */
    fun notifications(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (ok) return
        }
        appDetails(context, "Tap Notifications, then turn them on")
    }
}

private enum class Perm { SMS, NOTIFICATIONS, NOTIF_ACCESS, LOCATION, LOCATION_ALWAYS, CAMERA }

private fun granted(c: Context, p: String) =
    ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

private fun isOn(c: Context, perm: Perm): Boolean = when (perm) {
    Perm.SMS -> granted(c, Manifest.permission.RECEIVE_SMS) && granted(c, Manifest.permission.READ_SMS)
    Perm.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) granted(c, Manifest.permission.POST_NOTIFICATIONS)
    else androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled()
    Perm.NOTIF_ACCESS -> BankNotificationListener.isEnabled(c)
    Perm.LOCATION -> granted(c, Manifest.permission.ACCESS_FINE_LOCATION) || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)
    Perm.LOCATION_ALWAYS -> Build.VERSION.SDK_INT < 29 || granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    Perm.CAMERA -> granted(c, Manifest.permission.CAMERA)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsScreen(activity: ComponentActivity, onBack: () -> Unit) {
    val context = activity
    var tick by remember { mutableStateOf(0) }

    // Re-check every time the user comes back from a settings page.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // When the popup comes back "denied" almost instantly, Android didn't
    // show it (blocked). Then go to the settings page instead.
    var askedAt by remember { mutableStateOf(0L) }
    var asking by remember { mutableStateOf<Perm?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val perm = asking
        asking = null
        tick++
        if (perm == null || res.values.all { it }) return@rememberLauncherForActivityResult
        val instant = SystemClock.elapsedRealtime() - askedAt < 400
        val canAskAgain = res.keys.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        if (instant || !canAskAgain) openPage(context, perm)
    }

    fun request(perm: Perm, permissions: Array<String>) {
        asking = perm
        askedAt = SystemClock.elapsedRealtime()
        launcher.launch(permissions)
    }

    fun allow(perm: Perm) {
        when (perm) {
            Perm.SMS -> request(perm, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
            Perm.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) request(perm, arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                else PermissionPages.notifications(context)
            Perm.NOTIF_ACCESS -> BankNotificationListener.openSettings(context)
            Perm.LOCATION -> request(perm, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            Perm.LOCATION_ALWAYS ->
                if (!isOn(context, Perm.LOCATION)) {
                    Toast.makeText(context, "Allow location first", Toast.LENGTH_SHORT).show()
                } else if (Build.VERSION.SDK_INT >= 29) {
                    // Android 11+ opens Splitlee's location page for this itself.
                    request(perm, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                }
            Perm.CAMERA -> request(perm, arrayOf(Manifest.permission.CAMERA))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            "App permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Text(
            "Tap Allow and we'll take you straight to the right switch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
        )

        val on = remember(tick) { Perm.values().associateWith { isOn(context, it) } }
        PermRow(Icons.Filled.Sms, "SMS", "Reads bank messages to add expenses. Stays on your phone.", on.getValue(Perm.SMS)) { allow(Perm.SMS) }
        PermRow(Icons.Filled.Notifications, "Notifications", "\"₹426 spent — split it?\" alerts and reminders.", on.getValue(Perm.NOTIFICATIONS)) { allow(Perm.NOTIFICATIONS) }
        PermRow(Icons.Filled.NotificationsActive, "Notification access", "Catches bank alerts that arrive as chat messages instead of SMS.", on.getValue(Perm.NOTIF_ACCESS)) { allow(Perm.NOTIF_ACCESS) }
        PermRow(Icons.Filled.Place, "Location", "Pins each expense on the map.", on.getValue(Perm.LOCATION)) { allow(Perm.LOCATION) }
        if (Build.VERSION.SDK_INT >= 29) {
            PermRow(Icons.Filled.MyLocation, "Location all the time", "So SMS expenses get a place even when the app is closed.", on.getValue(Perm.LOCATION_ALWAYS)) { allow(Perm.LOCATION_ALWAYS) }
        }
        PermRow(Icons.Filled.CameraAlt, "Camera", "Only for scanning UPI QR codes. No photos are saved.", on.getValue(Perm.CAMERA)) { allow(Perm.CAMERA) }
    }
}

/** Settings page fallback for each permission. */
private fun openPage(context: Context, perm: Perm) {
    when (perm) {
        Perm.SMS -> PermissionPages.appDetails(context, "Tap Permissions → SMS → Allow")
        Perm.NOTIFICATIONS -> PermissionPages.notifications(context)
        Perm.NOTIF_ACCESS -> BankNotificationListener.openSettings(context)
        Perm.LOCATION -> PermissionPages.appDetails(context, "Tap Permissions → Location → Allow")
        Perm.LOCATION_ALWAYS -> PermissionPages.appDetails(context, "Tap Permissions → Location → Allow all the time")
        Perm.CAMERA -> PermissionPages.appDetails(context, "Tap Permissions → Camera → Allow")
    }
}

@Composable
private fun PermRow(icon: ImageVector, title: String, why: String, on: Boolean, onAllow: () -> Unit) {
    val accent = Brand.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 18.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (on) {
            Text("Allowed", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = accent)
        } else {
            Text(
                "Allow",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Brand.onAccent(),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent)
                    .clickable(onClick = onAllow)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}
