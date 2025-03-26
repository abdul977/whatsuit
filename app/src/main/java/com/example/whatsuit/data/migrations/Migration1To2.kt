package com.example.whatsuit.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new auto_reply_settings table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `auto_reply_settings` (
                `conversationId` TEXT NOT NULL,
                `isDisabled` INTEGER NOT NULL DEFAULT 0,
                `lastUpdated` INTEGER NOT NULL,
                PRIMARY KEY(`conversationId`)
            )
        """)

        // Migrate existing auto-reply disabled status from notifications table
        database.execSQL("""
            INSERT INTO auto_reply_settings (conversationId, isDisabled, lastUpdated)
            SELECT conversationId, autoReplyDisabled, timestamp 
            FROM notifications 
            WHERE autoReplyDisabled = 1
            GROUP BY conversationId
        """)
    }
}