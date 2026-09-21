package com.example.splitwiseauto

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

@RequiresApi(Build.VERSION_CODES.O)
fun showExpenseNotification(
    context: Context,
    transaction: Transaction,
    pendingNotificationId: Int
) {

    val channelId = "expense_channel"

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
