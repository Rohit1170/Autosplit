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

        // Personal messages (from a phone number) are dropped right here —
        // before any parsing — so a friend texting "money debited from
        // ***1234 ... 50,000" can never become an expense. Never log the body.
        if (sender == null || !isTransactionalSender(sender)) {
            Log.d("SMS_RECEIVER", "Ignored SMS from a non-bank sender")
            return
        }

        // Send the reconstructed, full SMS to our existing parser
        val transaction = parseBankSms(
            sms = smsBody,
            sender = sender,
            receivedAtMillis = receivedAtMillis
        )

        // goAsync so the database work below can finish after onReceive
        // returns. The raw SMS body is only ever held in memory here — only
        // parsed, structured fields are persisted.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val debit = transaction?.takeIf { it.type == "DEBIT" } ?: return@launch

                // A redelivered copy of the same SMS is ignored — checked
                // BEFORE payment matching so a repeat can't fall through to
                // the generic flow and create a second pending expense.
                if (isDuplicateSms(context, sender, smsBody)) return@launch

                // Scan & Pay matcher runs FIRST. If this debit belongs to a
                // pending Scan & Pay payment (exact UPI ref, or same amount
                // within 15 minutes), the matcher marks it CONFIRMED and links
                // it to its expense/bill — and this SMS must NOT also go
                // through the generic flow below, or the user would be asked
                // to split a payment that's already assigned.
                val handledByPaymentMatcher =
                    PendingPaymentMatcher.tryConfirm(context, debit, smsBody, receivedAtMillis)
                if (handledByPaymentMatcher) return@launch

                // Not a Scan & Pay payment: normal flow (queue + split prompt).
                Log.d(
                    "SMS_RECEIVER",
                    "Transaction detected: ₹${debit.amount} at ${debit.merchant}"
                )

                val database = AppDatabase.getDatabase(context)
                val location = LocationHelper.getCurrentLocation(context)
                val withLocation = debit.copy(
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
            } catch (e: Exception) {
                // Never let one bad SMS crash the receiver; log type only,
                // never the message text.
                Log.e("SMS_RECEIVER", "Failed to process SMS: ${e.javaClass.simpleName}")
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
