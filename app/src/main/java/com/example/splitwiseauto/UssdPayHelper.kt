package com.example.splitwiseauto

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Offline "Pay via *99#" — ported from Flowpay's scan-to-pay USSD rail
 * (github.com/Flowpayup/Payments-Without-Internet, QRScannerActivity).
 *
 * How it works (and why it's NOT one pre-encoded string any more):
 *  - *99# is an INTERACTIVE, multi-step USSD session (VPA -> amount ->
 *    remark -> UPI PIN). Stuffing the VPA and amount into a single code
 *    (the old `*99*1*4*vpa*amount#`) is not a documented NPCI shortcut and
 *    `@`/`.` in a VPA break it on many carriers. TelephonyManager
 *    .sendUssdRequest() is also wrong here: it's one request -> one reply,
 *    so the session closes before the user can ever reach the PIN step.
 *  - Flowpay's approach, used here: copy the payee VPA to the clipboard
 *    (flagged sensitive), place a real call to the scan-to-pay branch
 *    [SCAN_TO_PAY_CODE], and show on-screen steps while the user completes
 *    the menu in the system USSD dialog. The PIN is typed only there.
 *  - There is no trustworthy USSD result, so the payment stays PENDING and
 *    is confirmed only by the bank's debit SMS (PendingPaymentMatcher) —
 *    exactly the same downstream pipeline as the UPI-app path.
 *  - Jio is all-IP (VoLTE) and does not carry USSD, so *99# is blocked up
 *    front on a Jio voice SIM instead of failing mysteriously mid-call.
 */
object UssdPayHelper {

    private const val TAG = "UssdPay"

    /** *99# -> 1 (Send money) -> 3 (UPI ID). Same branch Flowpay dials. */
    const val SCAN_TO_PAY_CODE = "*99*1*3#"

    /** NPCI per-transaction cap for *99#. Banks reject above this mid-session. */
    const val MAX_AMOUNT_MINOR = 5_000_00L

    /** The steps the user performs in the USSD dialog (Flowpay's instruction card). */
    fun steps(amountText: String): List<String> = listOf(
        "Paste the UPI ID (already copied) and send",
        "Enter the amount: $amountText",
        "Enter 1 to skip the remark",
        "Enter your UPI PIN",
        "Press Cancel to close the session when it's done"
    )

    // ---- Availability ------------------------------------------------------

    private const val PREFS = "ussd_pay"
    private const val KEY_USER_SAYS_NOT_WORKING = "user_reported_not_working"

    /** Carrier name of the SIM the call will actually go out on (default voice SIM). */
    fun voiceSimCarrier(context: Context): String? = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null
        val voiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
        val scoped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            voiceSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) tm.createForSubscriptionId(voiceSubId) else tm
        // simOperatorName needs no permission.
        scoped.simOperatorName?.takeIf { it.isNotBlank() }
            ?: scoped.networkOperatorName?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun isJio(carrier: String?): Boolean = carrier?.lowercase()?.contains("jio") == true

    fun hasUserReportedNotWorking(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_SAYS_NOT_WORKING, false)

    fun setUserReportedNotWorking(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_USER_SAYS_NOT_WORKING, value).apply()
    }

    /** Null when *99# can be offered; otherwise a user-facing reason. */
    fun blockedReason(context: Context): String? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return "*99# needs a phone with a SIM card."
        }
        val carrier = voiceSimCarrier(context)
        if (isJio(carrier)) {
            return "*99# isn't available on Jio — Jio's network doesn't carry USSD. " +
                "Switch your calling SIM to Airtel, Vi or BSNL to use it."
        }
        return null
    }

    // ---- Clipboard ---------------------------------------------------------

    /** Copies the payee VPA so the user can paste it into the USSD prompt. */
    fun copyVpa(context: Context, vpa: String): Boolean = try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("UPI ID", vpa)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't copy VPA: ${e.javaClass.simpleName}")
        false
    }

    /**
     * Wipes the VPA when the flow ends. clearPrimaryClip (API 28+) is used
     * instead of writing an empty clip so Android 13+ doesn't pop a second
     * "Copied" chip after the user has finished.
     */
    fun clearVpa(context: Context) {
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    // ---- Dialing -----------------------------------------------------------

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    private fun ussdUri(code: String): Uri = Uri.parse("tel:" + code.replace("#", Uri.encode("#")))

    /**
     * Places the USSD call directly (opens the system USSD dialog over the
     * app). Needs CALL_PHONE; without it, falls back to the dialer
     * pre-filled so the user taps Call. Returns false only if nothing could
     * be launched at all.
     */
    fun dial(context: Context, code: String = SCAN_TO_PAY_CODE): Boolean {
        val direct = hasCallPermission(context)
        val intent = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, ussdUri(code))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: RuntimeException) {
            // ActivityNotFound / SecurityException: last try via the dialer.
            Log.w(TAG, "Direct USSD dial failed: ${e.javaClass.simpleName}")
            if (!direct) return false
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, ussdUri(code)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
        }
    }
}
