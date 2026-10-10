package com.example.splitwiseauto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Check recent bank SMS": an on-phone diagnosis (no adb/logcat needed).
 * Reads the last few days of the SMS inbox — in memory only, nothing stored —
 * and shows, for every message that looks money-related, what the app made
 * of it and why. Missed debits can be imported with one tap.
 *
 * If a bank message the user can see in their Messages app is NOT in this
 * list at all, Android never gave it to the app: it arrived as an RCS chat
 * message, which third-party apps cannot read.
 */
object SmsCheck {

    enum class Status { ADDED, MISSED, CREDIT, NOT_BANK_SENDER, NOT_UNDERSTOOD }

    data class Row(
        val sender: String,          // shown masked if it's a phone number
        val rawSender: String,
        val body: String,            // kept in memory only, for Import
        val receivedAt: Long,
        val amount: String?,
        val merchant: String?,
        val status: Status
    )

    private const val LOOKBACK_DAYS = 3
    private val MONEY_HINT = Regex("""(?i)(rs\.?|inr|₹)\s*[\d,]+""")

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun scan(context: Context): List<Row> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val since = System.currentTimeMillis() - LOOKBACK_DAYS * 24L * 60 * 60 * 1000
        val rows = mutableListOf<Row>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && rows.size < 40) {
                val sender = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                val date = c.getLong(2)
                // Only money-looking messages are listed (OTPs, chats etc. are skipped).
                if (!MONEY_HINT.containsMatchIn(body)) continue
                rows += classify(context, sender, body, date)
            }
        }
        rows
    }

    private fun classify(context: Context, sender: String, body: String, date: Long): Row {
        val shown = SmsIngestion.maskSender(sender)
        if (!isTransactionalSender(sender)) {
            return Row(shown, sender, body, date, null, null, Status.NOT_BANK_SENDER)
        }
        val t = parseBankSms(body, sender, date)
            ?: return Row(shown, sender, body, date, null, null, Status.NOT_UNDERSTOOD)
        val status = when {
            t.type != "DEBIT" -> Status.CREDIT
            SmsIngestion.isAlreadyHandled(context, sender, body) -> Status.ADDED
            else -> Status.MISSED
        }
        return Row(shown, sender, body, date, t.amount, t.merchant, status)
    }

    /** Runs a missed debit through the normal pipeline (split prompt / Scan & Pay match). */
    suspend fun import(context: Context, row: Row) = withContext(Dispatchers.IO) {
        SmsIngestion.process(context, row.rawSender, row.body, row.receivedAt)
    }
}

