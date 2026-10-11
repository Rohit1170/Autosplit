package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface SettlementDao {

    @Insert
    suspend fun insert(settlement: Settlement): Long

    @Update
    suspend fun update(settlement: Settlement)

    @Query("SELECT * FROM settlements")
    suspend fun getAll(): List<Settlement>

    @Query("SELECT * FROM settlements WHERE friendId = :friendId ORDER BY settledAt DESC")
    suspend fun getForFriend(friendId: Int): List<Settlement>

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM settlements WHERE friendId = :friendId")
    suspend fun deleteForFriend(friendId: Int)
}
