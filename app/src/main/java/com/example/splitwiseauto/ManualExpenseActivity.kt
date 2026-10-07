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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
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

    override fun onStop() {
        super.onStop()
        // Keep the home-screen widget in step with anything changed here.
        SplitleeWidget.requestUpdate(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingExpenseId = intent.getIntExtra("expenseId", -1).takeIf { it > 0 }
        editingBank = intent.getStringExtra("bank")
        editingAccountIdentifier = intent.getStringExtra("accountIdentifier")
        val initialTimestampMillis = intent.getLongExtra("timestampMillis", -1L).takeIf { it > 0 }

        setContent {
            SplitWiseAutoTheme {
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
                        isEditing = editingExpenseId != null,
                        onCancel = ::finish,
                        onContinue = { amount, merchant, date, category, payer, timestampMillis, pickedByUser ->
                            val original = intent.getStringExtra("category")
                            // Learn only from a real choice: a tap on a category
                            // for a new expense, or an actual change when editing.
                            val learn = pickedByUser &&
                                (editingExpenseId == null || !category.equals(original, ignoreCase = true))
                            openSplitConfirmation(amount, merchant, date, category, payer, timestampMillis, learn)
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
        learnCategory: Boolean
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
                putExtra("bank", editingBank ?: "Cash")
                putExtra("upi", "Manual")
                putExtra("balance", "Unknown")
                putExtra("category", category)
                putExtra(ExpenseConfirmationActivity.EXTRA_LEARN_CATEGORY, learnCategory)
                putExtra("payer", payer)
                putExtra("timestampMillis", timestampMillis)
                editingAccountIdentifier?.let { putExtra("accountIdentifier", it) }
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
    isEditing: Boolean,
    onCancel: () -> Unit,
    onContinue: (Double, String, String, String, String, Long, Boolean) -> Unit
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
    var friends by remember { mutableStateOf<List<Friend>>(emptyList()) }

    LaunchedEffect(Unit) {
        friends = AppDatabase.getDatabase(context).friendDao().getAllFriends()
            .filterNot { it.name.equals("Me", ignoreCase = true) }
    }

    if (showPayerPicker) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPayerPicker = false },
            title = { Text("Who paid?") },
            text = {
                Column {
                    (listOf("Me") + friends.map { it.name }).forEach { person ->
                        TextButton(
                            onClick = {
                                payer = person
                                showPayerPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(person) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPayerPicker = false }) { Text("Cancel") } }
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

                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it; validationMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Bill amount") },
                            leadingIcon = {
                                Text(
                                    "₹",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = expenseFieldColors()
                        )

                        OutlinedTextField(
                            value = merchant,
                            onValueChange = {
                                merchant = it
                                if (!categoryChosenManually) selectedCategory = categorize(it).name
                                validationMessage = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Description") },
                            placeholder = { Text("e.g. Dinner at CP Corner") },
                            singleLine = true,
                            colors = expenseFieldColors()
                        )

                        OutlinedTextField(
                            value = date,
                            onValueChange = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val calendar = Calendar.getInstance()
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val pickedCalendar = Calendar.getInstance().apply {
                                                set(year, month, day, 12, 0, 0)
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
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                            label = { Text("Date") },
                            supportingText = { Text("Tap to choose a date") },
                            singleLine = true,
                            readOnly = true,
                            colors = expenseFieldColors()
                        )

                        Text("Category", style = MaterialTheme.typography.titleSmall)
                        // Rows of 3 (7 categories now: Groceries and
                        // Entertainment were added); empty slots keep widths even.
                        expenseCategories.chunked(3).forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCategories.forEach { category ->
                                    CategoryOption(
                                        category = category,
                                        selected = selectedCategory == category.name,
                                        onClick = {
                                            selectedCategory = category.name
                                            categoryChosenManually = true
                                            categoryTappedByUser = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
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
                            categoryTappedByUser
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
    @Composable fun accent(): androidx.compose.ui.graphics.Color =
        if (androidx.compose.foundation.isSystemInDarkTheme()) androidx.compose.ui.graphics.Color(0xFF2FD39A)
        else androidx.compose.ui.graphics.Color(0xFF14A87C)
    // The teal is bright enough in both modes that dark text reads better than white.
    @Composable fun onAccent(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF06231A)
}

@Composable
private fun CategoryOption(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val selectedContainer = if (darkTheme) {
        category.fgColor.copy(alpha = 0.24f)
    } else {
        category.bgColor
    }

    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) selectedContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, category.fgColor)
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = if (selected) category.fgColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = category.name,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) category.fgColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
