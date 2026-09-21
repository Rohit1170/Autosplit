package com.example.splitwiseauto

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
//import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import com.example.splitwiseauto.ui.theme.positiveColor
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * "Scan & Pay" — scans a UPI QR and hands the payment off to the person via
 * a real phone call (ACTION_CALL), the same way any other *99#/123Pay call
 * would happen. This is the HONEST-HANDOFF model, not automation: the app
 * never sees, handles, or automates the UPI PIN, never replies inside the
 * USSD/IVR session, and uses no AccessibilityService. See PaymentAttempt,
 * PaymentSessionManager, DialManager and PaymentSmsAdopter for the pieces
 * that place the call, watch it end, and confirm the outcome from the
 * bank's own SMS — never from call state or call duration alone.
 *
 * UI note on the scanner's targeting box: it's a fixed frame with an
 * animated scan line and a green "found it" pulse on detection — not a
 * dynamic box that tracks the QR's exact pixel corners as you move the
 * phone. That would need mapping the camera analysis frame's coordinates
 * (accounting for sensor rotation and the PreviewView's fill-center
 * scaling) onto screen space, which is easy to get subtly wrong (mirrored
 * or rotated) in a way that's only visible on a real device — so the
 * simpler, reliably-correct pattern was used instead.
 */
class QrScanActivity : ComponentActivity() {

