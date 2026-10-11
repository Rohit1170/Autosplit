package com.example.splitwiseauto

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SPL-SEC-002 exploit replay, on a real device or emulator: an explicit
 * SMS_RECEIVED broadcast from an app that does not hold BROADCAST_SMS
 * (this test runs with Splitlee's own uid, which doesn't either) must never
 * reach SmsReceiver, so no "To split" row may appear.
 *
 * Run: .\gradlew connectedDebugAndroidTest (device connected).
 */
@RunWith(AndroidJUnit4::class)
class SmsReceiverSpoofTest {

    @Test
    fun forgedSmsBroadcastIsNotDelivered() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = AppDatabase.getDatabase(ctx).pendingExpenseNotificationDao()
        val before = dao.getAll().size

        // Preconditions that make delivery impossible on every Android
        // version: the installed receiver demands BROADCAST_SMS, and a normal
        // app (us, or any attacker app) does not hold it.
        val info = ctx.packageManager.getReceiverInfo(ComponentName(ctx, SmsReceiver::class.java), 0)
        assertEquals("android.permission.BROADCAST_SMS", info.permission)
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            ctx.checkCallingOrSelfPermission("android.permission.BROADCAST_SMS")
        )

        // The attack: explicit intent straight at the receiver. The PDU
        // content does not matter - if delivery were allowed, onReceive
        // would run; with the permission the system drops it.
        val forged = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            .setComponent(ComponentName(ctx, SmsReceiver::class.java))
            .putExtra("format", "3gpp")
            .putExtra("pdus", arrayOf<Any>(ByteArray(0)))
        ctx.sendBroadcast(forged)

        Thread.sleep(3_000)
        assertEquals("forged SMS must not create a pending expense", before, dao.getAll().size)
    }
}
