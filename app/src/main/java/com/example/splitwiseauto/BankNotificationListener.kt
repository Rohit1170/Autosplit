package com.example.splitwiseauto

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Reads bank alerts from the NOTIFICATIONS of messaging apps.
 *
 * Why: banks (IDBI first) now send many alerts as RCS business chats
 * ("IDBI Bank Limited" with a verified tick). RCS never goes through
 * Android's SMS system — no SMS_RECEIVED broadcast, not in the SMS inbox —
 * so [SmsReceiver] and the inbox catch-up can't see them. The notification
 * the messaging app shows is the one place the text is visible to us.
 *
 * Privacy:
 *  - Only notifications from messaging apps are looked at; everything else
 *    is ignored without reading its content.
 *  - Only senders that look like a bank / payment brand pass (a contact
 *    called "Rahul" never does), and the text must still parse as a debit.
 *  - Text is held in memory only and never logged; only the parsed fields
 *    are stored, exactly like SMS.
 *  - Same pipeline as SMS ([SmsIngestion.process]), whose duplicate check
 *    makes sure an alert that arrives as BOTH SMS and notification is
 *    counted once.
 *
 * Needs "Notification access", which the user turns on in Settings
 * ([openSettings]); Android binds this service only after that.
 */
class BankNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(SmsIngestion.TAG, "notification listener connected")
        NotificationAlerts.markConnected(applicationContext)
        // Pick up bank alerts that are still in the shade from while we were
        // disconnected (phone restart, app update, access just switched on).
        if (!NotificationAlerts.isOn(applicationContext)) return
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        active.forEach { handle(it, maxAgeMillis = RECENT_ON_CONNECT_MILLIS) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn, maxAgeMillis = RECENT_LIVE_MILLIS)
    }

    private fun handle(sbn: StatusBarNotification, maxAgeMillis: Long) {
        try {
            // Splitlee's own switch: with it off, nothing is read at all —
            // even though Android's access may still be granted.
            // Package name only — other apps' notification content is never opened.
            if (!isMessagingApp(applicationContext, sbn.packageName)) return
            val ctx = applicationContext
            if (!NotificationAlerts.isOn(ctx)) {
                Log.i(SmsIngestion.TAG, "notification: messaging app, but Splitlee's switch is off")
                NotificationAlerts.noteEvent(ctx, "Skipped — the switch in Splitlee is off")
                return
            }
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

            val now = System.currentTimeMillis()
            val all = extractMessages(n, sbn.postTime)
            if (all.isEmpty()) {
                Log.i(SmsIngestion.TAG, "notification: ${sbn.packageName} — no readable message text")
                NotificationAlerts.noteEvent(ctx, "Messaging notification had no readable text")
                return
            }
            // RCS timestamps come from the bank's server, which can be a few
            // minutes ahead of the phone's clock — allow that skew.
            val recent = all.filter { now - it.time in -CLOCK_SKEW_MILLIS..maxAgeMillis }
            val messages = recent.filter { isBankNotificationSender(it.sender) }
            Log.i(
                SmsIngestion.TAG,
                "notification: ${sbn.packageName} msgs=${all.size} recent=${recent.size} bank=${messages.size}"
            )
            if (messages.isEmpty()) {
                NotificationAlerts.noteEvent(
                    ctx,
                    if (recent.isEmpty()) "Message too old to import" else "Not from a bank sender — ignored"
                )
                return
            }

            val app = applicationContext
            scope.launch {
                messages.forEach { m ->
                    try {
                        SmsIngestion.process(app, m.sender, m.body, m.time, fromNotification = true)
                    } catch (e: Exception) {
                        Log.e(SmsIngestion.TAG, "notification alert failed: ${e.javaClass.simpleName}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(SmsIngestion.TAG, "notification read failed: ${e.javaClass.simpleName}")
        }
    }

    private data class Msg(val sender: String, val body: String, val time: Long)

    /**
     * Messaging apps use MessagingStyle (several messages per conversation
     * notification); fall back to title + big text for plain notifications.
     */
    private fun extractMessages(n: Notification, postTime: Long): List<Msg> {
        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()

        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        } catch (e: Exception) { null }
        if (style != null && style.messages.isNotEmpty()) {
            val conversation = style.conversationTitle?.toString()?.trim()
            return style.messages.takeLast(MAX_MESSAGES_PER_NOTIFICATION).mapNotNull { m ->
                val body = m.text?.toString()?.trim().orEmpty()
                if (body.isEmpty()) return@mapNotNull null
                // In a 1:1 business chat the message person is the bank.
                val sender = m.person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: conversation?.takeIf { it.isNotEmpty() }
                    ?: title
                Msg(sender, body, if (m.timestamp > 0) m.timestamp else postTime)
            }
        }

        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() || body.isEmpty()) return emptyList()
        return listOf(Msg(title, body, postTime))
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private const val RECENT_LIVE_MILLIS = 2L * 60 * 60 * 1000
        private const val RECENT_ON_CONNECT_MILLIS = 12L * 60 * 60 * 1000
        private const val MAX_MESSAGES_PER_NOTIFICATION = 5
        private const val CLOCK_SKEW_MILLIS = 15L * 60 * 1000

        /** SMS / RCS apps shipped by Google and the big Indian-market OEMs. */
        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",     // Google Messages (SMS + RCS)
            "com.samsung.android.messaging",         // Samsung Messages
            "com.android.mms",                       // Xiaomi/Redmi/POCO, Oppo, Realme, Vivo
            "com.android.messaging",                 // AOSP
            "com.oneplus.mms",
            "com.coloros.mms",
            "com.motorola.messaging",
            "com.microsoft.android.smsorganizer",    // SMS Organizer
            "com.truecaller"
        )

        fun isMessagingApp(context: Context, pkg: String): Boolean {
            if (pkg == context.packageName) return false
            if (pkg in MESSAGING_PACKAGES) return true
            val defaultSms = try { Telephony.Sms.getDefaultSmsPackage(context) } catch (e: Exception) { null }
            return pkg == defaultSms
        }

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Opens Android's Notification access screen (straight to Splitlee on 11+). */
        fun openSettings(context: Context) {
            val component = ComponentName(context, BankNotificationListener::class.java)
            val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            } else null
            val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            listOfNotNull(direct, fallback).forEach { intent ->
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (e: Exception) { /* try the next one */ }
            }
        }
    }
}

