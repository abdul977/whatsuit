package com.example.whatsuit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing a conversation history entry.
 * Stores messages and responses for each notification thread.
 */
@Entity(
    tableName = "conversation_history",
    foreignKeys = [
        ForeignKey(
            entity = NotificationEntity::class,
            parentColumns = ["id"],
            childColumns = ["notificationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("notificationId")
    ]
)
data class ConversationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Reference to the notification this conversation belongs to
     */
    val notificationId: Long,
    
    /**
     * The user's message or notification content
     */
    val message: String,
    
    /**
     * The AI-generated response
     */
    val response: String,
    
    /**
     * Timestamp of when this conversation entry was created
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if this conversation entry is recent (within last 24 hours)
     */
    fun isRecent(): Boolean {
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - timestamp < twentyFourHoursInMillis
    }

    companion object {
        /**
         * Creates a new conversation history entry
         */
        fun create(
            notificationId: Long,
            message: String,
            response: String
        ) = ConversationHistory(
            notificationId = notificationId,
            message = message,
            response = response
        )
    }
}