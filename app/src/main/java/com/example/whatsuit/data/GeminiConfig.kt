package com.example.whatsuit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class representing Gemini API configuration.
 * Uses a single-row table pattern with id=1 check constraint in the database.
 */
@Entity(tableName = "gemini_config")
data class GeminiConfig(
    @PrimaryKey
    val id: Int = 1,
    
    /**
     * API key for Gemini service
     */
    val apiKey: String,
    
    /**
     * Model name to use (defaults to Gemini 1.5 Flash)
     */
    val modelName: String = "gemini-1.5-flash",
    
    /**
     * Maximum number of conversation history entries to keep per notification thread
     */
    val maxHistoryPerThread: Int = 10,
    
    /**
     * Timestamp of when this configuration was created
     */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Creates a default configuration instance
         */
        fun createDefault(apiKey: String) = GeminiConfig(
            apiKey = apiKey,
            modelName = "gemini-1.5-flash",
            maxHistoryPerThread = 10
        )
    }
}