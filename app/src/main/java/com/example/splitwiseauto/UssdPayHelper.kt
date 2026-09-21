package com.example.splitwiseauto

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Small, dependency-free helpers for the *99# USSD rail. Placing the call
 * itself and watching when it ends live in DialManager now — this object
 * used to also call TelephonyManager.sendUssdRequest() directly, but that
 * turned out to be a dead end: it can only ever start a NEW USSD session,
 * never reply inside one that's already open (confirmed by testing, and by
 * reading Flowpay's own source, which doesn't attempt it either), and on
 * top of that it's blocked outright on at least one real carrier tested
 * against this app (consistently failing with a generic error). The
 * locked design (honest handoff, not automation) doesn't want it anyway —
 * ACTION_CALL + the person completing the session themselves is the
 * actual behavior we want, not an accident of what didn't work.
 */
object UssdPayHelper {

    private const val SEND_TO_VPA_MENU_CODE = "99*1*3"

    /**
     * Builds the *99# dial string for a Send-Money-by-VPA session.
     *
     * When [vpa] and [amount] are both usable, this returns the COMPOUND
     * form NPCI's own *99# spec supports: `*99*1*3*<vpa>*<amount>#`. Extra
     * `*<field>` segments before the final `#` pre-fill that many prompts —
     * here, "Enter UPI ID" and "Enter Amount" — so the person only has to
     * confirm the payee, optionally add a remark, and enter their UPI PIN,
     * instead of typing the VPA and amount by hand at two separate prompts.
     * This is still a single ACTION_CALL dial, same as any phone number —
     * not automation of the session itself. Everything after dialing
     * (confirmation, remarks, PIN) is still the person's own input inside
     * the IVR, exactly as before; only the two fields that were previously
     * typed manually are now pre-filled in the dial string.
     *
     * Falls back to the bare `*99*1*3#` (jumps to "Enter UPI ID", skipping
     * the top-level menu, but requires typing the VPA and amount manually)
     * when [vpa] is null/blank, contains `*` or `#` (which would break the
     * field-separator syntax — a real VPA never legitimately contains
     * either), or [amount] is null/non-positive. Exact IVR behavior for the
     * compound form can still vary a little by telecom/bank backend and
     * hasn't been verified against every carrier, so the manual base code
     * always remains reachable as a safe fallback if a given SIM doesn't
     * honor the pre-filled fields.
     */
    fun buildUssdCode(vpa: String? = null, amount: Double? = null): String {
        val safeVpa = vpa?.takeIf { it.isNotBlank() && !it.contains('*') && !it.contains('#') }
        val safeAmount = amount?.takeIf { it > 0.0 }
        return if (safeVpa != null && safeAmount != null) {
            val amountText = safeAmount.toBigDecimal().stripTrailingZeros().toPlainString()
            "*$SEND_TO_VPA_MENU_CODE*$safeVpa*$amountText#"
        } else {
            "*$SEND_TO_VPA_MENU_CODE#"
        }
    }

    /**
     * Pre-copies the VPA so pasting it into the network's own USSD reply
     * prompt is one tap — kept as a fallback for a SIM/carrier that doesn't
     * honor the compound dial string above and still lands on a manual
     * "Enter UPI ID" prompt.
     */
    fun copyVpaToClipboard(activity: Activity, vpa: String) {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("UPI ID", vpa))
    }
}
