package com.example.splitwiseauto

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.splitwiseauto.ui.theme.positiveColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope


private data class ParticipantState(
    val friend: Friend,
    val selected: Boolean,
    val amount: Double
)

/** Local, Split-screen-only tokens — see the matching object in HomeTabContent.kt for why these aren't wired into Theme.kt. */
private object SplitTokens {
    @Composable fun accent(): Color = if (isSystemInDarkTheme()) Color(0xFF2FD39A) else Color(0xFF14A87C)
    @Composable fun warn(): Color = Color(0xFFF0A63C)
    @Composable fun onAccent(): Color = Color(0xFF06231A)
}


@Composable
fun ExpenseSplitScreen(
    expense: Expense,
    friends: List<Friend>,
    expenseParticipantDao: ExpenseParticipantDao,
    onSaved: () -> Unit
) {

    val scope = rememberCoroutineScope()

    /*
     * Selected friends.
     *
     * "Me" is always available as a participant.
     */
    val allPeople = remember(friends) {

        listOf(
            Friend(
                id = -1,
                name = "Me"
            )
        ) + friends
    }


    /*
     * Currently selected participants.
     */
    val selectedPeople = remember {

        mutableStateListOf<String>()
    }


    /*
     * Amount manually assigned to a person.
     */
    val customAmounts = remember {

        mutableStateOf(
            mutableMapOf<String, Double>()
        )
    }


    /*
     * Toggle participant.
     */
    fun togglePerson(name: String) {

        if (selectedPeople.contains(name)) {

            selectedPeople.remove(name)
            customAmounts.value =
                customAmounts.value.toMutableMap().apply {
                    remove(name)
                }

        } else {

            selectedPeople.add(name)
        }
    }


    /*
     * Calculate current amount for each participant.
     *
     * If nobody has manually entered an amount,
     * expense is divided equally.
     *
     * If one person manually changes their amount,
     * the remaining amount is divided equally
     * among everyone else.
     */
    fun calculateAmounts(): Map<String, Double> {

        if (selectedPeople.isEmpty()) {
            return emptyMap()
        }

        val total = expense.amount

        val manual = customAmounts.value

        val manuallyAssigned =
            manual.filterKeys {
                selectedPeople.contains(it)
            }


        /*
         * No manual amounts yet.
         *
         * Divide equally.
         */
        if (manuallyAssigned.isEmpty()) {

            val equalShare =
                total / selectedPeople.size

            return selectedPeople.associateWith {
                equalShare
            }
        }


        /*
         * Calculate amount already manually assigned.
         */
        val manualTotal =
            manuallyAssigned.values.sum()


        /*
         * Remaining amount after manual assignments.
         */
        val remaining =
            (total - manualTotal).coerceAtLeast(0.0)


        /*
         * People who don't have a manually
         * assigned amount.
         */
        val automaticPeople =
            selectedPeople.filter {
                !manuallyAssigned.containsKey(it)
            }


        /*
         * Everyone has a manual amount.
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
            remaining / automaticPeople.size


        return selectedPeople.associateWith { name ->

            if (manuallyAssigned.containsKey(name)) {
                manual[name] ?: 0.0
            } else {
                automaticShare
            }
        }
    }


    val amounts =
        calculateAmounts()


    val currentTotal =
        amounts.values.sum()


    val remaining =
        expense.amount - currentTotal


    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),

            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 16.dp
            ),

            verticalArrangement =
            Arrangement.spacedBy(10.dp)
        ) {

            item {

                Text(
                    text = "Split Expense",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )


                /*
                 * Expense information — teal gradient hero, matching the
                 * Home/Friends/Add-bill redesigns.
                 */
                val heroAccent = SplitTokens.accent()
                val heroOnAccent = SplitTokens.onAccent()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(heroAccent, heroAccent.copy(alpha = 0.78f)))
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = expense.merchant,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = heroOnAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text = "${expense.date} · ${expense.bank}",
                            style = MaterialTheme.typography.bodySmall,
                            color = heroOnAccent.copy(alpha = 0.72f)
                        )

                        Spacer(
                            modifier = Modifier.height(10.dp)
                        )

                        Text(
                            text = formatRupeeIndian(expense.amount),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = heroOnAccent
                        )
                    }
                }


                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                Text(
                    text = "Who was this for?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )
            }


            items(
                allPeople,
                key = { it.id }
            ) { person ->

                val selected =
                    selectedPeople.contains(person.name)

                val amount =
                    amounts[person.name] ?: 0.0


                ParticipantCard(
                    person = person,
                    selected = selected,
                    amount = amount,

                    onClick = {
                        togglePerson(person.name)
                    },

                    onAmountChange = { newAmount ->

                        if (!selected) {
                            return@ParticipantCard
                        }

                        customAmounts.value =
                            customAmounts.value
                                .toMutableMap()
                                .apply {
                                    put(
                                        person.name,
                                        newAmount
                                    )
                                }
                    }
                )
            }


            if (selectedPeople.isNotEmpty()) {

                item {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    val splitAddsUp = kotlin.math.abs(remaining) < 0.01
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                Arrangement.SpaceBetween
                            ) {

                                Text(
                                    text = "Total"
                                )

                                Text(
                                    text = formatRupeeIndian(currentTotal),
                                    fontWeight = FontWeight.Bold
                                )
                            }


                            Spacer(
                                modifier = Modifier.height(6.dp)
                            )


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                Arrangement.SpaceBetween
                            ) {

                                Text(
                                    text = "Remaining"
                                )

                                Text(
                                    text = formatRupeeIndian(remaining),
                                    fontWeight = FontWeight.Bold,
                                    color =
                                    if (splitAddsUp)
                                        positiveColor()
                                    else
                                        SplitTokens.warn()
                                )
                            }

                            if (splitAddsUp) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = positiveColor(),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Split adds up",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = positiveColor(),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        /*
         * Save button
         */
        Button(
            onClick = {

                if (selectedPeople.isEmpty()) {
                    return@Button
                }

                if (kotlin.math.abs(remaining) > 0.01) {
                    return@Button
                }


                scope.launch {

                    val participants =
                        amounts.map { (name, amount) ->

                            ExpenseParticipant(
                                expenseId = expense.id,
                                personName = name,
                                shareAmount = amount
                            )
                        }

                    expenseParticipantDao
                        .insertParticipants(participants)

                    onSaved()
                }
            },

            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp),

            shape = RoundedCornerShape(16.dp),

            colors = ButtonDefaults.buttonColors(
                containerColor = SplitTokens.accent(),
                contentColor = SplitTokens.onAccent()
            ),

            enabled =
            selectedPeople.isNotEmpty() &&
                    kotlin.math.abs(remaining) < 0.01
        ) {

            Text("Save Split", fontWeight = FontWeight.Bold)
        }
    }
}


