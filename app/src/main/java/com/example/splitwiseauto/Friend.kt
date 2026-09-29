package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String
)