private val TRAI_HEADER = Regex("""^[A-Z]{2}-[A-Z0-9]{3,9}(?:-[STG])?$""")
private val BRAND_WORDS = setOf(
    "BANK", "SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "IDBI", "PNB", "CANARA", "BARODA",
    "BOB", "INDUSIND", "FEDERAL", "IDFC", "RBL", "BANDHAN", "UCO", "YESBANK",
    "PAYTM", "PHONEPE", "GPAY", "CRED", "SLICE", "ONECARD", "MOBIKWIK", "AMAZONPAY",
    "CARD", "CARDS", "UPI"
)

/**
 * True for notification titles that are a bank / payment brand:
 *  - a TRAI sender code shown as-is ("JD-IDBIBK-S"), or
 *  - an RCS brand name containing a bank word ("IDBI Bank Limited").
 * Saved contacts ("Rahul", "Mom") and phone numbers are rejected, so
 * a forwarded or fake "debited" text from a person never counts.
 */
fun isBankNotificationSender(title: String): Boolean {
    val t = title.trim().uppercase(Locale.ROOT)
    if (t.isEmpty()) return false
    if (!isTransactionalSender(t)) return false // phone numbers, promo -P
    if (TRAI_HEADER.matches(t)) return true
    val words = t.split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
    if (words.any { it in BRAND_WORDS }) return true
    val joined = words.joinToString("")
    return joined.contains("AMAZONPAY") || joined.contains("GOOGLEPAY")
}
