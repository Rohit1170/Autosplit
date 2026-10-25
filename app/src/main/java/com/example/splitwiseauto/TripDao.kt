package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Query("SELECT * FROM trips ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<Trip>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Trip?

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: Int)
}
