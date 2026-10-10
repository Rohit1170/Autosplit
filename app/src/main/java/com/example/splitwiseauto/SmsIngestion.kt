package com.example.splitwiseauto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one SMS -> expense pipeline. Used by:
 *  - [SmsReceiver] for live SMS, and
 *  - [catchUpFromInbox] when the app opens, to pick up bank SMS the receiver
 *    never got (the phone's battery saver / "autostart" rules can stop a
 *    background app from receiving SMS — the bank SMS is then in the inbox
 *    but the app never saw it).
 *
 * Also used by [BankNotificationListener] for alerts that only exist as a
 * notification (RCS business messages never reach the SMS system).
 *
 * Duplicate-safe: each SMS is fingerprinted (sender + body, plus
 * sender-independent body / bank-reference keys), so the live receiver, the
 * catch-up and the notification listener can all see the same alert and it
 * is handled once.
 */
object SmsIngestion {

    // One tag for every SMS decision, so `adb logcat -s SplitleeSms` shows
    // exactly where an SMS stopped. Logs the sender code (phone numbers
    // masked) and the parsed amount — never the message text.
    const val TAG = "SplitleeSms"

    /** "JD-IDBIBK-S" stays readable; a personal number becomes "+91******3210". */
    fun maskSender(sender: String): String {
        val digits = sender.count { it.isDigit() }
        return if (digits >= 7 && sender.none { it.isLetter() }) {
            sender.take(3) + "*".repeat((sender.length - 7).coerceAtLeast(0)) + sender.takeLast(4)
        } else sender
    }
    private const val PREFS = "sms_catch_up"
    private const val KEY_LAST_SCAN = "last_scan_millis"

    /** Never reach further back than this on catch-up. */
    private const val MAX_LOOKBACK_MILLIS = 3L * 24 * 60 * 60 * 1000

    /** Only attach the phone's current location to an SMS this fresh. */
    private const val LOCATION_FRESH_MILLIS = 10L * 60 * 1000

    private val catchUpLock = Mutex()

    /** Parses one SMS and routes it: Scan & Pay match first, else split prompt. */
    suspend fun process(
        context: Context,
        sender: String,
        body: String,
        receivedAtMillis: Long,
        fromNotification: Boolean = false
    ) {
        val who = maskSender(sender) + if (fromNotification) " via notification" else ""
        // Personal messages (from a phone number / a contact) never become
        // expenses. Notifications show a display name instead of the sender
        // code, so they get the stricter brand-name check.
        val senderOk = if (fromNotification) {
            isBankNotificationSender(sender)
        } else {
            isTransactionalSender(sender)
        }
        if (!senderOk) {
            Log.i(TAG, "[$who] dropped: sender is not a bank/UPI sender")
            if (fromNotification) NotificationAlerts.noteEvent(context, "Not from a bank sender — ignored")
            return
        }

        // Transparency log for alerts read from notifications (no text).
        // Logged once per alert: messaging apps re-post the same notification
        // many times (new message in the chat, marked as read…).
        fun note(outcome: NotificationAlerts.Outcome, t: Transaction?) {
            if (!fromNotification || !firstSightingOfNotification(context, body)) return
            NotificationAlerts.record(
                context,
                NotificationAlerts.Entry(
                    sender = sender,
                    amount = t?.amount,
                    merchant = t?.merchant?.takeIf { it != "Unknown" },
                    time = receivedAtMillis,
                    outcome = outcome
                )
            )
        }

        val parsed = parseBankSms(body, sender, receivedAtMillis)
        if (parsed == null) {
            Log.i(TAG, "[$who] dropped: not recognised as a transaction (len=${body.length})")
            if (fromNotification) {
                NotificationAlerts.noteEvent(context, "Bank message read, but it isn't a payment we recognise")
            }
            // Not logged for notifications: a bank chat also carries offers,
            // OTPs and reminders — listing those would be noise.
            return
        }
        if (parsed.type != "DEBIT") {
            Log.i(TAG, "[$who] skipped: ${parsed.type} of Rs ${parsed.amount} (only debits become expenses)")
            note(NotificationAlerts.Outcome.NOT_A_DEBIT, parsed)
            if (fromNotification) NotificationAlerts.noteEvent(context, "Bank message read — not a payment (credit)")
            return
        }
        val debit = parsed

        if (isDuplicateSms(context, sender, body, debit)) {
            Log.i(TAG, "[$who] skipped: already handled (Rs ${debit.amount})")
            note(NotificationAlerts.Outcome.DUPLICATE, debit)
            if (fromNotification) NotificationAlerts.noteEvent(context, "Payment already added earlier — skipped")
            return
        }

        // Scan & Pay matcher runs FIRST — it now always posts its own
        // "confirmed by bank" notification, so a matched SMS is never silent.
        if (PendingPaymentMatcher.tryConfirm(context, debit, body, receivedAtMillis)) {
            Log.i(TAG, "[$who] matched a Scan & Pay payment (Rs ${debit.amount})")
            note(NotificationAlerts.Outcome.MATCHED_SCAN_PAY, debit)
            return
        }
        Log.i(TAG, "[$who] debit Rs ${debit.amount} at ${debit.merchant} -> split prompt")

        val isFresh = System.currentTimeMillis() - receivedAtMillis < LOCATION_FRESH_MILLIS
        val location = if (isFresh) LocationHelper.getCurrentLocation(context) else null
        val withLocation = debit.copy(latitude = location?.latitude, longitude = location?.longitude)

        val pendingId = AppDatabase.getDatabase(context).pendingExpenseNotificationDao().insert(
            PendingExpenseNotification(
                amount = withLocation.amount,
                merchant = withLocation.merchant,
                bank = withLocation.bank,
                date = withLocation.date,
                balance = withLocation.balance,
                upi = withLocation.upi,
                accountIdentifier = withLocation.accountIdentifier,
                latitude = withLocation.latitude,
                longitude = withLocation.longitude,
                timestampMillis = withLocation.receivedAtMillis
            )
        ).toInt()

        showExpenseNotification(context, withLocation, pendingId)
        note(NotificationAlerts.Outcome.ADDED, debit)
        if (fromNotification) NotificationAlerts.noteEvent(context, "Payment of Rs ${debit.amount} added to To split")
        Log.i(TAG, "[$who] saved to 'To split' (id=$pendingId) and notification requested")
    }

