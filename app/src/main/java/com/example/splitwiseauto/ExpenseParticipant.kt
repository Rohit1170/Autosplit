package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_participants")
data class ExpenseParticipant(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val expenseId: Int,

    val personName: String,

    val shareAmount: Double
)