    private var cameraExecutor: ExecutorService? = null
    private lateinit var dialManager: DialManager

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Camera permission is needed to scan a QR", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    private val callPhonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val readPhoneStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        dialManager = DialManager(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Needed to place the call directly (ACTION_CALL).
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }

        // Needed for call-state monitoring (the <5s = cancelled rule) and
        // SIM/carrier detection for the Jio rail check. Optional in the
        // sense that the flow still works without it — see DialManager and
        // SimRailSelector's docs for exactly what's lost without it.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            readPhoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

        // Scan & Pay's status indicator is a notification, not a
        // draw-over-other-apps overlay (see PaymentOverlayService's class
        // doc for why: that overlay used to block the soft keyboard on the
        // *99# "Enter UPI ID" system dialog on some OEM Android skins). So
        // there's nothing to request here — POST_NOTIFICATIONS is already
        // requested by MainActivity on app start.

        setContent {
            SplitWiseAutoTheme {
                ScanAndPayScreen(
                    activity = this,
                    dialManager = dialManager,
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        dialManager.cancel()
        PaymentOverlayService.stop(this)
    }
}

/** Where we are in a Scan & Pay attempt. */
private sealed class PayStep {
    object Confirm : PayStep()
    object Dialing : PayStep()
    data class InProgress(val attemptId: Int) : PayStep()
    data class Finished(val success: Boolean, val message: String) : PayStep()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanAndPayScreen(
    activity: Activity,
    dialManager: DialManager,
    onDismiss: () -> Unit
) {
    var scannedRequest by remember { mutableStateOf<UpiPaymentRequest?>(null) }
    var amountText by remember { mutableStateOf("") }
    var step by remember { mutableStateOf<PayStep>(PayStep.Confirm) }
    var rail by remember { mutableStateOf(PaymentRail.USSD_99) }
    var simSlot by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val choice = SimRailSelector.detect(activity)
        rail = choice.rail
        simSlot = choice.simSlot
    }

    fun startPayment() {
        val request = scannedRequest ?: return
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) return

        step = PayStep.Dialing
        coroutineScope.launch {
            val attempt = PaymentSessionManager.start(
                context = activity,
                upiId = request.vpa,
                payeeName = request.payeeName,
                expectedAmount = amount,
                simSlot = simSlot,
                rail = rail
            )

            val dialString = when (rail) {
                PaymentRail.USSD_99 -> {
                    UssdPayHelper.copyVpaToClipboard(activity, request.vpa)
                    "tel:${Uri.encode(UssdPayHelper.buildUssdCode())}"
                }
                // A UPI QR only ever gives us a VPA, never the payee's
                // phone number — and 123Pay's IVR rail is phone-number
                // based, not VPA-based. Without a number to feed
                // Upi123CallStringBuilder, the honest fallback per the
                // locked design is the person's own installed UPI app via
                // deep link, not a guessed/incomplete DTMF string.
                PaymentRail.IVR_123PAY, PaymentRail.UPI_DEEPLINK -> null
            }

            if (dialString == null) {
                val deepLink = "upi://pay?pa=${Uri.encode(request.vpa)}" +
                    (request.payeeName?.let { "&pn=${Uri.encode(it)}" } ?: "") +
                    "&am=${amount.toBigDecimal().stripTrailingZeros().toPlainString()}" +
                    (request.note?.let { "&tn=${Uri.encode(it)}" } ?: "")
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)))
                    PaymentSessionManager.onDialPlaced(activity, attempt.id)
                    step = PayStep.Finished(
                        true,
                        "Handed off to your UPI app — this app can't see that payment's outcome, " +
                            "but a confirming bank SMS will still create the expense here as usual."
                    )
                } catch (e: Exception) {
                    step = PayStep.Finished(false, "No UPI app found to complete this on Jio.")
                }
                return@launch
            }

            val placed = dialManager.placeCall(dialString) { durationMillis ->
                coroutineScope.launch {
                    PaymentSessionManager.onCallEnded(activity, attempt.id, durationMillis)
                    if (durationMillis < PaymentAttemptStateMachine.MIN_CALL_DURATION_MILLIS) {
                        PaymentOverlayService.stop(activity)
                        step = PayStep.Finished(false, "Call ended quickly — treated as cancelled.")
                    } else {
                        PaymentOverlayService.updateStatus("Waiting for your bank's confirmation SMS…")
                    }
                }
            }

            if (!placed) {
                step = PayStep.Finished(false, "Couldn't place the call — check the Phone permission.")
                return@launch
            }

            PaymentSessionManager.onDialPlaced(activity, attempt.id)
            PaymentOverlayService.onCancelClicked = {
                coroutineScope.launch {
                    dialManager.cancel()
                    PaymentSessionManager.onCancelled(activity, attempt.id)
                    PaymentOverlayService.stop(activity)
                    step = PayStep.Finished(false, "Cancelled.")
                }
            }
            PaymentOverlayService.show(
                activity,
                request.payeeName,
                amount,
                "Complete the payment in the dialer that opened"
            )
            step = PayStep.InProgress(attempt.id)
        }
    }

    // Polls the attempt's own row for a terminal status. SMS adoption
    // happens in SmsReceiver, a separate component from this screen, so
    // this is how the UI notices a confirmation arrived — never by
    // reading the USSD/call session itself. Also enforces the 10-minute
    // verification window while this screen is alive (process-death /
    // app-not-open coverage is PaymentSessionManager.reconcileOnStart).
    val inProgress = step as? PayStep.InProgress
    LaunchedEffect(inProgress?.attemptId) {
        val attemptId = inProgress?.attemptId ?: return@LaunchedEffect
        while (true) {
            delay(2_000)
            val attempt = AppDatabase.getDatabase(activity).paymentAttemptDao().getById(attemptId) ?: break
            when (attempt.statusEnum) {
                PaymentAttemptStatus.SUCCESS -> {
                    PaymentOverlayService.stop(activity)
                    step = PayStep.Finished(true, "Confirmed from your bank's SMS.")
                    break
                }
                PaymentAttemptStatus.FAILED -> {
                    PaymentOverlayService.stop(activity)
                    step = PayStep.Finished(false, "Payment failed — nothing logged.")
                    break
                }
                PaymentAttemptStatus.CANCELLED -> {
                    PaymentOverlayService.stop(activity)
                    step = PayStep.Finished(false, "Cancelled.")
                    break
                }
                else -> {
                    val age = System.currentTimeMillis() - attempt.createdAtMillis
                    if (age > PaymentAttemptStateMachine.VERIFICATION_WINDOW_MILLIS) {
                        AppDatabase.getDatabase(activity).paymentAttemptDao().claimStatus(
                            attempt.id,
                            PaymentAttemptStatus.TIMEOUT.name,
                            listOf(attempt.status)
                        )
                        PaymentOverlayService.stop(activity)
                        step = PayStep.Finished(
                            false,
                            "No confirmation yet. We'll keep watching for the SMS — check " +
                                "\"Missed expenses\" later if it comes through after this."
                        )
                        break
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (scannedRequest == null) {
            QrCameraPreview(
                onQrDetected = { raw ->
                    val request = parseUpiQr(raw)
                    if (request == null) {
                        Toast.makeText(activity, "Not a valid UPI QR", Toast.LENGTH_SHORT).show()
                    } else {
                        scannedRequest = request
                        amountText = request.amount?.let {
                            it.toBigDecimal().stripTrailingZeros().toPlainString()
                        } ?: ""
                    }
                },
                onClose = onDismiss
            )
        } else {
            val request = scannedRequest!!

            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {

                        when (val current = step) {
                            is PayStep.Confirm -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (request.payeeName?.firstOrNull() ?: '?').uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = request.payeeName ?: "Unknown payee",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = request.vpa,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                request.note?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }

                                Text(
                                    text = "Amount",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 22.dp)
                                )
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it },
                                    placeholder = { Text("Enter amount to pay") },
                                    leadingIcon = {
                                        Text("₹", style = MaterialTheme.typography.titleMedium)
                                    },
                                    textStyle = MaterialTheme.typography.titleLarge,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                )

                                Text(
                                    text = "Pay via",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 20.dp)
                                )
                                Row(
                                    modifier = Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = rail == PaymentRail.USSD_99,
                                        onClick = { rail = PaymentRail.USSD_99 },
                                        label = { Text("*99# (USSD)") }
                                    )
                                    FilterChip(
                                        selected = rail == PaymentRail.IVR_123PAY,
                                        onClick = { rail = PaymentRail.IVR_123PAY },
                                        label = { Text("123Pay (Jio)") }
                                    )
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(modifier = Modifier.padding(12.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "You'll place the call yourself and finish it in " +
                                                "the dialer — this app never sees or enters your UPI " +
                                                "PIN. It confirms success afterwards from your bank's " +
                                                "SMS.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                Column(modifier = Modifier.padding(top = 22.dp)) {
                                    Button(
                                        onClick = { startPayment() },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Pay", style = MaterialTheme.typography.titleMedium)
                                    }
                                    TextButton(
                                        onClick = { scannedRequest = null },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Text("Scan again")
                                    }
                                }
                            }

                            is PayStep.Dialing -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "Placing the call…",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            is PayStep.InProgress -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 3.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "Complete it in the dialer",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Finish the call as usual — this screen will update " +
                                            "itself once your bank confirms the debit by SMS.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                dialManager.cancel()
                                                PaymentSessionManager.onCancelled(activity, current.attemptId)
                                                PaymentOverlayService.stop(activity)
                                                step = PayStep.Finished(false, "Cancelled.")
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }

                            is PayStep.Finished -> {
                                val tint = if (current.success) positiveColor() else MaterialTheme.colorScheme.error
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(tint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (current.success) {
                                                Icons.Filled.CheckCircle
                                            } else {
                                                Icons.Filled.ErrorOutline
                                            },
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = if (current.success) "Payment sent" else "Payment not confirmed",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = current.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    Button(
                                        onClick = onDismiss,
                                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 22.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("Done")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-bleed camera preview with a scanner targeting overlay: a dimmed
 * scrim outside a rounded square frame, corner brackets, an animated scan
 * line while searching, and a green pulse + checkmark when a QR is found.
 * Detection freezes further analysis and waits [DETECT_PAUSE_MILLIS] so the
 * pulse is actually visible before handing off to [onQrDetected] — without
 * that pause the screen used to jump away the instant a code was read, so
 * the "found it" feedback never had a chance to show.
 */
@Composable
private fun QrCameraPreview(onQrDetected: (String) -> Unit, onClose: () -> Unit) {
    var detectedText by remember { mutableStateOf<String?>(null) }
    val reader = remember { QRCodeReader() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(detectedText) {
        val text = detectedText ?: return@LaunchedEffect
        delay(DETECT_PAUSE_MILLIS)
        onQrDetected(text)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val previewView = PreviewView(context)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                if (detectedText == null) {
                                    decodeQr(imageProxy, reader)?.let { text ->
                                        detectedText = text
                                    }
                                }
                                imageProxy.close()
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            context as LifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (e: Exception) {
                        // Camera bind failures (e.g. no camera hardware) just mean
                        // the preview stays blank — the user can still back out.
                    }
                }, ContextCompat.getMainExecutor(context))

                previewView
            }
        )

        ScannerOverlay(detected = detectedText != null)

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
        }

        Text(
            text = if (detectedText != null) "QR detected" else "Align the QR code within the frame",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private const val DETECT_PAUSE_MILLIS = 450L
private val SCAN_FRAME_COLOR_FOUND = Color(0xFF34D399)

/**
 * The scanner's targeting frame: a dimmed scrim with a rounded square
 * "window" punched out of it (so the live camera feed is only visible
 * there), corner brackets, and — while [detected] is false — an animated
 * scan line sweeping the window. On detection the brackets turn green and
 * a checkmark badge fades in at the center.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ScannerOverlay(detected: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan-line")
    val scanLineFraction by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan-line-fraction"
    )

    val frameColor by animateColorAsState(
        targetValue = if (detected) SCAN_FRAME_COLOR_FOUND else Color.White,
        animationSpec = tween(durationMillis = 250),
        label = "frame-color"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val boxSize = minOf(size.width, size.height) * 0.66f
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f
            val cornerRadiusPx = 28.dp.toPx()

            // Dimmed scrim with a rounded-square hole punched through to
            // the camera preview underneath (BlendMode.Clear + an
            // offscreen compositing layer is what makes the "hole" show
            // through to the view below instead of just going transparent
            // against an opaque black background).
            drawRect(color = Color.Black.copy(alpha = 0.6f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(cornerRadiusPx),
                blendMode = BlendMode.Clear
            )

            // Corner brackets.
            val bracketLen = boxSize * 0.14f
            val strokePx = 5.dp.toPx()

            fun bracket(x: Float, y: Float, dx: Int, dy: Int) {
                drawLine(
                    color = frameColor,
                    start = Offset(x, y),
                    end = Offset(x + bracketLen * dx, y),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = frameColor,
                    start = Offset(x, y),
                    end = Offset(x, y + bracketLen * dy),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
            bracket(left, top, 1, 1)
            bracket(left + boxSize, top, -1, 1)
            bracket(left, top + boxSize, 1, -1)
            bracket(left + boxSize, top + boxSize, -1, -1)

            if (!detected) {
                val lineY = top + boxSize * scanLineFraction
                val glowHeight = boxSize * 0.18f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, frameColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = lineY - glowHeight / 2f,
                        endY = lineY + glowHeight / 2f
                    ),
                    topLeft = Offset(left, lineY - glowHeight / 2f),
                    size = Size(boxSize, glowHeight)
                )
                drawLine(
                    color = frameColor,
                    start = Offset(left + 6.dp.toPx(), lineY),
                    end = Offset(left + boxSize - 6.dp.toPx(), lineY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        AnimatedVisibility(
            visible = detected,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SCAN_FRAME_COLOR_FOUND),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/** Decodes a single camera frame's luma plane into QR text, or null if none is found. */
private fun decodeQr(imageProxy: ImageProxy, reader: QRCodeReader): String? {
    return try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val source = PlanarYUVLuminanceSource(
            bytes,
            imageProxy.width,
            imageProxy.height,
            0,
            0,
            imageProxy.width,
            imageProxy.height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result: Result = reader.decode(bitmap)
        result.text
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
