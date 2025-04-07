package com.example.whatsuit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity class representing a custom prompt for a specific conversation.
 * When present, this prompt will be used instead of the default prompt template.
 */
@Entity(
    tableName = "conversation_prompts",
    indices = [Index(value = ["conversationId"], unique = true)]
)
data class ConversationPrompt(
    /**
     * The conversation ID this prompt is associated with
     */
    @PrimaryKey
    val conversationId: String,
    
    /**
     * The custom prompt template text with placeholders:
     * {context} - Previous conversation history
     * {message} - Current message
     */
    val promptTemplate: String,
    
    /**
     * Name/description of this custom prompt
     */
    val name: String = "Custom Prompt",
    
    /**
     * Timestamp of when this custom prompt was created or last updated
     */
    val lastUpdated: Long = System.currentTimeMillis()
)
