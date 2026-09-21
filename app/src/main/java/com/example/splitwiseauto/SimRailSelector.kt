package com.example.splitwiseauto

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * Picks which rail a Scan & Pay attempt should use. *99# USSD needs GSM
 * signalling that Jio's all-IP network doesn't carry, so a Jio-registered
 * SIM must use the 123Pay IVR rail (or the upi://pay deep-link fallback)
 * instead — see Upi123CallStringBuilder's doc for why the QR-only case
 * lands on the deep-link, not the DTMF builder, in practice.
 */
object SimRailSelector {

    data class Choice(
        val simSlot: Int?,
        val carrierName: String?,
        val rail: PaymentRail
    )

    /**
     * Best-effort detection of the default/bank-registered SIM's carrier.
     * Falls back to [PaymentRail.USSD_99] (the non-Jio default) whenever
     * READ_PHONE_STATE isn't granted or subscription info isn't available —
     * the confirm screen lets the person override this manually either way.
     */
    @SuppressLint("MissingPermission")
    fun detect(context: Context): Choice {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return Choice(simSlot = null, carrierName = null, rail = PaymentRail.USSD_99)
        }

        val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            ?: return Choice(simSlot = null, carrierName = null, rail = PaymentRail.USSD_99)

        val active = runCatching { subscriptionManager.activeSubscriptionInfoList }
            .getOrNull()
            .orEmpty()

        val defaultSub = active.firstOrNull()
            ?: return Choice(simSlot = null, carrierName = null, rail = PaymentRail.USSD_99)

        val carrierName = defaultSub.carrierName?.toString()
        val isJio = carrierName?.contains("jio", ignoreCase = true) == true

        return Choice(
            simSlot = defaultSub.simSlotIndex,
            carrierName = carrierName,
            rail = if (isJio) PaymentRail.IVR_123PAY else PaymentRail.USSD_99
        )
    }
}
