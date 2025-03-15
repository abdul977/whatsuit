package com.example.whatsuit.data

import android.content.Context
import android.util.Log
import com.example.whatsuit.data.NotificationEntity

class ConversationManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)

    data class ConversationContext(
        val threadId: String,
        val latestMessage: String,
        val historySize: Int,
        val participants: Set<String>,
        val lastActivity: Long,
        val recentMessages: List<MessageInfo> = emptyList()
    )

    data class MessageInfo(
        val content: String,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "ConversationManager"
        private const val RAPID_MESSAGE_WINDOW = 1000L // 1 second window for rapid messages
    }

    suspend fun getConversationContext(notificationId: Long, cachedNotification: NotificationEntity? = null): ConversationContext {
        Log.d(TAG, "Getting conversation context for notification: $notificationId")
        
        val notification = cachedNotification ?: database.notificationDao().getNotificationByIdSync(notificationId)
        if (notification == null) {
            Log.w(TAG, "Notification not found: $notificationId")
            return ConversationContext("", "", 0, emptySet(), System.currentTimeMillis())
        }

        val currentTime = System.currentTimeMillis()
        val history = database.geminiDao().getThreadedConversationHistory(notificationId, 50)
        
        // Get recent messages in the rapid message window
        val recentMessages = database.notificationDao()
            .getNotificationsInTimeRange(
                notification.conversationId,
                currentTime - RAPID_MESSAGE_WINDOW,
                currentTime
            )
            .map { MessageInfo(it.content, it.timestamp) }
            .sortedBy { it.timestamp }

        Log.d(TAG, "Found ${history.size} history entries and ${recentMessages.size} recent messages for conversation: ${notification.conversationId}")
        
        return ConversationContext(
            threadId = notification.conversationId,
            latestMessage = notification.content,
            historySize = history.size,
            participants = extractParticipants(history),
            lastActivity = notification.timestamp,
            recentMessages = recentMessages
        ).also {
            Log.d(TAG, "Created conversation context: threadId=${it.threadId}, historySize=${it.historySize}, recentMessages=${it.recentMessages.size}")
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
}
