package com.example.whatsuit.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

/**
 * Entity class representing a prompt template.
 * Stores customizable templates for generating AI responses.
 */
@Entity(tableName = "prompt_templates")
data class PromptTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Name/description of this template
     */
    val name: String,
    
    /**
     * The prompt template text with placeholders:
     * {context} - Previous conversation history
     * {message} - Current message
     */
    val template: String,
    
    /**
     * Whether this template is currently active
     */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    /**
     * Timestamp of when this template was created
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Creates the default concise response template
         */
        fun createDefault() = PromptTemplate(
            name = "Default Concise Response",
            template = """
                System: You are a helpful messaging assistant that maintains conversation context and remembers user details.
                Respond in a friendly, concise manner (maximum 50 words) while maintaining memory of previous conversations.
                
                {context}
                
                User: {message}
                Assistant:
            """.trimIndent()
        )

        /**
         * Processes a template by replacing placeholders with actual content
         */
        fun processTemplate(
            template: String,
            context: String,
            message: String
        ): String {
            return template
                .replace("{context}", context)
                .replace("{message}", message)
        }
    }
}