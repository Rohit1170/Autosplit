package com.example.splitwiseauto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import com.example.splitwiseauto.ui.theme.glassBorderColor
import com.example.splitwiseauto.ui.theme.glassCardColor
import com.example.splitwiseauto.ui.theme.positiveColor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

/**
 * A single expense's full detail — opened by tapping (not long-pressing) an
 * expense on Home. Shows the expense info, a small map with just this one
 * expense's location (rather than the cluttered all-expenses map), and who it
 * was split with.
 */
class ExpenseDetailActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getDatabase(this)
        val expenseId = intent.getIntExtra("expenseId", -1)

        if (expenseId <= 0) {
            finish()
            return
        }

        setContent {
            SplitWiseAutoTheme {
                var expense by remember { mutableStateOf<Expense?>(null) }
                var participants by remember { mutableStateOf<List<ExpenseParticipant>>(emptyList()) }
                var linkedPayment by remember { mutableStateOf<PendingPayment?>(null) }
                var loading by remember { mutableStateOf(true) }

                LaunchedEffect(expenseId) {
                    expense = database.expenseDao().getExpenseById(expenseId)
                    participants = database.expenseParticipantDao().getParticipantsForExpense(expenseId)
                    linkedPayment = database.pendingPaymentDao().findByBillId(expenseId.toString())
                    loading = false
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExpenseDetailScreen(
                        loading = loading,
                        expense = expense,
                        participants = participants,
                        linkedPayment = linkedPayment,
                        onClose = { finish() },
                        onEdit = { current ->
                            startActivity(
                                Intent(this@ExpenseDetailActivity, ManualExpenseActivity::class.java).apply {
                                    putExtra("expenseId", current.id)
                                    putExtra("amount", current.amount.toString())
                                    putExtra("merchant", current.merchant)
                                    putExtra("date", current.date)
                                    putExtra("category", current.category)
                                    putExtra("payer", current.payer)
                                    putExtra("bank", current.bank)
                                    current.accountIdentifier?.let { putExtra("accountIdentifier", it) }
                                    current.timestampMillis?.let { putExtra("timestampMillis", it) }
                                }
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseDetailScreen(
    loading: Boolean,
    expense: Expense?,
    participants: List<ExpenseParticipant>,
    linkedPayment: PendingPayment? = null,
    onClose: () -> Unit,
    onEdit: (Expense) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
            }
            Text(
                text = "Expense details",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (expense != null) {
                IconButton(onClick = { onEdit(expense) }) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit")
                }
            }
        }

        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            expense == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Expense not found — it may have been deleted.")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { ExpenseSummaryCard(expense = expense) }
                    linkedPayment?.let { payment -> item { ScanPayStatusCard(payment) } }
                    item { ExpenseLocationCard(expense = expense) }
                    item { ExpenseParticipantsCard(expense = expense, participants = participants) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ExpenseSummaryCard(expense: Expense) {
    val category = categoryByName(expense.category)
    val darkTheme = isSystemInDarkTheme()
    val badgeBackground = category.fgColor.copy(alpha = if (darkTheme) 0.28f else 0.18f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, glassBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(badgeBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.name,
                        tint = category.fgColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column {
                    Text(
                        text = expense.merchant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = category.fgColor.copy(alpha = if (darkTheme) 0.22f else 0.14f)
                    ) {
                        Text(
                            text = category.name,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = category.fgColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "₹${String.format(Locale.US, "%.2f", expense.amount)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            ExpenseDetailRow(label = "Date", value = expense.date)
            ExpenseDetailRow(
                label = "Bank",
                value = if (expense.accountIdentifier != null) {
                    "${expense.bank} · ${expense.accountIdentifier}"
                } else {
                    expense.bank
                }
            )
            ExpenseDetailRow(label = "Paid by", value = expense.payer)
        }
    }
}

@Composable
private fun ExpenseDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExpenseLocationCard(expense: Expense) {
    val darkTheme = isSystemInDarkTheme()
    val category = categoryByName(expense.category)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, glassBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Location",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val latitude = expense.latitude
            val longitude = expense.longitude

            if (latitude != null && longitude != null) {
                val markerPosition = LatLng(latitude, longitude)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(markerPosition, 15f)
                }
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        mapStyleOptions = if (darkTheme) MapStyleOptions(DETAIL_DARK_MAP_STYLE_JSON) else null
                    ),
                    // A single reference pin doesn't need zoom buttons, the map
                    // toolbar, or a compass cluttering a 180dp preview card.
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        mapToolbarEnabled = false,
                        compassEnabled = false,
                        myLocationButtonEnabled = false
                    )
                ) {
                    // Built INSIDE the GoogleMap content block on purpose.
                    // BitmapDescriptorFactory only works after the Maps SDK
                    // has initialized, which happens when a map first loads in
                    // the app process. Creating the icon before GoogleMap(...)
                    // (as this used to) crashed with "IBitmapDescriptorFactory
                    // is not initialized" whenever this was the first map
                    // opened since the app started, e.g. right after a fresh
                    // launch, before visiting the Map tab.
                    val accentIcon = remember(category.fgColor) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(category.fgColor.toArgb(), hsv)
                        BitmapDescriptorFactory.defaultMarker(hsv[0])
                    }
                    Marker(
                        state = MarkerState(position = markerPosition),
                        title = expense.merchant,
                        icon = accentIcon
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // No reverse-geocoded address is stored for an expense (only the
                // raw coordinates), so this shows the coordinates rather than
                // fabricating a street address.
                Text(
                    text = "%.5f, %.5f".format(Locale.US, latitude, longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No location recorded for this expense.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExpenseParticipantsCard(expense: Expense, participants: List<ExpenseParticipant>) {
    val context = LocalContext.current
    // No per-expense settlement flag exists on ExpenseParticipant, so "paid" is
    // derived from who fronted the money: the payer already paid, everyone else
    // owes their share until a settlement is recorded elsewhere in the app.
    val pendingWarnColor = Color(0xFFF59E0B)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, glassBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Split with",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (participants.isEmpty()) {
                Text(
                    text = "This expense hasn't been split yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                participants.forEachIndexed { index, participant ->
                    val isPaid = participant.personName.equals(expense.payer, ignoreCase = true)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = participant.personName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Column {
                                Text(
                                    text = participant.personName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPaid) "Paid" else "Pending",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPaid) positiveColor() else pendingWarnColor
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", participant.shareAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!isPaid) {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        // Honest handoff: this only opens the device's
                                        // own share sheet with a prefilled message — the
                                        // person still has to pick an app and hit send.
                                        val message = "Hey ${participant.personName}, just a reminder — " +
                                            "you owe ₹${String.format(Locale.US, "%.2f", participant.shareAmount)} " +
                                            "for ${expense.merchant} (${expense.date})."
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, message)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Remind ${participant.personName}"))
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = "Remind · ₹${participant.shareAmount.toBigDecimal().stripTrailingZeros().toPlainString()}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                    if (index != participants.lastIndex) {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val splitTotal = participants.sumOf { it.shareAmount }
                val addsUp = kotlin.math.abs(splitTotal - expense.amount) < 0.01

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (addsUp) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = positiveColor(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    Text(
                        text = if (addsUp) "Split adds up" else "Split doesn't add up — check amounts",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (addsUp) positiveColor() else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Compact "Night" Google Maps style for the single-pin preview card — only
// applied in system dark mode; MapProperties falls back to the normal map
// style otherwise. Kept local to this file (rather than shared with
// ExpenseMapScreen's copy) since that one is private to its own file.
private const val DETAIL_DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#1a201e"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1a201e"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8e9a95"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a4842"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#212a26"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2a3330"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#141917"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#8e9a95"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3a4a44"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a1a2a"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5e8df2"}]}
]
"""


/**
 * Scan & Pay status for an expense that came from (or was paid through) Scan
 * & Pay. For UNVERIFIED payments this carries the calm "couldn't verify"
 * notice; it never says "bank confirmed" unless the SMS actually matched.
 */
@Composable
private fun ScanPayStatusCard(payment: PendingPayment) {
    val status = payment.statusEnum
    val message = when (status) {
        PendingPaymentStatus.CONFIRMED ->
            payment.upiRef?.let { "Paid with Scan & Pay · Bank confirmed · UPI Ref $it" }
                ?: "Paid with Scan & Pay · Bank confirmed"
        PendingPaymentStatus.VERIFIED_PENDING ->
            "Paid with Scan & Pay · confirming with your bank…"
        PendingPaymentStatus.UNVERIFIED ->
            "We couldn't verify this with your bank SMS — please double-check in your UPI app."
        PendingPaymentStatus.PENDING ->
            "Scan & Pay · waiting for bank confirmation…"
        PendingPaymentStatus.UNCONFIRMED ->
            "Couldn't confirm this payment. Check your bank SMS."
        PendingPaymentStatus.FAILED ->
            "Your UPI app reported this payment failed."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = glassCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, glassBorderColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PaymentStatusChip(status)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