    /**
     * Reads inbox SMS received since the last scan (max 3 days back) and runs
     * each through [process]. The very first run only sets the starting point,
     * so installing/updating the app never floods you with old SMS.
     */
    suspend fun catchUpFromInbox(context: Context) = catchUpLock.withLock {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "catch-up: READ_SMS not granted")
            return@withLock
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_SCAN, -1L)
        if (last < 0) {
            prefs.edit().putLong(KEY_LAST_SCAN, now).apply()
            Log.i(TAG, "catch-up: first run, starting point set (older SMS are not imported)")
            return@withLock
        }
        val from = maxOf(last - 60_000L, now - MAX_LOOKBACK_MILLIS) // 1-min overlap

        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(from.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                Log.i(TAG, "catch-up: ${c.count} inbox SMS since last check")
                while (c.moveToNext()) {
                    val sender = c.getString(0) ?: continue
                    val body = c.getString(1) ?: continue
                    val date = c.getLong(2)
                    try {
                        process(context, sender, body, date)
                    } catch (e: Exception) {
                        Log.e(TAG, "Catch-up failed for one SMS: ${e.javaClass.simpleName}")
                    }
                }
            }
            prefs.edit().putLong(KEY_LAST_SCAN, now).apply()
        } catch (e: SecurityException) {
            Log.w(TAG, "catch-up: no SMS read access")
        }
    }

    // Normalised so the live broadcast and the inbox copy of the same SMS
    // produce the same key (whitespace can differ between the two).
    private fun fingerprint(sender: String, body: String): String =
        "sms_${(sender.trim().uppercase() + normalizeBody(body)).hashCode()}"

    private fun normalizeBody(body: String): String = body.replace(Regex("\\s+"), " ").trim()

    /**
     * Sender-independent keys, so the SAME alert seen twice through different
     * doors (SMS from "JD-IDBIBK-S" and the RCS/notification copy from
     * "IDBI Bank Limited") is handled once: the message text itself, and the
     * bank's UPI/transaction reference when there is one.
     */
    internal fun crossChannelKeys(body: String, parsed: Transaction?): List<String> {
        val keys = mutableListOf("body_${normalizeBody(body).lowercase().hashCode()}")
        val ref = parsed?.upi
        if (parsed != null && ref != null && ref != "Unknown" && ref.count { it.isDigit() } >= 6) {
            keys += "ref_${ref.uppercase()}_${parsed.amount}"
        }
        return keys
    }

    /** True only the first time this alert's notification copy is seen. */
    private fun firstSightingOfNotification(context: Context, body: String): Boolean {
        val key = "seen_notif_" + crossChannelKeys(body, null).first()
        val prefs = context.getSharedPreferences("processed_sms", Context.MODE_PRIVATE)
        synchronized(this) {
            if (prefs.contains(key)) return false
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        }
        return true
    }

    /** Read-only: has this SMS already been turned into an expense/prompt? */
    fun isAlreadyHandled(context: Context, sender: String, body: String): Boolean {
        val prefs = context.getSharedPreferences("processed_sms", Context.MODE_PRIVATE)
        return prefs.contains(fingerprint(sender, body)) ||
            crossChannelKeys(body, null).any { prefs.contains(it) }
    }

    private fun isDuplicateSms(context: Context, sender: String, body: String, parsed: Transaction): Boolean {
        val keys = listOf(fingerprint(sender, body)) + crossChannelKeys(body, parsed)
        val prefs = context.getSharedPreferences("processed_sms", Context.MODE_PRIVATE)
        synchronized(this) {
            if (keys.any { prefs.contains(it) }) return true
            val now = System.currentTimeMillis()
            prefs.edit().apply { keys.forEach { putLong(it, now) } }.commit()
        }
        return false
    }
}
