package com.example.splitwiseauto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

fun showExpenseNotification(
    context: Context,
    transaction: Transaction,
    pendingNotificationId: Int,
    // "scan_pay" when this notification is for a Scan & Pay payment the bank
    // SMS just confirmed — lets the confirmation screen link the saved expense
    // back to its PendingPayment. null for ordinary SMS-detected expenses.
    source: String? = null
) {

    val channelId = "expense_channel"

    // NotificationChannel itself is an API 26+ class — on API 24/25 devices
    // (this app's minSdk is 24) even constructing one throws at runtime.
    // Channels don't exist below O, and NotificationCompat.Builder.build()
    // below works fine without one, so this whole block is skippable
    // rather than something to route around with @RequiresApi (which only
    // silences the lint warning, it doesn't add the runtime check needed
    // to actually avoid the crash on those older devices).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Expense Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager.createNotificationChannel(channel)
    }

    // Open confirmation screen when notification is tapped
    val intent = Intent(
        context,
        ExpenseConfirmationActivity::class.java
    ).apply {
        putExtra("amount", transaction.amount)
        putExtra("merchant", transaction.merchant)
        putExtra("bank", transaction.bank)
        putExtra("date", transaction.date)
        putExtra("balance", transaction.balance)
        putExtra("upi", transaction.upi)
        transaction.accountIdentifier?.let { putExtra("accountIdentifier", it) }
        transaction.latitude?.let { putExtra("latitude", it) }
        transaction.longitude?.let { putExtra("longitude", it) }
        putExtra("timestampMillis", transaction.receivedAtMillis)
        putExtra("pendingNotificationId", pendingNotificationId)
        source?.let { putExtra("source", it) }
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        System.currentTimeMillis().toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
    )

    if (
        android.os.Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val notification = NotificationCompat.Builder(
        context,
        channelId
    )
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(
            "💸 ₹${transaction.amount} spent"
        )
        .setContentText(
            "${transaction.merchant} • Tap to split"
        )
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(
                    "₹${transaction.amount} spent at ${transaction.merchant}\n" +
                            "Who was this for? Tap to select people."
                )
        )
        .setPriority(
            NotificationCompat.PRIORITY_HIGH
        )
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat
        .from(context)
        .notify(
            System.currentTimeMillis().toInt(),
            notification
        )
}
