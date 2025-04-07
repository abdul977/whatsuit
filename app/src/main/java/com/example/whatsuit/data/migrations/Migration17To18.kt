package com.example.whatsuit.data.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from database version 17 to 18.
 * Adds the conversation_prompts table for storing custom prompts per conversation.
 */
class Migration17To18 : Migration(17, 18) {
    companion object {
        private const val TAG = "Migration17To18"
    }

    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            Log.d(TAG, "Starting migration 17 -> 18")
            
            // Create conversation_prompts table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_prompts (
                    conversationId TEXT PRIMARY KEY NOT NULL,
                    promptTemplate TEXT NOT NULL,
                    name TEXT NOT NULL DEFAULT 'Custom Prompt',
                    lastUpdated INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
                )
                """
            )
            
            // Create index for faster lookups
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_conversation_prompts_conversationId
                ON conversation_prompts(conversationId)
                """
            )
            
            Log.d(TAG, "Migration 17 -> 18 completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration 17 -> 18", e)
            throw e
        }
    }
}
