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
 * Duplicate-safe: each SMS is fingerprinted (sender + body), so the live
 * receiver and the catch-up can both see the same SMS and it is handled once.
 */
object SmsIngestion {

    private const val TAG = "SmsIngestion"
    private const val PREFS = "sms_catch_up"
    private const val KEY_LAST_SCAN = "last_scan_millis"

    /** Never reach further back than this on catch-up. */
    private const val MAX_LOOKBACK_MILLIS = 3L * 24 * 60 * 60 * 1000

    /** Only attach the phone's current location to an SMS this fresh. */
    private const val LOCATION_FRESH_MILLIS = 10L * 60 * 1000

    private val catchUpLock = Mutex()

    /** Parses one SMS and routes it: Scan & Pay match first, else split prompt. */
    suspend fun process(context: Context, sender: String, body: String, receivedAtMillis: Long) {
        // Personal messages (from a phone number) never become expenses.
        if (!isTransactionalSender(sender)) return

        val debit = parseBankSms(body, sender, receivedAtMillis)
            ?.takeIf { it.type == "DEBIT" } ?: return

        if (isDuplicateSms(context, sender, body)) return

        // Scan & Pay matcher runs FIRST — it now always posts its own
        // "confirmed by bank" notification, so a matched SMS is never silent.
        if (PendingPaymentMatcher.tryConfirm(context, debit, body, receivedAtMillis)) return

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
    }

    /**
     * Reads inbox SMS received since the last scan (max 3 days back) and runs
     * each through [process]. The very first run only sets the starting point,
     * so installing/updating the app never floods you with old SMS.
     */
    suspend fun catchUpFromInbox(context: Context) = catchUpLock.withLock {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return@withLock

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_SCAN, -1L)
        if (last < 0) {
            prefs.edit().putLong(KEY_LAST_SCAN, now).apply()
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
            Log.w(TAG, "No SMS read access for catch-up")
        }
    }

    private fun isDuplicateSms(context: Context, sender: String, body: String): Boolean {
        // Normalised so the live broadcast and the inbox copy of the same SMS
        // produce the same key (whitespace can differ between the two).
        val key = "sms_${(sender.trim().uppercase() + body.replace(Regex("\\s+"), " ").trim()).hashCode()}"
        val prefs = context.getSharedPreferences("processed_sms", Context.MODE_PRIVATE)
        synchronized(this) {
            if (prefs.contains(key)) return true
            prefs.edit().putLong(key, System.currentTimeMillis()).commit()
        }
        return false
    }
}
