package com.example.whatsuit.data

import android.content.Context
import android.util.Log

class ConversationManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val TAG = "ConversationManager"

    data class ConversationContext(
        val threadId: String,
        val latestMessage: String,
        val historySize: Int,
        val participants: Set<String>,
        val lastActivity: Long
    )

    suspend fun getConversationContext(notificationId: Long): ConversationContext {
        Log.d(TAG, "Getting conversation context for notification: $notificationId")
        
        val notification = database.notificationDao().getNotificationByIdSync(notificationId)
        val history = database.geminiDao().getThreadedConversationHistory(notificationId, 50)
        
        Log.d(TAG, "Found ${history.size} history entries for conversation: ${notification?.conversationId}")
        
        return ConversationContext(
            threadId = notification?.conversationId ?: "",
            latestMessage = notification?.content ?: "",
            historySize = history.size,
            participants = extractParticipants(history),
            lastActivity = notification?.timestamp ?: System.currentTimeMillis()
        ).also {
            Log.d(TAG, "Created conversation context: threadId=${it.threadId}, historySize=${it.historySize}")
        }
    }

    private fun extractParticipants(history: List<ConversationHistory>): Set<String> {
        return history.mapNotNull { entry ->
            when {
                entry.message.contains("@") -> extractEmailFromMessage(entry.message)
                entry.message.matches(Regex(".*[0-9]{10,}.*")) -> extractPhoneFromMessage(entry.message)
                else -> null
            }
        }.toSet()
    }

    private fun extractEmailFromMessage(message: String): String? {
        return message.split(" ")
            .find { it.matches(Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}")) }
    }

    private fun extractPhoneFromMessage(message: String): String? {
        return message.replace(Regex("[^0-9]"), "")
            .let { if (it.length >= 10) it else null }
    }

    companion object {
        private const val TAG = "ConversationManager"
    }
}
