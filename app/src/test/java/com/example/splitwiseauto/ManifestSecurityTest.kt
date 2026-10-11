package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * SPL-SEC-002 regression: every exported component that accepts outside
 * input must be guarded. Reads the source manifest (unit tests run in app/).
 */
class ManifestSecurityTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun components(tag: String): List<Element> {
        val f = File("src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(f)
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    private fun Element.actions(): List<String> {
        val nodes = getElementsByTagName("action")
        return (0 until nodes.length).map { (nodes.item(it) as Element).attr("name") }
    }

    @Test
    fun smsReceiverRequiresBroadcastSmsPermission() {
        val sms = components("receiver").firstOrNull { it.attr("name") == ".SmsReceiver" }
        assertNotNull("SmsReceiver must be declared", sms)
        assertEquals(
            "Without BROADCAST_SMS any installed app can forge a bank SMS (SPL-SEC-002)",
            "android.permission.BROADCAST_SMS",
            sms!!.attr("permission")
        )
    }

    /** Negative test: no exported receiver may listen for SMS without the guard. */
    @Test
    fun noUnguardedExportedSmsReceivers() {
        val smsActions = setOf(
            "android.provider.Telephony.SMS_RECEIVED",
            "android.provider.Telephony.SMS_DELIVER"
        )
        components("receiver")
            .filter { r -> r.actions().any { it in smsActions } }
            .forEach { r ->
                assertEquals(
                    "${r.attr("name")} listens for SMS but is not BROADCAST_SMS-protected",
                    "android.permission.BROADCAST_SMS",
                    r.attr("permission")
                )
            }
    }

    @Test
    fun notificationListenerIsSystemBindOnly() {
        val svc = components("service").first { it.attr("name") == ".BankNotificationListener" }
        assertEquals(
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            svc.attr("permission")
        )
    }

    @Test
    fun internalReceiversAreNotExported() {
        listOf(".ExpenseActionReceiver", ".SplitleeWidget").forEach { name ->
            val r = components("receiver").first { it.attr("name") == name }
            assertTrue("$name must not be exported", r.attr("exported") == "false")
        }
    }
}
