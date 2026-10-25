package com.example.splitwiseauto

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Settings > Manage trips (also "All trips" in the Home trip menu): search
 * and filter every trip, and close / reopen, hide / unhide, edit dates,
 * rename or delete one. Closing a trip means new expenses go to Main.
 */
class ManageTripsActivity : ComponentActivity() {
    companion object {
        /** Open with the "Close trip" dialog for this trip (from the reminder). */
        const val EXTRA_CLOSE_TRIP_ID = "close_trip_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this)
        val closeTripId = intent.getIntExtra(EXTRA_CLOSE_TRIP_ID, -1).takeIf { it >= 0 }
        setContent {
            SplitleeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    ManageTripsScreen(
                        closeTripId = closeTripId,
                        onBack = { finish() },
                        onView = { trip ->
                            ActiveTripStore.setActiveTrip(this, trip.id, trip.name, trip.isClosed)
                            // Back to Home (closes Settings too if it's open).
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            )
                            finish()
                        }
                    )
                }
            }
        }
    }
}

private enum class TripFilter(val label: String) { ALL("All"), OPEN("Open"), CLOSED("Closed"), HIDDEN("Hidden") }

private val TripsAccent: Color @Composable get() = Brand.accent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageTripsScreen(closeTripId: Int? = null, onBack: () -> Unit, onView: (Trip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var totals by remember { mutableStateOf<Map<Int, TripTotal>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TripFilter.ALL) }
    var selected by remember { mutableStateOf<Trip?>(null) }
    var renaming by remember { mutableStateOf<Trip?>(null) }
    var deleting by remember { mutableStateOf<Pair<Trip, Int>?>(null) }
    var version by remember { mutableStateOf(0) }
    var closing by remember { mutableStateOf<Trip?>(null) }

    LaunchedEffect(version) {
        trips = db.tripDao().getAll()
        totals = db.expenseDao().tripTotals().associateBy { it.tripId }
    }
    // Opened from the "Is your trip over?" reminder.
    LaunchedEffect(closeTripId) {
        if (closeTripId != null) closing = db.tripDao().getById(closeTripId)?.takeIf { !it.isClosed }
    }

    closing?.let { trip ->
        CloseTripDialog(
            trip = trip,
            onDismiss = { closing = null },
            onClosed = {
                closing = null
                version++
            }
        )
    }

    fun save(updated: Trip) {
        scope.launch {
            db.tripDao().update(updated)
            ActiveTripStore.syncWith(context, updated)
            if (updated.hidden) ActiveTripStore.clearIf(context, updated.id)
            version++
        }
    }

    fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply {
                    set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0)
                }
                onPicked(c.timeInMillis)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val shown = trips
        .filter { t ->
            when (filter) {
                TripFilter.ALL -> !t.hidden
                TripFilter.OPEN -> !t.hidden && !t.isClosed
                TripFilter.CLOSED -> !t.hidden && t.isClosed
                TripFilter.HIDDEN -> t.hidden
            }
        }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy<Trip> { it.isClosed }.thenByDescending { it.rangeStart })

    // Actions for one trip.
    selected?.let { trip ->
        AppSheet(
            title = trip.name,
            subtitle = trip.dateRangeLabel() + if (trip.isClosed) " · Closed" else " · Open",
            onDismiss = { selected = null },
            glass = true
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TripAction(Icons.Filled.Place, "View on Home") {
                    selected = null
                    onView(trip)
                }
                if (trip.isClosed) {
                    TripAction(Icons.Filled.LockOpen, "Reopen trip", "New expenses can join it again") {
                        selected = null
                        save(trip.copy(closedAtMillis = null, endMillis = null))
                    }
                } else {
                    TripAction(Icons.Filled.Lock, "Close trip", "Pick the last day; new expenses go to Main") {
                        selected = null
                        closing = trip
                    }
                }
                TripAction(Icons.Filled.CalendarMonth, "Start date", trip.dateRangeLabel()) {
                    pickDate(trip.rangeStart) { picked ->
                        selected = null
                        save(trip.copy(startMillis = picked))
                    }
                }
                TripAction(Icons.Filled.CalendarMonth, "End date", if (trip.rangeEnd == null) "Still going" else trip.dateRangeLabel()) {
                    pickDate(trip.rangeEnd ?: System.currentTimeMillis()) { picked ->
                        selected = null
                        save(trip.copy(endMillis = picked.coerceAtLeast(trip.rangeStart)))
                    }
                }
                TripAction(Icons.Filled.Edit, "Rename") {
                    selected = null
                    renaming = trip
                }
                if (trip.hidden) {
                    TripAction(Icons.Filled.Visibility, "Unhide", "Show it in the Home trip menu again") {
                        selected = null
                        save(trip.copy(hidden = false))
                    }
                } else {
                    TripAction(Icons.Filled.VisibilityOff, "Hide", "Only visible here, under Hidden") {
                        selected = null
                        save(trip.copy(hidden = true))
                    }
                }
                TripAction(Icons.Filled.Delete, "Delete trip", danger = true) {
                    selected = null
                    scope.launch { deleting = trip to db.expenseDao().countForTrip(trip.id) }
                }
            }
        }
    }

    renaming?.let { trip ->
        var name by remember(trip.id) { mutableStateOf(trip.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename trip") },
            text = {
                ModernTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Trip name",
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = null
                    save(trip.copy(name = name.trim()))
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { (trip, count) ->
        DeleteTripDialog(
            trip = trip,
            expenseCount = count,
            onDismiss = { deleting = null },
            onDelete = { alsoDeleteExpenses ->
                deleting = null
                scope.launch {
                    db.withTransaction {
                        if (alsoDeleteExpenses) db.expenseDao().deleteTripExpensesWithParticipants(trip.id)
                        else db.expenseDao().detachFromTrip(trip.id)
                        db.tripDao().deleteById(trip.id)
                    }
                    ActiveTripStore.clearIf(context, trip.id)
                    version++
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Trips",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            ModernTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search trips",
                leading = null,
                trailing = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TripFilter.values().toList()) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TripsAccent.copy(alpha = 0.16f),
                            selectedLabelColor = TripsAccent
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    if (trips.isEmpty()) "No trips yet. Start one from the Main menu on Home."
                    else "No trips match.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp)
                )
            }
        }

        items(shown, key = { it.id }) { trip ->
            TripRow(trip = trip, total = totals[trip.id], onClick = { selected = trip })
        }
    }
}

@Composable
private fun TripRow(trip: Trip, total: TripTotal?, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 18.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(TripsAccent.copy(alpha = if (trip.isClosed) 0.08f else 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (trip.isClosed) Icons.Filled.Lock else Icons.Filled.Place,
                contentDescription = null,
                tint = if (trip.isClosed) MaterialTheme.colorScheme.onSurfaceVariant else TripsAccent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                trip.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(trip.dateRangeLabel())
                    append(if (trip.isClosed) " · Closed" else " · Open")
                    if (trip.hidden) append(" · Hidden")
                    total?.let { append(" · ${it.count} expense${if (it.count == 1) "" else "s"}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatRupeeIndian(total?.total ?: 0.0),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TripAction(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val tint = if (danger) MaterialTheme.colorScheme.error else TripsAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable {
                Haptics.tap(view)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/**
 * Close a trip on a chosen last day. Suggests the day of the trip's last
 * expense; anything in the trip dated after the chosen day can be moved
 * back to Main in the same step.
 */
@Composable
internal fun CloseTripDialog(trip: Trip, onDismiss: () -> Unit, onClosed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggested by remember(trip.id) { mutableStateOf<Long?>(null) }
    var end by remember(trip.id) { mutableStateOf<Long?>(null) }
    var after by remember(trip.id) { mutableStateOf(0) }
    var moveLater by remember(trip.id) { mutableStateOf(true) }

    LaunchedEffect(trip.id) {
        val s = TripClosing.suggestedEnd(context, trip)
        suggested = s
        if (end == null) end = s
    }
    LaunchedEffect(end) {
        end?.let { after = TripClosing.countAfter(context, trip, it) }
    }
    val fmt = remember { java.text.SimpleDateFormat("EEE, d MMM yyyy", java.util.Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close ${trip.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Last day of the trip", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TripsAccent.copy(alpha = 0.10f))
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = end ?: System.currentTimeMillis() }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    end = Calendar.getInstance().apply {
                                        set(y, m, d, 12, 0, 0); set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis.coerceAtLeast(trip.rangeStart)
                                },
                                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                            ).apply { datePicker.minDate = trip.rangeStart }.show()
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = TripsAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(end?.let { fmt.format(java.util.Date(it)) } ?: "…", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Change", color = TripsAccent, style = MaterialTheme.typography.labelLarge)
                }
                suggested?.let {
                    Text(
                        "Suggested: your last trip expense (${fmt.format(java.util.Date(it))}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (after > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { moveLater = !moveLater }
                    ) {
                        androidx.compose.material3.Checkbox(checked = moveLater, onCheckedChange = { moveLater = it })
                        Text(
                            "Move $after expense${if (after == 1) "" else "s"} after this day to Main",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Text(
                    "New expenses will go to Main. You can reopen the trip anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = end != null, onClick = {
                val chosen = end
                if (chosen != null) scope.launch {
                    TripClosing.close(context, trip, chosen, moveLaterToMain = moveLater && after > 0)
                    onClosed()
                }
            }) { Text("Close trip") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
