@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.splitwiseauto

import android.Manifest
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import com.example.splitwiseauto.ui.theme.positiveColor
import com.example.splitwiseauto.ui.theme.positiveContainerColor
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * "Scan & Pay": scan a UPI QR, review the payment, hand it to the user's own
 * UPI app with a standard `upi://pay` intent, then show its status.
 *
 * Hard rules:
 *  - The UPI PIN is entered only inside the UPI app. This app never sees,
 *    collects, or logs it.
 *  - A PendingPayment row is written BEFORE the intent fires.
 *  - The UPI app's result only decides what to SHOW right away: SUCCESS ->
 *    VERIFIED_PENDING ("paid · confirming with bank…"), FAILURE -> FAILED,
 *    anything unclear -> stays PENDING ("Confirming payment…"). Only a
 *    matching bank debit SMS makes a payment CONFIRMED; "bank confirmed" is
 *    never shown before that.
 *
 * Optional extras for paying a specific bill (all may be absent, e.g. when
 * opened from the Home "Scan & Pay" button):
 *  - [EXTRA_BILL_ID] String, [EXTRA_BILL_LABEL] String (shown as "Paying for"),
 *    [EXTRA_BILL_AMOUNT] Double rupees (used when the QR has no amount),
 *    [EXTRA_BILL_NOTE] String (used when the QR has no note).
 */
class QrScanActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BILL_ID = "billId"
        const val EXTRA_BILL_LABEL = "billLabel"
        const val EXTRA_BILL_AMOUNT = "billAmount"
        const val EXTRA_BILL_NOTE = "billNote"
        /** Open straight on an existing payment's status screen (from the bell list). */
        const val EXTRA_PAYMENT_ID = "paymentId"

        private const val STATE_IN_FLIGHT_ID = "inFlightPaymentId"
        private const val STATE_UPI_APP_NAME = "upiAppName"
        private const val STATE_VIA_USSD = "paidViaUssd"
        private const val STATE_COPIED_VPA = "ussdCopiedVpa"
        private const val ACTION_UPI_APP_CHOSEN = "com.example.splitwiseauto.UPI_APP_CHOSEN"
    }

    /** The PendingPayment handed to a UPI app; non-null switches the screen to status mode. */
    private var inFlightPaymentId by mutableStateOf<String?>(null)

    /** Label of the UPI app picked in the chooser (e.g. "GPay"), for "Paid via …". */
    private var upiAppName by mutableStateOf<String?>(null)

    /** True when the in-flight payment runs over *99# (USSD) instead of a UPI app. */
    private var paidViaUssd by mutableStateOf(false)

    /** Whether this flow put a payee VPA on the clipboard (wiped when the screen closes). */
    private var ussdCopiedVpa = false

    /**
     * CALL_PHONE lets us open the USSD session directly (Flowpay's ACTION_CALL
     * path). If the user declines, we still pre-fill the dialer so they can tap
     * Call themselves — the payment is never blocked on this permission.
     */
    private val callPhonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            dialUssdNow()
        }

    private var cameraGranted by mutableStateOf(false)
    private var cameraDenied by mutableStateOf(false)

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            cameraGranted = granted
            cameraDenied = !granted
        }

    /**
     * Result from the UPI app (through the "Pay with" chooser). Best-effort:
     * the reference is stored for exact SMS matching, and the outcome only
     * picks which view to show first. It is never proof of payment.
     */
    private val upiPayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val paymentId = inFlightPaymentId ?: return@registerForActivityResult
            val rawResponse = result.data?.getStringExtra("response")
            PaymentDiagnostics.finished(result.resultCode, rawResponse)
            val response = parseUpiResponse(rawResponse)
            lifecycleScope.launch(Dispatchers.IO) {
                applyUpiAppResult(paymentId, response)
            }
        }

    /** Receives which app the user picked in the chooser (ACTION_CHOOSER's IntentSender callback). */
    private val chosenAppReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val component = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT)
                ?: return
            upiAppName = runCatching {
                val info = packageManager.getApplicationInfo(component.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrNull()
            PaymentDiagnostics.app = "${upiAppName ?: "?"} (${component.packageName})"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this) // 90/120 Hz where the screen supports it
        inFlightPaymentId = savedInstanceState?.getString(STATE_IN_FLIGHT_ID)
            ?: intent.getStringExtra(EXTRA_PAYMENT_ID)
        upiAppName = savedInstanceState?.getString(STATE_UPI_APP_NAME)
        paidViaUssd = savedInstanceState?.getBoolean(STATE_VIA_USSD) ?: false
        ussdCopiedVpa = savedInstanceState?.getBoolean(STATE_COPIED_VPA) ?: false

        ContextCompat.registerReceiver(
            this,
            chosenAppReceiver,
            IntentFilter(ACTION_UPI_APP_CHOSEN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        cameraGranted = hasCameraPermission()
        if (!cameraGranted && inFlightPaymentId == null) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val billId = intent.getStringExtra(EXTRA_BILL_ID)
        val billLabel = intent.getStringExtra(EXTRA_BILL_LABEL)
        val billAmount = intent.getDoubleExtra(EXTRA_BILL_AMOUNT, -1.0).takeIf { it > 0.0 }
        val billNote = intent.getStringExtra(EXTRA_BILL_NOTE)

        setContent {
            SplitWiseAutoTheme {
                ScanAndPayFlow(
                    cameraGranted = cameraGranted,
                    cameraDenied = cameraDenied,
                    billLabel = billLabel,
                    tripName = ActiveTripStore.getActiveTripName(this),
                    billAmount = billAmount,
                    billNote = billNote,
                    paymentId = inFlightPaymentId,
                    upiAppName = upiAppName,
                    paidViaUssd = paidViaUssd,
                    ussdBlockedReason = UssdPayHelper.blockedReason(this),
                    onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = { openAppSettings() },
                    onPay = { request, amountMinor, remarks, onError ->
                        launchUpiPayment(request, amountMinor, remarks, billId, onError)
                    },
                    onPayViaUssd = { request, amountMinor, remarks, onError ->
                        launchUssdPayment(request, amountMinor, remarks, billId, onError)
                    },
                    onCheckSmsAgain = { id, onDone -> checkSmsAgain(id, onDone) },
                    onMarkPaid = { id -> markPaidManually(id) },
                    onUssdRedial = { id -> redialUssd(id) },
                    onUssdCancel = { id -> cancelUssdPayment(id) },
                    onClose = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from system settings after granting camera access.
        if (!cameraGranted && hasCameraPermission()) {
            cameraGranted = true
            cameraDenied = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        inFlightPaymentId?.let { outState.putString(STATE_IN_FLIGHT_ID, it) }
        upiAppName?.let { outState.putString(STATE_UPI_APP_NAME, it) }
        outState.putBoolean(STATE_VIA_USSD, paidViaUssd)
        outState.putBoolean(STATE_COPIED_VPA, ussdCopiedVpa)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(chosenAppReceiver) }
        // The payee VPA is PII: don't leave it on the clipboard once the flow is over.
        if (isFinishing && ussdCopiedVpa) {
            UssdPayHelper.clearVpa(this)
            ussdCopiedVpa = false
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    /**
     * Inserts the PendingPayment row, then fires the UPI intent through a
     * "Pay with" chooser. The row goes in first so a payment is always tracked
     * even if this process dies while the UPI app is open.
     */
    private fun launchUpiPayment(
        request: UpiPaymentRequest,
        amountMinor: Long,
        remarks: String?,
        billId: String?,
        onError: (String) -> Unit
    ) {
        lifecycleScope.launch {
            val paymentRef = UpiIntentPayments.newPaymentRef()
            val uri = UpiIntentPayments.buildPayUri(request, amountMinor, remarks, paymentRef)
            val upiIntent = Intent(Intent.ACTION_VIEW, uri)
            PaymentDiagnostics.started(paymentRef, request.raw, uri.toString())

            // Which installed apps handle upi://pay. Deliberately NOT gated on
            // Intent.resolveActivity(): that only accepts a single "default"
            // match and wrongly reports nothing on some phones even with
            // GPay/PhonePe installed. We only give up if actually launching
            // fails (ActivityNotFoundException below).
            @Suppress("DEPRECATION")
            val upiApps = packageManager.queryIntentActivities(upiIntent, 0)
            android.util.Log.d("ScanPay", "UPI apps that can handle upi://pay: ${upiApps.size}")

            val dao = AppDatabase.getDatabase(this@QrScanActivity).pendingPaymentDao()
            withContext(Dispatchers.IO) {
                dao.insert(
                    PendingPayment(
                        id = paymentRef,
                        billId = billId,
                        amountMinor = UpiIntentPayments.effectiveAmountMinor(request, amountMinor),
                        payeeVpa = request.vpa,
                        payeeName = request.payeeName,
                        initiatedAt = System.currentTimeMillis(),
                        status = PendingPaymentStatus.PENDING.name
                    )
                )
            }
            upiAppName = null
            paidViaUssd = false
            inFlightPaymentId = paymentRef

            val chosenCallback = PendingIntent.getBroadcast(
                this@QrScanActivity,
                0,
                Intent(ACTION_UPI_APP_CHOSEN).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            try {
                if (upiApps.isEmpty()) {
                    // Visibility filtering (or an OEM quirk) hid the list:
                    // launch the upi:// intent directly and let Android route
                    // it. Throws ActivityNotFoundException only if truly no
                    // app can take it.
                    upiPayLauncher.launch(upiIntent)
                } else {
                    upiPayLauncher.launch(
                        Intent.createChooser(upiIntent, "Pay with", chosenCallback.intentSender)
                    )
                }
            } catch (e: ActivityNotFoundException) {
                // Nothing was handed off, so there's nothing to wait for.
                withContext(Dispatchers.IO) {
                    dao.updateStatus(paymentRef, PendingPaymentStatus.FAILED.name)
                }
                inFlightPaymentId = null
                onError("No UPI app found on this phone. Install GPay, PhonePe, Paytm or BHIM and try again.")
            }
        }
    }

    /**
     * Second, offline payment mode — *99# USSD, ported from Flowpay's
     * scan-to-pay rail (see [UssdPayHelper] for why it works this way).
     *
     * Order matters, same as Flowpay:
     *  1. PendingPayment row first, so the attempt survives a process death
     *     while the USSD dialog is open.
     *  2. Copy the payee VPA to the clipboard (the user pastes it at the
     *     first USSD prompt).
     *  3. Place the *99*1*3# call. The status screen underneath shows the
     *     step-by-step guide while the system USSD dialog is on top.
     *  4. No USSD result is trusted: the row stays PENDING until the bank's
     *     debit SMS confirms it (PendingPaymentMatcher) or the 30-minute
     *     timeout worker resolves it — the same pipeline as UPI-app payments.
     */
    private fun launchUssdPayment(
        request: UpiPaymentRequest,
        amountMinor: Long,
        remarks: String?,
        billId: String?,
        onError: (String) -> Unit
    ) {
        UssdPayHelper.blockedReason(this)?.let { onError(it); return }
        val effectiveAmountMinor = UpiIntentPayments.effectiveAmountMinor(request, amountMinor)
        if (effectiveAmountMinor > UssdPayHelper.MAX_AMOUNT_MINOR) {
            onError("*99# payments are limited to ₹5,000 per transaction. Use a UPI app for larger amounts.")
            return
        }
        lifecycleScope.launch {
            val paymentRef = UpiIntentPayments.newPaymentRef()
            val dao = AppDatabase.getDatabase(this@QrScanActivity).pendingPaymentDao()
            withContext(Dispatchers.IO) {
                dao.insert(
                    PendingPayment(
                        id = paymentRef,
                        billId = billId,
                        amountMinor = effectiveAmountMinor,
                        payeeVpa = request.vpa,
                        payeeName = request.payeeName,
                        initiatedAt = System.currentTimeMillis(),
                        status = PendingPaymentStatus.PENDING.name
                    )
                )
            }
            if (UssdPayHelper.copyVpa(this@QrScanActivity, request.vpa)) ussdCopiedVpa = true
            upiAppName = "*99#"
            paidViaUssd = true
            inFlightPaymentId = paymentRef

            // Ask for CALL_PHONE once; the launcher dials either way.
            if (UssdPayHelper.hasCallPermission(this@QrScanActivity)) {
                if (!dialUssdNow()) {
                    withContext(Dispatchers.IO) {
                        dao.updateStatus(paymentRef, PendingPaymentStatus.FAILED.name)
                    }
                    inFlightPaymentId = null
                    paidViaUssd = false
                    onError("Couldn't open *99#. Dial ${UssdPayHelper.SCAN_TO_PAY_CODE} from your phone app.")
                }
            } else {
                callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }

    /** Opens the USSD session (direct call, or pre-filled dialer without CALL_PHONE). */
    private fun dialUssdNow(): Boolean {
        val ok = UssdPayHelper.dial(this)
        if (!ok) {
            android.widget.Toast.makeText(
                this,
                "Couldn't open *99#. Dial ${UssdPayHelper.SCAN_TO_PAY_CODE} from your phone app.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        return ok
    }

    /**
     * "Dial *99# again" — e.g. the USSD session timed out mid-menu (they do,
     * after ~2 min). Same payment row; just re-copies the VPA and redials.
     */
    private fun redialUssd(paymentId: String) {
        lifecycleScope.launch {
            val payment = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@QrScanActivity).pendingPaymentDao().getById(paymentId)
            } ?: return@launch
            if (UssdPayHelper.copyVpa(this@QrScanActivity, payment.payeeVpa)) ussdCopiedVpa = true
            if (UssdPayHelper.hasCallPermission(this@QrScanActivity)) dialUssdNow()
            else callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    /**
     * The user explicitly abandons the *99# payment. Only from PENDING: if the
     * bank SMS already confirmed it, the bank wins. Clears the VPA too.
     */
    private fun cancelUssdPayment(paymentId: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@QrScanActivity).pendingPaymentDao().compareAndSetStatus(
                    paymentId,
                    PendingPaymentStatus.PENDING.name,
                    PendingPaymentStatus.FAILED.name
                )
            }
            if (ussdCopiedVpa) {
                UssdPayHelper.clearVpa(this@QrScanActivity)
                ussdCopiedVpa = false
            }
        }
    }

    /** Applies the UPI app's (unreliable) answer. Never produces CONFIRMED. */
    private suspend fun applyUpiAppResult(paymentId: String, response: UpiAppResponse?) {
        val dao = AppDatabase.getDatabase(this).pendingPaymentDao()
        val current = dao.getById(paymentId) ?: return

        // Keep the UPI app's reference for exact SMS matching, unless the
        // bank SMS already supplied the real one.
        response?.bestUpiRef?.let { ref ->
            if (current.upiRef == null && current.statusEnum != PendingPaymentStatus.CONFIRMED) {
                dao.setUpiRef(paymentId, ref)
            }
        }

        when (response.outcome()) {
            UpiAppOutcome.SUCCESS -> {
                dao.compareAndSetStatus(
                    paymentId,
                    PendingPaymentStatus.PENDING.name,
                    PendingPaymentStatus.VERIFIED_PENDING.name
                )
                // Record it as paid right away (no split prompt). If the SMS
                // already confirmed it, this just returns the linked expense.
                ScanPayLedger.ensureExpense(this, paymentId)
            }
            UpiAppOutcome.FAILURE -> {
                // Only from PENDING: if a debit SMS already confirmed it, the
                // bank's word wins over the UPI app's.
                dao.compareAndSetStatus(
                    paymentId,
                    PendingPaymentStatus.PENDING.name,
                    PendingPaymentStatus.FAILED.name
                )
            }
            UpiAppOutcome.UNKNOWN -> Unit // stays PENDING until the SMS or the timeout
        }
    }

    /** "Check SMS again": re-reads the inbox for this payment's debit SMS. */
    private fun checkSmsAgain(paymentId: String, onDone: (String?) -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                PendingPaymentMatcher.rescanInboxFor(this@QrScanActivity, paymentId)
            }
            onDone(
                when (result) {
                    PendingPaymentMatcher.RescanResult.CONFIRMED -> null
                    PendingPaymentMatcher.RescanResult.NO_PERMISSION ->
                        "Allow SMS access in Settings so the app can check your bank SMS."
                    PendingPaymentMatcher.RescanResult.CANDIDATE_ONLY ->
                        "Found a bank debit for this amount, but it doesn't name the payee, " +
                            "so it wasn't matched automatically. If it's this payment, tap \"I've paid — mark as paid\"."
                    PendingPaymentMatcher.RescanResult.NOT_FOUND -> "No matching bank SMS found yet."
                }
            )
        }
    }

    /**
     * "I've paid": the user vouches for it. Recorded as UNVERIFIED (paid, not
     * bank-confirmed) — never CONFIRMED, which only the SMS can set.
     */
    private fun markPaidManually(paymentId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@QrScanActivity).pendingPaymentDao()
            val moved = dao.compareAndSetStatus(
                paymentId,
                PendingPaymentStatus.UNCONFIRMED.name,
                PendingPaymentStatus.UNVERIFIED.name
            )
            if (moved > 0) ScanPayLedger.ensureExpense(this@QrScanActivity, paymentId)
        }
    }
}

// ---------------------------------------------------------------------------
// Flow: scan -> review -> (UPI app) -> status, all inside this one activity.
// ---------------------------------------------------------------------------

@Composable
private fun ScanAndPayFlow(
    cameraGranted: Boolean,
    cameraDenied: Boolean,
    billLabel: String?,
    tripName: String?,
    billAmount: Double?,
    billNote: String?,
    paymentId: String?,
    upiAppName: String?,
    paidViaUssd: Boolean,
    ussdBlockedReason: String?,
    onRequestCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onPay: (UpiPaymentRequest, Long, String?, (String) -> Unit) -> Unit,
    onPayViaUssd: (UpiPaymentRequest, Long, String?, (String) -> Unit) -> Unit,
    onCheckSmsAgain: (String, (String?) -> Unit) -> Unit,
    onMarkPaid: (String) -> Unit,
    onUssdRedial: (String) -> Unit,
    onUssdCancel: (String) -> Unit,
    onClose: () -> Unit
) {
    // Raw QR text survives rotation/process death; parsed on demand.
    var scannedRaw by rememberSaveable { mutableStateOf<String?>(null) }
    val request = remember(scannedRaw) { scannedRaw?.let { parseUpiQr(it) } }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            paymentId != null -> {
                BackHandler(onBack = onClose)
                PaymentStatusScreen(
                    paymentId = paymentId,
                    upiAppName = upiAppName,
                    paidViaUssd = paidViaUssd,
                    billLabel = billLabel,
                    onRetry = { payment ->
                        // Same payee and amount, fresh payment reference,
                        // over the same rail the user picked the first time.
                        val retry = if (paidViaUssd) onPayViaUssd else onPay
                        retry(
                            UpiPaymentRequest(
                                vpa = payment.payeeVpa,
                                payeeName = payment.payeeName,
                                amount = null,
                                currency = UpiIntentPayments.CURRENCY_INR,
                                note = null
                            ),
                            payment.amountMinor,
                            billNote
                        ) { /* error surfaces on the next status read */ }
                    },
                    onCheckSmsAgain = onCheckSmsAgain,
                    onMarkPaid = onMarkPaid,
                    onUssdRedial = onUssdRedial,
                    onUssdCancel = onUssdCancel,
                    onDone = onClose
                )
            }
            request == null -> ScanScreen(
                cameraGranted = cameraGranted,
                cameraDenied = cameraDenied,
                onRequestCamera = onRequestCamera,
                onOpenSettings = onOpenSettings,
                onValidQr = { raw -> scannedRaw = raw },
                onClose = onClose
            )
            else -> {
                BackHandler { scannedRaw = null }
                PaymentReviewScreen(
                    request = request,
                    billLabel = billLabel,
                    tripName = tripName,
                    billAmount = billAmount,
                    billNote = billNote,
                    onPay = onPay,
                    onPayViaUssd = onPayViaUssd,
                    ussdBlockedReason = ussdBlockedReason,
                    onCancel = { scannedRaw = null }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen 1: QR scanner
// ---------------------------------------------------------------------------

/** What the scan frame is showing right now. */
private sealed class ScanState {
    object Searching : ScanState()
    object Found : ScanState()
    data class Invalid(val message: String) : ScanState()
}

@Composable
private fun ScanScreen(
    cameraGranted: Boolean,
    cameraDenied: Boolean,
    onRequestCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onValidQr: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var scanState by remember { mutableStateOf<ScanState>(ScanState.Searching) }
    // Gate shared with the camera analyzer thread: once a code is read, stop
    // decoding until the user retries (no repeated errors / toast spam).
    val analysisPaused = remember { AtomicBoolean(false) }

    fun handleDecoded(raw: String?) {
        if (raw == null) {
            scanState = ScanState.Invalid("No QR code found in that image")
            return
        }
        if (parseUpiQr(raw) == null) {
            scanState = ScanState.Invalid("Not a valid UPI QR — try again")
            return
        }
        scanState = ScanState.Found
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            delay(FOUND_PAUSE_MILLIS) // let the green "found" frame register
            onValidQr(raw)
        }
    }

    fun retry() {
        scanState = ScanState.Searching
        analysisPaused.set(false)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        analysisPaused.set(true)
        scope.launch {
            val text = withContext(Dispatchers.Default) { decodeQrFromImage(context, uri) }
            handleDecoded(text)
        }
    }

    if (!cameraGranted) {
        CameraPermissionState(
            denied = cameraDenied,
            onRequestCamera = onRequestCamera,
            onOpenSettings = onOpenSettings,
            onPickFromGallery = { galleryLauncher.launch("image/*") },
            onClose = onClose
        )
        // A QR picked from the gallery still works without the camera.
        (scanState as? ScanState.Invalid)?.let { invalid ->
            InvalidQrBanner(message = invalid.message, onRetry = ::retry)
        }
        return
    }

    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val hasTorch = camera?.cameraInfo?.hasFlashUnit() == true

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraViewfinder(
            analysisPaused = analysisPaused,
            onCameraBound = { camera = it },
            onFrameDecoded = { text ->
                // Called on the analyzer thread; hop to the main thread.
                scope.launch { handleDecoded(text) }
            }
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val frameSize = minOf(maxWidth, maxHeight) * FRAME_FRACTION
            val frameTop = (maxHeight - frameSize) / 2 - FRAME_LIFT

            ScanFrameOverlay(state = scanState, frameSize = frameSize, frameTop = frameTop)

            // Inside the frame: inline error with retry, or the "found" check.
            Box(
                modifier = Modifier
                    .size(frameSize)
                    .align(Alignment.TopCenter)
                    .offset(y = frameTop),
                contentAlignment = Alignment.Center
            ) {
                when (val state = scanState) {
                    is ScanState.Invalid -> InvalidQrInFrame(state.message, onRetry = ::retry)
                    ScanState.Found -> Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(positiveColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "QR code read",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    ScanState.Searching -> Unit
                }
            }

            // Hint directly under the frame.
            Text(
                text = if (scanState == ScanState.Found) "Got it" else "Point at a UPI QR code",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = frameTop + frameSize + 20.dp)
                    .padding(horizontal = 24.dp)
            )

            // Top bar: close, title, torch.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraOverlayIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = onClose
                )
                Text(
                    text = "Scan & Pay",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (hasTorch) {
                    CameraOverlayIconButton(
                        icon = if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = if (torchOn) "Turn torch off" else "Turn torch on",
                        highlighted = torchOn,
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            // Bottom: gallery fallback.
            Surface(
                onClick = { galleryLauncher.launch("image/*") },
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Pick from gallery",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraOverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                else Color.Black.copy(alpha = 0.45f)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) MaterialTheme.colorScheme.onPrimary else Color.White
        )
    }
}

@Composable
private fun InvalidQrInFrame(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text("Try again")
        }
    }
}

/** Gallery-only path (no camera): the same error, shown as a card. */
@Composable
private fun InvalidQrBanner(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) {
                    Text("OK", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/** Shown instead of the viewfinder until camera access is granted. */
@Composable
private fun CameraPermissionState(
    denied: Boolean,
    onRequestCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFromGallery: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
            }
        }
        // Nothing below the close button while the system permission dialog
        // is up. (No early return: Compose compiler 1.4.3 corrupts its group
        // stack on return@Column from an inline lambda.)
        if (denied) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Camera access needed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scan & Pay uses the camera only to read the UPI QR code. " +
                    "Nothing is recorded or saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Open settings", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPickFromGallery,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick QR from gallery")
            }
            TextButton(onClick = onRequestCamera, modifier = Modifier.padding(top = 4.dp)) {
                Text("Ask again")
            }
        }
        }
    }
}

/** CameraX preview + frame analysis (existing ZXing decoder, no new dependencies). */
@Composable
private fun CameraViewfinder(
    analysisPaused: AtomicBoolean,
    onCameraBound: (Camera) -> Unit,
    onFrameDecoded: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val reader = remember { QRCodeReader() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val previewView = PreviewView(context)
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // Default analysis frames are only ~640x480: a QR shown on
                // another phone's screen is then too small/blurry to decode
                // (the same QR from the gallery worked). Ask for ~720p.
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy(
                                    android.util.Size(1280, 720),
                                    androidx.camera.core.resolutionselector.ResolutionStrategy
                                        .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                    )
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (!analysisPaused.get()) {
                                decodeQr(imageProxy, reader)?.let { text ->
                                    // First code wins; pause until the user retries.
                                    if (analysisPaused.compareAndSet(false, true)) {
                                        onFrameDecoded(text)
                                    }
                                }
                            }
                            imageProxy.close()
                        }
                    }
                try {
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    onCameraBound(camera)
                } catch (e: Exception) {
                    // No usable camera: the preview stays black; gallery still works.
                }
            }, ContextCompat.getMainExecutor(context))
            previewView
        }
    )
}

