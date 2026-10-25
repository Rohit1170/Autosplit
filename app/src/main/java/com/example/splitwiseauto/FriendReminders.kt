package com.example.splitwiseauto

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Phone numbers are stored as digits with country code, e.g. 919876543210. */
object PhoneNumbers {
    fun normalize(raw: String?): String? {
        val d = raw?.filter { it.isDigit() }.orEmpty()
        return when {
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
            d.length == 12 && d.startsWith("91") -> d
            d.length in 8..15 -> d
            else -> null
        }
    }

    /** 919876543210 → +91 98765 43210 */
    fun display(stored: String?): String? {
        stored ?: return null
        return if (stored.length == 12 && stored.startsWith("91"))
            "+91 ${stored.substring(2, 7)} ${stored.substring(7)}" else "+$stored"
    }
}

/**
 * Opens the phone's own contact picker (no contacts permission needed: the
 * picker grants access to just the picked number) and returns name + number.
 */
@Composable
fun rememberContactPicker(onPicked: (name: String?, phone: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) onPicked(c.getString(0), c.getString(1)) }
        }
    }
    return {
        try {
            launcher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Your own UPI QR (what friends scan in their UPI app to pay you). */
object MyUpiQr {
    fun link(vpa: String, name: String, amount: Double?): String =
        Uri.Builder().scheme("upi").authority("pay")
            .appendQueryParameter("pa", vpa)
            .appendQueryParameter("pn", name)
            .apply { if (amount != null && amount > 0.0) appendQueryParameter("am", String.format(java.util.Locale.US, "%.2f", amount)) }
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Splitlee settle up")
            .build().toString()

    fun bitmap(content: String, size: Int = 640): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
        val pixels = IntArray(size * size) { i ->
            if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Saves the QR to cache/exports (already shared by the FileProvider) and returns its content uri. */
    fun shareUri(context: Context, bmp: Bitmap): Uri? = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "splitlee_pay_qr.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    /** Valid-looking UPI ID: something@handle. */
    // Same offline check as the intro: rejects emails (@gmail.com) and bad format.
    fun isValidVpa(v: String): Boolean = UpiIdCheck.canSave(v)
}

/** Builds and hands off the reminder. Nothing is sent without the user's own tap. */
private object ReminderSender {
    fun message(name: String, amount: Double, forWhat: String?, vpa: String?, withQr: Boolean): String = buildString {
        append("Hi $name, gentle reminder: you owe me ${formatRupeeIndianRaw(amount)}")
        if (!forWhat.isNullOrBlank()) append(" for $forWhat")
        append(".")
        if (vpa != null) {
            append("\nPay to my UPI ID: $vpa")
            if (withQr) append(" (or scan the QR)")
        }
    }

    fun whatsApp(context: Context, phone: String?, text: String, image: Uri?) {
        // With a QR: share the image + text straight into that person's chat.
        if (image != null) {
            for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    setPackage(pkg)
                    putExtra(Intent.EXTRA_STREAM, image)
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (phone != null) putExtra("jid", "$phone@s.whatsapp.net")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try { context.startActivity(intent); return } catch (_: ActivityNotFoundException) {}
            }
        }
        // Text only (or WhatsApp refused the image share): open the chat with the text.
        val url = if (phone != null) "https://wa.me/$phone?text=${Uri.encode(text)}"
        else "https://wa.me/?text=${Uri.encode(text)}"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp isn't installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun sms(context: Context, phone: String?, text: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + (phone?.let { "+$it" } ?: "")))
            .putExtra("sms_body", text)
        try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No SMS app found", Toast.LENGTH_SHORT).show()
        }
    }

    fun other(context: Context, name: String, text: String, image: Uri?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            if (image != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Remind $name"))
    }
}

/**
 * Remind sheet: WhatsApp / SMS / other apps, with a prefilled message and,
 * when your UPI ID is set, a QR with the amount that the friend can scan in
 * any UPI app to pay you. You still press Send yourself.
 */
@Composable
fun RemindSheet(friendName: String, amount: Double, forWhat: String? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var friend by remember(friendName) { mutableStateOf<Friend?>(null) }
    var phone by remember(friendName) { mutableStateOf<String?>(null) }
    var vpa by remember { mutableStateOf(UserProfile.upiId(context)) }
    var editingPhone by remember { mutableStateOf(false) }
    var phoneText by remember { mutableStateOf("") }
    var vpaText by remember { mutableStateOf("") }
    val accent = Brand.accent()

    LaunchedEffect(friendName) {
        friend = db.friendDao().getFriendByName(friendName)
        phone = friend?.phone
    }

    val qrBitmap = remember(vpa, amount) {
        vpa?.let { runCatching { MyUpiQr.bitmap(MyUpiQr.link(it, UserProfile.name(context), amount)) }.getOrNull() }
    }

    fun savePhone(raw: String?) {
        val p = PhoneNumbers.normalize(raw)
        if (p == null) {
            Toast.makeText(context, "That doesn't look like a phone number", Toast.LENGTH_SHORT).show(); return
        }
        phone = p
        editingPhone = false
        friend?.let { f -> scope.launch(Dispatchers.IO) { db.friendDao().setPhone(f.id, p) } }
    }
    val pickContact = rememberContactPicker { _, number -> savePhone(number) }

    AppSheet(
        title = "Remind $friendName",
        subtitle = "Owes you ${formatRupeeIndian(amount)}",
        onDismiss = onDismiss,
        glass = true
    ) {
        // Your pay QR (with the amount).
        if (qrBitmap != null) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(qrBitmap.asImageBitmap(), contentDescription = "Your UPI QR", modifier = Modifier.size(170.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scan in any UPI app to pay ${formatRupeeIndian(amount)} to $vpa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Sent with the reminder, or show it if they're with you.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                "Add your UPI ID to send a pay QR with the reminder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModernTextField(
                    value = vpaText,
                    onValueChange = { vpaText = it.trim() },
                    label = "Your UPI ID",
                    placeholder = "e.g. name@okaxis",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    if (MyUpiQr.isValidVpa(vpaText)) {
                        UserProfile.setUpiId(context, vpaText); vpa = vpaText.trim()
                    } else Toast.makeText(context, "UPI ID looks like name@bank", Toast.LENGTH_SHORT).show()
                }) { Text("Save") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Number.
        if (phone == null || editingPhone) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModernTextField(
                    value = phoneText,
                    onValueChange = { phoneText = it },
                    label = "$friendName's number",
                    placeholder = "98765 43210",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = pickContact) {
                    Icon(Icons.Filled.Contacts, contentDescription = "Pick from contacts", tint = accent)
                }
                TextButton(onClick = { savePhone(phoneText) }, enabled = phoneText.isNotBlank()) { Text("Save") }
            }
            if (friend == null) {
                Text(
                    "$friendName isn't in your friends list, so the number won't be saved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    PhoneNumbers.display(phone).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { phoneText = phone.orEmpty(); editingPhone = true }) { Text("Change") }
            }
            Spacer(Modifier.height(8.dp))
        }

        fun text(withQr: Boolean) = ReminderSender.message(friendName, amount, forWhat, vpa, withQr)
        suspend fun qrUri(): Uri? = qrBitmap?.let { withContext(Dispatchers.IO) { MyUpiQr.shareUri(context, it) } }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionRow(
                Icons.Filled.Chat, "WhatsApp",
                onClick = {
                    AutoSettle.rememberReminder(context, friendName, amount)
                    scope.launch { ReminderSender.whatsApp(context, phone, text(qrBitmap != null), qrUri()); onDismiss() }
                },
                hint = if (phone != null) "Opens $friendName's chat, ready to send" else "You'll pick the chat in WhatsApp"
            )
            ActionRow(
                Icons.Filled.Sms, "SMS",
                onClick = {
                    AutoSettle.rememberReminder(context, friendName, amount)
                    ReminderSender.sms(context, phone, text(false)); onDismiss()
                },
                hint = if (vpa != null) "Message with your UPI ID (SMS can't carry the QR)" else "Prefilled message"
            )
            ActionRow(
                Icons.Filled.Share, "Other apps",
                onClick = {
                    AutoSettle.rememberReminder(context, friendName, amount)
                    scope.launch { ReminderSender.other(context, friendName, text(qrBitmap != null), qrUri()); onDismiss() }
                }
            )
        }
    }
}

/** Settings > Your UPI ID: edit it and show your QR (no amount) for friends to scan. */
@Composable
fun MyUpiSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var vpa by remember { mutableStateOf(UserProfile.upiId(context)) }
    var text by remember { mutableStateOf(vpa.orEmpty()) }
    val qr = remember(vpa) {
        vpa?.let { runCatching { MyUpiQr.bitmap(MyUpiQr.link(it, UserProfile.name(context), null)) }.getOrNull() }
    }
    AppSheet(title = "Your UPI ID", subtitle = "Friends scan this to pay you", onDismiss = onDismiss, glass = true) {
        if (qr != null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(10.dp)) {
                    Image(qr.asImageBitmap(), contentDescription = "Your UPI QR", modifier = Modifier.size(200.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(vpa.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModernTextField(
                value = text,
                onValueChange = { text = it.trim() },
                label = "UPI ID",
                placeholder = "e.g. name@okaxis",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = {
                // Saved (or cleared): close the sheet and confirm with a toast.
                when {
                    text.isBlank() -> {
                        UserProfile.setUpiId(context, null)
                        Toast.makeText(context, "UPI ID removed", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    MyUpiQr.isValidVpa(text) -> {
                        UserProfile.setUpiId(context, text.trim())
                        Toast.makeText(context, "UPI ID saved", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                    else -> Toast.makeText(context, "UPI ID looks like name@bank", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Save") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Used for the pay QR in reminders. Stays on this phone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
