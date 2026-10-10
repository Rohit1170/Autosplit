package com.example.splitwiseauto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Trust layer for reading bank alerts from notifications.
 *
 *  - [isOn]: Splitlee's OWN switch. Off by default. Even when Android's
 *    Notification access is granted, the listener does nothing unless this
 *    is on, so the user can pause it instantly without visiting Settings.
 *  - [log]: a short, on-device record of every bank alert the feature
 *    acted on (sender, amount, merchant, time, what happened) — never the
 *    message text. Shown in the app so the user can see exactly what was
 *    read. Not included in backups; cleared with [clearLog].
 */
object NotificationAlerts {

    private const val PREFS = "notification_alerts"
    private const val KEY_ON = "enabled"
    private const val KEY_LOG = "read_log"
    private const val MAX_LOG = 20

    enum class Outcome(val label: String) {
        ADDED("Added to To split"),
        MATCHED_SCAN_PAY("Confirmed a Scan & Pay payment"),
        DUPLICATE("Already added from SMS — skipped"),
        NOT_A_DEBIT("Not a payment — ignored"),
        NOT_UNDERSTOOD("Couldn't read this format — ignored")
    }

    data class Entry(
        val sender: String,
        val amount: String?,
        val merchant: String?,
        val time: Long,
        val outcome: Outcome
    )

    fun isOn(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
    }

    @Synchronized
    fun record(context: Context, entry: Entry) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = (listOf(entry) + readLog(context)).take(MAX_LOG)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("s", it.sender)
                    .put("a", it.amount ?: JSONObject.NULL)
                    .put("m", it.merchant ?: JSONObject.NULL)
                    .put("t", it.time)
                    .put("o", it.outcome.name)
            )
        }
        prefs.edit().putString(KEY_LOG, arr.toString()).apply()
    }

    fun readLog(context: Context): List<Entry> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOG, null)
            ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                sender = o.optString("s"),
                amount = if (o.isNull("a")) null else o.optString("a"),
                merchant = if (o.isNull("m")) null else o.optString("m"),
                time = o.optLong("t"),
                outcome = runCatching { Outcome.valueOf(o.optString("o")) }.getOrDefault(Outcome.NOT_UNDERSTOOD)
            )
        }
    }.getOrDefault(emptyList())

    fun clearLog(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LOG).apply()
    }

    // ---- Diagnostics (no message text, no contact names) ----

    private const val KEY_CONNECTED = "listener_connected_at"
    private const val KEY_LAST_EVENT = "last_event"

    fun markConnected(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CONNECTED, System.currentTimeMillis()).apply()
    }

    /** Remembers what happened to the most recent messaging-app notification. */
    fun noteEvent(context: Context, what: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_EVENT, "${System.currentTimeMillis()}|$what").apply()
    }

    data class Diagnostics(val connectedAt: Long?, val lastEventAt: Long?, val lastEvent: String?)

    fun diagnostics(context: Context): Diagnostics {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val connected = p.getLong(KEY_CONNECTED, -1L).takeIf { it > 0 }
        val raw = p.getString(KEY_LAST_EVENT, null)
        val at = raw?.substringBefore('|')?.toLongOrNull()
        return Diagnostics(connected, at, raw?.substringAfter('|'))
    }
}
