package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingExpenseNotificationDao {

    @Insert
    suspend fun insert(pending: PendingExpenseNotification): Long

    @Query("SELECT * FROM pending_expense_notifications ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<PendingExpenseNotification>

    @Query("SELECT COUNT(*) FROM pending_expense_notifications")
    suspend fun getCount(): Int

    @Query("DELETE FROM pending_expense_notifications WHERE id = :id")
    suspend fun deleteById(id: Int)
}
