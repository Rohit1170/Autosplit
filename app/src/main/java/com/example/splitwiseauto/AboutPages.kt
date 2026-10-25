package com.example.splitwiseauto

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** What changed in each version. Newest first. Add a block for every release. */
object Changelog {
    data class Release(val version: String, val date: String, val changes: List<String>)

    val releases = listOf(
        Release(
            "1.5", "Oct 2026", listOf(
                "Restore and back up in one step: sign in after the intro and your data comes back",
                "Fixed: after restoring, the intro no longer shows again",
                "Empty phones no longer upload empty backups over your real ones",
                "Delete any single backup from Settings → Backup → Your backups"
            )
        ),
        Release(
            "1.4", "Oct 2026", listOf(
                "Smooth motion: screens slide in from the right and back out",
                "Bottom sheets rise into place",
                "Menus like the trip switcher open with a little more life"
            )
        ),
        Release(
            "1.3", "Oct 2026", listOf(
                "Back up to your own Google Drive and restore on a new phone or after reinstalling",
                "New backup step in the intro, so you can restore straight away",
                "Settings: Personalise (app and widget themes) and About (version, what's new, privacy policy)",
                "App permissions screen takes you straight to the right switch",
                "Hide amounts now also stacks your account cards into one",
                "Remove friend and Delete group now use hold-to-confirm",
                "Saving your UPI ID closes the sheet and confirms it"
            )
        ),
        Release(
            "1.2", "Oct 2026", listOf(
                "Hold to delete an expense: the button fills red with rising vibration"
            )
        ),
        Release(
            "1.1", "Oct 2026", listOf(
                "First release",
                "Expenses added automatically from bank SMS and bank app notifications",
                "Split with friends and groups, settle up, and remind with your UPI QR",
                "Auto settle-up when a friend's payment SMS arrives",
                "Scan & Pay any UPI QR",
                "Trips, map of your spending, insights and the home-screen widget",
                "Foreign card spends converted to rupees",
                "Hide amounts, dark/light and Material You themes"
            )
        )
    )
}

/** The privacy policy, in plain words. Keep it true to what the app does. */
object PrivacyPolicy {
    const val UPDATED = "22 October 2026"
    const val CONTACT = "rohitraj776407@gmail.com"

    val sections: List<Pair<String, String>> = listOf(
        "The short version" to
            "Splitlee keeps your data on your phone. We don't have a server, we don't collect your data, and there are no ads, analytics or trackers.",
        "Bank SMS" to
            "With your permission, Splitlee reads incoming SMS on your phone to find bank transactions. It saves only the transaction (amount, merchant, card or account ending, date). The SMS text itself never leaves your phone.",
        "Notifications from other apps" to
            "If you turn on notification access, Splitlee looks only at notifications from messaging apps, to catch bank alerts sent as chat messages instead of SMS. Other notifications are ignored, and nothing is sent anywhere.",
        "Location" to
            "If you allow location, Splitlee saves where an expense happened so it can show it on the map. It stays on your phone.",
        "Camera" to
            "Used only to scan UPI QR codes. No photos are taken or saved.",
        "Contacts" to
            "When you add a friend from your contacts, Splitlee saves only the name and number you pick. It doesn't read your contact list.",
        "Google Drive backup (optional)" to
            "Backup is off unless you turn it on. When it's on, your Splitlee data is saved to a private, hidden Splitlee folder in your own Google Drive. Splitlee only gets access to that folder, not your other files, email or contacts. The backup is stored by Google and protected by your Google account. We never receive it. You can delete your backups or turn backup off at any time in Settings → Backup.",
        "Maps" to
            "The map uses Google Maps, so map tiles are loaded from Google when you open it.",
        "Payments" to
            "Scan & Pay hands the payment to your UPI app. Splitlee never sees your UPI PIN or bank login.",
        "Your control" to
            "You can change permissions any time in Settings → App permissions. Uninstalling Splitlee deletes all its data from your phone (your Drive backup stays until you delete it).",
        "Android's own backup" to
            "If your phone's Google backup is on, Android may also keep an end-to-end encrypted copy of Splitlee's data, as it does for other apps. Splitlee only allows this when the copy is encrypted with your screen lock.",
        "Contact" to
            "Questions? Email $CONTACT"
    )
}

fun appVersionLabel(context: Context): String = runCatching {
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode
    else @Suppress("DEPRECATION") info.versionCode.toLong()
    "${info.versionName ?: "?"} (build $code)"
}.getOrDefault("?")

/** Full-screen "What's new" or "Privacy policy". */
class InfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_CHANGELOG
        setContent {
            SplitleeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    InfoScreen(page = page, onBack = { finish() })
                }
            }
        }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_CHANGELOG = "changelog"
        const val PAGE_PRIVACY = "privacy"

        fun open(context: Context, page: String) {
            context.startActivity(Intent(context, InfoActivity::class.java).putExtra(EXTRA_PAGE, page))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun InfoScreen(page: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val accent = Brand.accent()
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

        if (page == InfoActivity.PAGE_PRIVACY) {
            Text("Privacy policy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            Text(
                "Updated ${PrivacyPolicy.UPDATED}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
            )
            PrivacyPolicy.sections.forEachIndexed { i, (title, body) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .modernCard(
                            base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            radius = 18.dp,
                            tint = if (i == 0) accent else null,
                            tintAlpha = 0.12f
                        )
                        .padding(16.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Email us",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${PrivacyPolicy.CONTACT}"))
                                    .putExtra(Intent.EXTRA_SUBJECT, "Splitlee privacy question")
                            )
                        }
                    }
                    .padding(8.dp)
            )
        } else {
            Text("What's new", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
            Text(
                "You're on ${appVersionLabel(context)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
            )
            Changelog.releases.forEachIndexed { i, r ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .modernCard(
                            base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            radius = 18.dp,
                            tint = if (i == 0) accent else null,
                            tintAlpha = 0.12f
                        )
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Version ${r.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (i == 0) {
                            Text(
                                "LATEST",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(r.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    r.changes.forEach { line ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(line, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
