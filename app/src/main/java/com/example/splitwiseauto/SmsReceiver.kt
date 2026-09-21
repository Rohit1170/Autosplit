package com.example.splitwiseauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messageParts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messageParts.isEmpty()) return

        /*
         * A single logical SMS longer than ~140-160 chars (very common for bank/
         * lender notifications, which pack in the amount, merchant, UPI ref, and
         * a fraud-warning footer) arrives split across MULTIPLE SmsMessage parts
         * in this same broadcast. They must be joined back into one string before
         * parsing — parsing each part on its own (as this used to do) can cut the
         * amount, reference, or merchant text mid-way, e.g. turning "Rs. 5,100"
         * into just "Rs. 5," if the split happens to land between the comma and
         * the rest of the digits, silently recording ₹5 instead of ₹5,100.
         */
        val sender = messageParts.first().displayOriginatingAddress
        val smsBody = messageParts.joinToString(separator = "") { it.messageBody }
        val receivedAtMillis = messageParts.first().timestampMillis

        Log.d(
            "SMS_RECEIVER",
            "SMS from: $sender\nMessage: $smsBody"
        )

        // Send the reconstructed, full SMS to our existing parser
        val transaction = parseBankSms(
            sms = smsBody,
            sender = sender,
            receivedAtMillis = receivedAtMillis
        )

        // goAsync fires unconditionally now, right after parsing — a
        // confirming/failing bank SMS is the ONLY thing that can ever
        // resolve a Scan & Pay attempt (never call state or duration alone),
        // so every SMS has to at least be checked against the payment-attempt
        // queue before falling through to the older detected-expense path.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Scan & Pay adoption gets first look. If this SMS resolves an
                // attempt that's waiting on verification (or already timed
                // out but still resolvable), PaymentSmsAdopter claims it,
                // inserts its own pending-notification row (source =
                // "scan_pay") on success, and this SMS is done — it is NOT
                // also run through the generic detected-expense flow below.
                val adopted = PaymentSmsAdopter.tryAdopt(
                    context = context,
                    smsBody = smsBody,
                    transaction = transaction,
                    receivedAtMillis = receivedAtMillis
                )
                if (adopted) {
                    return@launch
                }

                val transactions = listOfNotNull(
                    transaction?.takeIf { it.type == "DEBIT" && !isDuplicateSms(context, sender, smsBody) }?.also {
                        Log.d(
                            "SMS_RECEIVER",
                            "Transaction detected: ₹${it.amount} at ${it.merchant}"
                        )
                    }
                )

                if (transactions.isEmpty()) return@launch

                val database = AppDatabase.getDatabase(context)
                transactions.forEach { txn ->
                    val location = LocationHelper.getCurrentLocation(context)
                    val withLocation = txn.copy(
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )

                    // Persisted the moment it's detected — before the system notification
                    // even shows — so if the user swipes that notification away without
                    // tapping it, this expense is still recoverable from inside the app.
                    val pendingId = database.pendingExpenseNotificationDao().insert(
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
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun isDuplicateSms(context: Context, sender: String?, body: String): Boolean {
        val key = "sms_${(sender.orEmpty() + body).hashCode()}"
        val prefs = context.getSharedPreferences("processed_sms", Context.MODE_PRIVATE)
        if (prefs.contains(key)) return true
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        return false
    }
}
