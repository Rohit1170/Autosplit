package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    /** Most recent expense carrying this UPI/bank reference, if any (duplicate check). */
    @Query("SELECT * FROM expenses WHERE upi = :upi ORDER BY id DESC LIMIT 1")
    suspend fun findByUpi(upi: String): Expense?

    /**
     * Swaps a Scan & Pay placeholder reference for the bank's real UPI ref
     * once the SMS arrives, filling bank/account details only where the
     * expense doesn't already have them.
     */
    @Query(
        "UPDATE expenses SET upi = :upi, " +
            "bank = CASE WHEN bank = 'UPI' THEN :bank ELSE bank END, " +
            "accountIdentifier = COALESCE(accountIdentifier, :accountIdentifier) " +
            "WHERE id = :id"
    )
    suspend fun attachBankReference(id: Int, upi: String, bank: String, accountIdentifier: String?)

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

    /**
     * Same data as [getAllExpensesWithParticipants], but observed rather
     * than one-shot: Room re-runs this only when the `expenses` or
     * `expense_participants` tables actually change (its own
     * InvalidationTracker, not anything hand-rolled here), and every
     * collector shares that one emission instead of each issuing its own
     * full-table query. See ExpenseRepository, which is what actually
     * shares it across Home/Insights/Friends/Map.
     */
    @Transaction
    @Query("SELECT * FROM expenses ORDER BY id DESC")
    fun observeAllExpensesWithParticipants(): Flow<List<ExpenseWithParticipants>>
}
