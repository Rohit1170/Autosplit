package com.example.splitwiseauto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FriendDao {

    @Insert
    suspend fun insertFriend(friend: Friend): Long

    @Query("SELECT * FROM friends ORDER BY name ASC")
    suspend fun getAllFriends(): List<Friend>

    @Query("SELECT * FROM friends WHERE name = :name LIMIT 1")
    suspend fun getFriendByName(name: String): Friend?

    @Query("UPDATE friends SET name = :newName WHERE id = :friendId")
    suspend fun renameFriend(friendId: Int, newName: String)

    @Query("DELETE FROM friends WHERE id = :friendId")
    suspend fun deleteFriendById(friendId: Int)

    @Query("DELETE FROM friends")
    suspend fun deleteAllFriends()
}
