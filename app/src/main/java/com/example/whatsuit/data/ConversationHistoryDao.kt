package com.example.whatsuit.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data Access Object for handling conversation history database operations
 */
@Dao
interface ConversationHistoryDao {
    @Insert
    suspend fun insert(history: ConversationHistory)
    
    @Insert
    fun insertSync(history: ConversationHistory)
    
    @Query("SELECT * FROM conversation_history WHERE notificationId = :notificationId ORDER BY timestamp DESC")
    fun getHistoryForNotification(notificationId: Long): LiveData<List<ConversationHistory>>
    
    @Query("SELECT * FROM conversation_history WHERE notificationId = :notificationId ORDER BY timestamp DESC")
    fun getHistoryForNotificationSync(notificationId: Long): List<ConversationHistory>
    
    @Transaction
    @Query("""
        SELECT 
            JSON_GROUP_ARRAY(
                JSON_OBJECT(
                    'id', id,
                    'notificationId', notificationId,
                    'message', message,
                    'response', response,
                    'timestamp', timestamp
                )
            ) as conversationJson
        FROM conversation_history
        WHERE notificationId = :notificationId
        GROUP BY notificationId
    """)
    suspend fun getConversationHistoryAsJson(notificationId: Long): String?
    
    @Query("SELECT * FROM conversation_history WHERE notificationId = :notificationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(notificationId: Long, limit: Int): List<ConversationHistory>
    
    @Query("DELETE FROM conversation_history WHERE notificationId = :notificationId")
    suspend fun deleteHistoryForNotification(notificationId: Long)
    
    @Query("SELECT COUNT(*) FROM conversation_history WHERE notificationId = :notificationId")
    suspend fun getHistoryCount(notificationId: Long): Int
}
