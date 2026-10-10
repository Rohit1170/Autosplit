package com.example.splitwiseauto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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

    /** Per-expense paid-back status (Settled / Part paid), keyed by expense id. */
    private val _expenseSettlement = MutableStateFlow<Map<Int, SettleStatus.ExpenseSettlement>>(emptyMap())
    val expenseSettlement: StateFlow<Map<Int, SettleStatus.ExpenseSettlement>> = _expenseSettlement.asStateFlow()

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
            // Recompute whenever expenses/participants change. Without this,
            // balances were only recomputed on resume — which runs BEFORE
            // Room has re-emitted the expense just saved in the split screen,
            // so a new "paid by a friend" expense could show "You owe ₹0"
            // until the next refresh.
            viewModelScope.launch {
                ExpenseRepository.changes(expenseDao)
                    .drop(1)
                    .collect { refresh() }
            }
        }
    }

    private var refreshJob: kotlinx.coroutines.Job? = null

    fun refresh() {
        if (!::friendDao.isInitialized || !::expenseDao.isInitialized || !::settlementDao.isInitialized) {
            return
        }

        // Latest refresh wins: an older, slower compute can't overwrite a newer one.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val (displayFriends, calculatedBalances) =
                FriendBalances.compute(friendDao, expenseDao, settlementDao)

            _friends.value = displayFriends
            _balances.value = calculatedBalances
            _expenseSettlement.value = SettleStatus.compute(
                ExpenseRepository.allExpenses(expenseDao), displayFriends, calculatedBalances
            )
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
