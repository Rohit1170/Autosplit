package com.example.splitwiseauto

import org.junit.Assert.assertEquals
import org.junit.Test

class SettleStatusTest {

    private fun owed(id: Int, amount: Double, time: Long, friend: String = "Rahul") =
        SettleStatus.Share(id, friend, amount, time, friendOwesMe = true)

    private fun iOwe(id: Int, amount: Double, time: Long, friend: String = "Rahul") =
        SettleStatus.Share(id, friend, amount, time, friendOwesMe = false)

    @Test fun `nothing paid back - all pending`() {
        val r = SettleStatus.allocate(listOf(owed(1, 200.0, 1), owed(2, 250.0, 2)), mapOf("rahul" to 450.0))
        assertEquals(SettleStatus.State.PENDING, r[1]!!.state)
        assertEquals(SettleStatus.State.PENDING, r[2]!!.state)
    }

    @Test fun `full settle-up marks every split settled`() {
        val r = SettleStatus.allocate(listOf(owed(1, 200.0, 1), owed(2, 250.0, 2)), mapOf("rahul" to 0.0))
        assertEquals(SettleStatus.State.SETTLED, r[1]!!.state)
        assertEquals(SettleStatus.State.SETTLED, r[2]!!.state)
    }

    @Test fun `partial payment covers the oldest split first`() {
        // Paid back 300 of 450 -> 150 still owed.
        val r = SettleStatus.allocate(listOf(owed(2, 250.0, 2), owed(1, 200.0, 1)), mapOf("rahul" to 150.0))
        assertEquals(SettleStatus.State.SETTLED, r[1]!!.state)
        assertEquals(SettleStatus.State.PARTIAL, r[2]!!.state)
        assertEquals(100.0, r[2]!!.parts.single().cleared, 0.001)
        assertEquals(150.0, r[2]!!.remaining, 0.001)
    }

    @Test fun `opposite debts cancel out (netting)`() {
        // Rahul owes me 500, I owe Rahul 200 -> net 300 he owes me.
        val r = SettleStatus.allocate(listOf(owed(1, 500.0, 1), iOwe(2, 200.0, 2)), mapOf("rahul" to 300.0))
        assertEquals(SettleStatus.State.SETTLED, r[2]!!.state)   // my 200 is covered by his debt
        assertEquals(SettleStatus.State.PARTIAL, r[1]!!.state)   // 200 of his 500 offset
        assertEquals(300.0, r[1]!!.remaining, 0.001)
    }

    @Test fun `friends are allocated independently`() {
        val r = SettleStatus.allocate(
            listOf(owed(1, 100.0, 1, "Rahul"), owed(1, 100.0, 1, "Abin")),
            mapOf("rahul" to 0.0, "abin" to 100.0)
        )
        val s = r[1]!!
        assertEquals(SettleStatus.State.PARTIAL, s.state)        // Rahul settled, Abin not
        assertEquals(true, s.partFor("Rahul")!!.isSettled)
        assertEquals(false, s.partFor("Abin")!!.isSettled)
    }
}
