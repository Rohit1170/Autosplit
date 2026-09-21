package com.example.splitwiseauto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendViewModel : ViewModel() {

    private lateinit var friendDao: FriendDao
    private lateinit var expenseDao: ExpenseDao
    private lateinit var settlementDao: SettlementDao
    private lateinit var expenseParticipantDao: ExpenseParticipantDao

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _balances = MutableStateFlow<Map<Int, FriendBalance>>(emptyMap())
    val balances: StateFlow<Map<Int, FriendBalance>> = _balances.asStateFlow()

    private val _totalOwedToYou = MutableStateFlow(0.0)
    val totalOwedToYou: StateFlow<Double> = _totalOwedToYou.asStateFlow()

    // Settlement history for whichever friend's history dialog is open.
    private val _settlementHistory = MutableStateFlow<List<Settlement>>(emptyList())
    val settlementHistory: StateFlow<List<Settlement>> = _settlementHistory.asStateFlow()

    fun initialize(
        friendDao: FriendDao,
        expenseDao: ExpenseDao,
        settlementDao: SettlementDao,
        expenseParticipantDao: ExpenseParticipantDao
    ) {
        if (!::friendDao.isInitialized || !::expenseDao.isInitialized || !::settlementDao.isInitialized || !::expenseParticipantDao.isInitialized) {
            this.friendDao = friendDao
            this.expenseDao = expenseDao
            this.settlementDao = settlementDao
            this.expenseParticipantDao = expenseParticipantDao
            refresh()
        }
    }

    fun refresh() {
        if (!::friendDao.isInitialized || !::expenseDao.isInitialized || !::settlementDao.isInitialized) {
            return
        }

        viewModelScope.launch {
            val savedFriends = friendDao.getAllFriends()
            val displayFriends = savedFriends.filterNot {
                it.name.trim().equals("Me", ignoreCase = true)
            }
            val friendIdsByName = displayFriends.associateBy {
                it.name.trim().lowercase()
            }
            val calculatedBalances = mutableMapOf<Int, FriendBalance>()

            expenseDao.getAllExpensesWithParticipants().forEach { expenseWithParticipants ->
                val paidByMe = expenseWithParticipants.expense.payer.equals(
                    "Me",
                    ignoreCase = true
                )

                if (paidByMe) {
                    expenseWithParticipants.participants.forEach participantLoop@ { participant ->
                        val friend = friendIdsByName[participant.personName.trim().lowercase()]
                            ?: return@participantLoop
                        val currentBalance = calculatedBalances[friend.id] ?: FriendBalance()
                        calculatedBalances[friend.id] =
                            currentBalance.copy(
                                owedToYou = currentBalance.owedToYou + participant.shareAmount,
                                totalSplitShare = currentBalance.totalSplitShare + participant.shareAmount
                            )
                    }
                } else {
                    val payerFriend = friendIdsByName[
                        expenseWithParticipants.expense.payer.trim().lowercase()
                    ]
                    val myShare = expenseWithParticipants.participants
                        .filter { it.personName.equals("Me", ignoreCase = true) }
                        .sumOf { it.shareAmount }
                    if (payerFriend != null && myShare > 0) {
                        val currentBalance = calculatedBalances[payerFriend.id] ?: FriendBalance()
                        calculatedBalances[payerFriend.id] =
                            currentBalance.copy(
                                youOwe = currentBalance.youOwe + myShare,
                                totalSplitShare = currentBalance.totalSplitShare + myShare
                            )
                    }
                }
            }

            settlementDao.getAll().groupBy { it.friendId }.forEach { (friendId, settlements) ->
                var adjustedBalance = calculatedBalances[friendId] ?: return@forEach
                settlements.forEach { settlement ->
                    adjustedBalance = if (settlement.amount >= 0) {
                        adjustedBalance.copy(
                            owedToYou = (adjustedBalance.owedToYou - settlement.amount)
                                .coerceAtLeast(0.0)
                        )
                    } else {
                        adjustedBalance.copy(
                            youOwe = (adjustedBalance.youOwe + settlement.amount)
                                .coerceAtLeast(0.0)
                        )
                    }
                }
                calculatedBalances[friendId] = adjustedBalance
            }

            _friends.value = displayFriends
            _balances.value = calculatedBalances
            _totalOwedToYou.value = calculatedBalances.values.sumOf {
                it.netAmount.coerceAtLeast(0.0)
            }
        }
    }

    fun addFriend(name: String) {
        val cleanName = name.trim()

        if (cleanName.isBlank()) {
            return
        }

        viewModelScope.launch {

            // Prevent duplicate friend names
            val existingFriend = friendDao.getFriendByName(cleanName)

            if (existingFriend == null) {
                friendDao.insertFriend(
                    Friend(name = cleanName)
                )

                refresh()
            }
        }
    }

    fun renameFriend(friend: Friend, newName: String) {
        val cleanName = newName.trim()

        if (cleanName.isBlank() || cleanName.equals("Me", ignoreCase = true)) {
            return
        }

        if (cleanName.equals(friend.name, ignoreCase = true)) {
            return
        }

        viewModelScope.launch {

            // Don't collide with another existing friend's name.
            val existing = friendDao.getFriendByName(cleanName)
            if (existing != null && existing.id != friend.id) {
                return@launch
            }

            val oldName = friend.name

            // Update the friend record itself.
            friendDao.renameFriend(friend.id, cleanName)

            // Propagate the rename to already-saved expense history so past
            // splits and "paid by" fields keep showing the corrected name.
            expenseParticipantDao.renameParticipant(oldName, cleanName)
            expenseDao.renamePayer(oldName, cleanName)

            refresh()
        }
    }

    // amount == null settles the entire outstanding balance, exactly as
    // before. Passing an amount (e.g. "they only gave me half in cash")
    // records a PARTIAL settlement instead — the rest stays outstanding and
    // shows up again on the next screen refresh.
    fun settleUp(friend: Friend, amount: Double? = null) {
        val balance = _balances.value[friend.id] ?: return
        val net = balance.netAmount
        if (kotlin.math.abs(net) <= 0.01) return

        // Clamp to the same direction and to at most what's actually owed,
        // so a mistyped or overstated partial amount can never flip who
        // owes whom or settle more than is outstanding.
        val settleAmount = if (amount == null) {
            net
        } else {
            val magnitude = amount.coerceIn(0.0, kotlin.math.abs(net))
            if (net >= 0) magnitude else -magnitude
        }
        if (kotlin.math.abs(settleAmount) <= 0.01) return

        viewModelScope.launch {
            settlementDao.insert(
                Settlement(friendId = friend.id, amount = settleAmount)
            )
            refresh()
        }
    }

    fun removeFriend(friend: Friend) {
        viewModelScope.launch {
            settlementDao.deleteForFriend(friend.id)
            friendDao.deleteFriendById(friend.id)
            refresh()
        }
    }

    fun loadSettlementHistory(friendId: Int) {
        viewModelScope.launch {
            _settlementHistory.value = settlementDao.getForFriend(friendId)
        }
    }

    fun clearSettlementHistory() {
        _settlementHistory.value = emptyList()
    }

    // Fixes a mistyped settlement after the fact (e.g. entered 5000 instead
    // of 500) without letting the correction flip who owes whom — the sign
    // (who paid whom) from the original entry is preserved; only the size of
    // the payment changes.
    fun correctSettlement(settlement: Settlement, newAmount: Double) {
        if (newAmount <= 0.0) return
        val corrected = if (settlement.amount >= 0) newAmount else -newAmount
        if (corrected == settlement.amount) return

        viewModelScope.launch {
            settlementDao.update(settlement.copy(amount = corrected))
            refresh()
            loadSettlementHistory(settlement.friendId)
        }
    }

    fun deleteSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementDao.deleteById(settlement.id)
            refresh()
            loadSettlementHistory(settlement.friendId)
        }
    }
}

data class FriendBalance(
    val owedToYou: Double = 0.0,
    val youOwe: Double = 0.0,
    val totalSplitShare: Double = 0.0
) {
    val netAmount: Double
        get() = owedToYou - youOwe
}