/**
 * Dimmed scrim with a rounded-square window, teal corner marks and a scan
 * line. Corners turn green when a QR is read and red for an invalid one.
 */
@Composable
private fun ScanFrameOverlay(state: ScanState, frameSize: Dp, frameTop: Dp) {
    val accent = MaterialTheme.colorScheme.primary
    val frameColor by animateColorAsState(
        targetValue = when (state) {
            ScanState.Searching -> accent
            ScanState.Found -> positiveColor()
            is ScanState.Invalid -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(durationMillis = 200),
        label = "frame-color"
    )
    val transition = rememberInfiniteTransition(label = "scan-line")
    val lineFraction by transition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "scan-line-fraction"
    )
    val density = LocalDensity.current
    val boxPx = with(density) { frameSize.toPx() }
    val topPx = with(density) { frameTop.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val left = (size.width - boxPx) / 2f
        val radius = 28.dp.toPx()

        drawRect(color = Color.Black.copy(alpha = 0.62f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, topPx),
            size = Size(boxPx, boxPx),
            cornerRadius = CornerRadius(radius),
            blendMode = BlendMode.Clear
        )

        val arm = boxPx * 0.16f
        val stroke = 5.dp.toPx()
        fun corner(x: Float, y: Float, dx: Int, dy: Int) {
            drawLine(frameColor, Offset(x, y), Offset(x + arm * dx, y), stroke, StrokeCap.Round)
            drawLine(frameColor, Offset(x, y), Offset(x, y + arm * dy), stroke, StrokeCap.Round)
        }
        val inset = stroke / 2f
        corner(left + inset, topPx + inset, 1, 1)
        corner(left + boxPx - inset, topPx + inset, -1, 1)
        corner(left + inset, topPx + boxPx - inset, 1, -1)
        corner(left + boxPx - inset, topPx + boxPx - inset, -1, -1)

        if (state == ScanState.Searching) {
            val y = topPx + boxPx * lineFraction
            val glow = boxPx * 0.16f
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, frameColor.copy(alpha = 0.30f), Color.Transparent),
                    startY = y - glow / 2f,
                    endY = y + glow / 2f
                ),
                topLeft = Offset(left, y - glow / 2f),
                size = Size(boxPx, glow)
            )
            drawLine(
                frameColor,
                Offset(left + 12.dp.toPx(), y),
                Offset(left + boxPx - 12.dp.toPx(), y),
                2.dp.toPx()
            )
        }
    }
}

