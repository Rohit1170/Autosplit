package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SavedFilterDao {
    @Insert
    suspend fun insert(filter: SavedFilter): Long

    @Query("SELECT * FROM saved_filters ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<SavedFilter>

    @Query("DELETE FROM saved_filters WHERE id = :id")
    suspend fun deleteById(id: Int)
}
