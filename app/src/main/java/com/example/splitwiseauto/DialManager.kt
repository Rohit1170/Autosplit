package com.example.splitwiseauto

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * Places the call for a Scan & Pay attempt and watches when it ends, via a
 * single telephony listener — nothing here reads, automates, or replies
 * inside the call/USSD session itself (honest handoff, not the AirPay
 * model: see the class docs on QrScanActivity/PaymentAttempt). The
 * floating status card (PaymentOverlayService) is shown for the duration
 * and wired to [cancel].
 *
 * NOTE: monitoring call state (to tell a <5s hang-up from a real payment
 * call, per the locked design) needs READ_PHONE_STATE. That permission is
 * outside the brief's original allowed list (CALL_PHONE, RECEIVE_SMS,
 * CAMERA, SYSTEM_ALERT_WINDOW) — flagged explicitly rather than added
 * quietly. Without it, [placeCall] still places the call, but never sees
 * CALL_STATE_OFFHOOK/IDLE, so the attempt can only ever resolve later via
 * the SMS-timeout path (component 3's WAITING_FOR_VERIFICATION timeout),
 * never the <5s-cancel rule.
 */
class DialManager(private val activity: Activity) {

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var sawOffHook = false
    private var callStartedAtMillis: Long? = null
    private var onCallEnded: ((durationMillis: Long) -> Unit)? = null

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasReadPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Places [dialString] (a tel: URI — either a plain *99*1*3# USSD code
     * or a 123Pay DTMF-pause string) via ACTION_CALL and starts listening
     * for the call to end. Returns false if CALL_PHONE isn't granted or the
     * call couldn't be placed at all — the caller decides what to show.
     */
    @Suppress("MissingPermission")
    fun placeCall(dialString: String, onCallEnded: (durationMillis: Long) -> Unit): Boolean {
        if (!hasCallPhonePermission()) return false

        this.onCallEnded = onCallEnded
        this.sawOffHook = false
        this.callStartedAtMillis = null

        startListening()

        return try {
            activity.startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dialString)))
            true
        } catch (e: SecurityException) {
            stopListening()
            false
        }
    }

    /** User tapped Cancel on the overlay. Stops listening; the caller marks the attempt CANCELLED. */
    fun cancel() {
        stopListening()
    }

    private fun startListening() {
        if (!hasReadPhoneStatePermission()) return

        telephonyManager = activity.getSystemService(Activity.TELEPHONY_SERVICE) as? TelephonyManager
        val listener = object : PhoneStateListener() {
            @Suppress("DEPRECATION")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (!sawOffHook) {
                            sawOffHook = true
                            callStartedAtMillis = System.currentTimeMillis()
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        val startedAt = callStartedAtMillis
                        if (sawOffHook && startedAt != null) {
                            val duration = System.currentTimeMillis() - startedAt
                            val callback = onCallEnded
                            stopListening()
                            callback?.invoke(duration)
                        }
                    }
                }
            }
        }
        phoneStateListener = listener
        try {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            phoneStateListener = null
        }
    }

    private fun stopListening() {
        phoneStateListener?.let {
            @Suppress("DEPRECATION")
            telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null
    }
}
