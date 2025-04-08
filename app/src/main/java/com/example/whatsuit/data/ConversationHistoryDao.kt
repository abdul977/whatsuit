package com.example.whatsuit.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import androidx.annotation.NonNull

/**
 * Data Access Object for handling conversation history database operations
 */
@Dao
interface ConversationHistoryDao {
    @Insert
    suspend fun insert(history: ConversationHistory)
    
    /**
     * Non-suspending version of insert for Java interop
     */
    @Insert
    fun insertSync(history: ConversationHistory)
    
    @Update
    suspend fun update(history: ConversationHistory)
    
    @Query("SELECT * FROM conversation_history WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationHistory?
    
    @Transaction
    @Query("""
        UPDATE conversation_history 
        SET message = :newMessage,
            response = :newResponse,
            timestamp = :timestamp,
            isModified = 1
        WHERE id = :conversationId
        """)
    suspend fun updateConversationContent(
        conversationId: Long,
        newMessage: String,
        newResponse: String,
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Non-suspending version for Java interop
     */
    @Transaction
    @Query("""
        UPDATE conversation_history 
        SET message = :newMessage,
            response = :newResponse,
            timestamp = :timestamp,
            isModified = 1
        WHERE id = :conversationId
        """)
    fun updateConversationContentSync(conversationId: Long, newMessage: String, newResponse: String, timestamp: Long)
    
    /**
     * Gets conversation history for a notification, sorted by timestamp descending
     */
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

    /**
     * Gets all history entries for a given conversation ID
     */
    @Query("SELECT * FROM conversation_history WHERE conversationId = :conversationId ORDER BY timestamp DESC")
    fun getHistoryForConversationSync(conversationId: String): List<ConversationHistory>

    /**
     * Gets the latest history entry for a conversation
     */
    @Query("SELECT * FROM conversation_history WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestHistoryForConversationSync(conversationId: String): ConversationHistory?

    /**
     * Updates the analysis for a conversation history entry
     */
    @Query("""
        UPDATE conversation_history 
        SET analysis = :analysis,
            analysisTimestamp = :timestamp
        WHERE id = :id
    """)
    suspend fun updateAnalysis(id: Long, analysis: String?, timestamp: Long?)
}
