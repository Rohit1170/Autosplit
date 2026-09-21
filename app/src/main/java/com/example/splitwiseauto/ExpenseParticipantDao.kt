package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseParticipantDao {

    @Insert
    suspend fun insertParticipant(
        participant: ExpenseParticipant
    ): Long

    @Insert
    suspend fun insertParticipants(
        participants: List<ExpenseParticipant>
    )

    @Query(
        "SELECT * FROM expense_participants " +
                "WHERE expenseId = :expenseId"
    )
    suspend fun getParticipantsForExpense(
        expenseId: Int
    ): List<ExpenseParticipant>

    @Query(
        "UPDATE expense_participants SET personName = :newName " +
                "WHERE personName = :oldName"
    )
    suspend fun renameParticipant(oldName: String, newName: String)

    @Query("DELETE FROM expense_participants")
    suspend fun deleteAllParticipants()
}