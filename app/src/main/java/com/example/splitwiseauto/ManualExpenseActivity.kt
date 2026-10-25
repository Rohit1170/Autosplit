package com.example.splitwiseauto

import android.content.Intent
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import com.example.splitwiseauto.ui.theme.SplitWiseAutoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class ManualExpenseActivity : ComponentActivity() {

    private var editingExpenseId: Int? = null
    private var editingBank: String? = null
    private var editingAccountIdentifier: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefresh.requestHighest(this) // 90/120 Hz where the screen supports it
        CustomCategories.ensureLoaded(this)
        editingExpenseId = intent.getIntExtra("expenseId", -1).takeIf { it > 0 }
        editingBank = intent.getStringExtra("bank")
        editingAccountIdentifier = intent.getStringExtra("accountIdentifier")
        val initialTimestampMillis = intent.getLongExtra("timestampMillis", -1L).takeIf { it > 0 }

        setContent {
            SplitleeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ManualExpenseScreen(
                        initialAmount = intent.getStringExtra("amount").orEmpty(),
                        initialMerchant = intent.getStringExtra("merchant").orEmpty(),
                        initialDate = intent.getStringExtra("date"),
                        initialCategory = intent.getStringExtra("category"),
                        initialPayer = intent.getStringExtra("payer") ?: "Me",
                        initialTimestampMillis = initialTimestampMillis,
                        initialAccount = SpendAccount.from(editingBank, editingAccountIdentifier),
                        isEditing = editingExpenseId != null,
                        onCancel = ::finish,
                        onContinue = { amount, merchant, date, category, payer, timestampMillis, pickedByUser, account ->
                            val original = intent.getStringExtra("category")
                            // Learn only from a real choice: a tap on a category
                            // for a new expense, or an actual change when editing.
                            val learn = pickedByUser &&
                                (editingExpenseId == null || !category.equals(original, ignoreCase = true))
                            openSplitConfirmation(amount, merchant, date, category, payer, timestampMillis, learn, account)
                        }
                    )
                }
            }
        }
    }

    private fun openSplitConfirmation(
        amount: Double,
        merchant: String,
        date: String,
        category: String,
        payer: String,
        timestampMillis: Long,
        learnCategory: Boolean,
        account: SpendAccount
    ) {
        lifecycleScope.launch {
            // Only stamp a fresh location for a brand-new expense. Editing an
            // existing one (e.g. fixing the amount after settling half in
            // cash, once you're back home) must never move its pin to
            // wherever you happen to be right now — ExpenseConfirmationActivity
            // re-reads the original expense's saved location and keeps it
            // whenever expenseId is set, so no location extra is sent here.
            val location = if (editingExpenseId == null) {
                LocationHelper.getCurrentLocation(this@ManualExpenseActivity)
            } else {
                null
            }
            startActivity(
            Intent(this@ManualExpenseActivity, ExpenseConfirmationActivity::class.java).apply {
                putExtra("amount", amount.toString())
                putExtra("merchant", merchant)
                putExtra("date", date)
                // Which card/account (or cash) it was paid from — the Home
                // card with the same bank + account number counts it.
                putExtra("bank", account.bank)
                putExtra("upi", "Manual")
                putExtra("balance", "Unknown")
                putExtra("category", category)
                putExtra(ExpenseConfirmationActivity.EXTRA_LEARN_CATEGORY, learnCategory)
                putExtra("payer", payer)
                putExtra("timestampMillis", timestampMillis)
                account.accountIdentifier?.let { putExtra("accountIdentifier", it) }
                editingExpenseId?.let { putExtra("expenseId", it) }
                location?.let {
                    putExtra("latitude", it.latitude)
                    putExtra("longitude", it.longitude)
                }
            }
        )
            finish()
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ManualExpenseScreen(
    initialAmount: String,
    initialMerchant: String,
    initialDate: String?,
    initialCategory: String?,
    initialPayer: String,
    initialTimestampMillis: Long?,
    initialAccount: SpendAccount,
    isEditing: Boolean,
    onCancel: () -> Unit,
    onContinue: (Double, String, String, String, String, Long, Boolean, SpendAccount) -> Unit
) {
    var amountText by remember { mutableStateOf(initialAmount) }
    var merchant by remember { mutableStateOf(initialMerchant) }
    var date by remember {
        mutableStateOf(
            initialDate ?: SimpleDateFormat("dd-MMM-yy", Locale.US).format(Date())
        )
    }
    var dateMillis by remember {
        mutableStateOf(initialTimestampMillis ?: System.currentTimeMillis())
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember {
        mutableStateOf(initialCategory ?: categorize(initialMerchant).name)
    }
    var categoryChosenManually by remember { mutableStateOf(initialCategory != null) }
    // True only after the user actually taps a category (drives learning).
    var categoryTappedByUser by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var payer by remember { mutableStateOf(initialPayer) }
    var showPayerPicker by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(initialAccount) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(listOf(SpendAccount.CASH)) }
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    // Bumped whenever a custom category is added/removed so the picker re-reads it.
    var categoriesVersion by remember { mutableStateOf(0) }
    var showNewCategory by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (showNewCategory) {
        NewCategoryDialog(
            onDismiss = { showNewCategory = false },
            onCreate = { name, iconKey, color ->
                CustomCategories.add(context, name, iconKey, color)?.let { saved ->
                    selectedCategory = saved
                    categoryChosenManually = true
                    categoryTappedByUser = true
                }
                categoriesVersion++
                showNewCategory = false
            }
        )
    }

    categoryToDelete?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete \"$name\"?") },
            text = { Text("Expenses in this category will move to Other. Nothing is removed from your totals.") },
            confirmButton = {
                TextButton(onClick = {
                    categoryToDelete = null
                    if (selectedCategory.equals(name, ignoreCase = true)) selectedCategory = OTHER.name
                    scope.launch {
                        CustomCategories.delete(context, name)
                        categoriesVersion++
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") } }
        )
    }

    LaunchedEffect(Unit) {
        friends = AppDatabase.getDatabase(context).friendDao().getAllFriends()
            .filterNot { it.name.equals("Me", ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        accounts = SpendAccount.knownAccounts(
            AppDatabase.getDatabase(context).expenseDao().getAllExpenses(),
            include = initialAccount
        )
    }
    if (showAccountPicker) {
        AccountPickerSheet(
            accounts = accounts,
            current = account,
            onPick = {
                account = it
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    if (showPayerPicker) {
        PayerPickerSheet(
            people = listOf("Me") + friends.map { it.name }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() },
            current = payer,
            onPick = { person ->
                payer = person
                showPayerPicker = false
            },
            onDismiss = { showPayerPicker = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val heroAccent = AddBillTokens.accent()
                val heroOnAccent = AddBillTokens.onAccent()
                Card(
                    // No .clickable here — this used to (accidentally, it looks
                    // like) open the payer picker when you tapped the hero text.
                    // That action now lives where it visually belongs: the
                    // "Paid by" row below, which is clickable itself now.
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(heroAccent, heroAccent.copy(alpha = 0.78f))),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(
                            if (isEditing) "EDIT EXPENSE" else "NEW EXPENSE",
                            style = MaterialTheme.typography.labelLarge,
                            color = heroOnAccent.copy(alpha = 0.72f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isEditing) "Update this bill" else "Add a bill",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = heroOnAccent
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter the bill details, then choose who shared it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroOnAccent.copy(alpha = 0.84f)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Expense details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        WiseAmountField(
                            value = amountText,
                            onValueChange = { amountText = it; validationMessage = null },
                            label = "Bill amount"
                        )

                        ModernTextField(
                            value = merchant,
                            onValueChange = {
                                merchant = it
                                if (!categoryChosenManually) selectedCategory = categorize(it).name
                                validationMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = "Description",
                            placeholder = "e.g. Dinner at CP Corner"
                        )

                        // Date + time of the payment. A new expense starts at
                        // "now"; picking a date keeps the chosen time of day.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModernTextField(
                                value = date,
                                onValueChange = {},
                                label = "Date",
                                readOnly = true,
                                modifier = Modifier.weight(1.3f),
                                onClick = {
                                        val current = Calendar.getInstance().apply { timeInMillis = dateMillis }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val pickedCalendar = Calendar.getInstance().apply {
                                                    timeInMillis = dateMillis
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, day)
                                                }
                                                date = String.format(
                                                    Locale.US,
                                                    "%02d-%s-%02d",
                                                    day,
                                                    SimpleDateFormat("MMM", Locale.US)
                                                        .format(pickedCalendar.time),
                                                    year % 100
                                                )
                                                dateMillis = pickedCalendar.timeInMillis
                                                validationMessage = null
                                            },
                                            current.get(Calendar.YEAR),
                                            current.get(Calendar.MONTH),
                                            current.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                }
                            )
                            ModernTextField(
                                value = expenseTimeLabel(dateMillis) ?: "Add time",
                                onValueChange = {},
                                label = "Time",
                                readOnly = true,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                        val current = Calendar.getInstance().apply { timeInMillis = dateMillis }
                                        android.app.TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                dateMillis = Calendar.getInstance().apply {
                                                    timeInMillis = dateMillis
                                                    set(Calendar.HOUR_OF_DAY, hour)
                                                    set(Calendar.MINUTE, minute)
                                                    // Second = 1 marks a REAL chosen time
                                                    // (exactly 12:00:00 = old "date only").
                                                    set(Calendar.SECOND, 1)
                                                    set(Calendar.MILLISECOND, 0)
                                                }.timeInMillis
                                            },
                                            current.get(Calendar.HOUR_OF_DAY),
                                            current.get(Calendar.MINUTE),
                                            android.text.format.DateFormat.is24HourFormat(context)
                                        ).show()
                                }
                            )
                        }

                        Text("Category", style = MaterialTheme.typography.titleSmall)
                        // Built-ins + the user's own, then a "+ New" tile.
                        // Long-press a custom category to delete it.
                        val pickerCategories = remember(categoriesVersion) { allCategories() }
                        val pickerItems: List<Category?> = pickerCategories + listOf<Category?>(null)
                        pickerItems.chunked(3).forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCategories.forEach { category ->
                                    if (category == null) {
                                        NewCategoryTile(
                                            onClick = { showNewCategory = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        val isCustom = CustomCategories.isCustom(category.name)
                                        CategoryOption(
                                            category = category,
                                            selected = selectedCategory.equals(category.name, ignoreCase = true),
                                            onClick = {
                                                selectedCategory = category.name
                                                categoryChosenManually = true
                                                categoryTappedByUser = true
                                            },
                                            onLongClick = if (isCustom) {
                                                { categoryToDelete = category.name }
                                            } else null,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                repeat(3 - rowCategories.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPayerPicker = true },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = payer.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Paid by", style = MaterialTheme.typography.labelMedium)
                            Text(
                                payer,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Change who paid"
                        )
                    }
                }

                // Paid from: which card/account or cash. Only matters when
                // you paid (a friend's payment isn't from your accounts).
                if (payer.equals("Me", ignoreCase = true)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAccountPicker = true },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccountChipSwatch(account)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Paid from", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    account.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                account.caption,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "Change account"
                            )
                        }
                    }
                }

                validationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        val parsedAmount = amountText.replace(",", "").toDoubleOrNull()
                        val cleanMerchant = merchant.trim()
                        val cleanDate = date.trim()
                        validationMessage = when {
                            parsedAmount == null || parsedAmount <= 0.0 -> "Enter an amount greater than ₹0."
                            cleanMerchant.isBlank() -> "Enter where the bill was spent."
                            cleanDate.isBlank() -> "Enter a date."
                            else -> null
                        }
                        if (validationMessage == null) onContinue(
                            parsedAmount!!,
                            cleanMerchant,
                            cleanDate,
                            selectedCategory,
                            payer,
                            dateMillis,
                            categoryTappedByUser,
                            account
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        if (isEditing) "Update split" else "Continue to split",
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun expenseFieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

/** Local, Add-bill-screen-only tokens — see the matching object in HomeTabContent.kt for why these aren't wired into Theme.kt. */
private object AddBillTokens {
    @Composable fun accent(): androidx.compose.ui.graphics.Color = Brand.accent()
    // The teal is bright enough in both modes that dark text reads better than white.
    @Composable fun onAccent(): androidx.compose.ui.graphics.Color = Brand.onAccent()
}

/**
 * Modern category tile: icon in a tinted circle chip, label below. Selected
 * = soft wash of the category colour, glowing edge and a slight lift;
 * unselected = quiet glass. Springy press feedback; long-press for custom
 * categories (delete).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CategoryOption(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val lift by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f)
    val chipAlpha = 0.16f + 0.14f * lift
    Column(
        modifier = modifier
            .pressScale(source)
            .modernCard(
                base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                radius = 18.dp,
                tint = if (selected) category.fgColor else null,
                tintAlpha = 0.22f,
                borderColor = if (selected) category.fgColor else null
            )
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick?.let { hold -> { Haptics.hold(view); hold() } }
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(category.fgColor.copy(alpha = chipAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.fgColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.name,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewCategoryTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val accent = Brand.accent()
    Column(
        modifier = modifier
            .pressScale(source)
            .modernCard(base = Color.Transparent, radius = 18.dp, borderColor = accent.copy(alpha = 0.45f))
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(accent.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New category", tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("New", style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, iconKey: String, color: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf(CustomCategories.ICONS.keys.first()) }
    var color by remember { mutableStateOf(CustomCategories.COLORS.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val accent = Color(color)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModernTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= CustomCategories.MAX_NAME_LENGTH) name = it
                        error = null
                    },
                    label = "Name",
                    placeholder = "e.g. Pets, Gym, Gifts",
                    isError = error != null,
                    supportingText = error,
                    autoFocus = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                CustomCategories.ICONS.entries.toList().chunked(5).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        rowIcons.forEach { (key, vector) ->
                            val picked = key == iconKey
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (picked) accent.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { iconKey = key },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = key,
                                    tint = if (picked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CustomCategories.COLORS.forEach { c ->
                        val picked = c == color
                        Box(
                            modifier = Modifier
                                .size(if (picked) 30.dp else 26.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = CustomCategories.validate(name)
                if (problem != null) error = problem
                else onCreate(name, iconKey, color)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


/** Soft per-person tint so friends are easy to tell apart at a glance. */
private val PayerAvatarTints = listOf(
    Color(0xFF2FD39A), // mint
    Color(0xFF5AB8FF), // sky
    Color(0xFFA78BFA), // violet
    Color(0xFFF5B754), // amber
    Color(0xFFFF8A7A), // coral
    Color(0xFF3CC7C0)  // teal
)

private fun payerTint(name: String): Color =
    if (name.equals("Me", ignoreCase = true)) PayerAvatarTints[0]
    else PayerAvatarTints[1 + Math.floorMod(name.lowercase().hashCode(), PayerAvatarTints.size - 1)]

/**
 * "Who paid?" picker: glass bottom sheet with avatar rows, the current payer
 * highlighted with a mint check. Replaces the old centred text-button list.
 */
@Composable
private fun PayerPickerSheet(
    people: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val accent = Brand.accent()
    AppSheet(
        title = "Who paid?",
        subtitle = "Pick who paid for this expense",
        onDismiss = onDismiss,
        glass = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            people.forEach { person ->
                val isMe = person.equals("Me", ignoreCase = true)
                val selected = person.equals(current, ignoreCase = true)
                val tint = payerTint(person)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (selected) accent.copy(alpha = 0.14f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .then(
                            if (selected) Modifier.border(
                                1.dp,
                                accent.copy(alpha = 0.55f),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        )
                        .clickable {
                            Haptics.tap(view)
                            onPick(person)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isMe) "Y" else person.firstOrNull()?.uppercase() ?: "?",
                            color = tint,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMe) "You" else person.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else Color.Transparent)
                            .then(
                                if (selected) Modifier
                                else Modifier.border(
                                    1.5.dp,
                                    Color.White.copy(alpha = 0.18f),
                                    CircleShape
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF0B1210),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Where a manual expense was paid from: cash, the Slice app (UPI, no account
 * number) or a bank card/account seen in your SMS. Stored as the expense's
 * bank + accountIdentifier, the same keys Home groups its cards by.
 */
data class SpendAccount(val bank: String, val accountIdentifier: String?) {
    val isCash: Boolean get() = bank.equals("Cash", ignoreCase = true) && accountIdentifier == null
    val isSlice: Boolean get() = !isCash && accountIdentifier == null

    val label: String get() = when {
        isCash -> "Cash"
        isSlice -> "Slice"
        else -> accountIdentifier!!
    }
    val caption: String get() = when {
        isCash -> "In hand"
        isSlice -> "UPI"
        else -> bank
    }

    companion object {
        val CASH = SpendAccount("Cash", null)
        val SLICE = SpendAccount("Slice", null)

        fun from(bank: String?, accountIdentifier: String?): SpendAccount = when {
            bank == null -> CASH
            accountIdentifier != null -> SpendAccount(bank, accountIdentifier)
            bank.equals("Cash", ignoreCase = true) -> CASH
            // Keep the original bank text so editing doesn't rewrite it.
            else -> SpendAccount(bank, null)
        }

        /** Cash, then cards/accounts most recently used first, then Slice if seen. */
        fun knownAccounts(expenses: List<Expense>, include: SpendAccount): List<SpendAccount> {
            val cards = expenses
                .filter { it.accountIdentifier != null }
                .map { SpendAccount(it.bank, it.accountIdentifier) }
                .distinctBy { "${it.bank.lowercase()}|${it.accountIdentifier}" }
            val sliceSeen = expenses.any { it.accountIdentifier == null && !it.bank.equals("Cash", ignoreCase = true) }
            val list = buildList {
                add(CASH)
                addAll(cards)
                if (sliceSeen) add(SLICE)
            }
            return if (list.none { it.sameAs(include) }) list + include else list
        }
    }

    fun sameAs(other: SpendAccount): Boolean =
        (isCash && other.isCash) || (isSlice && other.isSlice) ||
            (bank.equals(other.bank, ignoreCase = true) && accountIdentifier == other.accountIdentifier)
}

/** Tiny card-shaped swatch: purple for Slice, mint for cash, slate for cards. */
@Composable
private fun AccountChipSwatch(account: SpendAccount) {
    val colors = when {
        account.isCash -> listOf(Color(0xFF178A62), Color(0xFF3FC28D))
        account.isSlice -> listOf(Color(0xFF5A0FB4), Color(0xFFC63AF0))
        else -> listOf(Color(0xFF2B3A52), Color(0xFF4A6A8F))
    }
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                account.isCash -> "\u20B9"
                account.isSlice -> "S"
                else -> account.bank.filter { it.isLetter() }.take(2).uppercase()
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** "Paid from" picker: same glass sheet style as "Who paid?". */
@Composable
private fun AccountPickerSheet(
    accounts: List<SpendAccount>,
    current: SpendAccount,
    onPick: (SpendAccount) -> Unit,
    onDismiss: () -> Unit
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val accent = Brand.accent()
    AppSheet(
        title = "Paid from",
        subtitle = "Counts towards that card on Home",
        onDismiss = onDismiss,
        glass = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            accounts.forEach { acc ->
                val selected = acc.sameAs(current)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                        .then(
                            if (selected) Modifier.border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            else Modifier
                        )
                        .clickable {
                            Haptics.tap(view)
                            onPick(acc)
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccountChipSwatch(acc)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            acc.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            acc.caption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (selected) accent else Color.Transparent)
                            .then(
                                if (selected) Modifier
                                else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color(0xFF0B1210),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
