package com.example.whatsuit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking

/**
 * Data Access Object for handling conversation-specific custom prompts
 */
@Dao
interface ConversationPromptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(prompt: ConversationPrompt)
    
    @Update
    suspend fun update(prompt: ConversationPrompt)
    
    @Delete
    suspend fun delete(prompt: ConversationPrompt)
    
    @Query("SELECT * FROM conversation_prompts WHERE conversationId = :conversationId")
    suspend fun getByConversationId(conversationId: String): ConversationPrompt?
    
    @Query("SELECT * FROM conversation_prompts")
    fun getAllPrompts(): Flow<List<ConversationPrompt>>
    
    @Query("DELETE FROM conversation_prompts WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: String)
    
    // Java-friendly methods for interop
    fun insertBlocking(prompt: ConversationPrompt) = runBlocking {
        insert(prompt)
    }
    
    fun updateBlocking(prompt: ConversationPrompt) = runBlocking {
        update(prompt)
    }
    
    fun deleteBlocking(prompt: ConversationPrompt) = runBlocking {
        delete(prompt)
    }
    
    fun getByConversationIdBlocking(conversationId: String): ConversationPrompt? = runBlocking {
        getByConversationId(conversationId)
    }
    
    fun deleteByConversationIdBlocking(conversationId: String) = runBlocking {
        deleteByConversationId(conversationId)
    }
}
