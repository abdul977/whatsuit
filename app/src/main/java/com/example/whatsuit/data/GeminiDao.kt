package com.example.whatsuit.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * Data Access Object for Gemini-related database operations.
 * Handles operations for configuration, conversation history, and prompt templates.
 */
@Dao
interface GeminiDao {
    // GeminiConfig operations
    @Query("SELECT * FROM gemini_config WHERE id = 1")
    suspend fun getConfig(): GeminiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: GeminiConfig)

    @Query("DELETE FROM gemini_config")
    suspend fun deleteConfig()

    // Conversation History operations
    @Query("""
        SELECT * FROM conversation_history 
        WHERE notificationId = :notificationId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun getConversationHistory(
        notificationId: Long,
        limit: Int
    ): List<ConversationHistory>

    @Insert
    suspend fun insertConversation(history: ConversationHistory): Long

    @Query("""
        DELETE FROM conversation_history 
        WHERE notificationId = :notificationId 
        AND id NOT IN (
            SELECT id FROM conversation_history 
            WHERE notificationId = :notificationId 
            ORDER BY timestamp DESC 
            LIMIT :keepCount
        )
    """)
    suspend fun pruneConversationHistory(
        notificationId: Long,
        keepCount: Int
    )
    
    @Query("""
        DELETE FROM conversation_history 
        WHERE notificationId = :notificationId 
        AND id NOT IN (
            SELECT id FROM conversation_history 
            WHERE notificationId = :notificationId 
            ORDER BY timestamp DESC 
            LIMIT :keepCount
        )
    """)
    fun pruneConversationHistorySync(
        notificationId: Long,
        keepCount: Int
    )

    @Query("DELETE FROM conversation_history WHERE notificationId = :notificationId")
    suspend fun clearConversationHistory(notificationId: Long)

    // Get recent conversations (last 24 hours) for context
    @Query("""
        SELECT * FROM conversation_history 
        WHERE notificationId = :notificationId 
        AND timestamp >= :cutoffTime
        ORDER BY timestamp DESC
    """)
    suspend fun getRecentConversations(
        notificationId: Long,
        cutoffTime: Long = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
    ): List<ConversationHistory>

    // Prompt Template operations
    @Query("SELECT * FROM prompt_templates WHERE is_active = 1 ORDER BY id ASC LIMIT 1")
    suspend fun getActiveTemplate(): PromptTemplate?

    @Query("SELECT * FROM prompt_templates ORDER BY created_at DESC")
    fun getAllTemplates(): LiveData<List<PromptTemplate>>

    @Insert
    suspend fun insertTemplate(template: PromptTemplate): Long

    @Update
    suspend fun updateTemplate(template: PromptTemplate)

    @Delete
    suspend fun deleteTemplate(template: PromptTemplate)

    @Query("UPDATE prompt_templates SET is_active = 0")
    suspend fun deactivateAllTemplates()

    @Query("UPDATE prompt_templates SET is_active = 1 WHERE id = :templateId")
    suspend fun activateTemplate(templateId: Long)

    @Transaction
    suspend fun setActiveTemplate(templateId: Long) {
        deactivateAllTemplates()
        activateTemplate(templateId)
    }
}