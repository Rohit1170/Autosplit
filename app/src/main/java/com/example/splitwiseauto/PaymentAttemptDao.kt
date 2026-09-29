package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PaymentAttemptDao {

    @Insert
    suspend fun insert(attempt: PaymentAttempt): Long

    @Query("SELECT * FROM payment_attempts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): PaymentAttempt?

    @Query("SELECT * FROM payment_attempts WHERE status IN (:statuses) ORDER BY createdAtMillis DESC")
    suspend fun getByStatuses(statuses: List<String>): List<PaymentAttempt>

    /**
     * Unconditional status write — used for forward transitions
     * (INITIATING -> IN_PROGRESS -> WAITING_FOR_VERIFICATION -> ...) that
     * only the retired *99# flow ever drove (legacy; no new rows are created).
     */
    @Query("UPDATE payment_attempts SET status = :newStatus WHERE id = :id")
    suspend fun setStatus(id: Int, newStatus: String)

    /**
     * Conditional status write, used for the terminal SUCCESS/FAILED
     * transition from SMS adoption: only succeeds if [id]'s status is
     * still one of [fromStatuses]. This is what makes a redelivered or
     * duplicate confirming SMS a no-op instead of double-adopting — by the
     * time a second copy of the SMS arrives, the row is no longer in
     * WAITING_FOR_VERIFICATION/TIMEOUT, so this returns 0 and the caller
     * knows to leave it alone. Returns the number of rows changed (0 or 1).
     */
    @Query("UPDATE payment_attempts SET status = :newStatus WHERE id = :id AND status IN (:fromStatuses)")
    suspend fun claimStatus(id: Int, newStatus: String, fromStatuses: List<String>): Int

    @Query("UPDATE payment_attempts SET adoptedExpenseId = :expenseId WHERE id = :id")
    suspend fun setAdoptedExpenseId(id: Int, expenseId: Int)
}