@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (enabled) SplitTokens.accent().copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) SplitTokens.accent() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantCard(
    person: Friend,
    selected: Boolean,
    amount: Double,
    onClick: () -> Unit,
    onAmountChange: (Double) -> Unit
) {

    val accent = SplitTokens.accent()

    Card(
        modifier = Modifier.fillMaxWidth(),

        shape = RoundedCornerShape(16.dp),

        colors = CardDefaults.cardColors(
            containerColor =
            if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),

        // Accent ring around the whole row when selected — on top of the
        // existing container-color swap, not instead of it.
        border = if (selected) BorderStroke(1.5.dp, accent) else null,

        onClick = onClick
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),

            verticalAlignment =
            Alignment.CenterVertically
        ) {

            /*
             * Circular avatar
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

                    fontWeight = FontWeight.Bold,

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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )


                if (selected) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    // −/share/+ stepper flanking the exact-entry field — the
                    // buttons nudge by ₹1 for quick adjustments, while the
                    // field itself still takes any precise amount directly
                    // (a pure ±1 stepper alone can't land on paise-level
                    // splits, so precise typing is kept rather than dropped).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepperButton(
                            icon = Icons.Filled.Remove,
                            contentDescription = "Decrease amount",
                            enabled = amount > 0.0,
                            onClick = { onAmountChange((amount - 1.0).coerceAtLeast(0.0)) }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        WiseAmountFieldFor(
                            amount = amount,
                            onAmountChange = { onAmountChange(it) },
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(min = 0.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        StepperButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "Increase amount",
                            enabled = true,
                            onClick = { onAmountChange(amount + 1.0) }
                        )
                    }
                }
            }


            Spacer(
                modifier = Modifier.size(8.dp)
            )


            /*
             * Circular selection indicator
             */
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outlineVariant
                    ),

                contentAlignment =
                Alignment.Center
            ) {

                if (selected) {

                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",

                        tint =
                        MaterialTheme.colorScheme.onPrimary,

                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
