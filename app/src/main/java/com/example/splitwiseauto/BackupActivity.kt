package com.example.splitwiseauto

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Settings > Backup: turn Google Drive backup on/off, back up now, restore. */
class BackupActivity : ComponentActivity() {
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
                    BackupScreen(activity = this, onBack = { finish() })
                }
            }
        }
    }
}

/** What the next Google token is for. */
private enum class Pending { ENABLE, BACKUP, RESTORE_LIST, DELETE, SIGN_OUT }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(activity: ComponentActivity, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = Brand.accent()

    // Re-read the store after every action.
    var tick by remember { mutableStateOf(0) }
    val enabled = remember(tick) { BackupStore.isEnabled(context) }
    val email = remember(tick) { BackupStore.email(context) }
    val lastAt = remember(tick) { BackupStore.lastAt(context) }
    val lastSize = remember(tick) { BackupStore.lastSize(context) }
    val needsSignIn = remember(tick) { BackupStore.needsSignIn(context) }
    val lastError = remember(tick) { BackupStore.lastError(context) }
    val wifiOnly = remember(tick) { BackupStore.wifiOnly(context) }

    var busy by remember { mutableStateOf<String?>(null) }
    var showExplainer by remember { mutableStateOf(false) }
    var restoreList by remember { mutableStateOf<Pair<String, List<DriveBackupFile>>?>(null) }
    var restoring by remember { mutableStateOf<DriveBackupFile?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun runWith(token: String, what: Pending) {
        scope.launch {
            when (what) {
                Pending.ENABLE -> {
                    busy = "Turning on backup…"
                    BackupStore.enable(context, DriveBackup.accountEmail(token))
                    busy = "Backing up…"
                    when (val r = DriveBackup.backupNow(context, token)) {
                        DriveBackup.Outcome.Done -> toast("Backup is on")
                        DriveBackup.Outcome.Empty -> toast("Backup is on. It starts once you add expenses.")
                        DriveBackup.Outcome.NeedsSignIn -> toast("Please sign in again")
                        is DriveBackup.Outcome.Failed -> toast("Backup is on. First backup failed: ${r.message}")
                    }
                }
                Pending.BACKUP -> {
                    busy = "Backing up…"
                    when (val r = DriveBackup.backupNow(context, token)) {
                        DriveBackup.Outcome.Done -> toast("Backed up")
                        DriveBackup.Outcome.Empty -> toast("Nothing to back up yet. Add an expense first.")
                        DriveBackup.Outcome.NeedsSignIn -> toast("Please sign in again")
                        is DriveBackup.Outcome.Failed -> toast(r.message)
                    }
                }
                Pending.RESTORE_LIST -> {
                    busy = "Looking for backups…"
                    try {
                        val files = DriveBackup.list(token)
                        if (files.isEmpty()) toast("No backups in this Google account yet")
                        else restoreList = token to files
                    } catch (e: Exception) {
                        toast(DriveBackup.friendlyError(e))
                    }
                }
                Pending.DELETE -> {
                    busy = "Deleting backups…"
                    try {
                        val n = DriveBackup.deleteAll(token)
                        toast(if (n == 0) "No backups to delete" else "Deleted $n backup${if (n == 1) "" else "s"} from Drive")
                    } catch (e: Exception) {
                        toast(DriveBackup.friendlyError(e))
                    }
                }
                Pending.SIGN_OUT -> {
                    busy = "Signing out…"
                    DriveBackup.revoke(token)
                    BackupStore.disable(context)
                    toast("Backup turned off")
                }
            }
            busy = null
            tick++
        }
    }

    val signIn = rememberDriveSignIn(
        onToken = { t -> pending?.let { runWith(t, it) }; pending = null },
        onError = { msg -> busy = null; pending = null; toast(msg) }
    )

    fun withToken(what: Pending) {
        pending = what
        signIn()
    }

    if (showExplainer) {
        DriveExplainerSheet(
            onContinue = { showExplainer = false; withToken(Pending.ENABLE) },
            onDismiss = { showExplainer = false }
        )
    }

    restoreList?.let { (token, files) ->
        RestorePickerSheet(
            files = files,
            onPick = { restoreList = null; restoring = it; busy = "Restoring…"
                scope.launch {
                    try {
                        val summary = DriveBackup.restore(context, token, it)
                        Toast.makeText(context, summary.label(), Toast.LENGTH_LONG).show()
                        if (!BackupStore.isEnabled(context)) BackupStore.enable(context, DriveBackup.accountEmail(token))
                        AppRestart.now(activity)
                    } catch (e: Exception) {
                        busy = null
                        restoring = null
                        toast(DriveBackup.friendlyError(e))
                    }
                }
            },
            onDelete = { f ->
                busy = "Deleting backup…"
                scope.launch {
                    try {
                        DriveBackup.delete(token, f.id)
                        val left = files.filter { it.id != f.id }
                        restoreList = if (left.isEmpty()) null else token to left
                        toast("Backup deleted from Drive")
                    } catch (e: Exception) {
                        toast(DriveBackup.friendlyError(e))
                    }
                    busy = null
                }
            },
            onDismiss = { restoreList = null }
        )
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
            "Backup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(18.dp))

        if (!enabled) {
            // ---------- Off ----------
            BackupCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Back up to your Google Drive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "So you never lose your expenses if you change phones or uninstall.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryPill("Turn on backup") { showExplainer = true }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Off · Splitlee works fully offline without it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            PromisesCard()
            Spacer(Modifier.height(10.dp))
            BackupRow(Icons.Filled.CloudDownload, "Your backups", "Restore or delete a backup in your Google Drive") {
                withToken(Pending.RESTORE_LIST)
            }
        } else {
            // ---------- On ----------
            BackupCard(tint = if (needsSignIn) Color(0xFFF0A63C) else accent) {
                Text(
                    when {
                        needsSignIn -> "PAUSED · SIGN IN AGAIN"
                        else -> "ON"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (needsSignIn) Color(0xFFF0A63C) else accent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (lastAt == 0L) "No backup yet" else "Last backup: ${BackupStore.whenLabel(lastAt)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    listOfNotNull(
                        email ?: "Your Google account",
                        "keeps last ${DriveBackup.KEEP}",
                        if (lastSize > 0) sizeLabel(lastSize) else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lastError != null && !needsSignIn) {
                    Spacer(Modifier.height(6.dp))
                    Text("Last try failed: $lastError", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF6B6B))
                }
                Spacer(Modifier.height(12.dp))
                PrimaryPill(if (needsSignIn) "Sign in again" else "Back up now") { withToken(Pending.BACKUP) }
            }
            Spacer(Modifier.height(10.dp))
            BackupRow(Icons.Filled.Schedule, "Automatic backup", "Every day, when the phone is idle", onClick = null)
            BackupSwitchRow(Icons.Filled.Wifi, "Only on Wi-Fi", "Save mobile data", wifiOnly) {
                BackupStore.setWifiOnly(context, it); tick++
            }
            BackupRow(Icons.Filled.CloudDownload, "Your backups", "Restore or delete a backup") {
                withToken(Pending.RESTORE_LIST)
            }
            Spacer(Modifier.height(4.dp))
            WhatsBackedUpCard()
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HoldToConfirmRow(
                    icon = Icons.Filled.DeleteForever,
                    label = "Hold to delete my backups",
                    holdingLabel = "Keep holding…",
                    hint = "Removes every Splitlee backup from your Drive",
                    holdingHint = "Let go to cancel",
                    onConfirmed = { withToken(Pending.DELETE) }
                )
                BackupRow(Icons.Filled.Logout, "Turn off & sign out", "Stops backups and removes Splitlee's access to your Google account") {
                    withToken(Pending.SIGN_OUT)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "We don't have a server. Your backup goes only to your own Google Drive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }

    busy?.let { BusyOverlay(it) }
}

private fun sizeLabel(bytes: Long): String =
    if (bytes >= 1024 * 1024) String.format("%.1f MB", bytes / 1048576.0) else "${(bytes / 1024).coerceAtLeast(1)} KB"

@Composable
private fun BackupCard(tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .modernCard(
                base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                radius = 22.dp,
                tint = tint,
                tintAlpha = 0.12f
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun BackupRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    val accent = Brand.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 18.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onClick != null) Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackupSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = Brand.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 18.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF06231A), checkedTrackColor = accent)
        )
    }
}

