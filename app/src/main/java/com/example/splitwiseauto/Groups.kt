package com.example.splitwiseauto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Data (DB v16, additive): a group is a name + a set of member names.
// Expenses can point at a group (Expense.groupId); nothing else changes.
// ---------------------------------------------------------------------------

@Entity(tableName = "split_groups")
data class SplitGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "group_members", primaryKeys = ["groupId", "personName"])
data class GroupMember(
    val groupId: Int,
    val personName: String
)

@Dao
interface GroupDao {
    @Insert
    suspend fun insertGroup(group: SplitGroup): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<GroupMember>)

    @Query("SELECT * FROM split_groups ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<SplitGroup>

    @Query("SELECT * FROM group_members")
    suspend fun allMembers(): List<GroupMember>

    @Query("UPDATE split_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: Int)

    @Query("DELETE FROM split_groups WHERE id = :id")
    suspend fun deleteGroup(id: Int)

    /** Deleting a group keeps its expenses (they just lose the group). */
    @Query("UPDATE expenses SET groupId = NULL WHERE groupId = :groupId")
    suspend fun detachExpenses(groupId: Int)
}

/** A group with its members, for screens. */
data class GroupWithMembers(val group: SplitGroup, val members: List<String>)

suspend fun loadGroups(db: AppDatabase): List<GroupWithMembers> {
    val members = db.groupDao().allMembers().groupBy { it.groupId }
    return db.groupDao().getAll().map { g ->
        GroupWithMembers(g, members[g.id].orEmpty().map { it.personName }.sortedBy { it.lowercase() })
    }
}

/** What's still open in one group, per member (positive = owes you). */
data class GroupBalance(val spent: Double, val byMember: Map<String, Double>) {
    val owedToYou: Double get() = byMember.values.sumOf { it.coerceAtLeast(0.0) }
    val youOwe: Double get() = byMember.values.sumOf { (-it).coerceAtLeast(0.0) }
}

/**
 * Same rule as trips: per-expense paid-back status (settle-ups applied to
 * the oldest splits first), summed over the group's expenses.
 */
fun groupBalance(
    groupExpenses: List<ExpenseWithParticipants>,
    settlement: Map<Int, SettleStatus.ExpenseSettlement>
): GroupBalance {
    val by = mutableMapOf<String, Double>()
    groupExpenses.forEach { e ->
        settlement[e.expense.id]?.parts?.forEach { p ->
            val signed = if (p.friendOwesMe) p.remaining else -p.remaining
            by[p.friend] = (by[p.friend] ?: 0.0) + signed
        }
    }
    return GroupBalance(groupExpenses.sumOf { it.expense.amount }, by)
}

// ---------------------------------------------------------------------------
// Friends tab: "Groups" row + group sheet + new-group dialog
// ---------------------------------------------------------------------------

@Composable
fun GroupsSection(
    friends: List<Friend>,
    settlement: Map<Int, SettleStatus.ExpenseSettlement>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<GroupWithMembers>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<ExpenseWithParticipants>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var showNew by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<GroupWithMembers?>(null) }

    LaunchedEffect(version, settlement) {
        groups = loadGroups(db)
        expenses = ExpenseRepository.allExpenses(db.expenseDao()).filter { it.expense.groupId != null }
    }
    val accent = Brand.accent()

    if (showNew) {
        GroupEditorDialog(
            title = "New group",
            initialName = "",
            initialMembers = emptySet(),
            friends = friends,
            onDismiss = { showNew = false },
            onSave = { name, members ->
                showNew = false
                scope.launch {
                    val id = db.groupDao().insertGroup(SplitGroup(name = name)).toInt()
                    db.groupDao().insertMembers(members.map { GroupMember(id, it) })
                    version++
                }
            }
        )
    }

    open?.let { g ->
        GroupSheet(
            group = g,
            expenses = expenses.filter { it.expense.groupId == g.group.id },
            settlement = settlement,
            friends = friends,
            onDismiss = { open = null },
            onChanged = {
                open = null
                version++
            }
        )
    }

    Column(modifier = modifier) {
        Text("Groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(groups, key = { it.group.id }) { g ->
                val bal = groupBalance(expenses.filter { it.expense.groupId == g.group.id }, settlement)
                GroupCard(g, bal, onClick = { open = g })
            }
            item {
                val source = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .height(112.dp)
                        .pressScale(source)
                        .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), radius = 20.dp)
                        .clickable(interactionSource = source, indication = null) { showNew = true }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, contentDescription = null, tint = accent) }
                    Spacer(Modifier.height(8.dp))
                    Text("New group", style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }
        }
    }
}

