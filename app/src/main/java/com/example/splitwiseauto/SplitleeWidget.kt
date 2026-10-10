package com.example.splitwiseauto

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round

/**
 * Splitlee home-screen widget (RemoteViews; Glance needs a newer Compose than
 * this project uses). Three layouts — small / medium (primary) / large —
 * picked by the launcher from a size map on Android 12+, or by us from the
 * reported size on older versions.
 *
 * Colours are resources (res/values-…/widget_colors.xml): Material You on Android
 * 12+, Splitlee light/dark fallback below that. Money semantics (owed = green,
 * you owe / spent more = amber) are fixed colours that never follow the
 * wallpaper.
 *
 * Data: [SpendingSummary] — the same numbers the app shows.
 * Updates: [requestUpdate] (debounced) from Room's change tracker; a daily
 * alarm just after midnight (so "today" and the month total roll over even if
 * no expense is added — the system's 30-min refresh is often skipped while
 * the phone sleeps); clock/time-zone changes; and every app open.
 */
class SplitleeWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MIDNIGHT_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Log.i(TAG, "Day/clock changed (${intent.action}) -> refresh")
                refreshAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        scheduleMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        // Last widget removed: stop the daily alarm.
        context.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(context))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // Re-arm on every update: alarms are wiped on reboot / app update, and
        // the system sends APPWIDGET_UPDATE after both.
        scheduleMidnightRefresh(context)
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        scope.launch {
            try {
                val summary = SpendingSummary.load(context)
                appWidgetIds.forEach { id ->
                    manager.updateAppWidget(id, buildViews(context, summary, manager.getAppWidgetOptions(id)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Widget update failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    /** Resized: Android 12+ already switches layouts itself; older versions need a re-render. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) onUpdate(context, manager, intArrayOf(appWidgetId))
    }

    internal data class Comparison(val text: String, @ColorRes val color: Int)

    companion object {
        private const val TAG = "SplitleeWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainHandler = Handler(Looper.getMainLooper())
        private const val DEBOUNCE_MS = 1_500L
        private const val REQUEST_MIDNIGHT = 9_001
        @Volatile private var appContext: Context? = null
        private val debouncedUpdate = Runnable { appContext?.let { sendUpdate(it) } }

        /**
         * Re-render every placed widget. Debounced: a burst of DB writes (one
         * expense = expense + participants rows) causes ONE refresh. No-op
         * when no widget is placed.
         */
        fun requestUpdate(context: Context) {
            appContext = context.applicationContext
            mainHandler.removeCallbacks(debouncedUpdate)
            mainHandler.postDelayed(debouncedUpdate, DEBOUNCE_MS)
        }

        private const val ACTION_MIDNIGHT_REFRESH = "com.example.splitwiseauto.widget.MIDNIGHT_REFRESH"

        /** Immediate (non-debounced) refresh of every placed widget. */
        fun refreshAll(context: Context) = sendUpdate(context.applicationContext)

        private fun midnightIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context.applicationContext,
                REQUEST_MIDNIGHT,
                Intent(context.applicationContext, SplitleeWidget::class.java)
                    .setAction(ACTION_MIDNIGHT_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        /** Millis of the next local midnight, plus a few seconds of slack. */
        internal fun nextMidnightMillis(now: Calendar = Calendar.getInstance()): Long =
            (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        /**
         * One alarm per day, just after midnight. Inexact-but-allowed-while-idle
         * (no exact-alarm permission needed): Doze may delay it by a few
         * minutes, never until the next morning. Re-armed each time it fires
         * (the fire triggers onUpdate) and on every widget update. No-op when
         * no widget is placed.
         */
        fun scheduleMidnightRefresh(context: Context) {
            try {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app) ?: return
                if (manager.getAppWidgetIds(ComponentName(app, SplitleeWidget::class.java)).isEmpty()) return
                val alarms = app.getSystemService(AlarmManager::class.java) ?: return
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis(), midnightIntent(app))
            } catch (e: Exception) {
                Log.w(TAG, "Could not schedule midnight refresh: ${e.javaClass.simpleName}")
            }
        }

        private fun sendUpdate(app: Context) {
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, SplitleeWidget::class.java))
            if (ids.isEmpty()) return
            app.sendBroadcast(
                Intent(app, SplitleeWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        // ---- size classes (dp) ----
        // Thresholds match what launchers actually report for 2x2 / 4x2 / 4x3.
        private val SMALL = SizeF(110f, 100f)
        private val MEDIUM = SizeF(240f, 110f)
        private val LARGE = SizeF(270f, 170f)

        private fun buildViews(context: Context, s: SpendingSummary, options: Bundle?): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The launcher picks the largest layout that fits, and switches
                // instantly on resize — no extra update needed.
                return RemoteViews(
                    mapOf(
                        SMALL to small(context, s),
                        MEDIUM to medium(context, s),
                        LARGE to large(context, s)
                    )
                )
            }
            val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
            return when {
                w == 0 || h == 0 -> medium(context, s)          // size unknown yet: default design
                w >= LARGE.width && h >= LARGE.height -> large(context, s)
                w >= MEDIUM.width && h >= MEDIUM.height -> medium(context, s)
                else -> small(context, s)
            }
        }

        private fun small(context: Context, s: SpendingSummary) =
            RemoteViews(context.packageName, R.layout.widget_splitlee).apply {
                bindTotal(context, s)
                setOnClickPendingIntent(R.id.widget_root, openApp(context, MainActivity.TAB_HOME, 10))
            }

        private fun medium(context: Context, s: SpendingSummary) =
            RemoteViews(context.packageName, R.layout.widget_splitlee_medium).apply {
                bindTotal(context, s)
                setTextViewText(
                    R.id.widget_stats,
                    "${s.monthCount} transaction${if (s.monthCount == 1) "" else "s"} · " +
                        "${rupees(s.dailyAverage)} daily avg"
                )
                bindBalancesAndActions(context, s)
            }

        private fun large(context: Context, s: SpendingSummary) =
            RemoteViews(context.packageName, R.layout.widget_splitlee_large).apply {
                setTextViewText(R.id.widget_date, "Today · ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date())}")
                bindTotal(context, s)
                setTextViewText(R.id.widget_total_label, "TOTAL EXPENSES") // header already shows the date
                setTextViewText(R.id.widget_txn_count, s.monthCount.toString())
                setTextViewText(R.id.widget_daily_avg, rupees(s.dailyAverage))
                bindBalancesAndActions(context, s)
            }

        private fun RemoteViews.bindTotal(context: Context, s: SpendingSummary) {
            val month = SimpleDateFormat("MMM", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
            setTextViewText(R.id.widget_total_label, "TOTAL EXPENSES · $month")
            setTextViewText(R.id.widget_total, rupees(s.monthTotal))
            val cmp = compareWithYesterday(s.todayTotal, s.yesterdayTotal)
            setTextViewText(R.id.widget_vs_yesterday, cmp.text)
            setSemanticTextColor(context, R.id.widget_vs_yesterday, cmp.color)
            setOnClickPendingIntent(R.id.widget_total_block, openApp(context, MainActivity.TAB_HOME, 10))
        }

        private fun RemoteViews.bindBalancesAndActions(context: Context, s: SpendingSummary) {
            setTextViewText(R.id.widget_owed, rupees(s.owedToYou))
            setTextViewText(R.id.widget_owe, rupees(s.youOwe))
            setOnClickPendingIntent(R.id.widget_root, openApp(context, MainActivity.TAB_HOME, 10))
            setOnClickPendingIntent(R.id.widget_owed_block, openApp(context, MainActivity.TAB_FRIENDS, 13))
            setOnClickPendingIntent(R.id.widget_owe_block, openApp(context, MainActivity.TAB_FRIENDS, 13))
            setOnClickPendingIntent(R.id.widget_scan, activity(context, 11, Intent(context, QrScanActivity::class.java)))
            setOnClickPendingIntent(R.id.widget_add, activity(context, 12, Intent(context, ManualExpenseActivity::class.java)))
        }

        /**
         * Colour from a resource. On Android 12+ the launcher resolves it at
         * draw time, so it follows light/dark switches without an update.
         */
        private fun RemoteViews.setSemanticTextColor(context: Context, viewId: Int, @ColorRes color: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setColorStateList(viewId, "setTextColor", color)
            } else {
                setTextColor(viewId, ContextCompat.getColor(context, color))
            }
        }

        private fun rupees(amount: Double) = formatRupeeIndian(round(amount))

        /**
         * "▲ ₹120 vs yesterday" (amber) / "▼ ₹80 vs yesterday" (green) /
         * "Same as yesterday" (neutral). Today so far vs all of yesterday.
         */
        internal fun compareWithYesterday(today: Double, yesterday: Double): Comparison {
            val diff = round(today) - round(yesterday)
            return when {
                round(today) == 0.0 && round(yesterday) == 0.0 ->
                    Comparison("Nothing spent yesterday", R.color.widget_text_secondary)
                diff == 0.0 -> Comparison("Same as yesterday", R.color.widget_text_secondary)
                diff > 0 -> Comparison("▲ ${formatRupeeIndian(diff)} vs yesterday", R.color.widget_warn)
                else -> Comparison("▼ ${formatRupeeIndian(-diff)} vs yesterday", R.color.widget_positive)
            }
        }

        private fun openApp(context: Context, tab: Int, requestCode: Int): PendingIntent =
            activity(
                context,
                requestCode,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, tab)
                    // Reuse the running app (onNewIntent) instead of restarting it.
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )

        private fun activity(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
