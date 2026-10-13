@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.splitwiseauto

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.room.withTransaction
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    friendViewModel: FriendViewModel = viewModel(),
    mapViewModel: MapViewModel = viewModel(),
    insightsViewModel: InsightsViewModel = viewModel(),
    pendingNotificationsViewModel: PendingNotificationsViewModel = viewModel(),
    // Widget deep link (e.g. "Owed to you" -> Friends). Shown once, then cleared.
    requestedTab: Int? = null,
    onRequestedTabShown: () -> Unit = {}
) {
    val expenses by homeViewModel.expenses.collectAsState()
    val total by homeViewModel.totalSpent.collectAsState()
    val heroTotal by homeViewModel.heroTotal.collectAsState()
    val mySpent by homeViewModel.mySpent.collectAsState()
    val friends by friendViewModel.friends.collectAsState()
    val friendBalances by friendViewModel.balances.collectAsState()
    val expenseSettlement by friendViewModel.expenseSettlement.collectAsState()
    val totalOwedToYou by friendViewModel.totalOwedToYou.collectAsState()
    val categories by homeViewModel.categorySummaries.collectAsState()
    val todayCategories by homeViewModel.todayCategories.collectAsState()
    var todayDrill by remember { mutableStateOf<CategorySummary?>(null) }
    todayDrill?.let { summary ->
        val data = remember(summary, todayCategories) { homeViewModel.todayDrill(summary) }
        CategoryDetailSheet(
            data = data,
            onDismiss = { todayDrill = null },
            onChanged = {
                homeViewModel.refresh()
                insightsViewModel.refresh()
            }
        )
    }
    val accountSpending by homeViewModel.accountSpending.collectAsState()
    val mapExpenses by mapViewModel.expensesWithLocation.collectAsState()
    val mapLoaded by mapViewModel.loaded.collectAsState()
    val pendingNotifications by pendingNotificationsViewModel.pending.collectAsState()
    val recentPayments by pendingNotificationsViewModel.payments.collectAsState()
    val settlementHistory by friendViewModel.settlementHistory.collectAsState()
    var showAddFriendDialog by remember {
        mutableStateOf(false)
    }
    var showPendingNotifications by remember {
        mutableStateOf(false)
    }
    var expenseForActions by remember { mutableStateOf<ExpenseWithParticipants?>(null) }
    var friendForActions by remember { mutableStateOf<Friend?>(null) }
    var friendToSettle by remember { mutableStateOf<Friend?>(null) }
    var friendToRename by remember { mutableStateOf<Friend?>(null) }
    var friendForHistory by remember { mutableStateOf<Friend?>(null) }
    var settlementBeingCorrected by remember { mutableStateOf<Settlement?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    // Home shows one month (default: this month) or all time — picked from
    // the month chip in the header and remembered between app opens.
    val periodContext = LocalContext.current
    var dateFilter by remember { mutableStateOf(HomePeriodStore.load(periodContext)) }
    val homeMonths by insightsViewModel.availableMonths.collectAsState()
    var showMonthMenu by remember { mutableStateOf(false) }
    var participantFilter by remember { mutableStateOf(ParticipantFilter.ALL) }
    // Only meaningful when participantFilter == ANY_FRIEND: empty means "any
    // friend at all", non-empty narrows to just these friends (e.g. Akhil,
    // or Akhil + Abin together).
    var selectedFriendNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedFilters by remember { mutableStateOf<List<SavedFilter>>(emptyList()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Main / Trip mode. null activeTripId == Main (everything, unfiltered,
    // exactly as before). Picking a trip narrows Home/Insights to just that
    // trip's expenses; the underlying data is never moved or duplicated.
    var trips by remember { mutableStateOf<List<Trip>>(emptyList()) }
    var activeTripId by remember { mutableStateOf<Int?>(ActiveTripStore.getActiveTripId(context)) }
    var activeTripName by remember { mutableStateOf<String?>(ActiveTripStore.getActiveTripName(context)) }
    var showTripPicker by remember { mutableStateOf(false) }
    var tripToDelete by remember { mutableStateOf<Trip?>(null) }
    var tripToDeleteCount by remember { mutableStateOf(0) }
    var showNewTripDialog by remember { mutableStateOf(false) }
    var newTripName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val database = AppDatabase.getDatabase(context)
        friendViewModel.initialize(
            friendDao = database.friendDao(),
            expenseDao = database.expenseDao(),
            settlementDao = database.settlementDao(),
            expenseParticipantDao = database.expenseParticipantDao()
        )
        trips = database.tripDao().getAll()
        savedFilters = database.savedFilterDao().getAll()
    }
    LaunchedEffect(expenses) {
        friendViewModel.refresh()
        mapViewModel.loadExpenses()
        insightsViewModel.loadExpenses(activeTripId)
        pendingNotificationsViewModel.refresh()
    }
    // The Home-only filter (date range + who it was split with) narrows the
    // same trip-scoped data Home already shows — Insights, Map and Friends
    // are unaffected by it.
    LaunchedEffect(activeTripId, dateFilter, participantFilter, selectedFriendNames) {
        homeViewModel.loadExpenses(activeTripId, dateFilter, participantFilter, selectedFriendNames)
        insightsViewModel.loadExpenses(activeTripId)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.loadExpenses(activeTripId, dateFilter, participantFilter, selectedFriendNames)
                friendViewModel.refresh()
                mapViewModel.loadExpenses()
                insightsViewModel.loadExpenses(activeTripId)
                pendingNotificationsViewModel.refresh()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Backed by a PagerState so tab switches — whether tapped from the bottom
    // bar or swiped — animate through HorizontalPager's own fling physics
    // instead of snapping instantly. selectedTab is kept as the read-only
    // mirror of pagerState.currentPage so every existing "selectedTab == N"
    // check below still works unchanged.
    val pagerState = rememberPagerState(initialPage = 0)
    val selectedTab = pagerState.currentPage
    val pagerScope = rememberCoroutineScope()
    fun goToTab(index: Int) {
        pagerScope.launch { pagerState.animateScrollToPage(index) }
    }
    LaunchedEffect(requestedTab) {
        val tab = requestedTab ?: return@LaunchedEffect
        pagerState.scrollToPage(tab.coerceIn(0, 3))
        onRequestedTabShown()
    }
    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = {
                showAddFriendDialog = false
            },
            onAddFriend = { name ->
                friendViewModel.addFriend(name)
                showAddFriendDialog = false
            }
        )
    }
    expenseForActions?.let { item ->
        ExpenseActionsSheet(
            item = item,
            onView = {
                context.startActivity(
                    Intent(context, ExpenseDetailActivity::class.java).putExtra("expenseId", item.expense.id)
                )
                expenseForActions = null
            },
            onEdit = {
                context.startActivity(
                    Intent(context, ManualExpenseActivity::class.java).apply {
                        putExtra("expenseId", item.expense.id)
                        putExtra("amount", item.expense.amount.toString())
                        putExtra("merchant", item.expense.merchant)
                        putExtra("date", item.expense.date)
                        putExtra("category", item.expense.category)
                        putExtra("payer", item.expense.payer)
                        putExtra("bank", item.expense.bank)
                        item.expense.accountIdentifier?.let { putExtra("accountIdentifier", it) }
                        item.expense.timestampMillis?.let { putExtra("timestampMillis", it) }
                    }
                )
                expenseForActions = null
            },
            onDelete = {
                homeViewModel.deleteExpense(item.expense.id)
                expenseForActions = null
            },
            onDismiss = { expenseForActions = null }
        )
    }
    friendForActions?.let { friend ->
        FriendActionsSheet(
            friend = friend,
            balance = friendBalances[friend.id] ?: FriendBalance(),
            onSettle = {
                friendToSettle = friend
                friendForActions = null
            },
            onHistory = {
                friendViewModel.loadSettlementHistory(friend.id)
                friendForHistory = friend
                friendForActions = null
            },
            onRename = {
                friendToRename = friend
                friendForActions = null
            },
            onRemove = {
                friendViewModel.removeFriend(friend)
                friendForActions = null
            },
            onDismiss = { friendForActions = null }
        )
    }
    friendToSettle?.let { friend ->
        SettleUpSheet(
            friend = friend,
            balance = friendBalances[friend.id] ?: FriendBalance(),
            onSettle = { settleAmount, onSaved -> friendViewModel.settleUp(friend, settleAmount, onSaved) },
            onUndo = { id -> friendViewModel.undoSettlement(id) },
            onDismiss = { friendToSettle = null }
        )
    }
    friendToRename?.let { friend ->
        RenameFriendDialog(
            currentName = friend.name,
            onDismiss = { friendToRename = null },
            onRename = { newName ->
                friendViewModel.renameFriend(friend, newName)
                friendToRename = null
            }
        )
    }
    friendForHistory?.let { friend ->
        SettlementHistoryDialog(
            friend = friend,
            settlements = settlementHistory,
            onDismiss = {
                friendForHistory = null
                friendViewModel.clearSettlementHistory()
            },
            onCorrect = { settlement -> settlementBeingCorrected = settlement },
            onDelete = { settlement -> friendViewModel.deleteSettlement(settlement) }
        )
    }
    settlementBeingCorrected?.let { settlement ->
        CorrectSettlementDialog(
            settlement = settlement,
            onDismiss = { settlementBeingCorrected = null },
            onSave = { newAmount ->
                friendViewModel.correctSettlement(settlement, newAmount)
                settlementBeingCorrected = null
            }
        )
    }
    if (showPendingNotifications) {
        PendingNotificationsDialog(
            pending = pendingNotifications,
            onDismiss = { showPendingNotifications = false },
            onDismissItem = { id -> pendingNotificationsViewModel.dismiss(id) },
            onJustMe = { id -> pendingNotificationsViewModel.justMe(id) },
            payments = recentPayments,
            onDismissPayment = { id -> pendingNotificationsViewModel.dismissPayment(id) }
        )
    }
    tripToDelete?.let { trip ->
        DeleteTripDialog(
            trip = trip,
            expenseCount = tripToDeleteCount,
            onDismiss = { tripToDelete = null },
            onDelete = { alsoDeleteExpenses ->
                tripToDelete = null
                coroutineScope.launch {
                    val database = AppDatabase.getDatabase(context)
                    database.withTransaction {
                        if (alsoDeleteExpenses) {
                            database.expenseDao().deleteTripExpensesWithParticipants(trip.id)
                        } else {
                            database.expenseDao().detachFromTrip(trip.id)
                        }
                        database.tripDao().deleteById(trip.id)
                    }
                    trips = database.tripDao().getAll()
                    // Deleting the trip you're in drops you back to Main.
                    if (activeTripId == trip.id) {
                        activeTripId = null
                        activeTripName = null
                        ActiveTripStore.setActiveTrip(context, null, null)
                    }
                    homeViewModel.loadExpenses(activeTripId, dateFilter, participantFilter, selectedFriendNames)
                    insightsViewModel.loadExpenses(activeTripId)
                }
            }
        )
    }
    if (showFilterDialog) {
        FilterDialog(
            dateFilter = dateFilter,
            participantFilter = participantFilter,
            selectedFriendNames = selectedFriendNames,
            friends = friends,
            savedFilters = savedFilters,
            onApply = { newDateFilter, newParticipantFilter, newSelectedFriendNames ->
                dateFilter = newDateFilter
                participantFilter = newParticipantFilter
                selectedFriendNames = newSelectedFriendNames
            },
            onSaveCustomFilter = { name, start, end ->
                coroutineScope.launch {
                    val database = AppDatabase.getDatabase(context)
                    database.savedFilterDao().insert(SavedFilter(name = name, startMillis = start, endMillis = end))
                    savedFilters = database.savedFilterDao().getAll()
                }
            },
            onDeleteSavedFilter = { saved ->
                coroutineScope.launch {
                    val database = AppDatabase.getDatabase(context)
                    database.savedFilterDao().deleteById(saved.id)
                    savedFilters = database.savedFilterDao().getAll()
                }
            },
            onDismiss = { showFilterDialog = false }
        )
    }
    if (showNewTripDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewTripDialog = false
                newTripName = ""
            },
            title = { Text("Start a new trip") },
            text = {
                ModernTextField(
                    value = newTripName,
                    onValueChange = { newTripName = it },
                    label = "Trip name",
                    placeholder = "e.g. Goa",
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newTripName.isNotBlank(),
                    onClick = {
                        val name = newTripName.trim()
                        coroutineScope.launch {
                            val database = AppDatabase.getDatabase(context)
                            val newId = database.tripDao().insert(Trip(name = name)).toInt()
                            trips = database.tripDao().getAll()
                            activeTripId = newId
                            activeTripName = name
                            ActiveTripStore.setActiveTrip(context, newId, name)
                        }
                        newTripName = ""
                        showNewTripDialog = false
                    }
                ) { Text("Start trip") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewTripDialog = false
                    newTripName = ""
                }) { Text("Cancel") }
            }
        )
    }
    Scaffold(
        bottomBar = {
            // Floating glass pill; the selection capsule follows the pager.
            FloatingTabBar(
                tabs = listOf(
                    TabSpec("Home", Icons.Filled.Home),
                    TabSpec("Friends", Icons.Filled.Group),
                    TabSpec("Map", Icons.Filled.Place),
                    TabSpec("Insights", Icons.Filled.BarChart)
                ),
                pagerState = pagerState,
                onSelect = { goToTab(it) }
            )
        },

        floatingActionButton = {

            if (selectedTab == 1) {
                ModernFab(
                    icon = Icons.Filled.Add,
                    contentDescription = "Add Friend",
                    onClick = { showAddFriendDialog = true }
                )
            } else if (selectedTab == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ModernFab(
                        icon = Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan & Pay",
                        primary = false,
                        onClick = {
                            context.startActivity(Intent(context, QrScanActivity::class.java))
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ModernFab(
                        icon = Icons.Filled.Add,
                        contentDescription = "Add Expense",
                        onClick = {
                            context.startActivity(Intent(context, ManualExpenseActivity::class.java))
                        }
                    )
                }
            }
        }
    ) { paddingValues ->

        // Mode chip (Main/Trip switcher) + bell live in a real header row now,
        // not a floating overlay — that overlay used to sit on top of
        // whichever tab was showing (including Home's own hero card), which
        // is what caused it to visually collide with the hero card and, at
        // some sizes, with itself. Giving it its own row here means it
        // occupies real, reserved space above the pager, so nothing below it
        // can ever be drawn underneath it again.
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Only the TOP inset from Scaffold's paddingValues
                    // belongs here (status bar / cutout clearance). The
                    // full PaddingValues also carries the bottomBar's
                    // reserved height in its bottom side — applying that
                    // to this small header row (via .padding(paddingValues))
                    // was adding a large, unwanted gap below the row,
                    // before the hero card even though this row has
                    // nothing to do with the bottom nav bar.
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val chipView = androidx.compose.ui.platform.LocalView.current
                // Period chip (left); trip chip + bell stay on the right.: "Oct ▾" / "All time ▾". All time is pinned on
                // top of the menu, then months newest first.
                Box {
                    Surface(
                        onClick = { Haptics.tap(chipView); showMonthMenu = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dateFilter.month?.let { periodChipLabel(it) }
                                    ?: if (dateFilter.isActive) "Custom" else "All time",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Choose period",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    GlassMenu(
                        expanded = showMonthMenu,
                        onDismissRequest = { showMonthMenu = false }
                    ) {
                        GlassMenuItem(
                            label = "All time",
                            selected = dateFilter.type == DateFilterType.ALL,
                            accent = HomeTokens.accent(),
                            onClick = {
                                dateFilter = DateFilter()
                                HomePeriodStore.save(context, null)
                                showMonthMenu = false
                            }
                        )
                        GlassMenuDivider()
                        GlassMenuHeader("Months")
                        homeMonths.forEach { m ->
                            GlassMenuItem(
                                label = m.displayLabel(),
                                caption = if (m == MonthKey.current()) "This month" else null,
                                selected = dateFilter.month == m,
                                accent = HomeTokens.accent(),
                                onClick = {
                                    dateFilter = DateFilter.forMonth(m)
                                    HomePeriodStore.save(context, m)
                                    showMonthMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                // Trip / mode chip: same glass dropdown as the month chip.
                Box {
                    Surface(
                        onClick = { Haptics.tap(chipView); showTripPicker = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeTripName == null) Icons.Filled.Home else Icons.Filled.Place,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeTripName ?: "Main",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Switch mode",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    GlassMenu(
                        expanded = showTripPicker,
                        onDismissRequest = { showTripPicker = false }
                    ) {
                        GlassMenuItem(
                            label = "Main",
                            selected = activeTripId == null,
                            accent = HomeTokens.accent(),
                            leadingIcon = Icons.Filled.Home,
                            onClick = {
                                activeTripId = null
                                activeTripName = null
                                ActiveTripStore.setActiveTrip(context, null, null)
                                showTripPicker = false
                            }
                        )
                        if (trips.isNotEmpty()) {
                            GlassMenuDivider()
                            GlassMenuHeader("Trips")
                        }
                        trips.forEach { trip ->
                            GlassMenuItem(
                                label = trip.name,
                                selected = activeTripId == trip.id,
                                accent = HomeTokens.accent(),
                                leadingIcon = Icons.Filled.Place,
                                onClick = {
                                    activeTripId = trip.id
                                    activeTripName = trip.name
                                    ActiveTripStore.setActiveTrip(context, trip.id, trip.name)
                                    showTripPicker = false
                                },
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                tripToDeleteCount = AppDatabase.getDatabase(context)
                                                    .expenseDao().countForTrip(trip.id)
                                                tripToDelete = trip
                                                showTripPicker = false
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete ${trip.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            )
                        }
                        GlassMenuDivider()
                        GlassMenuAction(
                            label = "New trip",
                            accent = HomeTokens.accent(),
                            icon = Icons.Filled.Add,
                            onClick = {
                                showTripPicker = false
                                showNewTripDialog = true
                            }
                        )
                    }
                }

                // The badge wraps the whole round button (not the icon inside
                // it): inside, the circle's clip cut the badge off.
                val attentionCount = pendingNotifications.size + recentPayments.size
                BadgedBox(
                    badge = {
                        // Red dot only — the list itself shows what's waiting.
                        if (attentionCount > 0) {
                            NotificationBadge(count = attentionCount)
                        }
                    }
                ) {
                    Surface(
                        onClick = {
                            // Refresh first so Scan & Pay statuses are current.
                            pendingNotificationsViewModel.refresh()
                            showPendingNotifications = true
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier.padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = if (attentionCount > 0) "Missed expenses, $attentionCount" else "Missed expenses"
                            )
                        }
                    }
                }
            }

            // Swipe left/right between Home -> Friends -> Map -> Insights (and back).
            // HorizontalPager tracks the finger and settles with its own fling
            // physics, so the page follows the drag smoothly instead of snapping
            // once a distance threshold is crossed. userScrollEnabled is turned
            // off exactly on the Map tab so panning the map never gets mistaken
            // for a tab-switch swipe (the map needs the whole gesture to itself);
            // it's still reachable by swiping in from Friends or Insights, or by
            // tapping its bottom-bar icon.
            // Every tab gets the SAME spacing: the header row above already
            // reserves the status-bar inset, so pages only need clearance
            // for the bottom nav bar. (Pages used to get the full
            // paddingValues, which added the status-bar gap a second time
            // below the bell row.) Each tab then starts its content
            // TAB_CONTENT_TOP_GAP below the header, so all four line up.
            val tabPageModifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())

            val barInset = paddingValues.calculateBottomPadding()
            CompositionLocalProvider(LocalBottomBarInset provides barInset) {
            HorizontalPager(
                pageCount = 4,
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = pagerState.currentPage != 2
            ) { page ->
                when (page) {

                    0 -> {
                        HomeTabContent(
                            expenses = expenses,
                            settlement = expenseSettlement,
                            total = total,
                            heroTotal = heroTotal,
                            categories = categories,
                            todayCategories = todayCategories,
                            onTodayCategoryClick = { todayDrill = it },
                            accountSpending = accountSpending,
                            // Derived here, not in the ViewModel — FriendViewModel already
                            // exposes totalOwedToYou; "you owe" is the same aggregation
                            // (negative netAmount side) computed from the same balances
                            // map HomeScreen already collects, so no new state/DAO query.
                            owedToYou = totalOwedToYou,
                            youOwe = friendBalances.values.sumOf { (-it.netAmount).coerceAtLeast(0.0) },
                            onSeeFriends = { goToTab(1) },
                            dateFilter = dateFilter,
                            participantFilter = participantFilter,
                            selectedFriendNames = selectedFriendNames,
                            onExpenseTap = { item ->
                                context.startActivity(
                                    Intent(context, ExpenseDetailActivity::class.java).apply {
                                        putExtra("expenseId", item.expense.id)
                                    }
                                )
                            },
                            onExpenseLongPress = { expenseForActions = it },
                            onFilterClick = { showFilterDialog = true },
                            onClearFilter = {
                                dateFilter = HomePeriodStore.load(context)
                                participantFilter = ParticipantFilter.ALL
                                selectedFriendNames = emptySet()
                            }
                        )
                    }

                    1 -> {
                        FriendsTabContent(
                            friends = friends,
                            balances = friendBalances,
                            mySpent = mySpent,
                            totalOwedToYou = totalOwedToYou,
                            onFriendLongPress = { friendForActions = it },
                            // Double-tap is the fast path straight to settling up;
                            // long-press keeps the full menu (settle/history/rename/remove).
                            onFriendDoubleTap = { f ->
                                if (kotlin.math.abs(friendBalances[f.id]?.netAmount ?: 0.0) > 0.01) friendToSettle = f
                                else friendForActions = f
                            }
                        )
                    }

                    2 -> {
                        ExpenseMapScreen(
                            expenses = mapExpenses,
                            active = pagerState.currentPage == 2,
                            loaded = mapLoaded,
                            modifier = tabPageModifier
                        )
                    }

                    3 -> {
                        InsightsScreen(
                            viewModel = insightsViewModel,
                            onDataChanged = { homeViewModel.refresh() }
                        )
                    }
                }
            }
            }
        }
    }
}


@Composable
private fun HomeTabContent(
    expenses: List<ExpenseWithParticipants>,
    settlement: Map<Int, SettleStatus.ExpenseSettlement> = emptyMap(),
    total: Double,
    heroTotal: Pair<String, Double>?,
    categories: List<CategorySummary>,
    todayCategories: List<CategorySummary> = emptyList(),
    onTodayCategoryClick: (CategorySummary) -> Unit = {},
    accountSpending: List<AccountSpendingSummary>,
    owedToYou: Double,
    youOwe: Double,
    onSeeFriends: () -> Unit,
    dateFilter: DateFilter,
    participantFilter: ParticipantFilter,
    selectedFriendNames: Set<String> = emptySet(),
    onExpenseTap: (ExpenseWithParticipants) -> Unit,
    onExpenseLongPress: (ExpenseWithParticipants) -> Unit,
    onFilterClick: () -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filterActive = dateFilter.isActive || participantFilter.isActive
    // Rows fade/slide in one after another only when the list first shows
    // up; rows scrolled into view later just appear.
    var entrancePlayed by remember { mutableStateOf(false) }
    LaunchedEffect(expenses.isNotEmpty()) {
        if (expenses.isNotEmpty() && !entrancePlayed) {
            kotlinx.coroutines.delay(800)
            entrancePlayed = true
        }
    }
    // "Any friend" narrowed to specific names reads better as those names
    // ("Akhil + Abin") than the generic "Any friend" label.
    val participantSummary = if (participantFilter == ParticipantFilter.ANY_FRIEND && selectedFriendNames.isNotEmpty()) {
        selectedFriendNames.sorted().joinToString(" + ")
    } else {
        participantFilter.summary()
    }
    val filterLabel = listOf(dateFilter.summary(), participantSummary)
        .filter { it != "All time" && it != "Everyone" }
        .joinToString(" · ")

    // Recent-expenses search + paging. Search matches merchant, bank,
    // category, UPI/ref, date text, account, who it was split with, and the
    // amount. Results are newest-first by actual transaction time, grouped
    // under month bars, and shown RECENT_PAGE_SIZE at a time. All derived
    // lists are remembered, so scrolling never recomputes them.
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    var sort by rememberSaveable { mutableStateOf(AmountSort.NEWEST) }
    val searchedExpenses = remember(expenses, searchQuery, sort) {
        val q = searchQuery.trim()
        val matched = if (q.isEmpty()) expenses else expenses.filter { it.matchesSearch(q) }
        when (sort) {
            // Stable sort: expenses with no timestamp keep their original
            // newest-added-first order and sink below the dated ones.
            AmountSort.NEWEST -> matched.sortedByDescending { it.expense.timestampMillis ?: Long.MIN_VALUE }
            AmountSort.HIGH_TO_LOW -> matched.sortedByDescending { it.expense.amount }
            AmountSort.LOW_TO_HIGH -> matched.sortedBy { it.expense.amount }
        }
    }
    val monthTotals = remember(searchedExpenses) {
        searchedExpenses
            .groupBy { recentMonthKeyOf(it) }
            .mapValues { (_, items) -> items.sumOf { it.expense.amount } }
    }
    // Back to the first page whenever the search text changes. Not keyed on
    // the list itself, so deleting a row or resuming the app doesn't
    // collapse what you'd already expanded.
    var visibleCount by remember(searchQuery, sort) { mutableStateOf(RECENT_PAGE_SIZE) }
    val visibleEntries = remember(searchedExpenses, visibleCount, monthTotals, sort) {
        buildRecentEntries(
            searchedExpenses.take(visibleCount),
            monthTotals,
            showMonthBars = sort == AmountSort.NEWEST
        )
    }

    // Entrance rise/fade animation removed. Profiling a real slow frame
    // (Android Studio Profiler, System Trace) showed this staggered
    // animation — hero -> balance cards -> Accounts -> category tiles ->
    // recent expenses — was the actual dominant cost on the main thread
    // right after Home first composes: it was recomposing every one of
    // those elements, including every visible expense row, on every
    // animation frame for up to ~900ms (5 staggered 600ms tweens). It was
    // never actually visible to the user (masked by the very jank it was
    // causing), so it was pure wasted work with zero payoff. Every element
    // below now renders at its final resting position/opacity immediately.
    // entranceRiseFade() and these progress values are left in place below
    // (always at 1f = fully visible, no offset) purely so the call sites
    // that reference them don't all need touching — they're permanent
    // no-ops now, not animations.
    val playEntrance = false
    val heroRiseProgress = remember { Animatable(1f) }
    val balanceRiseProgress = remember { Animatable(1f) }
    val accountsRiseProgress = remember { Animatable(1f) }
    val categoriesRiseProgress = remember { Animatable(1f) }
    val expensesRiseProgress = remember { Animatable(1f) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),

        contentPadding = PaddingValues(
            top = TAB_CONTENT_TOP_GAP,
            // Extra room at the bottom so the last card never sits under the
            // stacked Scan & Pay / Add Expense FABs on this tab.
            bottom = 104.dp + LocalBottomBarInset.current
        )
    ) {

        // Hero total card — same underlying number as before (scoped to
        // whatever filter is active below), just given a visual anchor
        // instead of a bare label + number.
        item {
            HomeHeroCard(
                label = when {
                    filterActive -> "TOTAL (${filterLabel.uppercase()})"
                    dateFilter.month == MonthKey.current() -> "THIS MONTH · ${dateFilter.month.shortLabel().uppercase()}"
                    dateFilter.month != null -> "${dateFilter.month.displayLabel().uppercase()} EXPENSES"
                    else -> "ALL-TIME EXPENSES"
                },
                amount = heroTotal?.second ?: total,
                counterKey = heroTotal?.first,
                animateEntrance = playEntrance,
                modifier = Modifier.entranceRiseFade { heroRiseProgress.value }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Owed-to-you / you-owe strip — both numbers already exist in
        // FriendViewModel's balances; this just surfaces them on Home so
        // there's no need to switch tabs to see where you stand. Tapping
        // either chip jumps straight to the Friends tab.
        if (owedToYou > 0.01 || youOwe > 0.01) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .entranceRiseFade { balanceRiseProgress.value },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BalanceChip(
                        modifier = Modifier.weight(1f),
                        label = "Owed to you",
                        amount = owedToYou,
                        icon = Icons.Filled.ArrowDownward,
                        color = HomeTokens.accent(),
                        onClick = onSeeFriends
                    )
                    BalanceChip(
                        modifier = Modifier.weight(1f),
                        label = "You owe",
                        amount = youOwe,
                        icon = Icons.Filled.ArrowUpward,
                        color = HomeTokens.warn(),
                        onClick = onSeeFriends
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (accountSpending.isNotEmpty()) {
            item {
                Column(modifier = Modifier.entranceRiseFade { accountsRiseProgress.value }) {
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.consumeHorizontalOverscroll()
                    ) {
                        items(
                            items = accountSpending,
                            key = { "${it.bank}|${it.accountIdentifier}" }
                        ) { summary ->
                            AccountCarouselCard(
                                summary = summary,
                                shareOfTotal = if (total > 0.0) (summary.total / total).toFloat().coerceIn(0f, 1f) else 0f
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }


        // Category treemap — already a tinted, icon-labeled mosaic (see
        // TreemapView.kt); left as-is, just given a heading to match the
        // rest of the redesigned sections.
        if (categories.isNotEmpty() || todayCategories.isNotEmpty()) {

            item {
                Column(modifier = Modifier.entranceRiseFade { categoriesRiseProgress.value }) {
                    // Today only: the month-by-category view lives on Insights.
                    CategoryDonutCard(
                        items = todayCategories,
                        periodLabel = "Today",
                        title = "Today by category",
                        emptyText = "Nothing spent yet today",
                        onItemClick = onTodayCategoryClick
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }


        // Recent expenses heading + search + filter
        item(key = "recent_header") {
            Column(modifier = Modifier.entranceRiseFade { expensesRiseProgress.value }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Expenses",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (searchOpen) searchQuery = ""
                            searchOpen = !searchOpen
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search expenses",
                                tint = if (searchOpen || searchQuery.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        // Sort: one icon, tap to cycle. Grey ⇅ = newest
                        // first; ↓ = amount high→low; ↑ = amount low→high.
                        IconButton(onClick = { sort = sort.next() }) {
                            Icon(
                                imageVector = when (sort) {
                                    AmountSort.NEWEST -> Icons.Filled.SwapVert
                                    AmountSort.HIGH_TO_LOW -> Icons.Filled.ArrowDownward
                                    AmountSort.LOW_TO_HIGH -> Icons.Filled.ArrowUpward
                                },
                                contentDescription = when (sort) {
                                    AmountSort.NEWEST -> "Sort: newest first"
                                    AmountSort.HIGH_TO_LOW -> "Sort: amount high to low"
                                    AmountSort.LOW_TO_HIGH -> "Sort: amount low to high"
                                },
                                tint = if (sort != AmountSort.NEWEST) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(onClick = onFilterClick) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "Filter expenses",
                                tint = if (filterActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                if (searchOpen) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Compact 40dp pill instead of the 56dp OutlinedTextField,
                    // styled like the expense rows below it.
                    val focusManager = LocalFocusManager.current
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(50),
                        color = HomeTokens.card2(),
                        border = BorderStroke(1.dp, HomeTokens.line())
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = HomeTokens.mut(),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search merchant, amount, friend…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HomeTokens.mut(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(HomeTokens.accent()),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(searchFocus)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close search",
                                tint = HomeTokens.mut(),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable {
                                        searchQuery = ""
                                        searchOpen = false
                                    }
                            )
                        }
                    }
                    // Focus the field (opening the keyboard) as soon as it
                    // appears. runCatching: if the item gets scrolled away
                    // before focus lands, skipping it is fine.
                    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
                    if (searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchedExpenses.size == 1) "1 match" else "${searchedExpenses.size} matches",
                            style = MaterialTheme.typography.labelMedium,
                            color = HomeTokens.mut()
                        )
                    }
                }

                if (filterActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onFilterClick,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = filterLabel,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onClearFilter) { Text("Clear") }
                    }
                }

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }
        }


        // Empty state
        if (expenses.isEmpty()) {

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .entranceRiseFade { expensesRiseProgress.value },

                    contentAlignment = Alignment.Center
                ) {
                    Text("No expenses yet")
                }
            }

        } else if (searchedExpenses.isEmpty()) {

            item(key = "recent_no_matches") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matches for \"${searchQuery.trim()}\"",
                        color = HomeTokens.mut()
                    )
                }
            }

        } else {

            // Newest-first, grouped under GPay-style month bars (month name +
            // that month's total), showing RECENT_PAGE_SIZE rows at a time.
            itemsIndexed(
                items = visibleEntries,
                key = { _, entry ->
                    when (entry) {
                        is RecentEntry.MonthBar ->
                            entry.month?.let { "bar_${it.year}_${it.month}" } ?: "bar_undated"
                        is RecentEntry.Item -> entry.item.expense.id
                    }
                },
                contentType = { _, entry ->
                    when (entry) {
                        is RecentEntry.MonthBar -> "month_bar"
                        is RecentEntry.Item -> "expense_row"
                    }
                }
            ) { index, entry ->
                Column(modifier = Modifier.staggeredEntrance(index, play = !entrancePlayed)) {
                when (entry) {
                    is RecentEntry.MonthBar -> MonthSpendBar(month = entry.month, total = entry.total)
                    is RecentEntry.Item -> {
                        HomeExpenseRow(
                            item = entry.item,
                            settlement = settlement[entry.item.expense.id],
                            onTap = { onExpenseTap(entry.item) },
                            onLongPress = { onExpenseLongPress(entry.item) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                }
            }

            if (searchedExpenses.size > visibleCount) {
                item(key = "recent_show_more") {
                    val remaining = searchedExpenses.size - visibleCount
                    OutlinedButton(
                        onClick = { visibleCount += RECENT_PAGE_SIZE },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Show ${minOf(remaining, RECENT_PAGE_SIZE)} more · $remaining left")
                    }
                }
            }
        }
    }
}

/**
 * Local, Home-screen-only design tokens matching the dark/light palette
 * from the design spec (teal --acc / amber warn / muted text / hairline
 * borders / elevated "card2" surface). Deliberately NOT wired into
 * MaterialTheme/Theme.kt — this only restyles the new Home components
 * added in this pass, so nothing else in the app changes.
 */
// Shared entrance-animation curve for every Home entrance animation (hero
// count-up + the staggered rise/fade below) — one interpolator wrapped once
// so every element eases identically instead of drifting apart.
// Same cubic-bezier curve as before, but Compose's pure-Kotlin easing: no
// android.graphics.Path at class load, so this file's top-level helpers
// (formatRupeeIndian, used by the widget) load in JVM unit tests.
private val EntranceEasing: Easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Whether Home's entrance animations have already played this app process.
 * A top-level var (not `remember`) on purpose: `remember`-scoped state
 * resets whenever HomeTabContent leaves and re-enters composition — which
 * happens on every swipe away from and back to the Home tab in the pager —
 * so it would replay the entrance each time. This survives that, and resets
 * only when the app process actually restarts (a real app open), matching
 * "entrance only, play once when the screen appears" — appears meaning the
 * app opening, not every tab revisit within one session.
 */
private object HomeEntrance {
    var hasPlayed = false
}

/**
 * False when the system's "Remove animations" accessibility setting has the
 * animator duration scale at 0 — Home's entrance animations are skipped
 * entirely in that case (final state shown immediately) rather than fighting
 * a setting the person turned on for a reason. Read once per screen entry;
 * this isn't expected to change while Home is on screen.
 */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }
}

/**
 * Entrance-only 14dp rise + fade, driven by a 0f-1f progress value. Built
 * from the plain `alpha`/`offset` modifiers (no graphicsLayer — this
 * project's Compose version doesn't resolve that import cleanly) so it
 * recomposes the handful of elements it's applied to while the animation
 * runs; that's a short, one-time cost, not a per-frame steady-state one. At
 * progress = 1f this is a no-op (alpha = 1, no offset), so it never changes
 * the screen's final resting layout, size, or colors.
 */
private fun Modifier.entranceRiseFade(progress: () -> Float): Modifier {
    val p = progress()
    return this
        .alpha(p)
        .offset(y = 14.dp * (1f - p))
}

private object HomeTokens {
    @Composable fun accent(): Color = if (isSystemInDarkTheme()) Color(0xFF2FD39A) else Color(0xFF14A87C)
    @Composable fun accentSoft(): Color = if (isSystemInDarkTheme()) Color(0xFF2FD39A).copy(alpha = 0.13f) else Color(0xFF14A87C).copy(alpha = 0.12f)
    @Composable fun warn(): Color = Color(0xFFF0A63C)
    @Composable fun card2(): Color = if (isSystemInDarkTheme()) Color(0xFF1A201E) else Color(0xFFE8EFEA)
    @Composable fun line(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color(0xFF12201A).copy(alpha = 0.10f)
    @Composable fun mut(): Color = if (isSystemInDarkTheme()) Color(0xFF8E9A95) else Color(0xFF5F6E66)
    // Text on the solid accent hero card — the teal is bright enough in
    // both modes that dark text reads better than white.
    @Composable fun onAccent(): Color = Color(0xFF06231A)
}

/**
 * ₹47,435 — Indian digit grouping, decimals dropped when they're just ".00".
 * Not private: TreemapView.kt's category tiles use this too, so every
 * rupee amount on Home is grouped the same way instead of each call site
 * rolling its own "%.0f" formatting (which is how the Bills tile ended up
 * showing "₹23216" with no thousands separator).
 */
/**
 * "10:19 AM" for an expense's real time, or null when there isn't one:
 * no timestamp, or a date picked by hand in older versions (saved as 12:00:00,
 * a placeholder rather than a real time of payment).
 */
fun expenseTimeLabel(timestampMillis: Long?): String? {
    if (timestampMillis == null || timestampMillis <= 0) return null
    val c = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
    val isPickedDatePlaceholder = c.get(java.util.Calendar.HOUR_OF_DAY) == 12 &&
        c.get(java.util.Calendar.MINUTE) == 0 &&
        c.get(java.util.Calendar.SECOND) == 0
    if (isPickedDatePlaceholder) return null
    return java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(timestampMillis))
}

/** "09-Oct-26 · 10:19 AM" (or just the date when there's no real time). */
fun expenseDateTimeLabel(date: String, timestampMillis: Long?): String =
    expenseTimeLabel(timestampMillis)?.let { "$date · $it" } ?: date

fun formatRupeeIndian(amount: Double): String {
    val rounded = kotlin.math.round(amount * 100) / 100.0
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return if (rounded == kotlin.math.floor(rounded)) {
        nf.maximumFractionDigits = 0
        "₹${nf.format(rounded)}"
    } else {
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        "₹${nf.format(rounded)}"
    }
}

/**
 * Same as [formatRupeeIndian] for amounts stored as text (parsed SMS keeps
 * "50000.00"). Falls back to the raw text if it isn't a number.
 */
fun formatRupeeIndian(amount: String): String =
    amount.replace(",", "").trim().toDoubleOrNull()?.let { formatRupeeIndian(it) } ?: "₹$amount"

@Suppress("UNUSED_PARAMETER") // animateEntrance kept so callers don't change
@Composable
private fun HomeHeroCard(
    label: String,
    amount: Double,
    // Which total this is (trip + filters); null = not loaded yet.
    counterKey: String?,
    animateEntrance: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = HomeTokens.accent()

    // No count-up animation — the amount just shows immediately, including
    // on the app's first open (animateEntrance is now unused but kept as a
    // parameter so callers don't need to change).

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent, accent.copy(alpha = 0.78f))
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = HomeTokens.onAccent().copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Counts from the last total you saw to the current one (only
            // when it changed); see ExpenseCounter.kt.
            AnimatedRupeeCounter(
                target = amount,
                storageKey = counterKey ?: "home_total|loading",
                ready = counterKey != null,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                color = HomeTokens.onAccent(),
                deltaColor = HomeTokens.onAccent().copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun BalanceChip(
    label: String,
    amount: Double,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = HomeTokens.card2(),
        border = BorderStroke(1.dp, HomeTokens.line())
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTokens.mut()
                )
                Text(
                    text = formatRupeeIndian(amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AccountCarouselCard(
    summary: AccountSpendingSummary,
    shareOfTotal: Float
) {
    val isSlice = summary.accountIdentifier == null
    val cardAccent = if (isSlice) Color(0xFFA37FF0) else HomeTokens.accent()
    Box(
        modifier = Modifier
            .width(172.dp)
            .modernCard(base = HomeTokens.card2(), radius = 22.dp, tint = cardAccent, tintAlpha = 0.10f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                // No card/account number = the Slice app's UPI payments (one card).
                text = summary.accountIdentifier ?: "Slice",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary.bank,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = HomeTokens.mut()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = formatRupeeIndian(summary.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Share bar — this account's slice of the total shown above.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(HomeTokens.line())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(shareOfTotal.coerceIn(0.03f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(cardAccent.copy(alpha = 0.6f), cardAccent)))
                )
            }
        }
    }
}

/**
 * Home-only recent-expense row — a restyled sibling of the shared
 * [ExpenseCard] (kept as-is since other screens may still use it), adding
 * a date line and a SPLIT badge when the expense has more than one
 * participant, on the new card2/hairline token surface.
 */
/**
 * Gap between the shared Main/bell header row and the first thing on each
 * tab. Home, Friends, Insights and Map all use this one value so every tab
 * starts at exactly the same height.
 */
internal val TAB_CONTENT_TOP_GAP = 12.dp

private const val RECENT_PAGE_SIZE = 15

/** One line of the Recent Expenses list: a month bar or an expense row. */
private sealed interface RecentEntry {
    data class MonthBar(val month: MonthKey?, val total: Double) : RecentEntry
    data class Item(val item: ExpenseWithParticipants) : RecentEntry
}

/** Calendar month an expense belongs to; null for (old) undated expenses. */
private fun recentMonthKeyOf(item: ExpenseWithParticipants): MonthKey? =
    item.expense.timestampMillis?.let { MonthKey.fromMillis(it) }

/**
 * Interleaves a month bar before the first row of each month. [monthTotals]
 * covers every matching expense, not just the visible page, so a bar always
 * shows the month's full total even when only part of it is expanded.
 */
private fun buildRecentEntries(
    visible: List<ExpenseWithParticipants>,
    monthTotals: Map<MonthKey?, Double>,
    showMonthBars: Boolean = true
): List<RecentEntry> {
    if (!showMonthBars) return visible.map { RecentEntry.Item(it) }
    val entries = ArrayList<RecentEntry>(visible.size + 4)
    var isFirst = true
    var currentMonth: MonthKey? = null
    for (item in visible) {
        val month = recentMonthKeyOf(item)
        if (isFirst || month != currentMonth) {
            entries += RecentEntry.MonthBar(month, monthTotals[month] ?: 0.0)
            currentMonth = month
            isFirst = false
        }
        entries += RecentEntry.Item(item)
    }
    return entries
}

private fun ExpenseWithParticipants.matchesSearch(query: String): Boolean {
    val q = query.lowercase()
    val e = expense
    // "500" should find ₹500.00, and "499.5" should find ₹499.50.
    val amountText = if (e.amount % 1.0 == 0.0) {
        e.amount.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", e.amount)
    }
    return e.merchant.lowercase().contains(q) ||
            e.bank.lowercase().contains(q) ||
            e.category.lowercase().contains(q) ||
            e.upi.lowercase().contains(q) ||
            e.date.lowercase().contains(q) ||
            (e.accountIdentifier?.lowercase()?.contains(q) == true) ||
            amountText.contains(q) ||
            participants.any { it.personName.lowercase().contains(q) }
}

/** "October" for this year, "October 2025" for older years. */
private fun monthBarLabel(month: MonthKey?): String {
    if (month == null) return "Undated"
    val pattern = if (month.year == MonthKey.current().year) "MMMM" else "MMMM yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(month.monthStartCalendar().time)
}

/** GPay-style month strip: month name on the left, that month's total on the right. */
@Composable
private fun MonthSpendBar(month: MonthKey?, total: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HomeTokens.accentSoft())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthBarLabel(month),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = HomeTokens.accent(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${formatRupeeIndian(total)} spent",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HomeExpenseRow(
    item: ExpenseWithParticipants,
    settlement: SettleStatus.ExpenseSettlement? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val view = androidx.compose.ui.platform.LocalView.current
    // Use the expense's own saved category, not a fresh merchant-keyword
    // guess — this row used to call categorize(item.expense.merchant),
    // which silently ignored any category the user had actually edited
    // (or that categorize() would guess differently today than it did
    // when the expense was first saved), so this row's icon/color could
    // drift out of sync with the real category shown in "By category"
    // above (which correctly reads expense.category).
    val category = categoryByName(item.expense.category)
    val darkTheme = isSystemInDarkTheme()
    val iconBackground = if (darkTheme) category.fgColor.copy(alpha = 0.24f) else category.bgColor
    val isSplit = item.participants.size > 1

    // Press feedback: shrink a touch while the finger is down (draw phase).
    val pressScale = remember { Animatable(1f) }
    val rowScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .modernCard(base = HomeTokens.card2(), radius = 18.dp)
            .pointerInput(item.expense.id) {
                detectTapGestures(
                    onPress = {
                        rowScope.launch { pressScale.animateTo(0.97f, tween(120, easing = MotionEaseOut)) }
                        tryAwaitRelease()
                        rowScope.launch { pressScale.animateTo(1f, tween(200, easing = MotionEaseOut)) }
                    },
                    onTap = { Haptics.tap(view); onTap?.invoke() },
                    onLongPress = { Haptics.hold(view); onLongPress?.invoke() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = iconBackground, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = category.name,
                    tint = category.fgColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.expense.merchant,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSplit || settlement != null) {
                        // SPLIT → PART PAID → SETTLED as friends pay back
                        // (derived from Friends balances, oldest splits first).
                        val (chipText, chipColor) = when (settlement?.state) {
                            SettleStatus.State.SETTLED -> "SETTLED" to com.example.splitwiseauto.ui.theme.positiveColor()
                            SettleStatus.State.PARTIAL -> "PART PAID" to warnColor()
                            else -> "SPLIT" to HomeTokens.accent()
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = chipColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = chipColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${expenseDateTimeLabel(item.expense.date, item.expense.timestampMillis)} · " +
                        item.participants.joinToString(" + ") { it.personName } +
                        if (settlement?.state == SettleStatus.State.PARTIAL) {
                            " · ${formatRupeeIndian(settlement.remaining)} left"
                        } else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = HomeTokens.mut(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatRupeeIndian(item.expense.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun ComingSoonScreen(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
    }
}


@Composable
private fun CategoryCard(
    summary: CategorySummary
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),

        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),

        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = summary.category.fgColor.copy(alpha = 0.20f),
                        shape = CircleShape
                    ),

                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = summary.category.icon,
                    contentDescription = summary.category.name,
                    tint = summary.category.fgColor
                )
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = summary.category.name,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = formatRupeeIndian(summary.total),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "${summary.percent}% of total",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}


@Composable
fun ExpenseCard(
    item: ExpenseWithParticipants,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val category = categorize(
        item.expense.merchant
    )
    val iconBackground = if (androidx.compose.foundation.isSystemInDarkTheme()) {
        category.fgColor.copy(alpha = 0.24f)
    } else {
        category.bgColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(item.expense.id) {
                detectTapGestures(
                    onTap = { Haptics.tap(view); onTap?.invoke() },
                    onLongPress = { Haptics.hold(view); onLongPress?.invoke() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {

        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),

            verticalAlignment = Alignment.CenterVertically
        ) {

            // Category icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = iconBackground,
                        shape = CircleShape
                    ),

                contentAlignment = Alignment.Center
            ) {

                Icon(
                    imageVector = category.icon,
                    contentDescription = category.name,
                    tint = category.fgColor
                )
            }


            Spacer(
                modifier = Modifier.width(12.dp)
            )


            // Merchant + participants
            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = item.expense.merchant,
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = item.participants.joinToString(" + ") {
                        it.personName
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }


            // Amount + date
            Column(
                horizontalAlignment = Alignment.End
            ) {

                Text(
                    text = formatRupeeIndian(item.expense.amount),
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = expenseDateTimeLabel(item.expense.date, item.expense.timestampMillis),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}


@Composable
private fun SettlementHistoryDialog(
    friend: Friend,
    settlements: List<Settlement>,
    onDismiss: () -> Unit,
    onCorrect: (Settlement) -> Unit,
    onDelete: (Settlement) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    AppSheet(
        title = "${friend.name} · settlements",
        onDismiss = onDismiss,
        trailing = { TextButton(onClick = onDismiss) { Text("Close") } }
    ) {
        if (settlements.isEmpty()) {
            Text(
                text = "No settlements recorded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            settlements.forEachIndexed { index, settlement ->
                if (index > 0) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settlement.amount >= 0) {
                                "Received ${formatRupeeIndian(settlement.amount)}"
                            } else {
                                "Paid ${formatRupeeIndian(abs(settlement.amount))}"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Text(
                            text = dateFormat.format(Date(settlement.settledAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { onCorrect(settlement) }) { Text("Edit") }
                    TextButton(onClick = { onDelete(settlement) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectSettlementDialog(
    settlement: Settlement,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    // Resets whenever a different settlement is being corrected.
    var amountText by remember(settlement.id) {
        mutableStateOf(String.format(Locale.US, "%.2f", abs(settlement.amount)))
    }
    val parsedAmount = amountText.replace(",", "").toDoubleOrNull()
    val valid = parsedAmount != null && parsedAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct amount") },
        text = {
            Column {
                Text(
                    text = if (settlement.amount >= 0) {
                        "This entry recorded a payment received. Direction (who paid whom) can't change here — only the amount."
                    } else {
                        "This entry recorded a payment made. Direction (who paid whom) can't change here — only the amount."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                WiseAmountField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Amount",
                    isError = amountText.isNotBlank() && !valid,
                    autoFocus = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(parsedAmount!!) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
private fun FilterDialog(
    dateFilter: DateFilter,
    participantFilter: ParticipantFilter,
    selectedFriendNames: Set<String>,
    friends: List<Friend>,
    savedFilters: List<SavedFilter>,
    onApply: (DateFilter, ParticipantFilter, Set<String>) -> Unit,
    onSaveCustomFilter: (name: String, start: Long, end: Long) -> Unit,
    onDeleteSavedFilter: (SavedFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    var selectedType by remember { mutableStateOf(dateFilter.type) }
    var customStart by remember { mutableStateOf(dateFilter.startMillis) }
    var customEnd by remember { mutableStateOf(dateFilter.endMillis) }
    var customName by remember { mutableStateOf(dateFilter.label.orEmpty()) }
    var selectedParticipant by remember { mutableStateOf(participantFilter) }
    // Picking specific friends under "Any friend" — e.g. just Akhil, or
    // Akhil + Abin together. Empty stays "any friend at all".
    var selectedFriends by remember { mutableStateOf(selectedFriendNames) }

    fun pickDate(current: Long?, onPicked: (Long) -> Unit, endOfDay: Boolean) {
        val cal = Calendar.getInstance()
        current?.let { cal.timeInMillis = it }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, if (endOfDay) 23 else 0, if (endOfDay) 59 else 0, if (endOfDay) 59 else 0)
                }
                onPicked(picked.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val customRangeValid = customStart != null && customEnd != null && customStart!! <= customEnd!!
    val canApply = selectedType != DateFilterType.CUSTOM || customRangeValid

    val applyNow = {
        if (selectedType == DateFilterType.CUSTOM && customRangeValid && customName.isNotBlank()) {
            onSaveCustomFilter(customName.trim(), customStart!!, customEnd!!)
        }
        onApply(
            DateFilter(
                type = selectedType,
                startMillis = if (selectedType == DateFilterType.CUSTOM) customStart else null,
                endMillis = if (selectedType == DateFilterType.CUSTOM) customEnd else null,
                label = if (selectedType == DateFilterType.CUSTOM) customName.trim().ifBlank { null } else null
            ),
            if (selectedParticipant == ParticipantFilter.WITH_ME) ParticipantFilter.ANY_FRIEND else selectedParticipant,
            if (selectedParticipant == ParticipantFilter.ANY_FRIEND) selectedFriends else emptySet()
        )
        onDismiss()
    }

    // Full-width bottom sheet (a Dialog anchored to the bottom) instead of a
    // narrow AlertDialog: the old one squeezed 4 equal pills into ~280dp and
    // truncated every label ("7 da…", "Ever…").
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    // Swallow taps so touching the sheet doesn't dismiss it.
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .heightIn(max = 640.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp, bottom = 16.dp)
                ) {
                    // Grab handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            selectedType = DateFilterType.ALL
                            customStart = null
                            customEnd = null
                            customName = ""
                            selectedParticipant = ParticipantFilter.ALL
                            selectedFriends = emptySet()
                        }) { Text("Reset") }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FilterSectionLabel("When")
                    SegmentedControl(
                        options = listOf(
                            DateFilterType.ALL to "All",
                            DateFilterType.LAST_WEEK to "7 days",
                            DateFilterType.LAST_MONTH to "30 days",
                            DateFilterType.CUSTOM to "Custom"
                        ),
                        selected = selectedType,
                        onSelect = { selectedType = it }
                    )

                    if (selectedType == DateFilterType.CUSTOM) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DateField(
                                label = "From",
                                value = customStart?.let { dateFormat.format(Date(it)) },
                                modifier = Modifier.weight(1f),
                                onClick = { pickDate(customStart, { customStart = it }, endOfDay = false) }
                            )
                            DateField(
                                label = "To",
                                value = customEnd?.let { dateFormat.format(Date(it)) },
                                modifier = Modifier.weight(1f),
                                onClick = { pickDate(customEnd, { customEnd = it }, endOfDay = true) }
                            )
                        }
                        if (customStart != null && customEnd != null && !customRangeValid) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Start date must be before end date.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (savedFilters.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                savedFilters.forEach { saved ->
                                    FilterPill(
                                        label = saved.name,
                                        selected = customStart == saved.startMillis && customEnd == saved.endMillis,
                                        onClick = {
                                            customStart = saved.startMillis
                                            customEnd = saved.endMillis
                                            customName = saved.name
                                        },
                                        onRemove = { onDeleteSavedFilter(saved) }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ModernTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = "Save as… (optional)",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                    FilterSectionLabel("Who")
                    val effectiveParticipant =
                        if (selectedParticipant == ParticipantFilter.WITH_ME) ParticipantFilter.ANY_FRIEND else selectedParticipant
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column {
                            listOf(
                                Triple(ParticipantFilter.ALL, "Everyone", "All expenses"),
                                Triple(ParticipantFilter.ONLY_ME, "Just me", "Only my own spending"),
                                Triple(ParticipantFilter.ANY_FRIEND, "Shared", "Split with friends"),
                                Triple(ParticipantFilter.WITHOUT_ME, "For others", "I paid, but it wasn't for me")
                            ).forEachIndexed { index, (value, title, subtitle) ->
                                if (index > 0) {
                                    Divider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                                ChoiceRow(
                                    title = title,
                                    subtitle = subtitle,
                                    selected = effectiveParticipant == value,
                                    onClick = { selectedParticipant = value }
                                )
                            }
                        }
                    }

                    if (effectiveParticipant == ParticipantFilter.ANY_FRIEND && friends.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (selectedFriends.isEmpty()) "Any friend · tap to narrow down" else "Only with",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            friends.forEach { friend ->
                                val checked = selectedFriends.any { it.equals(friend.name, ignoreCase = true) }
                                FilterPill(
                                    label = friend.name,
                                    selected = checked,
                                    onClick = {
                                        selectedFriends = if (checked) {
                                            selectedFriends.filterNot { it.equals(friend.name, ignoreCase = true) }.toSet()
                                        } else {
                                            selectedFriends + friend.name
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = applyNow,
                        enabled = canApply,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Apply", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}

/** One connected track with a sliding-looking selected segment. */
@Composable
private fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DateField(label: String, value: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value ?: "Pick date",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Content-sized pill (friends, saved ranges) — never squeezed or truncated. */
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected && onRemove == null) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            if (onRemove != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete $label",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove)
                )
            }
        }
    }
}


/**
 * Delete a trip. Safe default: its expenses are KEPT and move to Main
 * (they were only ever tagged with the trip). Deleting them too is an
 * explicit opt-in, because that also changes friend balances.
 */
@Composable
private fun DeleteTripDialog(
    trip: Trip,
    expenseCount: Int,
    onDismiss: () -> Unit,
    onDelete: (alsoDeleteExpenses: Boolean) -> Unit
) {
    var alsoDeleteExpenses by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"${trip.name}\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (expenseCount == 0) "This trip has no expenses."
                    else if (alsoDeleteExpenses) "Its $expenseCount expense${if (expenseCount == 1) "" else "s"} " +
                        "will be deleted too. Balances with friends from those splits will change."
                    else "Its $expenseCount expense${if (expenseCount == 1) "" else "s"} will be kept " +
                        "and moved to Main. Nothing is lost.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (expenseCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoDeleteExpenses = !alsoDeleteExpenses }
                    ) {
                        Checkbox(checked = alsoDeleteExpenses, onCheckedChange = { alsoDeleteExpenses = it })
                        Text("Also delete its expenses", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(alsoDeleteExpenses) }) {
                Text(
                    if (alsoDeleteExpenses) "Delete trip & expenses" else "Delete trip",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/**
 * Bell alert: just a small red dot (no number) with a ring in the
 * background colour so it looks cut out of the button. It pops in when
 * something new arrives.
 */
@Composable
private fun NotificationBadge(count: Int) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(count) {
        // Occasional event: a small pop is fine, but subtle (never from 0).
        pop.snapTo(0.6f)
        pop.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
    }
    Box(
        modifier = Modifier
            .offset(x = (-10).dp, y = 8.dp)
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(13.dp)
            .background(MaterialTheme.colorScheme.background, CircleShape)
            .padding(2.5.dp)
            .background(Color(0xFFFF4D4F), CircleShape)
    )
}


private fun periodChipLabel(m: MonthKey): String =
    if (m.year == MonthKey.current().year) m.shortLabel() else "${m.shortLabel()} ${m.year % 100}"

/** Remembers Home's period (a month, or all time). Default: this month. */
private object HomePeriodStore {
    private const val PREFS = "home_period"
    private const val KEY = "period" // "all" or "2026-10"

    fun load(context: android.content.Context): DateFilter {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY, null)
        return when {
            raw == "all" -> DateFilter()
            raw != null -> runCatching {
                val (y, m) = raw.split("-").map { it.toInt() }
                DateFilter.forMonth(MonthKey(y, m))
            }.getOrElse { DateFilter.forMonth(MonthKey.current()) }
            else -> DateFilter.forMonth(MonthKey.current())
        }
    }

    fun save(context: android.content.Context, month: MonthKey?) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putString(KEY, month?.let { "${it.year}-${it.month}" } ?: "all")
            .apply()
    }
}


