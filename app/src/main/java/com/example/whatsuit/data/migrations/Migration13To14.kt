package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

class Migration13To14 : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            Log.d(TAG, "Starting migration 13 -> 14")
            
            // Update the template to match the current PromptTemplate definition
            val newTemplate = """
                System: You are a helpful messaging assistant that maintains conversation context and remembers user details.
                Respond in a friendly, concise manner (maximum 50 words) while maintaining memory of previous conversations.
                
                {context}
                
                User: {message}
                Assistant:
            """.trimIndent()
            
            // Update existing template
            database.execSQL(
                "UPDATE prompt_templates SET template = ? WHERE name = 'Default Concise Response'",
                arrayOf(newTemplate)
            )
            
            Log.d(TAG, "Successfully completed migration 13 -> 14")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration 13 -> 14", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "Migration13To14"
    }
}
