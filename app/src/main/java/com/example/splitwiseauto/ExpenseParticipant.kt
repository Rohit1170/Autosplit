package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// expenseId: this is the child side of the ExpenseWithParticipants
// @Relation join, run for every expense on every screen that loads history
// (Home, Insights, Friends, Map) — without an index this is a full table
// scan per query as the table grows. personName: filtered/grouped by in
// FriendViewModel's balance calculation and Home's participant filters.
@Entity(
    tableName = "expense_participants",
    indices = [Index("expenseId"), Index("personName")]
)
data class ExpenseParticipant(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val expenseId: Int,

    val personName: String,

    val shareAmount: Double
)