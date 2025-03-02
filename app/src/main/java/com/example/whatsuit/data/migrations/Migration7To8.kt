package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 7 to 8
 * Updates the default prompt template to better define assistant role and context handling
 */
class Migration7To8 : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // First check if the template exists
        val cursor = database.query("SELECT COUNT(*) FROM prompt_templates WHERE name = 'Default Concise Response'")
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()

        val newTemplate = """
            System: You are a helpful messaging assistant that maintains conversation context and remembers user details.
            Respond in a friendly, concise manner (maximum 50 words) while maintaining memory of previous conversations.

            {context}

            User: {message}
            Assistant:
        """.trimIndent()

        if (count > 0) {
            // Update existing template
            database.execSQL("""
                UPDATE prompt_templates 
                SET template = ?
                WHERE name = 'Default Concise Response'
            """, arrayOf(newTemplate))
        } else {
            // Insert new template for fresh installations
            database.execSQL("""
                INSERT INTO prompt_templates (name, template, is_active, created_at)
                VALUES ('Default Concise Response', ?, 1, strftime('%s', 'now') * 1000)
            """, arrayOf(newTemplate))
        }
    }
}