private const val FOUND_PAUSE_MILLIS = 350L
private const val FRAME_FRACTION = 0.68f
private val FRAME_LIFT = 32.dp

// ---------------------------------------------------------------------------
// Screen 2a: review before handing off to the UPI app
// ---------------------------------------------------------------------------

@Composable
private fun PaymentReviewScreen(
    request: UpiPaymentRequest,
    billLabel: String?,
    tripName: String?,
    billAmount: Double?,
    billNote: String?,
    onPay: (UpiPaymentRequest, Long, String?, (String) -> Unit) -> Unit,
    onPayViaUssd: (UpiPaymentRequest, Long, String?, (String) -> Unit) -> Unit,
    ussdBlockedReason: String?,
    onCancel: () -> Unit
) {
    val amountFixedByQr = request.amount != null
    var amountText by rememberSaveable(request.vpa) {
        mutableStateOf(
            (request.amount ?: billAmount)?.toBigDecimal()?.stripTrailingZeros()?.toPlainString() ?: ""
        )
    }
    var remarks by rememberSaveable(request.vpa) { mutableStateOf(billNote.orEmpty()) }
    var launching by remember { mutableStateOf(false) }
    var launchingUssd by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val amount = amountText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }
    val displayName = request.payeeName ?: request.vpa

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Review payment", onBack = onCancel)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Payee + amount + remarks.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.first().uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (request.payeeName != null) request.vpa else "UPI ID",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (amountFixedByQr) {
                        Text(
                            text = formatRupeeIndian(request.amount!!),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = "Set by this QR code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        WiseAmountField(
                            value = amountText,
                            onValueChange = { amountText = it; errorText = null },
                            enabled = !launching,
                            label = "Amount",
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    if (request.note != null) {
                        // The QR's own note is what gets sent; shown, not editable.
                        Text(
                            text = "Note",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = request.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {
                        ModernTextField(
                            value = remarks,
                            onValueChange = { if (it.length <= REMARKS_MAX) remarks = it },
                            enabled = !launching,
                            label = "Remarks (optional)",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // What this payment is for — never ambiguous.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (billLabel != null) "Paying for" else "Not linked to a bill",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = billLabel
                                ?: "It'll be saved as your own expense. You can split it from Home later.",
                            style = if (billLabel != null) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (billLabel == null && tripName != null) {
                            Text(
                                text = "Added to trip: $tripName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // PIN reassurance.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "You'll enter your UPI PIN in your UPI app. Splitlee never sees it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            errorText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Pinned, thumb-friendly actions.
        BottomActions {
            Button(
                onClick = {
                    val value = amount ?: return@Button
                    launching = true
                    errorText = null
                    onPay(request, rupeesToMinor(value), remarks.trim().ifEmpty { null }) { message ->
                        launching = false
                        errorText = message
                    }
                },
                enabled = amount != null && !launching && !launchingUssd,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (launching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = amount?.let { "Pay ${formatRupeeIndian(it)}" } ?: "Enter an amount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val value = amount ?: return@OutlinedButton
                    launchingUssd = true
                    errorText = null
                    onPayViaUssd(request, rupeesToMinor(value), remarks.trim().ifEmpty { null }) { message ->
                        launchingUssd = false
                        errorText = message
                    }
                },
                enabled = amount != null && !launching && !launchingUssd && ussdBlockedReason == null,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (launchingUssd) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Pay via *99# (no internet)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            ussdBlockedReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }
            TextButton(
                onClick = onCancel,
                enabled = !launching && !launchingUssd,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen 2b: status after returning from the UPI app (same screen, live)
// ---------------------------------------------------------------------------

@Composable
private fun PaymentStatusScreen(
    paymentId: String,
    upiAppName: String?,
    paidViaUssd: Boolean,
    billLabel: String?,
    onRetry: (PendingPayment) -> Unit,
    onCheckSmsAgain: (String, (String?) -> Unit) -> Unit,
    onMarkPaid: (String) -> Unit,
    onUssdRedial: (String) -> Unit,
    onUssdCancel: (String) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var payment by remember { mutableStateOf<PendingPayment?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var checkMessage by remember { mutableStateOf<String?>(null) }

    // Live status from the payment's own row. The SMS matcher runs in
    // SmsReceiver, so polling is how this screen flips PENDING/VERIFIED_PENDING
    // to CONFIRMED on its own. Stops once nothing more can change.
    LaunchedEffect(paymentId, refreshTick) {
        val dao = AppDatabase.getDatabase(context).pendingPaymentDao()
        while (true) {
            val latest = withContext(Dispatchers.IO) { dao.getById(paymentId) } ?: break
            payment = latest
            if (!latest.statusEnum.isAwaitingSms) break
            delay(STATUS_POLL_MILLIS)
        }
    }

    val current = payment
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Payment", onBack = onDone)
        if (current == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {

        val status = current.statusEnum
        val style = statusStyle(status)
        val payee = current.payeeName ?: current.payeeVpa
        val amountText = formatRupeeIndian(current.amountMinor / 100.0)
        val appName = upiAppName ?: "your UPI app"

        val (title, subtext) = when (status) {
            PendingPaymentStatus.VERIFIED_PENDING ->
                "$amountText paid to $payee" to "Paid via $appName · confirming with bank…"
            PendingPaymentStatus.CONFIRMED ->
                "$amountText paid to $payee" to
                    (current.upiRef?.let { "Bank confirmed · UPI Ref $it" } ?: "Bank confirmed")
            PendingPaymentStatus.PENDING ->
                if (paidViaUssd) "Complete the payment in *99#" to "Then wait for your bank's SMS — it can take a minute or two"
                else "Confirming payment…" to "Waiting for bank confirmation…"
            PendingPaymentStatus.FAILED ->
                if (paidViaUssd) "Payment cancelled" to "This *99# payment to $payee was cancelled."
                else "Payment failed" to "${appName.replaceFirstChar { it.uppercase() }} reported that the payment to $payee didn't go through. " +
                    "No money was taken. Some UPI apps (often Google Pay) block payments to personal UPI IDs " +
                    "started from another app. Try PhonePe, Paytm or BHIM, use *99#, or pay inside your UPI " +
                    "app directly. Splitlee still picks up the bank SMS."
            PendingPaymentStatus.UNVERIFIED ->
                "$amountText paid to $payee" to "Not yet verified by your bank SMS"
            PendingPaymentStatus.UNCONFIRMED ->
                "Couldn't confirm this payment" to "Check your bank SMS."
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(style.container),
                contentAlignment = Alignment.Center
            ) {
                if (status == PendingPaymentStatus.PENDING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 4.dp,
                        color = style.tint
                    )
                } else {
                    Icon(
                        imageVector = style.icon,
                        contentDescription = style.label,
                        tint = style.tint,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            PaymentStatusChip(status)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Flowpay's on-screen guide: the USSD dialog sits on top of this
            // screen, so the steps stay visible while the user works the menu.
            if (paidViaUssd && status == PendingPaymentStatus.PENDING) {
                UssdStepsCard(amountText = amountText, vpa = current.payeeVpa)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Details card: amount / payee / ref / time / what it's for.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow("Amount", amountText)
                    DetailRow("To", payee)
                    if (current.payeeName != null) DetailRow("UPI ID", current.payeeVpa)
                    if (status == PendingPaymentStatus.CONFIRMED) {
                        current.upiRef?.let { DetailRow("UPI Ref", it) }
                        current.confirmedAt?.let { DetailRow("Confirmed", formatTimestamp(it)) }
                    }
                    DetailRow("Started", formatTimestamp(current.initiatedAt))
                    DetailRow(
                        "For",
                        billLabel ?: if (current.billId != null) "Saved as an expense" else "Not linked to a bill yet"
                    )
                }
            }

            // Status-specific explanation.
            val explanation = when (status) {
                PendingPaymentStatus.PENDING -> if (paidViaUssd)
                    "*99# gives the app no result, so this updates on its own when your bank's debit " +
                        "SMS arrives. You can close this screen once you've entered your PIN."
                else
                    "We didn't get a clear answer from $appName. This updates on its own when your " +
                        "bank's debit SMS arrives — you can close this screen."
                PendingPaymentStatus.VERIFIED_PENDING ->
                    "It's saved as paid. We'll quietly match it with your bank's debit SMS."
                PendingPaymentStatus.FAILED -> if (paidViaUssd)
                    "Nothing was saved. If money did leave your account, it'll show in your bank SMS — " +
                        "add it as a manual expense."
                else
                    "No money should have left your account, so nothing was saved."
                PendingPaymentStatus.UNVERIFIED ->
                    "We couldn't verify this with your bank SMS — please double-check in your UPI app."
                PendingPaymentStatus.UNCONFIRMED ->
                    "No matching bank SMS arrived within 30 minutes. If money did leave your account, " +
                        "check again or mark it as paid."
                PendingPaymentStatus.CONFIRMED -> null
            }
            explanation?.let {
                Spacer(modifier = Modifier.height(12.dp))
                NoticeCard(text = it, style = style)
            }
            // The exact link sent + the UPI app's raw reply, for diagnosing
            // failures like "receiver's UPI ID not available".
            if (status == PendingPaymentStatus.FAILED && !paidViaUssd &&
                PaymentDiagnostics.paymentId == current.id
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                TechnicalDetails()
            }
            checkMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        BottomActions {
            when (status) {
                PendingPaymentStatus.PENDING -> if (paidViaUssd) {
                    PrimaryAction("Dial *99# again") { onUssdRedial(current.id) }
                    OutlinedButton(
                        onClick = { onUssdCancel(current.id); refreshTick++ },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Cancel payment")
                    }
                    SecondaryAction("Close — I've entered my PIN", onDone)
                } else {
                    PrimaryAction("Done", onClick = onDone)
                }
                PendingPaymentStatus.FAILED -> {
                    PrimaryAction("Retry") { onRetry(current) }
                    SecondaryAction("Done", onDone)
                }
                PendingPaymentStatus.UNCONFIRMED, PendingPaymentStatus.UNVERIFIED -> {
                    PrimaryAction(if (checking) "Checking…" else "Check SMS again", enabled = !checking) {
                        checking = true
                        checkMessage = null
                        onCheckSmsAgain(current.id) { message ->
                            checking = false
                            checkMessage = message
                            refreshTick++
                        }
                    }
                    if (status == PendingPaymentStatus.UNCONFIRMED) {
                        OutlinedButton(
                            onClick = {
                                onMarkPaid(current.id)
                                checkMessage = null
                                refreshTick++
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("I've paid — mark as paid")
                        }
                    }
                    SecondaryAction("Done", onDone)
                }
                else -> PrimaryAction("Done", onClick = onDone)
            }
        }
        }
    }
}

/** Numbered *99# steps (ported from Flowpay's instructions card). */
@Composable
private fun UssdStepsCard(amountText: String, vpa: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "In the *99# window",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            UssdPayHelper.steps(amountText).forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${i + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            TextButton(onClick = { UssdPayHelper.copyVpa(context, vpa) }) {
                Text("Copy UPI ID again")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/** Icon + colors per status, so every state reads by shape as well as color. */
private data class StatusStyle(
    val icon: ImageVector,
    val tint: Color,
    val container: Color,
    val label: String
)

@Composable
private fun statusStyle(status: PendingPaymentStatus): StatusStyle {
    val scheme = MaterialTheme.colorScheme
    val warn = warnColor()
    return when (status) {
        PendingPaymentStatus.VERIFIED_PENDING ->
            StatusStyle(Icons.Filled.CheckCircle, positiveColor(), positiveContainerColor(), "Paid")
        PendingPaymentStatus.CONFIRMED ->
            StatusStyle(Icons.Filled.Verified, positiveColor(), positiveContainerColor(), "Bank confirmed")
        PendingPaymentStatus.PENDING ->
            StatusStyle(Icons.Filled.Info, scheme.primary, scheme.primaryContainer, "Confirming")
        PendingPaymentStatus.FAILED ->
            StatusStyle(Icons.Filled.Cancel, scheme.error, scheme.errorContainer, "Failed")
        PendingPaymentStatus.UNVERIFIED ->
            StatusStyle(Icons.Filled.Info, warn, warn.copy(alpha = 0.16f), "Not verified")
        PendingPaymentStatus.UNCONFIRMED ->
            StatusStyle(Icons.Filled.WarningAmber, warn, warn.copy(alpha = 0.16f), "Unconfirmed")
    }
}

@Composable
private fun NoticeCard(text: String, style: StatusStyle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(style.container)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (style.icon == Icons.Filled.Cancel) Icons.Filled.Info else style.icon,
            contentDescription = null,
            tint = style.tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** Bottom-pinned action area. */
@Composable
private fun BottomActions(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().imePadding()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors()
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(label)
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

private const val REMARKS_MAX = 50
private const val STATUS_POLL_MILLIS = 1_500L

/** Decodes a QR from a gallery image (downsampled), or null if none is found. */
private fun decodeQrFromImage(context: Context, uri: Uri): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        bitmap.recycle()

        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val reader = QRCodeReader()
        runCatching { reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text }.getOrNull()
            ?: runCatching {
                reader.reset()
                reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints).text
            }.getOrNull()
            ?: runCatching {
                // Light-on-dark QR (dark-mode screenshots).
                reader.reset()
                reader.decode(BinaryBitmap(HybridBinarizer(source.invert())), hints).text
            }.getOrNull()
    } catch (e: Exception) {
        null
    }
}

private val QR_HINTS = mapOf(
    DecodeHintType.TRY_HARDER to true,
    DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
    DecodeHintType.CHARACTER_SET to "UTF-8"
)

@Volatile private var frameCounter = 0

/** Decodes a single camera frame's luma plane into QR text, or null if none is found. */
private fun decodeQr(imageProxy: ImageProxy, reader: QRCodeReader): String? {
    return try {
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height

        // The Y-plane's rowStride can be wider than the image itself —
        // padding many devices add for memory alignment. The old code
        // copied the buffer straight across assuming no padding, which
        // shears every row after the first on any device where that
        // padding exists, producing an image ZXing can never actually
        // decode (matches "not scanning anything" — not occasional,
        // just device-dependent). Copying row by row, skipping the
        // padding, is what makes this decode consistently.
        val bytes = ByteArray(width * height)
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(bytes)
        } else {
            val rowBuffer = ByteArray(rowStride)
            for (row in 0 until height) {
                val bytesToRead = minOf(rowStride, buffer.remaining())
                if (bytesToRead <= 0) break
                buffer.get(rowBuffer, 0, bytesToRead)
                for (col in 0 until width) {
                    val srcIndex = col * pixelStride
                    if (srcIndex < bytesToRead) {
                        bytes[row * width + col] = rowBuffer[srcIndex]
                    }
                }
            }
        }

        val full = PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)
        // Centre square (where the on-screen frame is): makes a small QR
        // fill more of the image, which helps a lot with phone screens.
        val side = (minOf(width, height) * 0.75).toInt()
        val center = PlanarYUVLuminanceSource(
            bytes, width, height, (width - side) / 2, (height - side) / 2, side, side, false
        )
        frameCounter = (frameCounter + 1) % 3
        // Cheap tries every frame; inverted (light-on-dark QR, e.g. dark-mode
        // payment apps) and the global binarizer (screen glare / moiré)
        // every third frame to keep scanning smooth.
        val attempts = buildList<com.google.zxing.Binarizer> {
            add(HybridBinarizer(full))
            add(HybridBinarizer(center))
            if (frameCounter == 0) {
                add(HybridBinarizer(full.invert()))
                add(GlobalHistogramBinarizer(center))
                add(HybridBinarizer(center.invert()))
            }
        }
        attempts.firstNotNullOfOrNull { binarizer ->
            try {
                reader.decode(BinaryBitmap(binarizer), QR_HINTS).text
            } catch (e: Exception) {
                null
            } finally {
                reader.reset()
            }
        }
    } catch (e: NotFoundException) {
        null
    } catch (e: FormatException) {
        null
    } catch (e: ChecksumException) {
        null
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}

/** Collapsed by default; shows [PaymentDiagnostics.report] with a Copy button. */
@Composable
private fun TechnicalDetails() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { open = !open }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (open) "Hide technical details" else "Show technical details")
        }
        if (open) {
            val report = remember { PaymentDiagnostics.report() }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        report,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Splitlee payment details", report))
                            android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Copy") }
                }
            }
        }
    }
}