@Composable
private fun GroupCard(g: GroupWithMembers, bal: GroupBalance, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val accent = Brand.accent()
    val warn = Color(0xFFF0A63C)
    val net = bal.owedToYou - bal.youOwe
    Column(
        modifier = Modifier
            .width(168.dp)
            .height(112.dp)
            .pressScale(source)
            .modernCard(base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), radius = 20.dp)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                g.group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${g.members.size} member${if (g.members.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            when {
                net > 0.01 -> "Owed ${formatRupeeIndian(net)}"
                net < -0.01 -> "You owe ${formatRupeeIndian(-net)}"
                else -> "All settled"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = when {
                net > 0.01 -> accent
                net < -0.01 -> warn
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun GroupSheet(
    group: GroupWithMembers,
    expenses: List<ExpenseWithParticipants>,
    settlement: Map<Int, SettleStatus.ExpenseSettlement>,
    friends: List<Friend>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val bal = remember(expenses, settlement) { groupBalance(expenses, settlement) }
    var editing by remember { mutableStateOf(false) }
    val accent = Brand.accent()
    val warn = Color(0xFFF0A63C)

    if (editing) {
        GroupEditorDialog(
            title = "Edit group",
            initialName = group.group.name,
            initialMembers = group.members.toSet(),
            friends = friends,
            onDismiss = { editing = false },
            onSave = { name, members ->
                editing = false
                scope.launch {
                    db.withTransaction {
                        db.groupDao().rename(group.group.id, name)
                        db.groupDao().clearMembers(group.group.id)
                        db.groupDao().insertMembers(members.map { GroupMember(group.group.id, it) })
                    }
                    onChanged()
                }
            }
        )
    }

    AppSheet(
        title = group.group.name,
        subtitle = "${expenses.size} expense${if (expenses.size == 1) "" else "s"} · ${formatRupeeIndian(bal.spent)} spent",
        onDismiss = onDismiss,
        glass = true
    ) {
        Text("Balances in this group", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.members.forEach { name ->
                val v = bal.byMember.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: 0.0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) { Text(name.firstOrNull()?.uppercase() ?: "?", color = accent, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            v > 0.01 -> "owes you ${formatRupeeIndian(v)}"
                            v < -0.01 -> "you owe ${formatRupeeIndian(-v)}"
                            else -> "settled"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            v > 0.01 -> accent
                            v < -0.01 -> warn
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        if (expenses.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            expenses.sortedByDescending { it.expense.timestampMillis ?: 0L }.take(8).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.expense.merchant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${e.expense.date} · paid by ${if (e.expense.payer.equals("Me", true)) "you" else e.expense.payer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(formatRupeeIndian(e.expense.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Settle up from each friend's card. It clears their whole balance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editing = true }) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit")
            }
        }
        Spacer(Modifier.height(10.dp))
        HoldToConfirmRow(
            icon = Icons.Filled.Delete,
            label = "Hold to delete group",
            holdingLabel = "Keep holding…",
            hint = "Its expenses are kept",
            holdingHint = "Let go to cancel",
            onConfirmed = {
                scope.launch {
                    db.withTransaction {
                        db.groupDao().detachExpenses(group.group.id)
                        db.groupDao().clearMembers(group.group.id)
                        db.groupDao().deleteGroup(group.group.id)
                    }
                    onChanged()
                }
            }
        )
    }
}

/** Name + tick the friends in it. */
@Composable
private fun GroupEditorDialog(
    title: String,
    initialName: String,
    initialMembers: Set<String>,
    friends: List<Friend>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val picked = remember { mutableStateListOf<String>().apply { addAll(initialMembers) } }
    val view = LocalView.current
    val accent = Brand.accent()
    val people = friends.filterNot { it.name.equals("Me", ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Group name",
                    placeholder = "e.g. Flatmates",
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Members (you're always in it)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (people.isEmpty()) {
                    Text("Add friends first on the Friends tab.", style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    modifier = Modifier
                        .height((people.size.coerceAtMost(6) * 46).dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    people.forEach { f ->
                        val on = picked.any { it.equals(f.name, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    Haptics.tap(view)
                                    if (on) picked.removeAll { it.equals(f.name, ignoreCase = true) } else picked.add(f.name)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (on) accent else Color.Transparent)
                                    .then(if (on) Modifier else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Brand.onAccent(), modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && picked.isNotEmpty(),
                onClick = { onSave(name.trim(), picked.toList()) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------------------
// Split screen: group chips ("pick a group → its members are selected")
// ---------------------------------------------------------------------------

@Composable
fun GroupChipsRow(
    groups: List<GroupWithMembers>,
    selectedGroupId: Int?,
    onPick: (GroupWithMembers?) -> Unit
) {
    if (groups.isEmpty()) return
    val accent = Brand.accent()
    val view = LocalView.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.group.id }) { g ->
            val selected = g.group.id == selectedGroupId
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                    .then(if (selected) Modifier.border(1.dp, accent, RoundedCornerShape(50)) else Modifier)
                    .clickable {
                        Haptics.tap(view)
                        onPick(if (selected) null else g)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    g.group.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
