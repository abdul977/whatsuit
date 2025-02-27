package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 2 to 3
 * Adds tables for Gemini API configuration, conversation history, and prompt templates
 */
class Migration2To3 : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create gemini_config table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS gemini_config (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                api_key TEXT NOT NULL,
                model_name TEXT NOT NULL DEFAULT 'gemini-1.5-flash',
                max_history_per_thread INTEGER DEFAULT 10,
                created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
            )
        """.trimIndent())

        // Create conversation_history table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS conversation_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                notification_id INTEGER,
                message TEXT NOT NULL,
                response TEXT NOT NULL,
                timestamp INTEGER DEFAULT (strftime('%s', 'now') * 1000),
                FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Create prompt_templates table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS prompt_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                template TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
            )
        """.trimIndent())

        // Add default prompt template
        database.execSQL("""
            INSERT INTO prompt_templates (name, template)
            VALUES (
                'Default Concise Response',
                'System: Generate a clear and concise response (maximum 50 words) that directly addresses the query.
                Context: Previous conversation - {context}
                User: {message}
                Assistant: Provide a direct response under 50 words.'
            )
        """.trimIndent())
        
        // Create index for conversation history
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_conversation_notification_id ON conversation_history(notification_id)"
        )
    }
}