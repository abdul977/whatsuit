package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration to update prompt templates with improved memory instructions
 * - Updates existing prompt templates to better handle remembering user details like names
 */
class Migration4To5 : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Update default prompt template with improved memory instructions
        val enhancedTemplate = """
            System: You are a helpful messaging assistant with excellent memory capabilities. 
            IMPORTANT: You must always remember user details like their name, preferences, and previous topics discussed.
            When a user shares personal information (especially their name), store and recall it consistently in future interactions.
            Respond in a friendly, concise manner (maximum 50 words).
            
            Previous conversation history:
            {context}
            
            User: {message}
            Assistant:
        """.trimIndent()
        
        // Update the template where the name matches the default template
        database.execSQL("""
            UPDATE prompt_templates 
            SET template = ? 
            WHERE name = 'Default Concise Response'
        """, arrayOf(enhancedTemplate))
        
        // Update the template name to reflect its enhanced memory capabilities
        database.execSQL("""
            UPDATE prompt_templates 
            SET name = 'Default Concise Response with Strong Memory' 
            WHERE name = 'Default Concise Response'
        """)
    }
}