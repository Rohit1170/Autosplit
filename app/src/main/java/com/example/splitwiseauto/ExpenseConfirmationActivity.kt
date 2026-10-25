package com.example.splitwiseauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import com.example.splitwiseauto.ui.theme.positiveColor
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class ExpenseConfirmationActivity : ComponentActivity() {

    companion object {
        /** Set by ManualExpenseActivity when the user picked/changed the category by hand. */
        const val EXTRA_LEARN_CATEGORY = "learnCategory"
    }

    private lateinit var database: AppDatabase

    private var amount: Double = 0.0
    private var merchant: String = ""
    private var bank: String = ""
    private var date: String = ""
    private var balance: String = ""
    private var upi: String = ""
    private var category: String = "Other"
    private var payer: String = "Me"
    private var accountIdentifier: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var editingExpenseId: Int? = null
    private var timestampMillis: Long = System.currentTimeMillis()
    private var pendingNotificationId: Int? = null
    private var tripId: Int? = null
    // Group this split belongs to (kept when editing; picked on the screen).
    private var groupId: Int? = null
    private var source: String? = null
    private var paymentAttemptId: Int? = null
    // Foreign-currency spend ("PHP 518.09"); amount stays INR.
    private var originalAmount: Double? = null
    private var originalCurrency: String? = null
    private var fxEstimated: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this) // 90/120 Hz where the screen supports it
        CustomCategories.ensureLoaded(this)

        database = AppDatabase.getDatabase(this)
        editingExpenseId = intent.getIntExtra("expenseId", -1).takeIf { it > 0 }

        amount =
            intent.getStringExtra("amount")
                ?.replace(",", "")
                ?.toDoubleOrNull()
                ?: 0.0

        merchant =
            intent.getStringExtra("merchant")
                ?: "Unknown"

        bank =
            intent.getStringExtra("bank")
                ?: "Unknown"

        date =
            intent.getStringExtra("date")
                ?: "Unknown"

        balance =
            intent.getStringExtra("balance")
                ?: "Unknown"

        upi =
            intent.getStringExtra("upi")
                ?: "Unknown"

        category = intent.getStringExtra("category") ?: categorize(merchant).name
        payer = intent.getStringExtra("payer") ?: "Me"
        accountIdentifier = intent.getStringExtra("accountIdentifier")

        latitude = if (intent.hasExtra("latitude")) intent.getDoubleExtra("latitude", 0.0) else null
        longitude = if (intent.hasExtra("longitude")) intent.getDoubleExtra("longitude", 0.0) else null
        timestampMillis = intent.getLongExtra("timestampMillis", -1L).takeIf { it > 0 } ?: System.currentTimeMillis()
        pendingNotificationId = intent.getIntExtra("pendingNotificationId", -1).takeIf { it >= 0 }
        source = intent.getStringExtra("source")
        paymentAttemptId = intent.getIntExtra("paymentAttemptId", -1).takeIf { it >= 0 }
        originalAmount = if (intent.hasExtra("originalAmount")) intent.getDoubleExtra("originalAmount", 0.0) else null
        originalCurrency = intent.getStringExtra("originalCurrency")
        fxEstimated = intent.getBooleanExtra("fxEstimated", false)

        if (editingExpenseId != null) {
            lifecycleScope.launch {
                // Preserve whichever trip (if any) this expense already
                // belongs to, regardless of which mode happens to be active
                // right now while editing it. Also preserve its original
                // location — editing an expense (e.g. fixing the amount
                // after settling half in cash, once you're back home) must
                // never move its pin to wherever you happen to be right now,
                // so the saved lat/long always wins over anything passed in.
                val original = database.expenseDao().getExpenseById(editingExpenseId!!)
                tripId = original?.tripId
                groupId = original?.groupId
                latitude = original?.latitude
                longitude = original?.longitude

                val savedAmounts = database.expenseParticipantDao()
                    .getParticipantsForExpense(editingExpenseId!!)
                    .associate { it.personName to it.shareAmount }
                showExpenseConfirmation(savedAmounts)
            }
        } else {
            // New expense: tag it with the active trip — unless that trip is
            // closed (then it goes to Main). "Main" mode == null.
            tripId = ActiveTripStore.getRecordingTripId(this)
            showExpenseConfirmation(emptyMap())
        }
    }

    /** Brief success moment (check + pulse) shown after saving, before closing. */
    private var savedFlash by androidx.compose.runtime.mutableStateOf<String?>(null)
    private var saving = false

    private fun showExpenseConfirmation(initialSelectedAmounts: Map<String, Double>) {
        setContent {

            SplitleeTheme {

                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ExpenseConfirmationScreen(
                        expenseAmount = amount,
                        merchant = merchant,
                        bank = bank,
                        date = date,
                        friends = database.friendDao(),
                        initialSelectedAmounts = initialSelectedAmounts,
                        initialGroupId = groupId,
                        isScanPay = source == "scan_pay",
                        // Quick outcomes only for a NEW detected expense, not when editing.
                        showQuickActions = editingExpenseId == null,
                        onSave = { selectedAmounts, pickedGroupId ->
                            groupId = pickedGroupId
                            saveExpense(
                                selectedAmounts
                            )
                        },
                        onJustMe = { saveExpense(mapOf("Me" to amount)) },
                        onCancel = {
                            pendingNotificationId?.let { id ->
                                QuickExpenseActions.cancelNotification(this@ExpenseConfirmationActivity, id)
                                lifecycleScope.launch {
                                    database.pendingExpenseNotificationDao().deleteById(id)
                                }
                            }
                            // Not just finish(): this screen is only ever
                            // reached from a notification tap and has no
                            // reason to leave a resumable task behind (see
                            // the matching note in saveExpense()).
                            finishAndRemoveTask()
                        }
                    )
                }
                savedFlash?.let { message -> SavedOverlay(message) }
                }
            }
        }
    }

    private fun saveExpense(
        selectedAmounts: Map<String, Double>
    ) {
        // One save per screen: ignore extra taps while saving / showing the check.
        if (saving) return
        saving = true

        lifecycleScope.launch {

            // Merchant -> category: a NEW expense whose category nobody chose
            // (the SMS / notification path) asks the learned map first, then
            // the built-in merchant map, then the old keyword guess. When the
            // user picked the category by hand (Manual/Edit screen), remember
            // it for this merchant. Existing expenses are never re-categorised.
            if (editingExpenseId == null && !intent.hasExtra("category")) {
                category = MerchantCategorizer.resolve(this@ExpenseConfirmationActivity, merchant)
            }
            if (intent.getBooleanExtra(EXTRA_LEARN_CATEGORY, false)) {
                MerchantCategorizer.learn(this@ExpenseConfirmationActivity, merchant, category)
            }

            /*
             * First save the expense.
             */
            val expenseId = editingExpenseId ?: database.expenseDao().insertExpense(
                    Expense(
                        amount = amount,
                        merchant = merchant,
                        bank = bank,
                        date = date,
                        upi = upi,
                        payer = payer,
                        category = category,
                        accountIdentifier = accountIdentifier,
                        latitude = latitude,
                        longitude = longitude,
                        timestampMillis = timestampMillis,
                        tripId = tripId,
                        paymentAttemptId = paymentAttemptId,
                        groupId = groupId,
                        originalAmount = originalAmount,
                        originalCurrency = originalCurrency,
                        fxEstimated = fxEstimated
                    )
                ).toInt()

            if (editingExpenseId != null) {
                // Keep the foreign-currency details of the expense being edited.
                val existing = database.expenseDao().getExpenseById(expenseId)
                database.expenseDao().updateExpense(
                    Expense(
                        id = expenseId,
                        amount = amount,
                        merchant = merchant,
                        bank = bank,
                        date = date,
                        upi = upi,
                        payer = payer,
                        category = category,
                        accountIdentifier = accountIdentifier,
                        latitude = latitude,
                        longitude = longitude,
                        timestampMillis = timestampMillis,
                        tripId = tripId,
                        paymentAttemptId = paymentAttemptId,
                        groupId = groupId,
                        originalAmount = existing?.originalAmount,
                        originalCurrency = existing?.originalCurrency,
                        fxEstimated = existing?.fxEstimated ?: false
                    )
                )
                database.expenseDao().deleteParticipantsForExpense(expenseId)
            }


            /*
             * Convert the selected amounts into
             * ExpenseParticipant rows.
             */
            val participants =
                selectedAmounts.map { (personName, shareAmount) ->

                    ExpenseParticipant(
                        expenseId = expenseId,
                        personName = personName,
                        shareAmount = shareAmount
                    )
                }


            if (participants.isNotEmpty()) {

                database
                    .expenseParticipantDao()
                    .insertParticipants(participants)
            }

            pendingNotificationId?.let { id ->
                database.pendingExpenseNotificationDao().deleteById(id)
                QuickExpenseActions.cancelNotification(this@ExpenseConfirmationActivity, id)
            }

            paymentAttemptId?.let { id ->
                database.paymentAttemptDao().setAdoptedExpenseId(id, expenseId)
            }

            // Scan & Pay (UPI intent flow): the confirming SMS's reference is
            // this expense's `upi` and the PendingPayment's upiRef, so link
            // the payment to the expense it became — unless it was started
            // from a specific bill, which stays its link.
            if (source == PendingPaymentMatcher.SOURCE_SCAN_PAY && upi != "Unknown") {
                val paymentDao = database.pendingPaymentDao()
                paymentDao.findByUpiRef(upi)?.let { payment ->
                    if (payment.billId == null) {
                        paymentDao.linkBill(payment.id, expenseId.toString())
                    }
                }
            }

            // finishAndRemoveTask(), not finish(): this screen is only ever
            // opened from a notification tap, so it always sits in its own
            // task rather than on top of the app's normal back stack. Plain
            // finish() ends the Activity but can still leave that task's
            // entry sitting in Recents/App Switcher for a moment — long
            // enough that reopening it from there could resume this same
            // screen with the same pre-filled amount and let "Save Expense"
            // fire a second time, silently duplicating the expense.
            // finishAndRemoveTask() tears the whole task down immediately,
            // so there's nothing left in Recents to reopen.
            // Success moment, then close (skipped with "Remove animations").
            if (!reducedMotion(this@ExpenseConfirmationActivity)) {
                val friendsInSplit = selectedAmounts.keys.count { !it.equals("Me", ignoreCase = true) }
                savedFlash = if (friendsInSplit > 0) {
                    "Split with $friendsInSplit ${if (friendsInSplit == 1) "friend" else "friends"}"
                } else "Expense saved"
                kotlinx.coroutines.delay(SAVED_FLASH_MS)
            }
            finishAndRemoveTask()
        }

    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ExpenseConfirmationScreen(
    expenseAmount: Double,
    merchant: String,
    bank: String,
    date: String,
    friends: FriendDao,
    initialSelectedAmounts: Map<String, Double>,
    initialGroupId: Int? = null,
    isScanPay: Boolean = false,
    showQuickActions: Boolean = false,
    onSave: (Map<String, Double>, Int?) -> Unit,
    onJustMe: () -> Unit = {},
    onCancel: () -> Unit
) {

    var friendList by remember {
        mutableStateOf<List<Friend>>(emptyList())
    }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    /*
     * Selected people.
     */
    val selectedPeople =
        remember {
            mutableStateListOf<String>().apply {
                addAll(initialSelectedAmounts.keys)
            }
        }

    /*
     * Manually entered amounts.
     *
     * Only people that the user explicitly
     * edits are stored here.
     */
    val manualAmounts =
        remember {
            mutableStateMapOf<String, Double>()
        }
    var hasEditedSplit by remember { mutableStateOf(false) }

    // Guards against a double-fire of "Save Expense" — a fast double-tap, or
    // (belt-and-suspenders alongside finishAndRemoveTask()/excludeFromRecents
    // on this activity) this screen somehow being re-entered before the
    // first save has finished tearing the screen down — either of which
    // would otherwise insert the same expense twice.
    var saving by remember { mutableStateOf(false) }

    // Groups: picking one selects you + its members (equal split).
    val groupContext = androidx.compose.ui.platform.LocalContext.current
    var groups by remember { mutableStateOf<List<GroupWithMembers>>(emptyList()) }
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }

    /*
     * Load friends.
     */
    LaunchedEffect(Unit) {

        friendList =
            friends.getAllFriends()
        groups = loadGroups(AppDatabase.getDatabase(groupContext))
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onAddFriendWithPhone = { name, phone ->
                coroutineScope.launch {
                    if (
                        !name.equals("Me", ignoreCase = true) &&
                        friends.getFriendByName(name) == null
                    ) {
                        friends.insertFriend(Friend(name = name, phone = phone))
                    }

                    friendList = friends.getAllFriends()

                    /*
                     * Include the newly added friend in this split right
                     * away so their share amount is immediately editable,
                     * instead of leaving them unselected in the list below.
                     */
                    if (!selectedPeople.contains(name)) {
                        hasEditedSplit = true
                        selectedPeople.add(name)
                    }

                    showAddFriendDialog = false
                }
            }
        )
    }


    /*
     * "Me" is always available.
     */
    val people =
        listOf(
            Friend(
                id = -1,
                name = "Me"
            )
        ) + friendList
            .filterNot { it.name.trim().equals("Me", ignoreCase = true) }
            .distinctBy { it.name.trim().lowercase(Locale.ROOT) }


    /*
     * Calculate the current split.
     */
    fun calculateAmounts(): Map<String, Double> {

        if (selectedPeople.isEmpty()) {
            return emptyMap()
        }

        /*
         * Manual amounts that belong to
         * currently selected people.
         */
        val manual =
            manualAmounts.filterKeys {
                selectedPeople.contains(it)
            }

        if (!hasEditedSplit && manual.isEmpty() && initialSelectedAmounts.isNotEmpty()) {
            return selectedPeople.associateWith { person ->
                initialSelectedAmounts[person] ?: (expenseAmount / selectedPeople.size)
            }
        }

        /*
         * Nothing manually edited yet.
         *
         * Equal split.
         */
        if (manual.isEmpty()) {

            val equalAmount =
                expenseAmount / selectedPeople.size

            return selectedPeople.associateWith {
                equalAmount
            }
        }


        /*
         * Total explicitly assigned.
         */
        val manualTotal =
            manual.values.sum()


        /*
         * Remaining amount.
         */
        val remaining =
            expenseAmount - manualTotal


        /*
         * People whose amount is still automatic.
         */
        val automaticPeople =
            selectedPeople.filter {
                !manual.containsKey(it)
            }


        /*
         * If everyone has a manual amount,
         * just return those values.
         */
        if (automaticPeople.isEmpty()) {

            return selectedPeople.associateWith {
                manual[it] ?: 0.0
            }
        }


        /*
         * Divide remaining amount equally.
         */
        val automaticShare =
            if (remaining > 0) {
                remaining / automaticPeople.size
            } else {
                0.0
            }


        return selectedPeople.associateWith { person ->

            manual[person]
                ?: automaticShare
        }
    }


    val amounts =
        calculateAmounts()


    val totalAssigned =
        amounts.values.sum()


    val remaining =
        expenseAmount - totalAssigned


    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 16.dp,
                    vertical = 16.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close"
                    )
                }

                Text(
                    text = "Split Expense",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (isScanPay) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Scan & Pay",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }

                IconButton(onClick = { showAddFriendDialog = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add friend"
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = merchant,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = formatMoney(expenseAmount),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bank,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (groups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Split with a group",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                GroupChipsRow(
                    groups = groups,
                    selectedGroupId = selectedGroupId,
                    onPick = { picked ->
                        selectedGroupId = picked?.group?.id
                        if (picked != null) {
                            // You + the group's members (by saved friend name), equal split.
                            val known = friendList.map { it.name }
                            selectedPeople.clear()
                            selectedPeople.add("Me")
                            picked.members.forEach { m ->
                                val name = known.firstOrNull { it.equals(m, ignoreCase = true) } ?: return@forEach
                                if (!selectedPeople.contains(name)) selectedPeople.add(name)
                            }
                            manualAmounts.clear()
                            hasEditedSplit = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Who was this for?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap a person to select them. Adjust shares after selection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),

            contentPadding =
            PaddingValues(
                horizontal = 16.dp,
                vertical = 16.dp
            ),

            verticalArrangement =
            Arrangement.spacedBy(10.dp)
        ) {

            /*
             * Friends.
             */
            items(
                items = people,
                key = { it.id }
            ) { person ->

                val selected =
                    selectedPeople.contains(person.name)

                val personAmount =
                    amounts[person.name] ?: 0.0


                ParticipantTile(
                    person = person,
                    selected = selected,
                    amount = personAmount,

                    onSelected = {

                        if (selected) {

                            hasEditedSplit = true

                            selectedPeople.remove(
                                person.name
                            )

                            manualAmounts.remove(
                                person.name
                            )

                        } else {

                            hasEditedSplit = true

                            selectedPeople.add(
                                person.name
                            )
                        }
                    },

                    onAmountChanged = { newAmount ->

                        hasEditedSplit = true

                        /*
                         * If every other selected person already has a
                         * manual amount, marking this one manual too would
                         * leave nobody left to automatically absorb the
                         * remainder — the split would stop adding up, and
                         * editing this field would stop being reflected
                         * anywhere except itself. So when that's about to
                         * happen, clear everyone else's manual override
                         * first: they go back to auto-splitting whatever's
                         * left, same as if this were the very first edit.
                         */
                        val otherManual =
                            selectedPeople.filter {
                                it != person.name && manualAmounts.containsKey(it)
                            }

                        if (
                            selectedPeople.size > 1 &&
                            otherManual.size == selectedPeople.size - 1
                        ) {
                            otherManual.forEach { manualAmounts.remove(it) }
                        }

                        /*
                         * This amount becomes a manual
                         * override.
                         */
                        manualAmounts[
                            person.name
                        ] = newAmount
                    }
                )
            }


            /*
             * No friends message.
             */
            if (friendList.isEmpty()) {

                item {

                    Text(
                        text = "No friends added yet. You can still split this expense with yourself.",

                        style =
                        MaterialTheme.typography.bodySmall,

                        color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatMoney(totalAssigned),
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Remaining",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatMoney(remaining),
                    color =
                    if (abs(remaining) < 0.01) positiveColor()
                    else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (!saving && selectedPeople.isNotEmpty() && abs(remaining) < 0.01) {
                        saving = true
                        onSave(amounts, selectedGroupId)
                    }
                },
                enabled = !saving && selectedPeople.isNotEmpty() && abs(remaining) < 0.01,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("Save Expense")
            }

            if (showQuickActions) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!saving) {
                                saving = true
                                onJustMe()
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Just me")
                    }
                    TextButton(
                        onClick = onCancel,
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ignore")
                    }
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ParticipantTile(
    person: Friend,
    selected: Boolean,
    amount: Double,
    onSelected: () -> Unit,
    onAmountChanged: (Double) -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onSelected
            ),

        shape = RoundedCornerShape(24.dp),

        colors =
        CardDefaults.cardColors(
            containerColor =
            if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 8.dp else 3.dp
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),

            verticalAlignment =
            Alignment.CenterVertically
        ) {

            /*
             * Circular avatar.
             */
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),

                contentAlignment =
                Alignment.Center
            ) {

                Text(
                    text =
                    person.name
                        .firstOrNull()
                        ?.uppercase()
                        ?: "?",

                    fontWeight =
                    FontWeight.Bold,

                    color =
                    if (selected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }


            Spacer(
                modifier = Modifier.size(12.dp)
            )


            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = person.name,

                    style =
                    MaterialTheme.typography.titleMedium,

                    fontWeight =
                    FontWeight.Medium
                )


                if (!selected) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Tap to select",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }


                if (selected) {

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )


                    WiseAmountFieldFor(
                        amount = amount,
                        onAmountChange = { onAmountChanged(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }


            Spacer(
                modifier = Modifier.size(8.dp)
            )


            /*
             * Circular check. When selected it lines up with the amount
             * field (58dp tall → 13dp above its bottom edge) instead of the
             * whole card, which left it floating above the field.
             */
            Box(
                modifier = Modifier
                    .then(
                        if (selected) Modifier.align(Alignment.Bottom).padding(bottom = 13.dp)
                        else Modifier
                    )
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surface
                    ),

                // The outlined tile remains visible in dark mode before selection.

                contentAlignment =
                Alignment.Center
            ) {

                if (selected) {

                    Icon(
                        imageVector =
                        Icons.Filled.Check,

                        contentDescription =
                        "Selected",

                        tint =
                        MaterialTheme.colorScheme.onPrimary,

                        modifier =
                        Modifier.size(20.dp)
                    )
                } else {
                    // Icon, not a "+" glyph: font metrics pushed the text
                    // off-centre inside the circle.
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


private fun formatMoney(
    amount: Double
): String {

    return formatRupeeIndian(amount)
}


/**
 * How long the "saved" check stays on screen before the split screen closes.
 * The check itself takes ~970 ms to draw (ring 650 ms + tick 320 ms), so
 * this leaves ~730 ms of the finished tick on screen. Change this one
 * number to make it shorter/longer.
 */
private const val SAVED_FLASH_MS = 1_700L

/** Dimmed full-screen layer with the drawn check + pulse; blocks taps. */
@androidx.compose.runtime.Composable
private fun SavedOverlay(message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xCC0B1210))
            .pointerInputBlocker(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            SuccessBurst(fraction = 1f, celebrate = false, diameter = 150.dp)
            androidx.compose.material3.Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}

/** Swallows every touch so nothing underneath can be tapped during the flash. */
private fun Modifier.pointerInputBlocker(): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent().changes.forEach { it.consume() }
            }
        }
    }
