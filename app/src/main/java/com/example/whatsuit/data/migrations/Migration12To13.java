package com.example.whatsuit.data.migrations;

import android.util.Log;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Database migration from version 12 to 13.
 * Adds the conversation_reply_count table for tracking auto-reply limits per conversation.
 */
public class Migration12To13 extends Migration {
    private static final String TAG = "Migration12To13";

    public Migration12To13() {
        super(12, 13);
    }

    @Override
    public void migrate(SupportSQLiteDatabase database) {
        try {
            Log.d(TAG, "Starting migration 12 -> 13");

            // Create conversation_reply_count table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS conversation_reply_count (
                    conversationId TEXT PRIMARY KEY NOT NULL,
                    replyCount INTEGER NOT NULL DEFAULT 0,
                    lastReplyTimestamp INTEGER NOT NULL DEFAULT 0,
                    firstReplyTimestamp INTEGER NOT NULL DEFAULT 0
                )
            """);

            // Create index for efficient lookups
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_conversation_reply_count_conversation_id 
                ON conversation_reply_count (conversationId)
            """);

            // Create index for timestamp-based queries (useful for cleanup)
            database.execSQL("""
                CREATE INDEX IF NOT EXISTS idx_conversation_reply_count_last_reply 
                ON conversation_reply_count (lastReplyTimestamp)
            """);

            Log.d(TAG, "Successfully completed migration 12 -> 13");
        } catch (Exception e) {
            Log.e(TAG, "Error during migration 12 -> 13", e);
            throw e;
        }
    }
}