@Composable
fun SmsCheckSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<SmsCheck.Row>?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showDisclosure by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) { rows = SmsCheck.scan(context) }

    // Re-checked on every resume: the user comes back from Settings.
    var accessGranted by remember { mutableStateOf(BankNotificationListener.isEnabled(context)) }
    var alertsSwitchOn by remember { mutableStateOf(NotificationAlerts.isOn(context)) }
    var readLog by remember { mutableStateOf(NotificationAlerts.readLog(context)) }
    var diagnostics by remember { mutableStateOf(NotificationAlerts.diagnostics(context)) }
    val alertsFromNotifications = accessGranted && alertsSwitchOn
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessGranted = BankNotificationListener.isEnabled(context)
                readLog = NotificationAlerts.readLog(context)
                diagnostics = NotificationAlerts.diagnostics(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppSheet(
        title = "Recent bank SMS",
        onDismiss = onDismiss,
        trailing = { TextButton(onClick = onDismiss) { Text("Close") } }
    ) {
        val current = rows
        NotificationAlertsCard(
            switchOn = alertsSwitchOn,
            accessGranted = accessGranted,
            log = readLog,
            diagnostics = diagnostics,
            onSwitch = { on ->
                if (!on) {
                    NotificationAlerts.setOn(context, false)
                    alertsSwitchOn = false
                } else if (accessGranted) {
                    NotificationAlerts.setOn(context, true)
                    alertsSwitchOn = true
                } else {
                    showDisclosure = true
                }
            },
            onOpenAccessSettings = { BankNotificationListener.openSettings(context) },
            onClearLog = {
                NotificationAlerts.clearLog(context)
                readLog = emptyList()
            }
        )
        if (showDisclosure) {
            NotificationAccessDisclosure(
                onContinue = {
                    showDisclosure = false
                    NotificationAlerts.setOn(context, true)
                    alertsSwitchOn = true
                    BankNotificationListener.openSettings(context)
                },
                onDismiss = { showDisclosure = false }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (!SmsCheck.hasPermission(context)) {
            Text(
                "Allow SMS access for Splitlee in Settings so it can read your bank messages.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (current == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.isEmpty()) {
                    Text(
                        "No money-related SMS in the last 3 days.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                current.forEach { row ->
                    SmsCheckRow(row) {
                        scope.launch {
                            SmsCheck.import(context, row)
                            refresh++
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (alertsFromNotifications) {
                        "Bank messages that arrive as RCS chats (e.g. \"IDBI Bank Limited\") don't show " +
                            "in this list, but Splitlee still picks them up from their notification."
                    } else {
                        "Don't see a bank message here that you can see in Messages? It arrived as an " +
                            "RCS chat message. Turn on \"Read bank alerts from notifications\" above, " +
                            "or add it with + for now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SmsCheckRow(row: SmsCheck.Row, onImport: () -> Unit) {
    val (label, color) = when (row.status) {
        SmsCheck.Status.ADDED -> "Added" to com.example.splitwiseauto.ui.theme.positiveColor()
        SmsCheck.Status.MISSED -> "Missed" to warnColor()
        SmsCheck.Status.CREDIT -> "Money received — not an expense" to MaterialTheme.colorScheme.onSurfaceVariant
        SmsCheck.Status.NOT_BANK_SENDER -> "Ignored: sent from a phone number" to MaterialTheme.colorScheme.onSurfaceVariant
        SmsCheck.Status.NOT_UNDERSTOOD -> "Couldn't read this format" to MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(
                        row.amount?.let { formatRupeeIndian(it) },
                        row.merchant?.takeIf { it != "Unknown" }
                    ).joinToString(" · ").ifEmpty { row.sender },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${row.sender} · " +
                        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(row.receivedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
            }
            if (row.status == SmsCheck.Status.MISSED) {
                TextButton(onClick = onImport) { Text("Import") }
            }
        }
    }
}


/**
 * Splitlee's own on/off switch for reading bank alerts from notifications,
 * the Android access state, and a log of exactly what was read.
 */
@Composable
private fun NotificationAlertsCard(
    switchOn: Boolean,
    accessGranted: Boolean,
    log: List<NotificationAlerts.Entry>,
    diagnostics: NotificationAlerts.Diagnostics,
    onSwitch: (Boolean) -> Unit,
    onOpenAccessSettings: () -> Unit,
    onClearLog: () -> Unit
) {
    val active = switchOn && accessGranted
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Read bank alerts from notifications",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Switch(checked = switchOn, onCheckedChange = onSwitch)
            }
            Text(
                text = when {
                    active -> "On. Only bank messages in your messaging app are read. Turn this off " +
                        "any time to pause it instantly."
                    switchOn -> "Almost done: allow Notification access for Splitlee in Android Settings."
                    accessGranted -> "Paused. Splitlee reads nothing while this is off. Android access is " +
                        "still granted. You can remove it too."
                    else -> "For banks that send alerts as RCS chats (they never reach the SMS inbox). " +
                        "Off by default."
                },
                style = MaterialTheme.typography.bodySmall
            )
            if (switchOn && !accessGranted) {
                TextButton(onClick = onOpenAccessSettings) { Text("Allow access") }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        text = "Splitlee greyed out in that list? Open App info for Splitlee, tap ⋮ " +
                            "(top right) → Allow restricted settings, then try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (active) {
                // Plain-language health check, so a missed alert can be
                // diagnosed on the phone without adb.
                val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                Text(
                    text = buildString {
                        append(
                            diagnostics.connectedAt?.let { "Listening since ${fmt.format(Date(it))}." }
                                ?: "Not connected yet. If this stays, turn Notification access off and on again."
                        )
                        if (diagnostics.lastEventAt != null && diagnostics.lastEvent != null) {
                            append("\nLast message checked ${fmt.format(Date(diagnostics.lastEventAt))}: ")
                            append(diagnostics.lastEvent)
                        } else {
                            append("\nNo message notifications checked yet.")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (accessGranted) {
                TextButton(onClick = onOpenAccessSettings) { Text("Remove access in Android Settings") }
            }

            if (switchOn || log.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Alerts Splitlee picked up",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (log.isNotEmpty()) TextButton(onClick = onClearLog) { Text("Clear") }
                }
                if (log.isEmpty()) {
                    Text(
                        "Nothing yet. Every payment alert Splitlee picks up will be listed here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    log.take(10).forEach { e ->
                        Column {
                            Text(
                                text = listOfNotNull(
                                    e.amount?.let { formatRupeeIndian(it) },
                                    e.merchant
                                ).joinToString(" · ").ifEmpty { e.sender },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${e.sender} · " +
                                    SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(e.time)) +
                                    " · ${e.outcome.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "Only these details are kept. Message text is never stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Shown BEFORE sending the user to Android's Notification access screen,
 * so the scary system warning ("can read all notifications") is explained
 * up front, in plain words, together with what Splitlee actually does.
 */
@Composable
private fun NotificationAccessDisclosure(onContinue: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read bank alerts from notifications?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Some banks (like IDBI) now send payment alerts as RCS chats. These never reach " +
                        "the SMS inbox, so the only way to see them is the notification your messaging " +
                        "app shows.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Android will warn that Splitlee can read all notifications. Android can't give " +
                        "access to only some of them, but Splitlee:",
                    style = MaterialTheme.typography.bodyMedium
                )
                listOf(
                    "ignores every app except your messaging app",
                    "ignores every chat except bank and payment senders, and uses only payment alerts from those",
                    "keeps only the amount, merchant and time, never the message text",
                    "sends nothing anywhere: no server, no analytics",
                    "lists every payment alert it picked up, and can be turned off here any time"
                ).forEach { line ->
                    Text("•  $line", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
