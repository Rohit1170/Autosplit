@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.splitwiseauto

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs


/** Which subset of friends the filter chips are narrowing to. */
private enum class FriendsFilter { ALL, OWES_YOU, YOU_OWE, SETTLED }

@Composable
fun FriendsTabContent(
    friends: List<Friend>,
    balances: Map<Int, FriendBalance>,
    mySpent: Double,
    totalOwedToYou: Double,
    onFriendLongPress: (Friend) -> Unit,
    onFriendDoubleTap: (Friend) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayFriends = friends.filterNot {
        it.name.trim().equals("Me", ignoreCase = true)
    }

    // "You owe" isn't exposed as its own StateFlow from FriendViewModel — it's
    // the same balances map HomeScreen already collects (totalOwedToYou is
    // just the mirror aggregation on the other side), so deriving it here
    // needs no new ViewModel state or DAO query.
    val youOwe = balances.values.sumOf { (-it.netAmount).coerceAtLeast(0.0) }

    val settled = displayFriends.filter { abs((balances[it.id] ?: FriendBalance()).netAmount) <= 0.01 }
    val owesYou = displayFriends.filter { (balances[it.id] ?: FriendBalance()).netAmount > 0.01 }
    val youOweList = displayFriends.filter { (balances[it.id] ?: FriendBalance()).netAmount < -0.01 }

    var selectedFilter by remember { mutableStateOf(FriendsFilter.ALL) }
    var settledExpanded by remember { mutableStateOf(false) }

    val mainList = when (selectedFilter) {
        FriendsFilter.ALL -> owesYou + youOweList
        FriendsFilter.OWES_YOU -> owesYou
        FriendsFilter.YOU_OWE -> youOweList
        FriendsFilter.SETTLED -> emptyList()
    }
    // The collapsed "N settled" row only makes sense alongside the
    // everyone-at-once view; the other filters are already narrowed to a
    // single bucket, and picking "Settled" itself lists them directly below
    // instead of behind another collapse.
    val showSettledCollapseRow = settled.isNotEmpty() && selectedFilter == FriendsFilter.ALL

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),

        contentPadding = PaddingValues(
            top = TAB_CONTENT_TOP_GAP,
            bottom = 24.dp
        ),

        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        item {
            Text(
                text = "Friends",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            FriendsBalanceSummary(owedToYou = totalOwedToYou, youOwe = youOwe, mySpent = mySpent)
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FriendsFilterChip("All", selectedFilter == FriendsFilter.ALL) { selectedFilter = FriendsFilter.ALL }
                FriendsFilterChip("Owes you", selectedFilter == FriendsFilter.OWES_YOU) { selectedFilter = FriendsFilter.OWES_YOU }
                FriendsFilterChip("You owe", selectedFilter == FriendsFilter.YOU_OWE) { selectedFilter = FriendsFilter.YOU_OWE }
                FriendsFilterChip("Settled", selectedFilter == FriendsFilter.SETTLED) { selectedFilter = FriendsFilter.SETTLED }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (displayFriends.isEmpty()) {

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No friends yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

        } else if (selectedFilter == FriendsFilter.SETTLED) {

            if (settled.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No one's settled up yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FriendsTokens.mut()
                        )
                    }
                }
            } else {
                items(settled, key = { it.id }) { friend ->
                    FriendExpenseCard(
                        friend = friend,
                        balance = balances[friend.id] ?: FriendBalance(),
                        onLongPress = { onFriendLongPress(friend) },
                        onSettle = { onFriendDoubleTap(friend) },
                        onRemind = null
                    )
                }
            }

        } else {

            if (mainList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nothing here right now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FriendsTokens.mut()
                        )
                    }
                }
            } else {
                items(mainList, key = { it.id }) { friend ->
                    val balance = balances[friend.id] ?: FriendBalance()
                    FriendExpenseCard(
                        friend = friend,
                        balance = balance,
                        onLongPress = { onFriendLongPress(friend) },
                        onSettle = { onFriendDoubleTap(friend) },
                        onRemind = if (balance.netAmount > 0.01) {
                            { sendReminder(context, friend, balance.netAmount) }
                        } else null
                    )
                }
            }

            if (showSettledCollapseRow) {
                item {
                    SettledFriendsRow(
                        count = settled.size,
                        expanded = settledExpanded,
                        onToggle = { settledExpanded = !settledExpanded }
                    )
                }
                if (settledExpanded) {
                    items(settled, key = { "settled-${it.id}" }) { friend ->
                        FriendExpenseCard(
                            friend = friend,
                            balance = balances[friend.id] ?: FriendBalance(),
                            onLongPress = { onFriendLongPress(friend) },
                            onSettle = { onFriendDoubleTap(friend) },
                            onRemind = null,
                            muted = true
                        )
                    }
                }
            }
        }
    }
}

