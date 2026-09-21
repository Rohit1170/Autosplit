package com.example.splitwiseauto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Status indicator shown while a Scan & Pay payment is in progress — payee,
 * amount, a one-line "complete it in the dialer" reminder, and a Cancel
 * action. It does NOT read or inject into the dialer/call UI itself;
 * there's no public API for that, and the locked design (honest handoff,
 * not automation) doesn't want one. It only keeps SplitWiseAuto's own
 * context visible while the person finishes the call, and lets them back
 * out via Cancel.
 *
 * THIS IS A NOTIFICATION, NOT A FLOATING OVERLAY. It used to be a
 * WindowManager.addView TYPE_APPLICATION_OVERLAY card (a SYSTEM_ALERT_WINDOW
 * "draw over other apps" overlay). That was switched to a plain ongoing
 * notification after real-device testing showed several OEM Android skins
 * (confirmed on a MIUI device) refuse to show the soft keyboard for the
 * *99# "Enter UPI ID" system dialog for as long as ANY app has an active
 * draw-over-other-apps overlay on screen — this is a deliberate anti-overlay
 * (tapjacking/PIN-capture) protection in those skins, and it fired
 * regardless of this window's focus flags, because it's keyed on the
 * overlay's mere presence, not on whether it can take focus. Turning the
 * overlay permission off entirely restored the keyboard on that device,
 * which is what confirmed the cause. A notification carries the same
 * "payment in progress, tap to cancel" information without ever drawing a
 * window on top of the dialer/USSD screen, so it can't trigger that
 * protection. This also means SYSTEM_ALERT_WINDOW is no longer needed for
 * this feature at all (POST_NOTIFICATIONS, already granted, covers it).
 */
class PaymentOverlayService : Service() {

    private var currentPayee: String? = null
    private var currentAmount: Double = 0.0

    companion object {
        private const val EXTRA_PAYEE = "payee"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_STATUS = "status"
        private const val ACTION_CANCEL = "com.example.splitwiseauto.action.CANCEL_SCAN_PAY"

        private const val CHANNEL_ID = "scan_pay_status_channel"
        private const val NOTIFICATION_ID = 424_242

        var instance: PaymentOverlayService? = null

        /** Set by whoever calls [show]; cleared in [stop]. */
        var onCancelClicked: (() -> Unit)? = null

        // Kept only so existing callers that gated the old floating card on
        // "do we have the overlay permission" compile unchanged. A
        // notification doesn't need it, so this now always returns true.
        @Suppress("UNUSED_PARAMETER")
        fun canShowOverlay(context: Context): Boolean = true

        fun show(context: Context, payeeName: String?, amount: Double, status: String) {
            val intent = Intent(context, PaymentOverlayService::class.java)
                .putExtra(EXTRA_PAYEE, payeeName)
                .putExtra(EXTRA_AMOUNT, amount)
                .putExtra(EXTRA_STATUS, status)
            context.startService(intent)
        }

        fun updateStatus(text: String) {
            instance?.renotify(text)
        }

        fun stop(context: Context) {
            onCancelClicked = null
            context.stopService(Intent(context, PaymentOverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scan & Pay status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while a Scan & Pay payment is in progress"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            onCancelClicked?.invoke()
            stop(this)
            return START_NOT_STICKY
        }

        val payee = intent?.getStringExtra(EXTRA_PAYEE)
        val amount = intent?.getDoubleExtra(EXTRA_AMOUNT, 0.0) ?: 0.0
        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Complete the payment in the dialer"
        currentPayee = payee
        currentAmount = amount
        renotify(status)
        return START_NOT_STICKY
    }

    private fun renotify(status: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = if (currentPayee != null) {
            "Paying $currentPayee · ₹${currentAmount.toBigDecimal().stripTrailingZeros().toPlainString()}"
        } else {
            "Paying ₹${currentAmount.toBigDecimal().stripTrailingZeros().toPlainString()}"
        }

        val cancelIntent = Intent(this, PaymentOverlayService::class.java).setAction(ACTION_CANCEL)
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Cancel", cancelPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and here —
            // the payment flow itself doesn't depend on this notification,
            // so just skip it silently.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
