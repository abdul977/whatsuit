package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration to add conversation thread ID support
 * - Adds conversationId column if it doesn't exist
 * - Updates existing records with generated thread IDs
 */
class Migration3To4 : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add conversationId column if it doesn't exist
        database.execSQL("""
            ALTER TABLE notifications 
            ADD COLUMN IF NOT EXISTS conversationId TEXT
        """)

        // Update existing WhatsApp records
        database.execSQL("""
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
        database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_notifications_conversationId 
            ON notifications(conversationId)
        """)
    }
}