/** Local, Friends-screen-only tokens — see the matching object in HomeTabContent.kt for why these aren't wired into Theme.kt. */
private object FriendsTokens {
    @Composable fun accent(): Color = if (isSystemInDarkTheme()) Color(0xFF2FD39A) else Color(0xFF14A87C)
    @Composable fun warn(): Color = Color(0xFFF0A63C)
    @Composable fun card2(): Color = if (isSystemInDarkTheme()) Color(0xFF1A201E) else Color(0xFFE8EFEA)
    @Composable fun line(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color(0xFF12201A).copy(alpha = 0.10f)
    @Composable fun mut(): Color = if (isSystemInDarkTheme()) Color(0xFF8E9A95) else Color(0xFF5F6E66)
}

@Composable
private fun FriendsFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = FriendsTokens.accent().copy(alpha = 0.16f),
            selectedLabelColor = FriendsTokens.accent()
        )
    )
}

/**
 * Summary card — same "Friends owe you" / "You owe" pair as before, now
 * with a third row underneath for the user's own part: their total spend
 * after splitting (their share of every expense, whether or not it was
 * shared with anyone — same figure Home's personal total already tracks,
 * just surfaced here too).
 */
@Composable
private fun FriendsBalanceSummary(owedToYou: Double, youOwe: Double, mySpent: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Friends owe you",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatAmount(owedToYou),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FriendsTokens.accent()
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "You owe",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (youOwe > 0.01) formatAmount(youOwe) else "You owe ₹0",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (youOwe > 0.01) FriendsTokens.warn() else FriendsTokens.mut()
                    )
                }
            }

            Divider(color = FriendsTokens.line())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your part (after splitting)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatAmount(mySpent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SettledFriendsRow(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, FriendsTokens.line())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count settled up",
                style = MaterialTheme.typography.bodyMedium,
                color = FriendsTokens.mut()
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = FriendsTokens.mut()
            )
        }
    }
}

@Composable
private fun FriendExpenseCard(
    friend: Friend,
    balance: FriendBalance,
    onLongPress: () -> Unit,
    onSettle: () -> Unit,
    onRemind: (() -> Unit)?,
    muted: Boolean = false
) {
    val hasBalance = abs(balance.netAmount) > 0.01

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(friend.id) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (muted) Color.Transparent else FriendsTokens.card2()
        ),
        border = if (muted) BorderStroke(1.dp, FriendsTokens.line()) else null
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = friend.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (hasBalance) balanceLabel(balance) else "No pending expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = FriendsTokens.mut()
                    )
                }

                Text(
                    text = formatAmount(abs(balance.netAmount)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !hasBalance -> FriendsTokens.mut()
                        balance.netAmount > 0 -> FriendsTokens.accent()
                        else -> FriendsTokens.warn()
                    }
                )
            }

            if (hasBalance) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onRemind != null) {
                        OutlinedButton(onClick = onRemind, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remind", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = onSettle, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = FriendsTokens.accent(),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settle up", style = MaterialTheme.typography.labelMedium, color = FriendsTokens.accent())
                    }
                }
            }
        }
    }
}

private fun balanceLabel(balance: FriendBalance): String {
    return when {
        balance.netAmount > 0.01 -> "Owes you ${formatAmount(balance.netAmount)}"
        balance.netAmount < -0.01 -> "You owe ${formatAmount(abs(balance.netAmount))}"
        else -> "No pending expenses"
    }
}

private fun formatAmount(amount: Double): String {
    return formatRupeeIndian(amount)
}

/**
 * Opens the system share sheet with a prefilled reminder message — the
 * person picks whichever app (SMS/WhatsApp/etc.) actually reaches their
 * friend. Nothing is sent automatically and no contact is looked up; this
 * is the same kind of implicit ACTION_SEND the rest of the app already
 * uses for handing off to other apps, not a new permission or a silent send.
 */
private fun sendReminder(context: Context, friend: Friend, amount: Double) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Hey ${friend.name}, friendly reminder — you owe me ${formatAmount(amount)} on Splitlee."
        )
    }
    context.startActivity(Intent.createChooser(intent, "Remind ${friend.name}"))
}
