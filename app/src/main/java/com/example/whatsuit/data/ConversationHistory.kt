package com.example.whatsuit.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

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
        Index("notificationId"),
        Index("conversationId")
    ]
)
data class ConversationHistory(
    /**
     * Flag indicating if this entry has been modified
     */
    val isModified: Boolean = false,

    /**
     * Analysis of the conversation provided by Gemini
     */
    val analysis: String? = null,

    /**
     * Timestamp of when the analysis was last generated
     */
    val analysisTimestamp: Long? = null,

    /**
     * Unique identifier for the conversation thread
     */
    val conversationId: String,

    /**
     * The AI-generated response
     */
    val response: String,

    /**
     * Reference to the notification this conversation belongs to
     */
    val notificationId: Long,

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * The user's message or notification content
     */
    val message: String,

    /**
     * Timestamp of when this conversation entry was created or last modified
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if this conversation entry is recent (within last 24 hours)
     */
    @androidx.room.Ignore
    fun isRecent(): Boolean {
        val twentyFourHoursInMillis = 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() - timestamp < twentyFourHoursInMillis
    }

    /**
     * Creates a copy with modified content and updated timestamp
     */
    fun withModifiedContent(
        newMessage: String? = null,
        newResponse: String? = null
    ) = copy(
        message = newMessage?.trim() ?: message,
        response = newResponse?.trim() ?: response,
        timestamp = System.currentTimeMillis(),
        isModified = true
    )

    /**
     * Validates if the content is valid for editing
     * @return Pair<Boolean, String?> where first is validity and second is error message if invalid
     */
    fun validateEdit(newMessage: String?, newResponse: String?): Pair<Boolean, String?> {
        if (newMessage?.isBlank() == true || newResponse?.isBlank() == true) {
            return Pair(false, "Message and response cannot be empty")
        }

        if (newMessage == message && newResponse == response) {
            return Pair(false, "No changes made")
        }

        return Pair(true, null)
    }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): ConversationHistory {
            return Gson().fromJson(json, ConversationHistory::class.java)
        }

        /**
         * Creates a new conversation history entry
         */
        fun create(
            notificationId: Long,
            message: String,
            response: String
        ) = ConversationHistory(
            isModified = false,
            conversationId = "",  // Will be updated by Room after insert
            response = response,
            notificationId = notificationId,
            id = 0,
            message = message,
            timestamp = System.currentTimeMillis()
        )
    }
}
