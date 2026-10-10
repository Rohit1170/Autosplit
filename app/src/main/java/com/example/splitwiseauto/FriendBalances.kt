package com.example.splitwiseauto

/**
 * The one place friend balances are worked out. Used by the Friends tab
 * (FriendViewModel) and the home-screen widget, so both always agree.
 * Logic is unchanged from what FriendViewModel used to do inline.
 */
object FriendBalances {

    suspend fun compute(
        friendDao: FriendDao,
        expenseDao: ExpenseDao,
        settlementDao: SettlementDao
    ): Pair<List<Friend>, Map<Int, FriendBalance>> {
        val savedFriends = friendDao.getAllFriends()
        val displayFriends = savedFriends.filterNot {
            it.name.trim().equals("Me", ignoreCase = true)
        }
        val friendIdsByName = displayFriends.associateBy { it.name.trim().lowercase() }
        val calculatedBalances = mutableMapOf<Int, FriendBalance>()

        ExpenseRepository.allExpenses(expenseDao).forEach { expenseWithParticipants ->
            val paidByMe = expenseWithParticipants.expense.payer.equals("Me", ignoreCase = true)

            if (paidByMe) {
                expenseWithParticipants.participants.forEach participantLoop@{ participant ->
                    val friend = friendIdsByName[participant.personName.trim().lowercase()]
                        ?: return@participantLoop
                    val currentBalance = calculatedBalances[friend.id] ?: FriendBalance()
                    calculatedBalances[friend.id] = currentBalance.copy(
                        owedToYou = currentBalance.owedToYou + participant.shareAmount,
                        totalSplitShare = currentBalance.totalSplitShare + participant.shareAmount
                    )
                }
            } else {
                val payerFriend = friendIdsByName[expenseWithParticipants.expense.payer.trim().lowercase()]
                val myShare = expenseWithParticipants.participants
                    .filter { it.personName.equals("Me", ignoreCase = true) }
                    .sumOf { it.shareAmount }
                if (payerFriend != null && myShare > 0) {
                    val currentBalance = calculatedBalances[payerFriend.id] ?: FriendBalance()
                    calculatedBalances[payerFriend.id] = currentBalance.copy(
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
                        owedToYou = (adjustedBalance.owedToYou - settlement.amount).coerceAtLeast(0.0)
                    )
                } else {
                    adjustedBalance.copy(
                        youOwe = (adjustedBalance.youOwe + settlement.amount).coerceAtLeast(0.0)
                    )
                }
            }
            calculatedBalances[friendId] = adjustedBalance
        }

        return displayFriends to calculatedBalances
    }
}
