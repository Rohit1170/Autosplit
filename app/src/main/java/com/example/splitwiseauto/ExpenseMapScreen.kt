package com.example.splitwiseauto

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ExpenseMapScreen(
    expenses: List<Expense>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var selectedCluster by remember { mutableStateOf<List<Expense>?>(null) }
    val markerPositions = remember(expenses) {
        expenses.mapNotNull { expense ->
            expense.latitude?.let { latitude ->
                expense.longitude?.let { longitude -> LatLng(latitude, longitude) }
            }
        }
    }
    val savedFallback = remember { LocationHelper.getLastKnownLocation(context) }
    var currentLocation by remember { mutableStateOf<LocationSnapshot?>(null) }
    var locationStatus by remember { mutableStateOf("Finding your location…") }
    var isResolvingLocation by remember { mutableStateOf(true) }
    var mapLoaded by remember { mutableStateOf(false) }
    var initialCameraApplied by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(LocationHelper.hasLocationPermission(context))
    }

    var nearbyExpanded by remember { mutableStateOf(false) }
    val darkTheme = isSystemInDarkTheme()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            savedFallback?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(20.5937, 78.9629),
            if (savedFallback != null) 15f else 5.0f
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            LocationHelper.hasLocationPermission(context)
    }

    // Opening the Map is an explicit user action, so request location once here
    // instead of making the user find the locate button first.
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(mapLoaded, hasLocationPermission, markerPositions) {
        if (!mapLoaded || initialCameraApplied) return@LaunchedEffect

        try {
        // withTimeoutOrNull is a safety net: whatever happens inside (a Play Services
        // Task that never resolves, an unexpected suspend point, etc.) this can never
        // leave the "Finding your location…" overlay stuck on screen indefinitely.
        // The surrounding try/finally is a second safety net: even a real thrown
        // exception (not just a timeout) can never skip clearing the overlay below.
        withTimeoutOrNull(10_000) {
            if (hasLocationPermission) {
                locationStatus = "Finding your location…"
                repeat(2) { attempt ->
                    currentLocation = LocationHelper.getCurrentLocation(context)
                    if (currentLocation != null) return@repeat
                    if (attempt < 1) delay(750)
                }
                currentLocation?.let {
                    LocationHelper.saveLastKnownLocation(context, it)
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude),
                            16f
                        )
                    )
                    locationStatus = "Showing your current location"
                }
            }

            if (currentLocation == null && markerPositions.isNotEmpty()) {
                val boundsBuilder = LatLngBounds.Builder()
                markerPositions.forEach(boundsBuilder::include)
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 96)
                )
                locationStatus = "Showing saved expense locations"
            } else if (currentLocation == null && savedFallback != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(savedFallback.latitude, savedFallback.longitude),
                        15f
                    )
                )
                locationStatus = "Showing your last known location"
            } else if (currentLocation == null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(20.5937, 78.9629), 5.0f)
                )
                locationStatus = "No location available yet"
            } else {
                locationStatus = "Showing your current location"
            }
        }

        } finally {
            // Unconditional: guarantees the overlay always dismisses, even if the block
            // above timed out or threw before setting a final locationStatus.
            initialCameraApplied = true
            isResolvingLocation = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                // Full-bleed dark map to match the rest of the redesign —
                // only applied in system dark mode, so light mode keeps the
                // standard map instead of forcing dark on everyone.
                mapStyleOptions = if (darkTheme) MapStyleOptions(DARK_MAP_STYLE_JSON) else null
            ),
            // Keeps the native "my location" button clear of the floating
            // bell/mode-chip row drawn on top of this screen.
            contentPadding = PaddingValues(top = 64.dp),
            onMapLoaded = { mapLoaded = true },
            onMyLocationButtonClick = {
                // NOTE: onMyLocationClick below only fires if the user taps the blue
                // location dot on the map, not automatically once this button finishes
                // centering — so this callback must NOT turn our overlay on, since
                // nothing would reliably turn it back off again. The native button
                // already shows its own built-in loading feedback for this action.
                false // let the map SDK perform its own default camera move
            },
            onMyLocationClick = {
                currentLocation = LocationSnapshot(it.latitude, it.longitude)
                LocationHelper.saveLastKnownLocation(context, currentLocation!!)
                locationStatus = "Showing your current location"
                initialCameraApplied = true
                isResolvingLocation = false
            }
        ) {
            // Reading zoom here makes this block recompose as the user pinches/zooms,
            // so clusters merge and split live instead of only on the next full redraw.
            val zoom = cameraPositionState.position.zoom
            val clusters = remember(expenses, zoom) { clusterExpenses(expenses, zoom) }

            clusters.forEach { cluster ->
                if (cluster.expenses.size == 1) {
                    val expense = cluster.expenses.first()
                    val category = categorize(expense.merchant)
                    Marker(
                        state = MarkerState(position = cluster.center),
                        title = expense.merchant,
                        snippet = "₹${String.format(Locale.US, "%.2f", expense.amount)} • ${expense.date}",
                        icon = rememberPillMarkerIcon(
                            text = "₹${expense.amount.toBigDecimal().stripTrailingZeros().toPlainString()}",
                            color = category.fgColor
                        ),
                        onClick = {
                            selectedExpense = expense
                            false
                        }
                    )
                } else {
                    // Mixed-category clusters use the color of whichever
                    // category is most common in the group, and show the
                    // count rather than a summed amount (a sum across
                    // different categories/dates reads as misleading).
                    val dominantCategory = cluster.expenses
                        .groupBy { categorize(it.merchant) }
                        .maxByOrNull { it.value.size }
                        ?.key ?: OTHER
                    Marker(
                        state = MarkerState(position = cluster.center),
                        title = "${cluster.expenses.size} expenses",
                        snippet = "Tap to see them",
                        icon = rememberPillMarkerIcon(
                            text = "×${cluster.expenses.size}",
                            color = dominantCategory.fgColor
                        ),
                        onClick = {
                            // Repeat visits to the same place (e.g. a daily breakfast
                            // spot) have near-identical coordinates and will keep
                            // clustering together no matter how far you zoom in, so
                            // zooming can never split them apart. Show a picker of
                            // the individual expenses instead.
                            selectedCluster = cluster.expenses
                            true // consumed: we show our own picker instead of the default info window
                        }
                    )
                }
            }
        }

        when {
            isResolvingLocation -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Finding your location…",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            currentLocation == null && markerPositions.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No saved location yet", style = MaterialTheme.typography.titleMedium)
                    Text("Allow location and add an expense to see it on the map.")
                }
            }
        }

        if (currentLocation == null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = locationStatus,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        selectedExpense?.let { expense ->
            Card(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(expense.merchant, style = MaterialTheme.typography.titleMedium)
                    Text("₹${String.format(Locale.US, "%.2f", expense.amount)} • ${expense.date}")
                    Text(expense.bank, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Bottom sheet listing the nearest expenses to wherever the map's
        // reference point is (live location if we have it, else the saved
        // fallback) — this is what makes the map screen a tool for finding
        // nearby spending, not just a decorative pin display. Hidden while a
        // single-marker card is showing so the two don't stack.
        val referencePoint = currentLocation ?: savedFallback
        if (selectedExpense == null && referencePoint != null) {
            val nearby = remember(expenses, referencePoint) {
                expenses
                    .filter { it.latitude != null && it.longitude != null }
                    .map { expense ->
                        expense to haversineKm(
                            referencePoint.latitude, referencePoint.longitude,
                            expense.latitude!!, expense.longitude!!
                        )
                    }
                    .sortedBy { it.second }
                    .take(15)
            }

            if (nearby.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { nearbyExpanded = !nearbyExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.NearMe,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nearby (${nearby.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (nearbyExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = if (nearbyExpanded) "Collapse" else "Expand"
                            )
                        }

                        if (nearbyExpanded) {
                            LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                                items(nearby, key = { it.first.id }) { (expense, distanceKm) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                context.startActivity(
                                                    Intent(context, ExpenseDetailActivity::class.java).apply {
                                                        putExtra("expenseId", expense.id)
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = expense.merchant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatDistance(distanceKm),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "₹${String.format(Locale.US, "%.2f", expense.amount)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedCluster?.let { clusterExpenses ->
            AlertDialog(
                onDismissRequest = { selectedCluster = null },
                title = { Text("${clusterExpenses.size} expenses here") },
                text = {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(clusterExpenses) { expense ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCluster = null
                                        context.startActivity(
                                            Intent(context, ExpenseDetailActivity::class.java).apply {
                                                putExtra("expenseId", expense.id)
                                            }
                                        )
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(expense.merchant, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "₹${String.format(Locale.US, "%.2f", expense.amount)} • ${expense.date}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedCluster = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

/** Bitmap "pill" marker icons are cached by (text, color) so pinch-zoom recomposition doesn't redraw the same bitmap every frame. */
private val pillMarkerCache = mutableMapOf<String, BitmapDescriptor>()

@Composable
private fun rememberPillMarkerIcon(text: String, color: Color): BitmapDescriptor {
    val argb = color.toArgb()
    return remember(text, argb) {
        pillMarkerCache.getOrPut("$text-$argb") { createPillBitmapDescriptor(text, argb) }
    }
}

private fun createPillBitmapDescriptor(text: String, backgroundArgb: Int): BitmapDescriptor {
    val density = 3f // fixed density-independent scale; markers don't need to track the device's actual density
    val textSizePx = 13f * density
    val paddingHPx = 10f * density
    val heightPx = 26f * density

    val textPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.WHITE
        textSize = textSizePx
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val textWidth = textPaint.measureText(text)
    val widthPx = (textWidth + paddingHPx * 2).coerceAtLeast(heightPx)

    val bitmap = android.graphics.Bitmap.createBitmap(
        widthPx.toInt().coerceAtLeast(1),
        heightPx.toInt().coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)

    val bgPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = backgroundArgb
    }
    val rect = android.graphics.RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
    canvas.drawRoundRect(rect, heightPx / 2f, heightPx / 2f, bgPaint)

    // A thin white ring so the pill stays legible over both light and dark map tiles.
    val ringPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = android.graphics.Color.argb(180, 255, 255, 255)
    }
    canvas.drawRoundRect(rect, heightPx / 2f, heightPx / 2f, ringPaint)

    val textY = bitmap.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, bitmap.width / 2f, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}

private fun formatDistance(km: Double): String = if (km < 1.0) {
    "${(km * 1000).toInt()} m away"
} else {
    "${String.format(Locale.US, "%.1f", km)} km away"
}

// Standard "Night" Google Maps style (roads/water/POI recolored for a dark
// background) — only applied when the system is in dark mode; MapProperties
// falls back to the normal map otherwise.
private const val DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#1a201e"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1a201e"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8e9a95"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#3a4842"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#212a26"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#6b8f7e"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#12251c"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2a3330"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#141917"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#8e9a95"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3a4a44"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#212a26"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0a1a2a"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5e8df2"}]}
]
"""

private data class MapCluster(
    val center: LatLng,
    val expenses: List<Expense>
)

/**
 * Naive grid-based clustering: buckets expenses into cells sized relative to the
 * current zoom level, then collapses each bucket into one cluster centered on the
 * average position of the expenses in it. Not pixel-perfect (a point right on a
 * cell boundary can end up in a different bucket than a very close neighbour),
 * but it's enough to stop a zoomed-out map from being a wall of overlapping pins,
 * without pulling in a new, unverified clustering dependency.
 */
private fun clusterExpenses(expenses: List<Expense>, zoom: Float): List<MapCluster> {
    val locatable = expenses.filter { it.latitude != null && it.longitude != null }
    if (locatable.isEmpty()) return emptyList()

    // Bigger cells at low zoom (zoomed out, world/country view) so nearby pins
    // merge into one cluster; smaller cells at high zoom (street level) so real
    // individual expenses stay separate. Clamped so it never over- or under-clusters.
    val cellSizeDegrees = (60.0 / 2.0.pow(zoom.toDouble())).coerceIn(0.0008, 20.0)

    return locatable
        .groupBy { expense ->
            val latCell = floor(expense.latitude!! / cellSizeDegrees).toInt()
            val lngCell = floor(expense.longitude!! / cellSizeDegrees).toInt()
            latCell to lngCell
        }
        .values
        .map { group ->
            MapCluster(
                center = LatLng(
                    group.map { it.latitude!! }.average(),
                    group.map { it.longitude!! }.average()
                ),
                expenses = group
            )
        }
}