/** The honest privacy promises, shown before turning backup on. */
@Composable
internal fun PromisesCard() {
    BackupCard {
        Promise(true, "We don't have a server", "Your data never reaches us.")
        Promise(true, "Only a private Splitlee folder", "Hidden in your Drive. We can't see your other files, mail or contacts.")
        Promise(true, "Protected by your Google account", "Stored by Google like the rest of your Drive.")
        Promise(true, "No ads, tracking or analytics", "Ever.")
    }
}

@Composable
private fun Promise(yes: Boolean, title: String, body: String) {
    val accent = Brand.accent()
    val color = if (yes) accent else Color(0xFFFF6B6B)
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (yes) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WhatsBackedUpCard() {
    BackupCard {
        Text(
            "WHAT'S BACKED UP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Promise(true, "Your Splitlee data", "Expenses, friends, groups, trips, settle-ups, categories and settings.")
        Promise(false, "Not your SMS messages", "Only the expenses Splitlee made from them.")
    }
}

/** Shown right before Google's consent screen. */
@Composable
internal fun DriveExplainerSheet(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AppSheet(title = "Next, Google will ask", onDismiss = onDismiss) {
        Text(
            "You'll see this line on Google's screen. Here's what it means:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "“See, create and delete its own configuration data in your Google Drive”",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Promise(true, "Splitlee gets one hidden folder", "It doesn't show in your Drive and holds only the backup.")
        Promise(false, "Splitlee can't see anything else", "Not your files, photos, Gmail or contacts.")
        Spacer(Modifier.height(14.dp))
        PrimaryPill("Continue with Google", onContinue)
        Spacer(Modifier.height(4.dp))
        Text(
            "Not now",
            style = MaterialTheme.typography.titleSmall,
            color = Brand.accent(),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss)
                .padding(12.dp)
        )
    }
}

@Composable
private fun RestorePickerSheet(
    files: List<DriveBackupFile>,
    onPick: (DriveBackupFile) -> Unit,
    onDelete: (DriveBackupFile) -> Unit,
    onDismiss: () -> Unit
) {
    // Default to the newest backup that actually has data.
    var chosen by remember(files) { mutableStateOf(files.firstOrNull { !it.looksEmpty } ?: files.first()) }
    var confirmDelete by remember { mutableStateOf<DriveBackupFile?>(null) }
    confirmDelete?.let { f ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this backup?") },
            text = { Text("${BackupStore.whenLabel(f.modifiedMillis).replaceFirstChar { it.uppercase() }} · ${f.device ?: "Unknown phone"}\nIt's removed from your Google Drive. This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null; onDelete(f) }) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
    AppSheet(title = "Your backups", subtitle = "Pick one to restore, or delete ones you don't need", onDismiss = onDismiss) {
        files.forEachIndexed { i, f ->
            val selected = f.id == chosen.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .modernCard(
                        base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        radius = 16.dp,
                        tint = if (selected) Brand.accent() else null,
                        tintAlpha = 0.14f
                    )
                    .clickable { chosen = f }
                    .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(BackupStore.whenLabel(f.modifiedMillis).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                        if (i == 0) {
                            Spacer(Modifier.width(8.dp))
                            Text("LATEST", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Brand.accent())
                        }
                    }
                    Text(
                        listOfNotNull(
                            f.device,
                            sizeLabel(f.size),
                            when {
                                f.looksEmpty -> "empty"
                                f.expenses != null -> "${f.expenses} expenses"
                                else -> null
                            }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (f.looksEmpty) Color(0xFFF0A63C) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Brand.accent())
                androidx.compose.material3.IconButton(onClick = { confirmDelete = f }) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = "Delete this backup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HoldToConfirmRow(
            icon = Icons.Filled.CloudDownload,
            label = "Hold to restore",
            holdingLabel = "Keep holding…",
            hint = "Your current data on this phone will be replaced",
            holdingHint = "Let go to cancel",
            onConfirmed = { onPick(chosen) }
        )
    }
}

@Composable
internal fun BusyOverlay(message: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 26.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Brand.accent())
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
