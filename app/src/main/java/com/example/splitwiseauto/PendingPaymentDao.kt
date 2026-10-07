package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingPaymentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PendingPayment)

    /** Payments still waiting on a bank SMS (PENDING or VERIFIED_PENDING), newest first. */
    @Query(
        "SELECT * FROM pending_payments WHERE status IN ('PENDING', 'VERIFIED_PENDING') " +
            "ORDER BY initiatedAt DESC"
    )
    suspend fun getPending(): List<PendingPayment>

    /** Payments the UPI app (or the user) marked FAILED since [sinceMillis]. */
    @Query("SELECT * FROM pending_payments WHERE status = 'FAILED' AND initiatedAt >= :sinceMillis")
    suspend fun getFailedSince(sinceMillis: Long): List<PendingPayment>

    /**
     * Payments that still need the user's attention, for the bell list:
     * waiting on SMS, or resolved without a bank SMS.
     */
    @Query(
        "SELECT * FROM pending_payments WHERE status IN " +
            "('PENDING', 'VERIFIED_PENDING', 'UNCONFIRMED', 'UNVERIFIED') ORDER BY initiatedAt DESC"
    )
    suspend fun getNeedingAttention(): List<PendingPayment>

    /** Removes a payment the user dismissed from the bell list. Never used on CONFIRMED rows. */
    @Query("DELETE FROM pending_payments WHERE id = :id AND status != 'CONFIRMED'")
    suspend fun deleteUnconfirmed(id: String): Int

    @Query("SELECT * FROM pending_payments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingPayment?

    @Query("SELECT * FROM pending_payments ORDER BY initiatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PendingPayment>

    @Query("SELECT * FROM pending_payments WHERE upiRef = :upiRef LIMIT 1")
    suspend fun findByUpiRef(upiRef: String): PendingPayment?

    /** The most recent payment linked to this bill/expense id, if any. */
    @Query("SELECT * FROM pending_payments WHERE billId = :billId ORDER BY initiatedAt DESC LIMIT 1")
    suspend fun findByBillId(billId: String): PendingPayment?

    @Query("UPDATE pending_payments SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * Moves a payment to [newStatus] only if it's still in [expectedStatus].
     * Returns rows changed (0 = something else already moved it), so the SMS
     * matcher, the UPI-app result and the timeout worker never overwrite
     * each other.
     */
    @Query("UPDATE pending_payments SET status = :newStatus WHERE id = :id AND status = :expectedStatus")
    suspend fun compareAndSetStatus(id: String, expectedStatus: String, newStatus: String): Int

    /** Marks CONFIRMED from [expectedStatus], attaching the bank reference and SMS time. */
    @Query(
        "UPDATE pending_payments SET status = 'CONFIRMED', upiRef = COALESCE(:upiRef, upiRef), " +
            "confirmedAt = :confirmedAt WHERE id = :id AND status = :expectedStatus"
    )
    suspend fun confirm(id: String, expectedStatus: String, upiRef: String?, confirmedAt: Long): Int

    @Query("UPDATE pending_payments SET upiRef = :upiRef WHERE id = :id")
    suspend fun setUpiRef(id: String, upiRef: String)

    @Query("UPDATE pending_payments SET billId = :billId WHERE id = :id")
    suspend fun linkBill(id: String, billId: String)

    /** Payments in [status] initiated before [cutoffMillis] — the timeout worker's input. */
    @Query("SELECT * FROM pending_payments WHERE status = :status AND initiatedAt < :cutoffMillis")
    suspend fun getWithStatusOlderThan(status: String, cutoffMillis: Long): List<PendingPayment>
}
