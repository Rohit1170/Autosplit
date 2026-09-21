package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY id DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT * FROM expenses WHERE id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: Int): Expense?

    @Query("UPDATE expenses SET payer = :newName WHERE payer = :oldName")
    suspend fun renamePayer(oldName: String, newName: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM expense_participants WHERE expenseId = :expenseId")
    suspend fun deleteParticipantsForExpense(expenseId: Int)

    @Query("DELETE FROM expenses WHERE id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Int)

    @Transaction
    suspend fun deleteExpenseWithParticipants(expenseId: Int) {
        deleteParticipantsForExpense(expenseId)
        deleteExpenseById(expenseId)
    }

    @Transaction
    @Query("SELECT * FROM expenses ORDER BY id DESC")
    suspend fun getAllExpensesWithParticipants(): List<ExpenseWithParticipants>
}
