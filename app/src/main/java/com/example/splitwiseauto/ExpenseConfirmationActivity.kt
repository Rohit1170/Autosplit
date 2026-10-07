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
    private var source: String? = null
    private var paymentAttemptId: Int? = null

    override fun onStop() {
        super.onStop()
        // Keep the home-screen widget in step with anything changed here.
        SplitleeWidget.requestUpdate(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                latitude = original?.latitude
                longitude = original?.longitude

                val savedAmounts = database.expenseParticipantDao()
                    .getParticipantsForExpense(editingExpenseId!!)
                    .associate { it.personName to it.shareAmount }
                showExpenseConfirmation(savedAmounts)
            }
        } else {
            // New expense: tag it with whichever trip is currently active
            // ("Main" mode == null, so nothing changes there).
            tripId = ActiveTripStore.getActiveTripId(this)
            showExpenseConfirmation(emptyMap())
        }
    }

    private fun showExpenseConfirmation(initialSelectedAmounts: Map<String, Double>) {
        setContent {

            SplitWiseAutoTheme {

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
                        isScanPay = source == "scan_pay",
                        onSave = { selectedAmounts ->

                            saveExpense(
                                selectedAmounts
                            )
                        },
                        onCancel = {
                            pendingNotificationId?.let { id ->
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
            }
        }
    }

    private fun saveExpense(
        selectedAmounts: Map<String, Double>
    ) {

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
                        paymentAttemptId = paymentAttemptId
                    )
                ).toInt()

            if (editingExpenseId != null) {
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
                        paymentAttemptId = paymentAttemptId
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
    isScanPay: Boolean = false,
    onSave: (Map<String, Double>) -> Unit,
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

    /*
     * Load friends.
     */
    LaunchedEffect(Unit) {

        friendList =
            friends.getAllFriends()
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            onDismiss = { showAddFriendDialog = false },
            onAddFriend = { name ->
                coroutineScope.launch {
                    if (
                        !name.equals("Me", ignoreCase = true) &&
                        friends.getFriendByName(name) == null
                    ) {
                        friends.insertFriend(Friend(name = name))
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
                        onSave(amounts)
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


                    OutlinedTextField(
                        value =
                        if (amount == 0.0)
                            ""
                        else
                            String.format(
                                Locale.US,
                                "%.2f",
                                amount
                            ),

                        onValueChange = { value ->

                            val cleanValue =
                                value.replace(",", "")

                            val parsed =
                                cleanValue.toDoubleOrNull()

                            if (parsed != null) {

                                onAmountChanged(
                                    parsed
                                )
                            }

                            if (cleanValue.isBlank()) {

                                onAmountChanged(
                                    0.0
                                )
                            }
                        },

                        modifier =
                        Modifier.fillMaxWidth(),

                        label = {
                            Text("Share amount")
                        },

                        singleLine = true,

                        keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                            KeyboardType.Decimal
                        )
                    )
                }
            }


            Spacer(
                modifier = Modifier.size(8.dp)
            )


            /*
             * Circular check.
             */
            Box(
                modifier = Modifier
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
                    Text(
                        text = "+",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
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
