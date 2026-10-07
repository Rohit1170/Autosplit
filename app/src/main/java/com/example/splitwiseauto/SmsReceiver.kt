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

        // goAsync so the database work can finish after onReceive returns.
        // The raw SMS body is only ever held in memory — only parsed,
        // structured fields are persisted. All routing lives in SmsIngestion.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsIngestion.process(context, sender, smsBody, receivedAtMillis)
            } catch (e: Exception) {
                // Never let one bad SMS crash the receiver; type only, never text.
                Log.e("SMS_RECEIVER", "Failed to process SMS: ${e.javaClass.simpleName}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
