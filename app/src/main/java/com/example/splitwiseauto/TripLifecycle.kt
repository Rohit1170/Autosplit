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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

private const val DAY = 86_400_000L

/** Closing a trip on a chosen end date (shared by Manage trips, the reminder and auto-close). */
object TripClosing {

    fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    /** Suggested end date: the trip's last expense (or its start when empty). */
    suspend fun suggestedEnd(context: Context, trip: Trip): Long =
        AppDatabase.getDatabase(context).expenseDao().lastTripExpenseTime(trip.id) ?: trip.rangeStart

    /** Expenses in the trip dated after [endMillis]'s day. */
    suspend fun countAfter(context: Context, trip: Trip, endMillis: Long): Int =
        AppDatabase.getDatabase(context).expenseDao().countTripExpensesAfter(trip.id, endOfDay(endMillis))

    /**
     * Closes [trip] with its last day = [endMillis]. With [moveLaterToMain],
     * trip expenses dated after that day go back to Main (they happened after
     * you were back). New expenses go to Main from now on.
     */
    suspend fun close(context: Context, trip: Trip, endMillis: Long, moveLaterToMain: Boolean): Trip {
        val db = AppDatabase.getDatabase(context)
        val end = endOfDay(endMillis).coerceAtLeast(trip.rangeStart)
        val closed = trip.copy(closedAtMillis = System.currentTimeMillis(), endMillis = end)
        db.withTransaction {
            if (moveLaterToMain) db.expenseDao().moveTripExpensesAfterToMain(trip.id, end)
            db.tripDao().update(closed)
        }
        ActiveTripStore.syncWith(context, closed)
        TripReminders.cancel(context, trip.id)
        return closed
    }
}

/**
 * Notices trips you've stopped using:
 *  - no trip expense for [NOTIFY_AFTER_DAYS] days while you're spending in
 *    Main (or [QUIET_DAYS] with no spending at all) → "Is your Goa trip
 *    over?" with the last trip expense date as the suggested end date;
 *  - still open after [AUTO_CLOSE_DAYS] idle days → closed automatically on
 *    the last trip expense date (you're told, and can reopen it).
 */
object TripReminders {
    const val NOTIFY_AFTER_DAYS = 3
    const val QUIET_DAYS = 7
    const val AUTO_CLOSE_DAYS = 14
    private const val REMIND_EVERY_DAYS = 3

    private const val CHANNEL = "trip_channel"
    private const val PREFS = "trip_reminders"
    private const val UNIQUE_WORK = "trip_inactivity"

    fun notificationId(tripId: Int) = 70_000 + tripId

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<TripInactivityWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context, tripId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId(tripId))
    }

    suspend fun check(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        db.tripDao().getAll().filter { !it.isClosed }.forEach { trip ->
            val last = db.expenseDao().lastTripExpenseTime(trip.id) ?: trip.rangeStart
            val idleDays = (now - last) / DAY
            when {
                idleDays >= AUTO_CLOSE_DAYS -> {
                    // Nothing was added after `last`, so nothing needs moving.
                    val closed = TripClosing.close(context, trip, last, moveLaterToMain = false)
                    notify(
                        context, closed,
                        title = "${trip.name} trip closed",
                        text = "No trip expenses since ${dayLabel(last)}, so it ended on that day. " +
                            "New spending goes to Main. Reopen it in Settings > Manage trips.",
                        closeOn = null
                    )
                }
                idleDays >= NOTIFY_AFTER_DAYS -> {
                    val movedOn = db.expenseDao().countMainExpensesAfter(last) > 0
                    val lastNotified = prefs.getLong("n_${trip.id}", 0L)
                    if ((movedOn || idleDays >= QUIET_DAYS) && now - lastNotified >= REMIND_EVERY_DAYS * DAY) {
                        prefs.edit().putLong("n_${trip.id}", now).apply()
                        notify(
                            context, trip,
                            title = "Is your ${trip.name} trip over?",
                            text = "Last trip expense was on ${dayLabel(last)}. Close it so new spending stays in Main.",
                            closeOn = last
                        )
                    }
                }
            }
        }
    }

    private fun dayLabel(millis: Long): String =
        java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(millis))

    private fun notify(context: Context, trip: Trip, title: String, text: String, closeOn: Long?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Trip reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        // Tap: Manage trips with the close dialog open (pick another date there).
        val open = PendingIntent.getActivity(
            context,
            notificationId(trip.id),
            Intent(context, ManageTripsActivity::class.java)
                .putExtra(ManageTripsActivity.EXTRA_CLOSE_TRIP_ID, if (closeOn != null) trip.id else -1)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_splitlee)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (closeOn != null) {
            val closeNow = PendingIntent.getBroadcast(
                context,
                notificationId(trip.id),
                Intent(context, TripCloseReceiver::class.java)
                    .putExtra(TripCloseReceiver.EXTRA_TRIP_ID, trip.id)
                    .putExtra(TripCloseReceiver.EXTRA_END, closeOn),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Close on ${dayLabel(closeOn)}", closeNow)
            builder.addAction(0, "Pick date", open)
        }
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(trip.id), builder.build()) }
    }
}

class TripInactivityWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        TripReminders.check(applicationContext)
        return Result.success()
    }
}

/** "Close on 5 Oct" from the reminder: closes without opening the app. */
class TripCloseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tripId = intent.getIntExtra(EXTRA_TRIP_ID, -1)
        val end = intent.getLongExtra(EXTRA_END, -1L)
        if (tripId < 0 || end < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val trip = AppDatabase.getDatabase(context).tripDao().getById(tripId)
                if (trip != null && !trip.isClosed) {
                    TripClosing.close(context.applicationContext, trip, end, moveLaterToMain = true)
                }
                TripReminders.cancel(context, tripId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_END = "end_millis"
    }
}
