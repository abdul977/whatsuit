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
            name = "Default Concise Response with Strong Memory",
            template = """
                System: You are a helpful messaging assistant with excellent memory capabilities. 
                IMPORTANT: You must always remember user details like their name, preferences, and previous topics discussed.
                When a user shares personal information (especially their name), store and recall it consistently in future interactions.
                Respond in a friendly, concise manner (maximum 50 words).
                
                Previous conversation history:
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