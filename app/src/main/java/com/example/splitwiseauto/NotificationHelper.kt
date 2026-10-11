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
    source: String? = null,
    // SPL-SEC-004: set when this debit COULD be a Scan & Pay payment but the
    // evidence wasn't enough to confirm it automatically. The notification
    // then asks the user, and only their "Yes" links the two.
    askAboutPayment: PendingPayment? = null
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
        android.util.Log.w(SmsIngestion.TAG, "notification NOT shown: notification permission is off")
        return
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        android.util.Log.w(SmsIngestion.TAG, "notification NOT shown: notifications disabled for the app")
    }

    val notification = NotificationCompat.Builder(
        context,
        channelId
    )
        .setSmallIcon(R.drawable.ic_stat_splitlee)
        .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.splitlee_brand_green))
        .apply {
            if (askAboutPayment == null) {
                setContentTitle("💸 ${formatRupeeIndian(transaction.amount)} spent")
                setContentText("${transaction.merchant} • Split, just me, or ignore")
                setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${formatRupeeIndian(transaction.amount)} spent at ${transaction.merchant}\n" +
                            "Who was this for?"
                    )
                )
                // One tap, no app: Split opens the split screen; Just me saves it as
                // your own expense; Ignore drops it (ExpenseActionReceiver).
                addAction(0, "Split", pendingIntent)
                addAction(0, "Just me", expenseAction(context, ExpenseActionReceiver.ACTION_JUST_ME, pendingNotificationId))
                addAction(0, "Ignore", expenseAction(context, ExpenseActionReceiver.ACTION_IGNORE, pendingNotificationId))
            } else {
                val payee = askAboutPayment.payeeName ?: askAboutPayment.payeeVpa
                setContentTitle("${formatRupeeIndian(transaction.amount)} debited. Your Scan & Pay to $payee?")
                setContentText("Not matched automatically. Tap Yes if it was.")
                setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Your bank shows a ${formatRupeeIndian(transaction.amount)} debit " +
                            "(${transaction.merchant}). It wasn't matched to your Scan & Pay " +
                            "to $payee automatically because it doesn't clearly name the payee. " +
                            "Was it that payment?"
                    )
                )
                addAction(0, "Yes, it was", scanPayConfirmAction(context, pendingNotificationId, askAboutPayment.id))
                addAction(0, "No, split it", pendingIntent)
                addAction(0, "Just me", expenseAction(context, ExpenseActionReceiver.ACTION_JUST_ME, pendingNotificationId))
            }
        }
        .setPriority(
            NotificationCompat.PRIORITY_HIGH
        )
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    NotificationManagerCompat
        .from(context)
        .notify(
            // Stable per pending row, so the action buttons can dismiss it.
            QuickExpenseActions.notificationIdFor(pendingNotificationId),
            notification
        )
}

/** Debit that might be [payment]: ask the user instead of confirming it (SPL-SEC-004). */
fun showScanPayCandidateNotification(
    context: Context,
    transaction: Transaction,
    pendingNotificationId: Int,
    payment: PendingPayment
) = showExpenseNotification(context, transaction, pendingNotificationId, askAboutPayment = payment)

private fun scanPayConfirmAction(context: Context, pendingId: Int, paymentId: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        pendingId * 4 + 3,
        Intent(context, ExpenseActionReceiver::class.java)
            .setAction(ExpenseActionReceiver.ACTION_CONFIRM_SCAN_PAY)
            .putExtra(ExpenseActionReceiver.EXTRA_PENDING_ID, pendingId)
            .putExtra(ExpenseActionReceiver.EXTRA_PAYMENT_ID, paymentId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

private fun expenseAction(context: Context, action: String, pendingId: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        // Unique per (row, action) so the two buttons never share an intent.
        pendingId * 4 + if (action == ExpenseActionReceiver.ACTION_JUST_ME) 1 else 2,
        Intent(context, ExpenseActionReceiver::class.java)
            .setAction(action)
            .putExtra(ExpenseActionReceiver.EXTRA_PENDING_ID, pendingId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )


/**
 * Posted when a bank debit SMS confirms a Scan & Pay payment. The expense is
 * already saved (no split prompt), so tapping just opens the app.
 */
fun showPaymentConfirmedNotification(
    context: Context,
    payee: String,
    amountMinor: Long,
    appSaidFailed: Boolean
) {
    val channelId = "expense_channel"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Expense Notifications", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) return

    val amount = formatRupeeIndian(amountMinor / 100.0)
    val title = if (appSaidFailed) "Money was debited: $amount to $payee" else "Paid $amount to $payee"
    val text = if (appSaidFailed) {
        "Your UPI app said this payment failed, but your bank SMS shows the debit. It's saved as an expense."
    } else {
        "Confirmed by your bank SMS · saved as an expense."
    }
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_stat_splitlee)
        .setColor(androidx.core.content.ContextCompat.getColor(context, R.color.splitlee_brand_green))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(open)
        .build()
    runCatching {
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }
}
