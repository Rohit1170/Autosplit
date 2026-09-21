@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.splitwiseauto

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    pendingNotificationsViewModel: PendingNotificationsViewModel = viewModel()
) {
    val expenses by homeViewModel.expenses.collectAsState()
    val total by homeViewModel.totalSpent.collectAsState()
    val mySpent by homeViewModel.mySpent.collectAsState()
    val friends by friendViewModel.friends.collectAsState()
    val friendBalances by friendViewModel.balances.collectAsState()
    val totalOwedToYou by friendViewModel.totalOwedToYou.collectAsState()
    val categories by homeViewModel.categorySummaries.collectAsState()
    val accountSpending by homeViewModel.accountSpending.collectAsState()
    val mapExpenses by mapViewModel.expensesWithLocation.collectAsState()
    val pendingNotifications by pendingNotificationsViewModel.pending.collectAsState()
    val settlementHistory by friendViewModel.settlementHistory.collectAsState()
    var showAddFriendDialog by remember {
        mutableStateOf(false)
    }
    var showPendingNotifications by remember {
        mutableStateOf(false)
    }
    var expenseForActions by remember { mutableStateOf<ExpenseWithParticipants?>(null) }
    var friendForActions by remember { mutableStateOf<Friend?>(null) }
    var friendToRename by remember { mutableStateOf<Friend?>(null) }
    var friendForHistory by remember { mutableStateOf<Friend?>(null) }
    var settlementBeingCorrected by remember { mutableStateOf<Settlement?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var dateFilter by remember { mutableStateOf(DateFilter()) }
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
        AlertDialog(
            onDismissRequest = { expenseForActions = null },
            title = { Text(item.expense.merchant) },
            text = { Text("Choose what you want to do with this expense.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(context, ManualExpenseActivity::class.java).apply {
                            putExtra("expenseId", item.expense.id)
                            putExtra("amount", item.expense.amount.toString())
                            putExtra("merchant", item.expense.merchant)
                            putExtra("date", item.expense.date)
                            putExtra("category", item.expense.category)
                            putExtra("payer", item.expense.payer)
                            putExtra("bank", item.expense.bank)
                            item.expense.accountIdentifier?.let {
                                putExtra("accountIdentifier", it)
                            }
                            item.expense.timestampMillis?.let {
                                putExtra("timestampMillis", it)
                            }
                        }
                    )
                    expenseForActions = null
                }) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        homeViewModel.deleteExpense(item.expense.id)
                        expenseForActions = null
                    }) { Text("Delete") }
                    TextButton(onClick = { expenseForActions = null }) { Text("Cancel") }
                }
            }
        )
    }
    friendForActions?.let { friend ->
        val balance = friendBalances[friend.id] ?: FriendBalance()
        val amount = kotlin.math.abs(balance.netAmount)
        // Resets whenever a different friend's dialog opens.
        var partialAmountText by remember(friend.id) { mutableStateOf("") }
        val partialAmount = partialAmountText.replace(",", "").toDoubleOrNull()
        val partialAmountValid = partialAmount != null && partialAmount > 0.0 && partialAmount <= amount + 0.001

        AlertDialog(
            onDismissRequest = { friendForActions = null },
            title = { Text(friend.name) },
            text = {
                Column {
                    Text(
                        if (amount > 0.01) {
                            if (balance.netAmount > 0) {
                                "${friend.name} owes you ₹${"%.2f".format(amount)}."
                            } else {
                                "You owe ${friend.name} ₹${"%.2f".format(amount)}."
                            }
                        } else {
                            "There is no pending balance with ${friend.name}."
                        }
                    )

                    if (amount > 0.01) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Only got part of it (e.g. some in cash)? Enter that amount to settle just that part — the rest stays outstanding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = partialAmountText,
                            onValueChange = { partialAmountText = it },
                            label = { Text("Partial amount") },
                            singleLine = true,
                            isError = partialAmountText.isNotBlank() && !partialAmountValid,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (amount > 0.01) {
                    Row {
                        if (partialAmountText.isNotBlank()) {
                            TextButton(
                                enabled = partialAmountValid,
                                onClick = {
                                    friendViewModel.settleUp(friend, partialAmount)
                                    friendForActions = null
                                }
                            ) { Text("Settle partial") }
                        }
                        TextButton(onClick = {
                            friendViewModel.settleUp(friend)
                            friendForActions = null
                        }) { Text("Settle full") }
                    }
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        friendViewModel.loadSettlementHistory(friend.id)
                        friendForHistory = friend
                        friendForActions = null
                    }) { Text("History") }
                    TextButton(onClick = {
                        friendToRename = friend
                        friendForActions = null
                    }) { Text("Rename") }
                    TextButton(onClick = {
                        friendViewModel.removeFriend(friend)
                        friendForActions = null
                    }) { Text("Remove friend") }
                    TextButton(onClick = { friendForActions = null }) { Text("Cancel") }
                }
            }
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
            onDismissItem = { id -> pendingNotificationsViewModel.dismiss(id) }
        )
    }
    if (showTripPicker) {
        AlertDialog(
            onDismissRequest = { showTripPicker = false },
            title = { Text("Switch mode") },
            text = {
                Column {
                    Surface(
                        onClick = {
                            activeTripId = null
                            activeTripName = null
                            ActiveTripStore.setActiveTrip(context, null, null)
                            showTripPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Filled.Home, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Main")
                        }
                    }
                    trips.forEach { trip ->
                        Surface(
                            onClick = {
                                activeTripId = trip.id
                                activeTripName = trip.name
                                ActiveTripStore.setActiveTrip(context, trip.id, trip.name)
                                showTripPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Filled.Place, contentDescription = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(trip.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTripPicker = false
                    showNewTripDialog = true
                }) { Text("+ New Trip") }
            },
            dismissButton = {
                TextButton(onClick = { showTripPicker = false }) { Text("Cancel") }
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
                OutlinedTextField(
                    value = newTripName,
                    onValueChange = { newTripName = it },
                    label = { Text("Trip name") },
                    singleLine = true,
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
            NavigationBar {

                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        goToTab(0)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Home"
                        )
                    },
                    label = {
                        Text("Home")
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        goToTab(1)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Friends"
                        )
                    },
                    label = {
                        Text("Friends")
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        goToTab(2)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = "Map"
                        )
                    },
                    label = {
                        Text("Map")
                    }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = {
                        goToTab(3)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = "Insights"
                        )
                    },
                    label = {
                        Text("Insights")
                    }
                )
            }
        },

        floatingActionButton = {

            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = {
                        showAddFriendDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Friend"
                    )
                }
            } else if (selectedTab == 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SmallFloatingActionButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, QrScanActivity::class.java)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "Scan & Pay"
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = {
                            context.startActivity(
                                Intent(context, ManualExpenseActivity::class.java)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Expense"
                        )
                    }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                Surface(
                    onClick = { showTripPicker = true },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeTripName ?: "Main",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    onClick = { showPendingNotifications = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingNotifications.isNotEmpty()) {
                                    Badge {
                                        Text(pendingNotifications.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Missed expenses"
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
                            total = total,
                            categories = categories,
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
                                dateFilter = DateFilter()
                                participantFilter = ParticipantFilter.ALL
                                selectedFriendNames = emptySet()
                            },
                            modifier = Modifier.padding(paddingValues)
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
                            onFriendDoubleTap = { friendForActions = it },
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    2 -> {
                        ExpenseMapScreen(
                            expenses = mapExpenses,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    3 -> {
                        InsightsScreen(
                            viewModel = insightsViewModel,
                            modifier = Modifier.padding(paddingValues)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun HomeTabContent(
    expenses: List<ExpenseWithParticipants>,
    total: Double,
    categories: List<CategorySummary>,
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

    // Entrance-only staggered rise/fade — hero -> balance cards -> Accounts
    // -> category tiles -> recent expenses, 75ms apart. Gated on HomeEntrance
    // .hasPlayed (see its definition) so this plays once per app open, not
    // on every revisit of the Home tab within the same session, and is
    // skipped outright when the system's animator duration scale is 0.
    val animationsEnabled = rememberAnimationsEnabled()
    val playEntrance = animationsEnabled && !HomeEntrance.hasPlayed
    val heroRiseProgress = remember { Animatable(if (playEntrance) 0f else 1f) }
    val balanceRiseProgress = remember { Animatable(if (playEntrance) 0f else 1f) }
    val accountsRiseProgress = remember { Animatable(if (playEntrance) 0f else 1f) }
    val categoriesRiseProgress = remember { Animatable(if (playEntrance) 0f else 1f) }
    val expensesRiseProgress = remember { Animatable(if (playEntrance) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!playEntrance) return@LaunchedEffect
        HomeEntrance.hasPlayed = true
        val stagger = 75
        launch { heroRiseProgress.animateTo(1f, tween(600, 0 * stagger, EntranceEasing)) }
        launch { balanceRiseProgress.animateTo(1f, tween(600, 1 * stagger, EntranceEasing)) }
        launch { accountsRiseProgress.animateTo(1f, tween(600, 2 * stagger, EntranceEasing)) }
        launch { categoriesRiseProgress.animateTo(1f, tween(600, 3 * stagger, EntranceEasing)) }
        launch { expensesRiseProgress.animateTo(1f, tween(600, 4 * stagger, EntranceEasing)) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),

        contentPadding = PaddingValues(
            top = 16.dp,
            // Extra room at the bottom so the last card never sits under the
            // stacked Scan & Pay / Add Expense FABs on this tab.
            bottom = 104.dp
        )
    ) {

        // Hero total card — same underlying number as before (scoped to
        // whatever filter is active below), just given a visual anchor
        // instead of a bare label + number.
        item {
            HomeHeroCard(
                label = if (filterActive) "TOTAL (${filterLabel.uppercase()})" else "TOTAL EXPENSES",
                amount = total,
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        if (categories.isNotEmpty()) {

            item {
                Column(modifier = Modifier.entranceRiseFade { categoriesRiseProgress.value }) {
                    Text(
                        text = "By category",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Treemap(
                        items = categories,
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                }
            }
        }


        // Recent expenses heading + filter
        item {
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

        } else {

            // Expense list — rises/fades in as one group with the "Recent
            // Expenses" heading above (all rows share the same progress
            // value; not staggered row-by-row, since that group can be long).
            items(
                items = expenses,
                key = { item ->
                    item.expense.id
                }
            ) { item ->

                HomeExpenseRow(
                    item = item,
                    onTap = { onExpenseTap(item) },
                    onLongPress = { onExpenseLongPress(item) },
                    modifier = Modifier.entranceRiseFade { expensesRiseProgress.value }
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
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
private val EntranceInterpolator = PathInterpolatorCompat.create(0.22f, 1f, 0.36f, 1f)
private val EntranceEasing = Easing { fraction -> EntranceInterpolator.getInterpolation(fraction) }

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

@Composable
private fun HomeHeroCard(
    label: String,
    amount: Double,
    animateEntrance: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = HomeTokens.accent()

    // Counts up from ₹0 only when the caller says this is a genuine entrance
    // (HomeEntrance.hasPlayed was still false for this app process) — a
    // later legitimate change to `amount` (e.g. a filter narrowing the
    // total) snaps straight to the new value instead of re-playing the
    // count-up, since that's not "the app opening" anymore.
    val displayedAmount = remember { Animatable(if (animateEntrance) 0f else amount.toFloat()) }
    var hasPlayedEntrance by remember { mutableStateOf(!animateEntrance) }
    LaunchedEffect(amount) {
        if (!hasPlayedEntrance) {
            hasPlayedEntrance = true
            displayedAmount.animateTo(
                targetValue = amount.toFloat(),
                animationSpec = tween(durationMillis = 1200, easing = EntranceEasing)
            )
        } else {
            displayedAmount.snapTo(amount.toFloat())
        }
    }

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
            Text(
                text = formatRupeeIndian(displayedAmount.value.toDouble()),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = HomeTokens.onAccent()
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
    Surface(
        modifier = Modifier.width(168.dp),
        shape = RoundedCornerShape(18.dp),
        color = HomeTokens.card2(),
        border = BorderStroke(1.dp, HomeTokens.line())
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
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
                        .background(HomeTokens.accent())
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
@Composable
private fun HomeExpenseRow(
    item: ExpenseWithParticipants,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val category = categorize(item.expense.merchant)
    val darkTheme = isSystemInDarkTheme()
    val iconBackground = if (darkTheme) category.fgColor.copy(alpha = 0.24f) else category.bgColor
    val isSplit = item.participants.size > 1

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(item.expense.id) {
                detectTapGestures(
                    onTap = { onTap?.invoke() },
                    onLongPress = { onLongPress?.invoke() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = HomeTokens.card2(),
        border = BorderStroke(1.dp, HomeTokens.line())
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
                    if (isSplit) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = HomeTokens.accentSoft()
                        ) {
                            Text(
                                text = "SPLIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = HomeTokens.accent(),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${item.expense.date} · ${item.participants.joinToString(" + ") { it.personName }}",
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
                text = "₹${"%.0f".format(summary.total)}",
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
                    onTap = { onTap?.invoke() },
                    onLongPress = { onLongPress?.invoke() }
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
                    text = "₹${"%.2f".format(item.expense.amount)}",
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = item.expense.date,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${friend.name} — settlement history") },
        text = {
            if (settlements.isEmpty()) {
                Text("No settlements recorded yet.")
            } else {
                Column {
                    settlements.forEachIndexed { index, settlement ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (settlement.amount >= 0) {
                                        "Received ₹${"%.2f".format(settlement.amount)}"
                                    } else {
                                        "Paid ₹${"%.2f".format(abs(settlement.amount))}"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                                Text(
                                    text = dateFormat.format(Date(settlement.settledAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onCorrect(settlement) }) { Text("Edit") }
                            TextButton(onClick = { onDelete(settlement) }) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    isError = amountText.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter expenses") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Date range",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    DateFilterType.ALL to "All time",
                    DateFilterType.LAST_WEEK to "Last week",
                    DateFilterType.LAST_MONTH to "Last month",
                    DateFilterType.CUSTOM to "Custom"
                ).forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                        Text(label)
                    }
                }

                if (selectedType == DateFilterType.CUSTOM) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            pickDate(customStart, { customStart = it }, endOfDay = false)
                        }) {
                            Text(customStart?.let { dateFormat.format(Date(it)) } ?: "Start date")
                        }
                        Text("–")
                        TextButton(onClick = {
                            pickDate(customEnd, { customEnd = it }, endOfDay = true)
                        }) {
                            Text(customEnd?.let { dateFormat.format(Date(it)) } ?: "End date")
                        }
                    }
                    if (customStart != null && customEnd != null && !customRangeValid) {
                        Text(
                            text = "Start date must be before end date.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (savedFilters.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Saved filters",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        savedFilters.forEach { saved ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    customStart = saved.startMillis
                                    customEnd = saved.endMillis
                                    customName = saved.name
                                }) { Text(saved.name) }
                                IconButton(onClick = { onDeleteSavedFilter(saved) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Delete saved filter ${saved.name}",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Name this range (to save it)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Split with",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                listOf(
                    ParticipantFilter.ALL to "Everyone",
                    ParticipantFilter.WITH_ME to "With me",
                    ParticipantFilter.WITHOUT_ME to "Without me",
                    ParticipantFilter.ONLY_ME to "Only me",
                    ParticipantFilter.ANY_FRIEND to "Any friend"
                ).forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedParticipant = type },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedParticipant == type, onClick = { selectedParticipant = type })
                        Text(label)
                    }
                }

                if (selectedParticipant == ParticipantFilter.ANY_FRIEND && friends.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Leave all unchecked for any friend, or pick one or more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    friends.forEach { friend ->
                        val checked = selectedFriends.any { it.equals(friend.name, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFriends = if (checked) {
                                        selectedFriends.filterNot { it.equals(friend.name, ignoreCase = true) }.toSet()
                                    } else {
                                        selectedFriends + friend.name
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selectedFriends = if (isChecked) {
                                        selectedFriends + friend.name
                                    } else {
                                        selectedFriends.filterNot { it.equals(friend.name, ignoreCase = true) }.toSet()
                                    }
                                }
                            )
                            Text(friend.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canApply,
                onClick = {
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
                        selectedParticipant,
                        if (selectedParticipant == ParticipantFilter.ANY_FRIEND) selectedFriends else emptySet()
                    )
                    onDismiss()
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onApply(DateFilter(), ParticipantFilter.ALL, emptySet())
                    onDismiss()
                }) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
