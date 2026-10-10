package com.example.splitwiseauto

import kotlin.math.max

/**
 * Per-expense "has this split been paid back?" status, derived (never
 * stored) from the same numbers the Friends tab shows.
 *
 * How it works, per friend:
 *  - Every split creates a debt in one direction: they owe me (I paid,
 *    they have a share) or I owe them (they paid, I have a share).
 *  - The Friends tab already knows what is STILL outstanding (the net
 *    balance after settle-ups and after debts in opposite directions cancel
 *    each other out).
 *  - Everything else has been paid back, and it's applied to the OLDEST
 *    splits first. So a ₹300 partial payment against splits of ₹200 (Mon)
 *    and ₹250 (Wed) marks Monday's as settled and Wednesday's as ₹100 of
 *    ₹250 paid.
 *
 * Because it's derived, it can never drift from the balances: correcting
 * or deleting a settle-up, or editing an expense, updates it automatically.
 * Nothing in the database changes.
 */
object SettleStatus {

    private const val EPS = 0.01

    /** One friend's part of one expense. */
    data class Share(
        val expenseId: Int,
        val friend: String,          // display name as saved
        val amount: Double,
        val time: Long?,
        val friendOwesMe: Boolean    // true: I paid; false: they paid
    )

    data class Part(val friend: String, val share: Double, val cleared: Double, val friendOwesMe: Boolean) {
        val remaining: Double get() = max(0.0, share - cleared)
        val isSettled: Boolean get() = remaining < EPS
        val isPartial: Boolean get() = !isSettled && cleared > EPS
    }

    enum class State { PENDING, PARTIAL, SETTLED }

    data class ExpenseSettlement(val parts: List<Part>) {
        val state: State = when {
            parts.all { it.isSettled } -> State.SETTLED
            parts.any { it.cleared > EPS } -> State.PARTIAL
            else -> State.PENDING
        }
        val remaining: Double get() = parts.sumOf { it.remaining }
        fun partFor(name: String): Part? = parts.firstOrNull { it.friend.equals(name.trim(), ignoreCase = true) }
    }

    /** Builds the debts exactly the way [FriendBalances] counts them. */
    fun sharesFrom(expenses: List<ExpenseWithParticipants>, friendNames: Collection<String>): List<Share> {
        val friends = friendNames.associateBy { it.trim().lowercase() }
        val out = mutableListOf<Share>()
        expenses.forEach { e ->
            val time = e.expense.timestampMillis
            if (e.expense.payer.equals("Me", ignoreCase = true)) {
                e.participants.forEach { p ->
                    val name = friends[p.personName.trim().lowercase()] ?: return@forEach
                    if (p.shareAmount > EPS) out += Share(e.expense.id, name, p.shareAmount, time, true)
                }
            } else {
                val payer = friends[e.expense.payer.trim().lowercase()] ?: return@forEach
                val myShare = e.participants
                    .filter { it.personName.equals("Me", ignoreCase = true) }
                    .sumOf { it.shareAmount }
                if (myShare > EPS) out += Share(e.expense.id, payer, myShare, time, false)
            }
        }
        return out
    }

    /**
     * @param netByFriend current net balance per friend (lower-cased name):
     *   positive = they owe me, negative = I owe them. Same as Friends tab.
     */
    fun allocate(shares: List<Share>, netByFriend: Map<String, Double>): Map<Int, ExpenseSettlement> {
        val parts = mutableMapOf<Int, MutableList<Part>>()
        shares.groupBy { it.friend.trim().lowercase() }.forEach { (friendKey, friendShares) ->
            val net = netByFriend[friendKey] ?: 0.0
            listOf(true, false).forEach { owesMe ->
                val list = friendShares.filter { it.friendOwesMe == owesMe }
                    .sortedWith(compareBy<Share>({ it.time }, { it.expenseId }))
                val gross = list.sumOf { it.amount }
                val outstanding = if (owesMe) max(net, 0.0) else max(-net, 0.0)
                var paidBack = (gross - outstanding).coerceIn(0.0, gross)
                list.forEach { s ->
                    val cleared = minOf(s.amount, paidBack)
                    paidBack -= cleared
                    parts.getOrPut(s.expenseId) { mutableListOf() } +=
                        Part(s.friend, s.amount, cleared, s.friendOwesMe)
                }
            }
        }
        return parts.mapValues { ExpenseSettlement(it.value) }
    }

    /** Convenience for screens: friends + their net balances by id. */
    fun compute(
        expenses: List<ExpenseWithParticipants>,
        friends: List<Friend>,
        balances: Map<Int, FriendBalance>
    ): Map<Int, ExpenseSettlement> {
        val net = friends.associate { it.name.trim().lowercase() to (balances[it.id]?.netAmount ?: 0.0) }
        return allocate(sharesFrom(expenses, friends.map { it.name }), net)
    }
}
