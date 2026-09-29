package com.example.splitwiseauto

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Drives a *99# "send money to UPI ID" session the same way Scan & Pay's
 * UPI-intent flow hands off to a UPI app — this is the second, offline
 * payment mode for when the user has no internet: the whole menu path
 * (send money -> pay to VPA -> VPA -> amount) is pre-encoded as ONE ussd
 * string and dialed in a single shot. The carrier network carries it
 * through those menu steps itself and the session ends at "enter UPI
 * PIN" — which is always completed by the user in the OS's own USSD
 * dialog, never read or touched by this app.
 *
 * NOTE ON THE MENU CODE: *99# menu numbering can differ slightly by
 * telecom operator. [SEND_TO_VPA_MENU_CODE] below is the commonly
 * published NPCI path for "Send Money" -> "To UPI ID/VPA". If a real
 * device shows a different result, this is the one constant to adjust —
 * everything else (parsing, permission handling, fallback) stays the same.
 */
object UssdPayHelper {

    private const val SEND_TO_VPA_MENU_CODE = "99*1*4"

    fun buildUssdCode(vpa: String, amount: Double): String {
        val amountStr = String.format(Locale.US, "%.2f", amount).trimEnd('0').trimEnd('.')
        return "*$SEND_TO_VPA_MENU_CODE*$vpa*$amountStr#"
    }

    fun hasCallPhonePermission(activity: Activity): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Tries to dial [ussdCode] directly via TelephonyManager (one tap, no
     * dialer app involved). Requires CALL_PHONE + API 26+. Falls back to
     * [dialManually] whenever that isn't available, so this always does
     * *something* useful rather than silently failing.
     */
    @SuppressLint("MissingPermission")
    fun triggerPayment(activity: Activity, ussdCode: String) {
        val canSendDirectly = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            hasCallPhonePermission(activity)

        if (!canSendDirectly) {
            dialManually(activity, ussdCode)
            return
        }

        val telephonyManager =
            activity.getSystemService(Activity.TELEPHONY_SERVICE) as? TelephonyManager

        if (telephonyManager == null) {
            dialManually(activity, ussdCode)
            return
        }

        try {
            telephonyManager.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        Toast.makeText(
                            activity,
                            "USSD: $response",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        Toast.makeText(
                            activity,
                            "USSD request failed — opening dialer instead",
                            Toast.LENGTH_SHORT
                        ).show()
                        dialManually(activity, ussdCode)
                    }
                },
                null
            )
        } catch (e: SecurityException) {
            dialManually(activity, ussdCode)
        }
    }

    /**
     * Opens the phone dialer pre-filled with the USSD code. Needs no
     * permission at all — the user still has to tap Call themselves,
     * which is also the safer default for anyone who hasn't granted
     * CALL_PHONE.
     */
    fun dialManually(activity: Activity, ussdCode: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(ussdCode)}"))
        activity.startActivity(dialIntent)
    }
}
