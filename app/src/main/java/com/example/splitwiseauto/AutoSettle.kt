package com.example.splitwiseauto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Auto settle-up: when money comes IN and it looks like a friend paying you
 * back, ask "Akhil paid you ₹1,250? Mark as settled". Never settles without
 * your tap: an SMS isn't proof of who paid.
 */
object AutoSettle {
    private const val TAG = "AutoSettle"
    private const val CHANNEL = "settle_channel"
    private const val PREFS = "auto_settle"
    private const val REMIND_TTL_MS = 30L * 24 * 60 * 60 * 1000
    private const val SAME_PROMPT_MS = 15L * 60 * 1000

    fun notificationId(friendId: Int) = 80_000 + friendId

    /** Called from the reminder sheet: remember what we asked this friend for. */
    fun rememberReminder(context: Context, friendName: String, amount: Double) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("rem_${friendName.trim().lowercase(Locale.ROOT)}", "$amount|${System.currentTimeMillis()}")
            .apply()
    }

    /** Called for every parsed CREDIT SMS / bank notification. */
    suspend fun onCredit(context: Context, credit: Transaction, body: String) {
        val amount = credit.amount.replace(",", "").toDoubleOrNull() ?: return
        val db = AppDatabase.getDatabase(context)
        val (friends, balances) = FriendBalances.compute(db.friendDao(), db.expenseDao(), db.settlementDao())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val debtors = friends.mapNotNull { f ->
            val owed = balances[f.id]?.netAmount ?: return@mapNotNull null
            if (owed <= 0.01) return@mapNotNull null
            val rem = prefs.getString("rem_${f.name.trim().lowercase(Locale.ROOT)}", null)
                ?.split('|')?.takeIf { it.size == 2 }
                ?.let { (a, t) -> a.toDoubleOrNull()?.takeIf { now - (t.toLongOrNull() ?: 0L) < REMIND_TTL_MS } }
            AutoSettleMatcher.Debtor(f.id, f.name, owed, prefs.getString("vpa_${f.id}", null), rem)
        }
        val match = AutoSettleMatcher.match(body, amount, debtors) ?: return

        // The same credit can arrive as an SMS and as a notification: ask once.
        val key = "asked_${match.friendId}_${String.format(Locale.US, "%.2f", amount)}"
        if (now - prefs.getLong(key, 0L) < SAME_PROMPT_MS) return
        prefs.edit().putLong(key, now).apply()

        Log.i(TAG, "credit matched a friend (${match.reason}); asking to settle")
        notify(context, match, amount)
    }

    private fun notify(context: Context, m: AutoSettleMatcher.Match, received: Double) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Paid back", NotificationManager.IMPORTANCE_HIGH))
        }
        val first = m.name.substringBefore(' ')
        val amt = formatRupeeIndianRaw(m.amount)
        val partial = m.amount < m.owed - 0.5
        val body = buildString {
            append("${formatRupeeIndianRaw(received)} came into your account")
            if (m.reason != AutoSettleMatcher.Reason.AMOUNT) append(" from $first") else append(", the exact amount $first owes")
            append(". ")
            append(if (partial) "Record $amt of ${formatRupeeIndianRaw(m.owed)} as paid back?" else "Mark $first as settled up?")
        }
        fun action(yes: Boolean) = PendingIntent.getBroadcast(
            context,
            notificationId(m.friendId) * 2 + if (yes) 1 else 0,
            Intent(context, AutoSettleReceiver::class.java)
                .setAction(if (yes) AutoSettleReceiver.ACTION_YES else AutoSettleReceiver.ACTION_NO)
                .putExtra(AutoSettleReceiver.EXTRA_FRIEND_ID, m.friendId)
                .putExtra(AutoSettleReceiver.EXTRA_AMOUNT, m.amount)
                .putExtra(AutoSettleReceiver.EXTRA_NAME, first)
                .putExtra(AutoSettleReceiver.EXTRA_VPA, m.vpaInSms),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            context, notificationId(m.friendId),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_splitlee)
            .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.splitlee_brand_green))
            .setContentTitle("💰 $first paid you $amt?")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, if (partial) "Yes, record $amt" else "Yes, settle", action(true))
            .addAction(0, "Not $first", action(false))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(m.friendId), n) }
    }
}

/** "Yes, settle" / "Not Akhil" on the paid-back notification. */
class AutoSettleReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_YES = "com.example.splitwiseauto.action.AUTO_SETTLE_YES"
        const val ACTION_NO = "com.example.splitwiseauto.action.AUTO_SETTLE_NO"
        const val EXTRA_FRIEND_ID = "friendId"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_NAME = "name"
        const val EXTRA_VPA = "vpa"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val friendId = intent.getIntExtra(EXTRA_FRIEND_ID, -1).takeIf { it >= 0 } ?: return
        NotificationManagerCompat.from(context).cancel(AutoSettle.notificationId(friendId))
        if (intent.action != ACTION_YES) return
        val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0).takeIf { it > 0.0 } ?: return
        val name = intent.getStringExtra(EXTRA_NAME) ?: "them"
        val vpa = intent.getStringExtra(EXTRA_VPA)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(app)
                // Re-check: never record more than they still owe right now.
                val (_, balances) = FriendBalances.compute(db.friendDao(), db.expenseDao(), db.settlementDao())
                val owed = balances[friendId]?.netAmount ?: 0.0
                val record = minOf(amount, owed)
                if (record > 0.01) {
                    db.settlementDao().insert(Settlement(friendId = friendId, amount = record))
                    // Remember their UPI ID so next time the match is certain.
                    if (vpa != null) {
                        app.getSharedPreferences("auto_settle", Context.MODE_PRIVATE).edit()
                            .putString("vpa_$friendId", vpa).apply()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Recorded ${formatRupeeIndianRaw(record)} from $name", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "$name is already settled up", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoSettle", "settle failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }
}
