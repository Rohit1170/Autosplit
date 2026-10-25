package com.example.splitwiseauto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val name: String,

    // Digits with country code (919876543210). Used for WhatsApp/SMS reminders.
    val phone: String? = null
)