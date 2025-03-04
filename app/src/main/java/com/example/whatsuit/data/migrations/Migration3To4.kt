package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration to add conversation thread ID support
 * - Adds conversationId column if it doesn't exist
 * - Updates existing records with generated thread IDs
 */
class Migration3To4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add conversationId column if it doesn't exist
        db.execSQL("""
            ALTER TABLE notifications 
            ADD COLUMN IF NOT EXISTS conversationId TEXT
        """)

        // Update existing WhatsApp records
        db.execSQL("""
            UPDATE notifications 
            SET conversationId = packageName || '_' || 
                CASE 
                    WHEN packageName LIKE '%whatsapp%' AND title REGEXP '[0-9+]'
                    THEN replace(replace(replace(title, '+', ''), '-', ''), ' ', '')
                    ELSE title
                END
            WHERE conversationId IS NULL
        """)

        // Create index for faster lookups
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS index_notifications_conversationId 
            ON notifications(conversationId)
        """)

        // Create new conversation_history table with updated schema
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversation_history_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                notificationId INTEGER NOT NULL,
                conversationId TEXT NOT NULL,
                message TEXT NOT NULL,
                response TEXT NOT NULL,
                timestamp INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
                isModified INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (notificationId) REFERENCES notifications(id) ON DELETE CASCADE
            )
        """)

        // Copy data from old table to new table
        db.execSQL("""
            INSERT INTO conversation_history_new (
                id, 
                notificationId, 
                conversationId,
                message, 
                response, 
                timestamp,
                isModified
            )
            SELECT 
                ch.id, 
                ch.notificationId,
                COALESCE(n.conversationId, ''),
                ch.message,
                ch.response,
                ch.timestamp,
                0 
            FROM conversation_history ch
            LEFT JOIN notifications n ON ch.notificationId = n.id
        """)

        // Drop old table and rename new table
        db.execSQL("DROP TABLE IF EXISTS conversation_history")
        db.execSQL("ALTER TABLE conversation_history_new RENAME TO conversation_history")

        // Create indices
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversation_history_notificationId ON conversation_history(notificationId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_conversation_history_conversationId ON conversation_history(conversationId)"
        )
    }
}
