package com.example.splitwiseauto

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Period filter for Main (a trip always shows the whole trip). */
private enum class MapPeriod(val label: String) { MONTH("This month"), ALL("All time") }

private const val DAY_MS = 86_400_000L

/**
 * Map tab: category pins, mint group bubbles, glass controls, a sheet with
 * the total for the visible area, a tap card, trip route + day filters,
 * search over your own expenses and a spending heatmap. Everything here is
 * read-only; no data is changed.
 */
@Composable
fun ExpenseMapScreen(
    expenses: List<Expense>,
    active: Boolean = true,
    loaded: Boolean = true,
    modifier: Modifier = Modifier,
    // Trip mode: open on the trip's latest expense, not your current location.
    tripId: Int? = null,
    // Height of the floating tab bar: the map runs underneath it, overlays sit above it.
    bottomInset: Dp = 0.dp,
    // False while [expenses] still belong to the previous mode (Main vs a trip):
    // the camera waits for the new mode's data before moving.
    dataReady: Boolean = true
) {
    // Fresh install / no located expenses: show an empty state instead of
    // spinning up Google Maps, the location prompt and marker/bounds code.
    if (!loaded) {
        LoadingStateView(modifier.padding(bottom = bottomInset))
        return
    }
    if (expenses.isEmpty()) {
        EmptyStateView(
            icon = Icons.Filled.Place,
            title = "No expenses on the map yet",
            message = "Expenses saved with a location will appear here as pins.",
            modifier = modifier.padding(bottom = bottomInset)
        )
        return
    }

    // Lazily create the Google Map only after the Map tab has actually been
    // opened once (MapView.onCreate is heavy; the pager pre-composes pages).
    var mapActivated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) mapActivated = true }
    if (!mapActivated) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    val accent = Brand.accent()

    // ---- filters ----------------------------------------------------------
    var period by rememberSaveable { mutableStateOf(MapPeriod.MONTH) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var heatmap by rememberSaveable { mutableStateOf(false) }
    var dayFilter by remember(tripId) { mutableStateOf<Int?>(null) }

    var trip by remember(tripId) { mutableStateOf<Trip?>(null) }
    LaunchedEffect(tripId) {
        trip = tripId?.let { AppDatabase.getDatabase(context).tripDao().getById(it) }
    }
    val tripStart = remember(trip) { trip?.let { startOfDay(it.rangeStart) } }
    fun dayOf(e: Expense): Int? {
        val start = tripStart ?: return null
        val ts = e.timestampMillis ?: return null
        return ((ts - start) / DAY_MS).toInt().coerceAtLeast(0) + 1
    }

    val monthStart = remember { startOfMonth(System.currentTimeMillis()) }
    val located = remember(expenses) { expenses.filter { it.latitude != null && it.longitude != null } }

    // Categories present (for chips), most used first.
    val categoryChips = remember(located) {
        located.groupBy { categoryByName(it.category).name }
            .entries.sortedByDescending { it.value.size }
            .map { it.key }
            .take(6)
    }
    val tripDays = remember(located, tripStart) {
        if (tripStart == null) emptyList() else located.mapNotNull { dayOf(it) }.distinct().sorted()
    }

    val filtered = remember(located, period, categoryFilter, query, dayFilter, tripId, tripStart) {
        located.asSequence()
            .filter { tripId != null || period == MapPeriod.ALL || (it.timestampMillis ?: 0L) >= monthStart }
            .filter { categoryFilter == null || categoryByName(it.category).name == categoryFilter }
            .filter { query.isBlank() || it.merchant.contains(query.trim(), ignoreCase = true) }
            .filter { dayFilter == null || dayOf(it) == dayFilter }
            .toList()
    }

    // ---- selection --------------------------------------------------------
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var selectedCluster by remember { mutableStateOf<List<Expense>?>(null) }
    var sheetExpanded by rememberSaveable { mutableStateOf(false) }

    // ---- location / camera (unchanged behaviour) ---------------------------
    val savedFallback = remember { LocationHelper.getLastKnownLocation(context) }
    var currentLocation by remember { mutableStateOf<LocationSnapshot?>(null) }
    var isResolvingLocation by remember { mutableStateOf(true) }
    var mapLoaded by remember { mutableStateOf(false) }
    var initialCameraApplied by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(LocationHelper.hasLocationPermission(context)) }
    val markerPositions = remember(located) { located.map { LatLng(it.latitude!!, it.longitude!!) } }

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
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    var cameraTripId by remember { mutableStateOf(tripId) }
    // Switching mode (Main <-> a trip, or another trip) re-centres the map:
    // a trip goes to its latest expense, Main goes to where you are now.
    LaunchedEffect(tripId) {
        if (tripId != cameraTripId) {
            cameraTripId = tripId
            selectedExpense = null
            selectedCluster = null
            initialCameraApplied = false
            isResolvingLocation = true
        }
    }

    LaunchedEffect(mapLoaded, hasLocationPermission, markerPositions, tripId, initialCameraApplied, dataReady) {
        if (!mapLoaded || initialCameraApplied || !dataReady) return@LaunchedEffect
        if (tripId != null) {
            val latest = located.maxByOrNull { it.timestampMillis ?: 0L }
            if (latest != null) {
                try {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(latest.latitude!!, latest.longitude!!), 14f)
                    )
                } finally {
                    initialCameraApplied = true
                    isResolvingLocation = false
                }
                return@LaunchedEffect
            }
        }
        try {
            withTimeoutOrNull(10_000) {
                if (hasLocationPermission) {
                    repeat(2) { attempt ->
                        currentLocation = LocationHelper.getCurrentLocation(context)
                        if (currentLocation != null) return@repeat
                        if (attempt < 1) delay(750)
                    }
                    currentLocation?.let {
                        LocationHelper.saveLastKnownLocation(context, it)
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                        )
                    }
                }
                if (currentLocation == null && markerPositions.isNotEmpty()) {
                    val b = LatLngBounds.Builder()
                    markerPositions.forEach(b::include)
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 96))
                } else if (currentLocation == null && savedFallback != null) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(savedFallback.latitude, savedFallback.longitude), 15f)
                    )
                }
            }
        } finally {
            initialCameraApplied = true
            isResolvingLocation = false
        }
    }

    fun fitTo(list: List<Expense>) {
        val pts = list.map { LatLng(it.latitude!!, it.longitude!!) }
        if (pts.isEmpty()) return
        coroutineScope.launch {
            runCatching {
                if (pts.size == 1) cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pts.first(), 15f))
                else {
                    val b = LatLngBounds.Builder(); pts.forEach(b::include)
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 140))
                }
            }
        }
    }
    fun fitAll() = fitTo(filtered)

    // Day chip: filter AND fly to that day's expenses.
    fun selectDay(d: Int?) {
        dayFilter = d
        selectedExpense = null
        fitTo(located.filter { (d == null || dayOf(it) == d) &&
            (categoryFilter == null || categoryByName(it.category).name == categoryFilter) })
    }

    fun goToMyLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        coroutineScope.launch {
            val loc = LocationHelper.getCurrentLocation(context) ?: return@launch
            currentLocation = loc
            LocationHelper.saveLastKnownLocation(context, loc)
            runCatching {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f))
            }
        }
    }

    // Visible area (updated when the camera stops moving).
    var visibleBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    LaunchedEffect(cameraPositionState.isMoving, mapLoaded) {
        if (mapLoaded && !cameraPositionState.isMoving) {
            visibleBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
    }
    val inArea = remember(filtered, visibleBounds) {
        val b = visibleBounds
        if (b == null) filtered else filtered.filter { b.contains(LatLng(it.latitude!!, it.longitude!!)) }
    }
    val referencePoint = currentLocation ?: savedFallback


    val mapStyle = remember(dark) {
        runCatching { MapStyleOptions(if (dark) DARK_MAP_STYLE_JSON else LIGHT_MAP_STYLE_JSON) }.getOrNull()
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission && LocationHelper.hasLocationPermission(context),
                mapStyleOptions = mapStyle
            ),
            uiSettings = MapUiSettings(
                compassEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                indoorLevelPickerEnabled = false
            ),
            contentPadding = PaddingValues(
                top = TAB_CONTENT_TOP_GAP + if (tripId == null) 60.dp else 0.dp,
                bottom = bottomInset + 96.dp
            ),
            onMapLoaded = { mapLoaded = true },
            onMapClick = {
                focusManager.clearFocus()
                selectedExpense = null
            }
        ) {
            val zoom = cameraPositionState.position.zoom

            // Trip route: the trip's expenses joined in date order.
            if (tripId != null && !heatmap) {
                val route = remember(filtered) {
                    filtered.filter { it.timestampMillis != null }
                        .sortedBy { it.timestampMillis }
                        .map { LatLng(it.latitude!!, it.longitude!!) }
                }
                if (route.size >= 2) {
                    Polyline(
                        points = route,
                        color = accent.copy(alpha = 0.6f),
                        width = 6f,
                        pattern = listOf(Dot(), Gap(14f))
                    )
                }
            }

            if (heatmap) {
                val spots = remember(filtered) { heatSpots(filtered) }
                val max = spots.maxOfOrNull { it.second } ?: 1.0
                spots.forEach { (center, total) ->
                    val r = 120.0 + 900.0 * sqrt(total / max)
                    Circle(center = center, radius = r, fillColor = Color(0xFFFF5C5C).copy(alpha = 0.10f), strokeWidth = 0f, strokeColor = Color.Transparent)
                    Circle(center = center, radius = r * 0.6, fillColor = Color(0xFFFFA23C).copy(alpha = 0.18f), strokeWidth = 0f, strokeColor = Color.Transparent)
                    Circle(center = center, radius = r * 0.3, fillColor = Color(0xFFFF5C5C).copy(alpha = 0.32f), strokeWidth = 0f, strokeColor = Color.Transparent)
                }
            } else {
                val clusters = remember(filtered, zoom) { clusterExpenses(filtered, zoom) }
                clusters.forEach { cluster ->
                    if (cluster.expenses.size == 1) {
                        val expense = cluster.expenses.first()
                        val category = categoryByName(expense.category)
                        val isSelected = selectedExpense?.id == expense.id
                        Marker(
                            state = MarkerState(position = cluster.center),
                            icon = if (isSelected) rememberExpensePin(
                                emoji = categoryEmoji(category.name),
                                text = formatRupeeIndian(expense.amount),
                                color = category.fgColor,
                                selected = true,
                                dark = dark
                            ) else rememberDotPin(categoryEmoji(category.name), category.fgColor, dark),
                            anchor = if (isSelected) Offset(0.5f, 1f) else Offset(0.5f, 0.5f),
                            alpha = if (selectedExpense == null || isSelected) 1f else 0.45f,
                            zIndex = if (isSelected) 2f else 0f,
                            onClick = {
                                Haptics.tap(view)
                                selectedExpense = expense
                                true
                            }
                        )
                    } else {
                        Marker(
                            state = MarkerState(position = cluster.center),
                            icon = rememberClusterIcon(cluster.expenses.size, accent),
                            anchor = Offset(0.5f, 0.5f),
                            alpha = if (selectedExpense == null) 1f else 0.45f,
                            zIndex = 1f,
                            onClick = {
                                Haptics.tap(view)
                                selectedExpense = null
                                // Same spot visited again and again won't split by zooming,
                                // so close in: list them; otherwise zoom into the group.
                                val spread = cluster.expenses.map { LatLng(it.latitude!!, it.longitude!!) }
                                val b = LatLngBounds.Builder(); spread.forEach(b::include)
                                val bounds = b.build()
                                val tight = haversineKm(
                                    bounds.southwest.latitude, bounds.southwest.longitude,
                                    bounds.northeast.latitude, bounds.northeast.longitude
                                ) < 0.05
                                if (tight || zoom >= 17f) {
                                    selectedCluster = cluster.expenses
                                } else {
                                    coroutineScope.launch {
                                        runCatching { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140)) }
                                    }
                                }
                                true
                            }
                        )
                    }
                }
            }
        }

        // ---- top: search only (Main). A trip already has its chip in the header row.
        if (tripId == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = TAB_CONTENT_TOP_GAP)
                    .padding(horizontal = 14.dp)
            ) {
                MapSearchBar(
                    query = query,
                    onQuery = { query = it; selectedExpense = null },
                    onDone = { focusManager.clearFocus(); fitAll() }
                )
            }
        }

        // ---- right: two small glass buttons ---------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = TAB_CONTENT_TOP_GAP + if (tripId != null) 4.dp else 62.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MapRoundButton(Icons.Filled.MyLocation, "My location") { Haptics.tap(view); goToMyLocation() }
            MapRoundButton(Icons.Filled.ZoomOutMap, "Show all") { Haptics.tap(view); fitAll() }
        }

        if (isResolvingLocation) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(mapGlass())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                Spacer(Modifier.width(10.dp))
                Text("Finding your location…", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- bottom: tap card, or the sheet (collapsed by default) ----------
        val bottomModifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + 10.dp)

        val sel = selectedExpense
        if (sel != null) {
            Box(bottomModifier) {
                MapExpenseCard(
                    expense = sel,
                    accent = accent,
                    onOpen = {
                        context.startActivity(
                            Intent(context, ExpenseDetailActivity::class.java).putExtra("expenseId", sel.id)
                        )
                    },
                    onDirections = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${sel.latitude},${sel.longitude}")
                                )
                            )
                        }
                    },
                    onClose = { selectedExpense = null }
                )
            }
        } else {
            Box(bottomModifier) {
                MapAreaSheet(
                    expanded = sheetExpanded,
                    onExpandedChange = { sheetExpanded = it },
                    heatmap = heatmap,
                    onHeatmap = { heatmap = it; selectedExpense = null },
                    inArea = inArea,
                    title = when {
                        tripId != null && dayFilter != null -> "Day $dayFilter"
                        tripId != null -> listOfNotNull(trip?.name, trip?.dateRangeLabel()).joinToString(" · ").ifEmpty { "This trip" }
                        else -> period.label
                    },
                    referencePoint = referencePoint,
                    tripDays = if (tripId != null) tripDays else emptyList(),
                    dayFilter = dayFilter,
                    onDay = { selectDay(it) },
                    period = if (tripId == null) period else null,
                    onPeriod = { period = it; selectedExpense = null },
                    categoryChips = categoryChips,
                    categoryFilter = categoryFilter,
                    onCategory = { categoryFilter = it; selectedExpense = null },
                    accent = accent,
                    onPick = { e ->
                        selectedExpense = e
                        coroutineScope.launch {
                            runCatching {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(LatLng(e.latitude!!, e.longitude!!), maxOf(cameraPositionState.position.zoom, 15f))
                                )
                            }
                        }
                    }
                )
            }
        }

        selectedCluster?.let { group ->
            AppSheet(
                title = "${group.size} expenses here",
                subtitle = formatRupeeIndian(group.sumOf { it.amount }) + " in total",
                onDismiss = { selectedCluster = null },
                glass = true
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(group.sortedByDescending { it.timestampMillis ?: 0L }, key = { it.id }) { e ->
                        MapExpenseRow(e, trailingNote = e.date) {
                            selectedCluster = null
                            context.startActivity(
                                Intent(context, ExpenseDetailActivity::class.java).putExtra("expenseId", e.id)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Overlay pieces
// ---------------------------------------------------------------------------

@Composable
private fun mapGlass(): Color = when {
    AppThemeStore.isMaterial -> MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    isSystemInDarkTheme() -> Color(0xFF12201B).copy(alpha = 0.90f)
    else -> Color.White.copy(alpha = 0.95f)
}

@Composable
private fun mapLine(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.07f)

@Composable
private fun MapSearchBar(query: String, onQuery: (String) -> Unit, onDone: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(mapGlass())
            .border(1.dp, mapLine(), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search your expenses by merchant", color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(Brand.accent()),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDone() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear",
                tint = muted,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onQuery("") }
            )
        }
    }
}

@Composable
private fun MapChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(source, haptic = false)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent else mapGlass())
            .border(1.dp, if (selected) Color.Transparent else mapLine(), RoundedCornerShape(18.dp))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Brand.onAccent() else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MapRoundButton(
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    accent: Color = Brand.accent(),
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressScale(source, haptic = false)
            .clip(CircleShape)
            .background(if (active) accent else mapGlass())
            .border(1.dp, if (active) Color.Transparent else mapLine(), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (active) Brand.onAccent() else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TripMapHeader(trip: Trip?, total: Double, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(mapGlass())
            .border(1.dp, mapLine(), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "TRIP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                listOfNotNull(trip?.name, trip?.dateRangeLabel()).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatRupeeIndian(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            Text(
                "$count on map",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MapAreaSheet(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    heatmap: Boolean,
    onHeatmap: (Boolean) -> Unit,
    inArea: List<Expense>,
    title: String,
    referencePoint: LocationSnapshot?,
    tripDays: List<Int>,
    dayFilter: Int?,
    onDay: (Int?) -> Unit,
    period: MapPeriod?,
    onPeriod: (MapPeriod) -> Unit,
    categoryChips: List<String>,
    categoryFilter: String?,
    onCategory: (String?) -> Unit,
    accent: Color,
    onPick: (Expense) -> Unit
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(mapGlass())
            .border(1.dp, mapLine(), shape)
            // Drag up to open, down to close; a tap toggles.
            .pointerInput(expanded) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -40f && !expanded) { Haptics.tap(view); onExpandedChange(true) }
                        if (total > 40f && expanded) { Haptics.tap(view); onExpandedChange(false) }
                    }
                ) { _, dy -> total += dy }
            }
            .padding(horizontal = 18.dp)
            .padding(top = 8.dp, bottom = 14.dp)
    ) {
        // Grab handle.
        Box(Modifier.fillMaxWidth().padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
        }

        // Peek line: what's in view + total. Tap to open / close.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    Haptics.tap(view); onExpandedChange(!expanded)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$title · ${inArea.size} in view",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatRupeeIndian(inArea.sumOf { it.amount }),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (expanded) "Close" else "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
        Spacer(Modifier.height(12.dp))
        // Filters live here, not on the map.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (tripDays.isNotEmpty()) {
                SmallChip("All days", dayFilter == null, accent) { Haptics.tap(view); onDay(null) }
                tripDays.forEach { d -> SmallChip("Day $d", dayFilter == d, accent) { Haptics.tap(view); onDay(d) } }
            }
            if (period != null) {
                MapPeriod.values().forEach { p -> SmallChip(p.label, period == p, accent) { Haptics.tap(view); onPeriod(p) } }
            }
            SmallChip("🔥 Heatmap", heatmap, accent) { Haptics.tap(view); onHeatmap(!heatmap) }
        }
        if (categoryChips.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallChip("All", categoryFilter == null, accent) { Haptics.tap(view); onCategory(null) }
                categoryChips.forEach { name ->
                    SmallChip(name, categoryFilter == name, accent) {
                        Haptics.tap(view); onCategory(if (categoryFilter == name) null else name)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (heatmap) {
            val places = remember(inArea) {
                inArea.groupBy { it.merchant.trim().lowercase(Locale.ROOT) }
                    .values
                    .map { list -> Triple(list.first(), list.size, list.sumOf { it.amount }) }
                    .sortedByDescending { it.third }
                    .take(6)
            }
            val top = places.firstOrNull()?.third?.coerceAtLeast(1.0) ?: 1.0
            if (places.isEmpty()) MapSheetEmpty()
            places.forEachIndexed { i, (e, visits, total) ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(e) }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = accent)
                    Column(Modifier.weight(1f)) {
                        Text(e.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "$visits ${if (visits == 1) "visit" else "visits"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth((total / top).toFloat().coerceIn(0.05f, 1f))
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(formatRupeeIndian(total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val rows = remember(inArea, referencePoint) {
                val withDistance = inArea.map { e ->
                    e to referencePoint?.let { haversineKm(it.latitude, it.longitude, e.latitude!!, e.longitude!!) }
                }
                // Nearby first when you're actually near them; otherwise newest first.
                val near = withDistance.any { (it.second ?: Double.MAX_VALUE) <= NEARBY_KM }
                (if (near) withDistance.sortedBy { it.second }
                else withDistance.sortedByDescending { it.first.timestampMillis ?: 0L }).take(30)
            }
            if (rows.isEmpty()) MapSheetEmpty()
            LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                items(rows, key = { it.first.id }) { (e, km) ->
                    val note = if (km != null && km <= NEARBY_KM) "${formatDistance(km)} · ${expenseWhen(e)}" else expenseWhen(e)
                    MapExpenseRow(e, trailingNote = note) { onPick(e) }
                }
            }
        }
        }
    }
}

@Composable
private fun SmallChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Brand.onAccent() else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Distances are shown only when you're actually around (e.g. not 340 km from Goa). */
private const val NEARBY_KM = 30.0

/** "4 Oct · 8:40 PM" from the timestamp, else the stored date text. */
private fun expenseWhen(e: Expense): String =
    e.timestampMillis?.let {
        java.text.SimpleDateFormat("d MMM · h:mm a", Locale.getDefault()).format(java.util.Date(it))
    } ?: e.date

@Composable
private fun MapSheetEmpty() {
    Text(
        "Nothing in this part of the map. Move the map or change the filter.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp)
    )
}

@Composable
private fun MapExpenseRow(expense: Expense, trailingNote: String, onClick: () -> Unit) {
    val category = categoryByName(expense.category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(category.fgColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = category.fgColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(expense.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trailingNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(formatRupeeIndian(expense.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

/** Tap card: merchant, category, time, amount, split line, Directions + Open. */
@Composable
private fun MapExpenseCard(
    expense: Expense,
    accent: Color,
    onOpen: () -> Unit,
    onDirections: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val category = categoryByName(expense.category)
    var participants by remember(expense.id) { mutableStateOf<List<ExpenseParticipant>>(emptyList()) }
    LaunchedEffect(expense.id) {
        participants = runCatching {
            AppDatabase.getDatabase(context).expenseParticipantDao().getParticipantsForExpense(expense.id)
        }.getOrDefault(emptyList())
    }
    val paidByMe = expense.payer.equals("Me", ignoreCase = true)
    val others = participants.filterNot { it.personName.equals("Me", ignoreCase = true) }
    val splitLine: String? = when {
        participants.size <= 1 && paidByMe -> null
        paidByMe -> "Split ${participants.size} ways · others owe ${formatRupeeIndian(others.sumOf { it.shareAmount })}"
        else -> {
            val mine = participants.filter { it.personName.equals("Me", ignoreCase = true) }.sumOf { it.shareAmount }
            "Paid by ${expense.payer}" + if (mine > 0.01) " · your share ${formatRupeeIndian(mine)}" else ""
        }
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(mapGlass())
            .border(1.dp, mapLine(), shape)
            // Swipe the card down to close it.
            .pointerInput(expense.id) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 40f) { Haptics.tap(view); onClose() } }
                ) { _, dy -> total += dy }
            }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(category.fgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(category.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(expense.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${category.name} · ${expenseWhen(expense)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (splitLine != null) {
                    Text(splitLine, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(formatRupeeIndian(expense.amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MapActionButton("Directions", primary = false, accent = accent, modifier = Modifier.weight(1f), onClick = onDirections)
            MapActionButton("Open", primary = true, accent = accent, modifier = Modifier.weight(1f), onClick = onOpen)
        }
    }
}

@Composable
private fun MapActionButton(label: String, primary: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(42.dp)
            .pressScale(source)
            .clip(RoundedCornerShape(23.dp))
            .background(if (primary) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (primary) Brand.onAccent() else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun avatarColor(name: String): Color {
    val palette = listOf(0xFFFFB86B, 0xFF8FB8FF, 0xFFFF9EAA, 0xFFB79CFF, 0xFFFFD86B, 0xFF7FE0D0)
    return Color(palette[(name.lowercase().hashCode() and 0x7fffffff) % palette.size])
}

// ---------------------------------------------------------------------------
// Marker bitmaps
// ---------------------------------------------------------------------------

/** Emoji drawn inside the pin's coloured circle (Material icons can't be drawn into a marker bitmap simply). */
private fun categoryEmoji(name: String): String = when (name.lowercase(Locale.ROOT)) {
    "food" -> "🍔"
    "groceries" -> "🛒"
    "transport" -> "🚕"
    "travel" -> "✈️"
    "shopping" -> "🛍️"
    "bills" -> "🧾"
    "fuel" -> "⛽"
    "health" -> "💊"
    "subscriptions" -> "📺"
    "entertainment" -> "🎬"
    "other" -> "💸"
    else -> name.take(1).uppercase()
}

private val pinCache = mutableMapOf<String, BitmapDescriptor>()

@Composable
private fun rememberExpensePin(emoji: String, text: String, color: Color, selected: Boolean, dark: Boolean): BitmapDescriptor {
    val argb = color.toArgb()
    return remember(emoji, text, argb, selected, dark) {
        pinCache.getOrPut("p|$emoji|$text|$argb|$selected|$dark") { createExpensePin(emoji, text, argb, selected, dark) }
    }
}

@Composable
private fun rememberDotPin(emoji: String, color: Color, dark: Boolean): BitmapDescriptor {
    val argb = color.toArgb()
    return remember(emoji, argb, dark) {
        pinCache.getOrPut("d|$emoji|$argb|$dark") { createDotPin(emoji, argb, dark) }
    }
}

/** Small category dot (no amount) — the calm default pin. */
private fun createDotPin(emoji: String, colorArgb: Int, dark: Boolean): BitmapDescriptor {
    val d = 3f
    val size = (30f * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val r = size / 2f
    c.drawCircle(r, r, r, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) 0xFF0C1411.toInt() else android.graphics.Color.WHITE
    })
    c.drawCircle(r, r, r - 2.5f * d, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb })
    val ep = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * d
        textAlign = android.graphics.Paint.Align.CENTER
        color = android.graphics.Color.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    c.drawText(emoji, r, r - (ep.descent() + ep.ascent()) / 2f, ep)
    return runCatching { BitmapDescriptorFactory.fromBitmap(bmp) }.getOrElse { BitmapDescriptorFactory.defaultMarker() }
}

@Composable
private fun rememberClusterIcon(count: Int, accent: Color): BitmapDescriptor {
    val argb = accent.toArgb()
    return remember(count, argb) {
        pinCache.getOrPut("c|$count|$argb") { createClusterIcon(count, argb) }
    }
}

private fun createExpensePin(emoji: String, text: String, colorArgb: Int, selected: Boolean, dark: Boolean): BitmapDescriptor {
    val d = 3f
    val bodyH = 30f * d
    val tailH = 7f * d
    val circle = 24f * d
    val pad = 3f * d
    val stroke = 1.5f * d

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * d
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        color = when {
            selected -> 0xFF06130D.toInt()
            dark -> android.graphics.Color.WHITE
            else -> 0xFF14201B.toInt()
        }
    }
    val textW = textPaint.measureText(text)
    val width = pad + circle + 6f * d + textW + 10f * d
    val bmp = android.graphics.Bitmap.createBitmap(
        (width + stroke * 2).toInt(), (bodyH + tailH + stroke * 2).toInt(), android.graphics.Bitmap.Config.ARGB_8888
    )
    val c = android.graphics.Canvas(bmp)
    val left = stroke
    val top = stroke
    val rect = android.graphics.RectF(left, top, left + width, top + bodyH)

    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = when {
            selected -> colorArgb
            dark -> 0xEB0E1613.toInt()
            else -> android.graphics.Color.WHITE
        }
    }
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = stroke
        color = colorArgb
    }
    // Tail under the pill (points at the spot).
    val cx = left + width / 2f
    val tail = android.graphics.Path().apply {
        moveTo(cx - 6f * d, top + bodyH - 1f)
        lineTo(cx + 6f * d, top + bodyH - 1f)
        lineTo(cx, top + bodyH + tailH)
        close()
    }
    c.drawPath(tail, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb })
    c.drawRoundRect(rect, bodyH / 2f, bodyH / 2f, bodyPaint)
    c.drawRoundRect(rect, bodyH / 2f, bodyH / 2f, borderPaint)

    // Icon circle + emoji.
    val ccx = left + pad + circle / 2f
    val ccy = top + bodyH / 2f
    c.drawCircle(ccx, ccy, circle / 2f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = if (selected) 0xFF06130D.toInt() else colorArgb
    })
    val emojiPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * d
        textAlign = android.graphics.Paint.Align.CENTER
        color = android.graphics.Color.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    c.drawText(emoji, ccx, ccy - (emojiPaint.descent() + emojiPaint.ascent()) / 2f, emojiPaint)

    // Amount.
    val tx = left + pad + circle + 6f * d
    c.drawText(text, tx, ccy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)

    return runCatching { BitmapDescriptorFactory.fromBitmap(bmp) }.getOrElse { BitmapDescriptorFactory.defaultMarker() }
}

private fun createClusterIcon(count: Int, accentArgb: Int): BitmapDescriptor {
    val d = 3f
    val size = (44f * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val r = size / 2f
    val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = (accentArgb and 0x00FFFFFF) or (0x40 shl 24)
    }
    c.drawCircle(r, r, r, glow)
    c.drawCircle(r, r, 17f * d, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xB306130D.toInt() })
    c.drawCircle(r, r, 15f * d, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = accentArgb })
    val tp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (if (count > 99) 11f else 13f) * d
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
        color = 0xFF06130D.toInt()
    }
    c.drawText(if (count > 999) "999+" else count.toString(), r, r - (tp.descent() + tp.ascent()) / 2f, tp)
    return runCatching { BitmapDescriptorFactory.fromBitmap(bmp) }.getOrElse { BitmapDescriptorFactory.defaultMarker() }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Spending grouped into ~300 m cells: (centre, total). */
private fun heatSpots(expenses: List<Expense>): List<Pair<LatLng, Double>> {
    val cell = 0.003
    return expenses
        .groupBy { floor(it.latitude!! / cell).toInt() to floor(it.longitude!! / cell).toInt() }
        .values
        .map { g -> LatLng(g.map { it.latitude!! }.average(), g.map { it.longitude!! }.average()) to g.sumOf { it.amount } }
}

private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun startOfMonth(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ms)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun formatDistance(km: Double): String = if (km < 1.0) {
    "${(km * 1000).toInt()} m"
} else {
    "${String.format(Locale.US, "%.1f", km)} km"
}

// Splitlee dark: deep green land, quiet roads, no business/transit clutter.
private const val DARK_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#0e1613"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#0e1613"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#7f948b"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#2a3833"}]},
  {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
  {"featureType":"poi.business","stylers":[{"visibility":"off"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"#121d19"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#10231a"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#1b2723"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#0e1613"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#6f857c"}]},
  {"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#24352f"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b1c23"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#4f6f7c"}]}
]
"""

// Light: soft grey land, white roads, same decluttering.
private const val LIGHT_MAP_STYLE_JSON = """
[
  {"elementType":"geometry","stylers":[{"color":"#f3f5f4"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#6b7772"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#f3f5f4"}]},
  {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
  {"featureType":"poi.business","stylers":[{"visibility":"off"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#e2f0e6"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#ffffff"}]},
  {"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#e3e7e5"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#d7e9f2"}]}
]
"""

private data class MapCluster(
    val center: LatLng,
    val expenses: List<Expense>
)

/**
 * Grid clustering: cells sized by zoom; each cell becomes one cluster at the
 * average position. Good enough to keep a zoomed-out map readable without a
 * new clustering dependency.
 */
private fun clusterExpenses(expenses: List<Expense>, zoom: Float): List<MapCluster> {
    val locatable = expenses.filter { it.latitude != null && it.longitude != null }
    if (locatable.isEmpty()) return emptyList()
    val cellSizeDegrees = (60.0 / 2.0.pow(zoom.toDouble())).coerceIn(0.0008, 20.0)
    return locatable
        .groupBy { e ->
            floor(e.latitude!! / cellSizeDegrees).toInt() to floor(e.longitude!! / cellSizeDegrees).toInt()
        }
        .values
        .map { group ->
            MapCluster(
                center = LatLng(group.map { it.latitude!! }.average(), group.map { it.longitude!! }.average()),
                expenses = group
            )
        }
}
