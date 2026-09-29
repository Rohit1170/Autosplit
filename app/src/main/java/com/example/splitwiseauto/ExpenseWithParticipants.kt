package com.example.splitwiseauto

import androidx.room.Embedded
import androidx.room.Relation

data class ExpenseWithParticipants(
    @Embedded val expense: Expense,
    @Relation(
        parentColumn = "id",
        entityColumn = "expenseId"
    )
    val participants: List<ExpenseParticipant>
)