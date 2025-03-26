package com.example.whatsuit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "auto_reply_settings",
    indices = [Index(value = ["conversationId"], unique = true)]
)
data class AutoReplySettings(
    @PrimaryKey
    val conversationId: String,
    val isDisabled: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)